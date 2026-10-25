package com.carmagotchi.app

import android.content.Context
import android.provider.Settings.Global
import com.carmagotchi.app.data.Settings
import com.carmagotchi.app.domain.*
import com.carmagotchi.app.platform.Arrival
import com.carmagotchi.app.platform.DepartInfo
import com.carmagotchi.app.platform.Demo
import com.carmagotchi.app.platform.ParkedSfx
import com.carmagotchi.app.platform.PetRuntime
import com.carmagotchi.app.platform.PetService
import com.carmagotchi.app.platform.VehicleSignalSource
import com.carmagotchi.app.platform.readHatchInfo
import com.carmagotchi.app.ui.pet.sceneCues
import com.carmagotchi.app.ui.pet.sceneLook
import com.carmagotchi.app.ui.pet.slotCount
import com.carmagotchi.app.ui.pet.spec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * PetRuntime(플랫폼)을 UI 약속(PetController)에 잇는다. 장면(출발·도착)은 앱이 화면에 있고 P일 때만 띄우고,
 * 도착했는데 앱이 안 보이면 "아까 장면"으로 12시간 보관한다(PRD F2 6~7, F3 1·5).
 */
class RuntimePetController private constructor(private val app: Context) : PetController {
    private val rt = PetRuntime.get(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ui = MutableStateFlow(AppUiState())
    override val state: StateFlow<AppUiState> = ui
    private val kst = ZoneId.of("Asia/Seoul")
    private val parkedSfx = ParkedSfx(app, rt, scope)

    // 아래는 lock 안에서만 만진다
    private val lock = Any()
    private var visible = false
    private var scene: Scene? = null
    private var earlier: Pair<Scene, Instant>? = null
    private var seenArrival: String? = null
    private var seenDepart: DepartInfo? = null
    private var departAt: Instant = Instant.EPOCH
    private var departShown = true
    private var bubble: RoomBubble? = null
    private var nonce = 0
    private var sceneJob: Job? = null
    private var bubbleJob: Job? = null
    private var preview = Appearance(0, 0, 0, 0)
    private var previewOdo = 0.0
    @Volatile private var tickAt: Instant = Instant.now()
    @Volatile private var togetherSec = 0

    init {
        scope.launch { merge(rt.pet, rt.arrival, rt.depart, rt.settings, rt.uxRestricted, rt.gear).collect { refresh() } }
        scope.launch {
            while (true) {
                delay(Const.XP_TICK_SEC * 1000L)
                if (rt.pet.value != null) { tickAt = Instant.now(); togetherSec += Const.XP_TICK_SEC }
                rt.update { p -> p?.let { rt.engine.log(rt.engine.tick(it, rt.gear.value != Gear.P)).state } } // 함께 보낸 시간 XP: pet 발행이 refresh를 부른다
            }
        }
        scope.launch { var last: Pair<WorldEnv, Boolean?>? = null; while (true) { delay(2_000); val r = rt.source?.current(); val n = envOf(r) to r?.wiper; if (n != last) { last = n; refresh() } } } // 환경 신호는 바뀔 때만 갱신
        scope.launch { while (true) { delay(20_000); refresh() } } // 시계(now)를 늦게라도 갱신
        scope.launch {
            if (rt.pet.value == null) runCatching { readHatchInfo(app) }.onSuccess {
                synchronized(lock) { preview = rt.engine.hatch("a", it.vinSeedHash, it.odoKm, LocalDate.now()).appearance; previewOdo = it.odoKm }
                refresh()
            }
        }
    }

    /** Activity가 resumed인 동안 true(정차 중 효과음도 이때만 난다). 보이는 순간 서비스를 확인하고(없으면 시작) 못 띄운 출발 인사를 띄운다. */
    fun setVisible(v: Boolean) {
        synchronized(lock) { visible = v }
        parkedSfx.resumed = v
        if (!v) parkedSfx.stop("pause")
        if (v && rt.pet.value != null && rt.source == null) PetService.start(app)
        refresh()
    }

    private fun refresh() = synchronized(lock) {
        val now = rt.clock.instant()
        val pet = rt.pet.value
        val set = rt.settings.value
        val parked = rt.gear.value == Gear.P && !rt.uxRestricted.value
        if (pet == null) { scene = null; earlier = null; seenArrival = null; seenDepart = null; bubble = null }
        else {
            rt.arrival.value?.let { arr ->
                if (arr.trip.tripId != seenArrival) {
                    seenArrival = arr.trip.tripId
                    if (set.arriveCard) arriveScene(arr)?.let { sc -> if (!arr.missed && visible && parked) show(sc) else earlier = sc to now }
                }
            }
            rt.depart.value?.let { d ->
                if (d !== seenDepart) { seenDepart = d; departAt = now; departShown = false }
                if (!departShown && visible && parked && scene == null && Duration.between(departAt, now).toMinutes() < 30) {
                    departShown = true
                    if (set.departGreeting) departScene(d)?.let(::show)
                }
            }
        }
        if (!parked) { scene = null; parkedSfx.stop("gear") } // 주행 규제·D에서는 100ms 안에 걷고 소리도 멈춘다
        if (earlier?.let { Duration.between(it.second, now) > Duration.ofHours(12) } == true) earlier = null
        ui.value = build(pet, set, parked, now)
    }

    private fun show(sc: Scene) {
        scene = sc
        rt.pet.value?.let { p -> parkedSfx.play(sceneCues(sc.kind, sc.visitor != null, sceneLook(p.spec(), p.moodAt(rt.clock.instant()), sc).motion).still(), Origin.AUTO) }
        sceneJob?.cancel()
        sceneJob = scope.launch { delay(30_000); dismissScene() } // P 30초 뒤 방으로
    }

    // ---- 장면 만들기 ----

    private fun eventOf(key: String, default: EventId) = runCatching { EventId.valueOf(key) }.getOrDefault(default)

    private fun cuesOf(pet: PetState, trip: TripSummary?, extra: Set<String> = emptySet()): Set<Cue> = buildSet {
        trip?.let {
            if (it.hardBrakes >= 1) add(Cue.STAR) else if (it.sharpTurns >= 1) add(Cue.TILT)
            if (it.snow) add(Cue.FUR_HAT) else if (it.rain) add(Cue.UMBRELLA)
            if (it.night) add(Cue.STARRY)
            if (it.passengers > 0) add(Cue.CUSHION)
        }
        if (pet.ailments.isNotEmpty()) add(Cue.BANDAGE)
        if (hungry(pet)) add(Cue.EMPTY_BOWL)
        if ("SE-CHUSEOK" in extra || "SE-SEOLLAL" in extra) add(Cue.HANBOK)
        if (pet.serious) add(Cue.CRITICAL)
    }

    private fun arriveScene(a: Arrival): Scene? {
        val pick = rt.selector.arrive(a.step, a.trip) ?: return null
        val s = a.step.state
        rt.update { p -> p?.let { rt.selector.shown(it, pick) } }
        val event = eventOf(pick.line.key, when { pick.line.key == "DEFAULT" -> EventId.TRIP_END; a.trip.rain -> EventId.RAIN; a.trip.night -> EventId.NIGHT; else -> EventId.TRIP_START })
        return Scene(
            SceneKind.ARRIVE, event, pick.text, visitor = a.step.visitor, cues = cuesOf(s, a.trip), hardBrakes = a.trip.hardBrakes,
            picked = a.step.rewards.isNotEmpty(), rewards = a.step.rewards, at = a.trip.endAt,
        )
    }

    private fun departScene(d: DepartInfo): Scene? {
        val s = d.step.state
        val pick = rt.selector.depart(d.step, d.extraEvents, d.extraTags) ?: return null
        rt.update { p -> p?.let { rt.selector.shown(it, pick) } }
        val event = eventOf(pick.line.key, EventId.TRIP_START)
        val chips = departChips(s, d.extraTags)
        val grow = d.step.events.firstOrNull { it.id == EventId.STAGE_UP }
        val from = grow?.detail?.substringBefore(",")?.let { runCatching { Stage.valueOf(it) }.getOrNull() }?.let { Stage.entries.getOrNull(it.ordinal - 1) }
        val names = rt.settings.value.passengerNames.orEmpty()
        return Scene(
            SceneKind.DEPART, event, pick.text, cues = cuesOf(s, null, d.extraTags) + if (EventId.PASSENGER_ON in d.extraEvents) setOf(Cue.CUSHION) else emptySet(),
            chips = chips.take(2), todo = chips.firstOrNull()?.todo,
            greetNames = if (pick.line.key == "PASSENGER_ON" && names.isNotEmpty()) names else null, fromStage = from, heartsFull = "HEARTS_FULL" in d.step.tags, at = rt.clock.instant(),
        )
    }

    private fun sys(id: String, pet: PetState) = rt.lines.firstOrNull { it.id == "DLG-SYS-$id" }?.let { Dialogue.fill(it.text, mapOf("펫이름" to pet.name)) }

    private fun ailChip(pet: PetState, t: AilmentType): Chip {
        val (kind, label) = when (t) {
            AilmentType.TPMS -> ChipKind.TPMS to "발이 시려"
            AilmentType.WASHER -> ChipKind.WASHER to "눈이 따가워"
            AilmentType.OIL -> ChipKind.OIL to "기운이 없어"
            else -> ChipKind.WARNING to "몸이 안 좋아"
        }
        return Chip(kind, label, sys("AIL-${t.name}", pet))
    }

    private fun isEv(p: PetState) = p.energyProfile == EnergyProfile.EV || (p.signalProfile == SignalProfile.EMULATOR && p.lastFuelPct == null && p.lastSoc != null)

    private fun hungry(p: PetState) = hungerOf(if (isEv(p)) p.lastSoc else p.lastFuelPct, isEv(p)) == Hunger.HUNGRY

    private fun hungerChip(p: PetState) = Chip(ChipKind.HUNGER, "배고파", sys(if (isEv(p)) "HUNGRY-EV" else "HUNGRY-FUEL", p))

    private fun departChips(s: PetState, tags: Set<String>): List<Chip> = buildList {
        s.ailments.map { it.type }.firstOrNull { it.severity >= Severity.SERIOUS }?.let { add(ailChip(s, it)) }
        tags.firstOrNull { it.startsWith("NOTICE:") }?.let { t -> add(ailChip(s, AilmentType.valueOf(t.removePrefix("NOTICE:")))) }
        if ("NOTICE_HUNGER" in tags) add(hungerChip(s))
    }

    private fun roomChips(p: PetState): List<Chip> =
        p.ailments.sortedByDescending { it.type.severity }.map { ailChip(p, it.type) } + listOfNotNull(if (hungry(p)) hungerChip(p) else null)

    // ---- 앨범 ----

    private fun firstTitle(kind: String) = when (kind) {
        "FIRST_RAIN" -> "첫 비" to EventId.RAIN
        "FIRST_SNOW" -> "첫눈" to EventId.RAIN
        "FIRST_NIGHT" -> "첫 밤길" to EventId.NIGHT
        "FIRST_PASSENGER" -> "첫 동승" to EventId.PASSENGER_ON
        "FIRST_SEA" -> "첫 바다" to EventId.NEW_PLACE
        "FIRST_MOUNTAIN" -> "첫 산" to EventId.NEW_PLACE
        "FIRST_REST_AREA", "FIRST_REST" -> "첫 휴식" to EventId.REST_STOP
        "FIRST_MEAL" -> "첫 밥" to EventId.REFUEL
        "FIRST_RECOVERY" -> "첫 회복" to EventId.WARN_CLEARED
        else -> null
    }

    private fun memoryOf(p: PetState, e: MemoryEntry): Memory? {
        val id = "${e.at.toEpochMilli()}-${e.kind}-${e.ref}"
        fun m(title: String, ev: EventId, v: String? = null, with: String? = null, place: String? = null) = Memory(id, title, e.at, ev, v, with, place)
        val vis = e.ref?.let { r -> Content.VISITORS.firstOrNull { it.id == r } }
        return when (e.kind) {
            "BIRTH" -> m("${Dialogue.fill("{펫이름|이/가}", mapOf("펫이름" to p.name))} 태어난 날", EventId.TRIP_START)
            "VISITOR" -> vis?.let { m(Dialogue.fill("{n|를/을} 만났어", mapOf("n" to it.name.substringAfter(' ')))!!, EventId.TRIP_START, v = it.id) }
            "NEW_PLACE" -> m("${e.ref ?: "새 장소"}에 처음 갔어", EventId.NEW_PLACE, place = e.ref)
            "MILESTONE" -> m("함께 ${e.ref}km 왔어", EventId.MILESTONE_KM)
            "COMPANION" -> e.ref?.split(":")?.takeIf { it.size == 2 }?.let { (n, c) -> m("${Dialogue.fill("{n|랑/이랑}", mapOf("n" to n))} ${c}번째 나들이", EventId.PASSENGER_ON, with = n) }
            "STAGE" -> m("${when (e.ref) { "BABY" -> "아기"; "CHILD" -> "꼬마"; "ADULT" -> "어른"; "BUDDY" -> "단짝"; else -> "새 모습" }}가 된 날", EventId.STAGE_UP)
            "BIRTHDAY" -> m("${e.ref}번째 생일", EventId.BIRTHDAY)
            "SEASON" -> m("계절 나들이", EventId.SEASON_EVENT)
            else -> firstTitle(e.kind)?.let { (t, ev) -> m(t, ev) }
        }
    }

    /** 가장 최근 일요일 17시를 끝으로 하는 일주일의 장면(최대 4). 부화 뒤 첫 일요일 17시 전이면 null. */
    private fun weekly(p: PetState, now: Instant, demo: Boolean): List<Memory>? {
        var t = now.atZone(kst).withMinute(0).withSecond(0).withNano(0).withHour(17)
        while (t.dayOfWeek != DayOfWeek.SUNDAY || t.toInstant().isAfter(now)) t = t.minusDays(1)
        val end = t.toInstant()
        if (!demo && p.hatchedAt.isAfter(end)) return null
        val rank = { k: String -> if (k.startsWith("FIRST")) 0 else if (k == "VISITOR") 1 else if (k == "NEW_PLACE") 2 else if (k == "COMPANION") 4 else 3 }
        return p.memory.filter { it.kind != "BIRTH" && (demo || it.at.isBefore(end)) && it.at.isAfter(end.minus(Duration.ofDays(7))) }
            .sortedWith(compareBy({ rank(it.kind) }, { -it.at.toEpochMilli() })).mapNotNull { memoryOf(p, it) }.take(4)
    }

    // ---- 상태 조립 ----

    private fun envOf(r: VehicleSignalSource.Raw?) = WorldEnv(r?.tempC, r?.windows?.values?.maxOrNull()?.toDouble() ?: 0.0, r?.lowBeam == true, r?.fog == true, listOfNotNull(r?.soc, r?.fuel).maxOrNull()) // 에너지: PHEV는 둘 중 남은 쪽

    private fun build(pet: PetState?, set: Settings, parked: Boolean, now: Instant): AppUiState {
        val src = rt.source?.current()
        return AppUiState(
            pet = pet, scene = scene, pendingEarlierScene = earlier?.first, bubble = bubble, isParked = parked,
            reduceMotion = Global.getFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f) == 0f,
            demoMode = set.demoMode,
            settings = AppSettings(
                arriveCard = set.arriveCard, departGreeting = set.departGreeting, driveSounds = !set.driveSoundsOff, parkedSounds = !set.sfxOff, quietToday = now.toEpochMilli() < set.quietUntilMs,
                guestMode = pet?.guestModeUntil?.isAfter(now) == true, locationOn = pet?.locationOn ?: false,
                gleoPresent = rt.lastGleo.value != null,
            ),
            passengerNames = set.passengerNames.orEmpty(), placed = set.placed.orEmpty(), now = now,
            odometerKm = if (pet == null) previewOdo else src?.odo?.takeIf { it > 0 } ?: pet.trips.lastOrNull()?.endOdoKm ?: pet.odoAtHatchKm,
            rain = src?.wiper == true, env = envOf(src),
            roomChips = pet?.let(::roomChips).orEmpty(),
            memories = pet?.memory?.mapNotNull { memoryOf(pet, it) }?.asReversed().orEmpty(),
            weekly = pet?.let { weekly(it, now, set.demoMode) },
            hatchPreview = preview, xpLog = rt.engine.xpLog.value,
            tickAt = tickAt, tickDriving = rt.gear.value != Gear.P, togetherSec = togetherSec,
        )
    }

    // ---- PetController ----

    override fun hatch(name: String, locationOn: Boolean, carBirthday: LocalDate) {
        scope.launch { runCatching { rt.hatch(name, locationOn, carBirthday) } }
    }

    private val spam = PetSpam()

    /** 연타로 귀찮아함: 소리·애정·하트 없이 얼굴과 말풍선만. 처음 걸릴 때만 기분 -5. */
    private fun annoyed(pet: PetState, first: Boolean) {
        if (first) rt.update { p -> p?.let { rt.engine.annoy(it) } }
        val text = rt.lines.firstOrNull { it.id == "DLG-ROOM-PET-ANNOY-01" }?.text
            ?: if (pet.stage == Stage.EGG) "부르르. / 해설: 간지러워서 싫은가 봐요." else "그만~ 너무 간지러워!" // 대사 ID가 문서에 생기면 거기서 읽는다
        synchronized(lock) {
            bubble = RoomBubble(text, null, ++nonce, annoyed = true)
            bubbleJob?.cancel(); bubbleJob = scope.launch { delay(6_000); synchronized(lock) { bubble = null }; refresh() }
        }
        refresh()
    }

    override fun interact(action: Interaction) {
        val pet = rt.pet.value ?: return
        if (action == Interaction.PET) {
            val r = spam.hit(rt.clock.instant().toEpochMilli())
            if (r != PetSpam.Result.NONE) { annoyed(pet, r == PetSpam.Result.TRIGGER); return }
        }
        val kind = if (action == Interaction.PET) Bond.PET else Bond.SNACK
        val next = rt.engine.bond(pet, kind).state
        val tags = rt.selector.baseTags(pet).toMutableSet()
        val key: String
        var reaction: Interaction? = action
        if (action == Interaction.PET) { key = "PET"; if (next.moodUpdatedAt == pet.moodUpdatedAt) tags += "PET_CAP" }
        else {
            key = "SNACK"
            if (pet.snacks == 0) { tags += "NO_SNACK"; reaction = null }
            else if (next.snacks == pet.snacks) { tags += "SNACK_CAP"; reaction = null }
        }
        val text = rt.selector.pick(pet, "ROOM", key, tags, mapOf("펫이름" to pet.name), "$key${rt.clock.instant()}")?.text ?: return
        if (reaction != null) rt.update { p -> p?.let { rt.engine.log(rt.engine.bond(it, kind)).state } }
        // MO-PET 시작에 SND-PET, MO-SNACK은 입이 움직이는 500ms부터 한 입(300ms 간격) 2번
        when (reaction) {
            Interaction.PET -> parkedSfx.play(listOf(SfxCue(0, DriveSound.PET)), Origin.TAP)
            Interaction.SNACK -> parkedSfx.play(List(2) { SfxCue(500 + 300 * it, DriveSound.MUNCH, it) }.still(), Origin.TAP)
            null -> {}
        }
        synchronized(lock) {
            bubble = RoomBubble(text, reaction, ++nonce, hint = if ("NO_SNACK" in tags) sys("SNACK-HINT", pet) else null)
            bubbleJob?.cancel(); bubbleJob = scope.launch { delay(6_000); synchronized(lock) { bubble = null }; refresh() }
        }
        refresh()
    }

    override fun roam(screens: Float) { rt.update { p -> p?.let { rt.engine.log(rt.engine.roam(it, screens)).state } } }

    override fun placeItem(itemId: String) {
        val pet = rt.pet.value ?: return
        rt.updateSettings { s ->
            val cur = s.placed.orEmpty()
            s.copy(placed = if (itemId in cur) cur - itemId else if (cur.size < slotCount(pet.stage)) cur + itemId else cur)
        }
    }

    override fun dismissScene() { synchronized(lock) { scene = null; sceneJob?.cancel() }; refresh() }
    override fun ackSnackPop() { if (rt.pet.value?.snackPop != 0) rt.update { it?.copy(snackPop = 0) } }
    override fun ackEarlierScene() { synchronized(lock) { earlier = null }; refresh() }

    override fun chooseCompanion(name: String?) { rt.setCompanion(name); dismissScene() }

    override fun renamePet(name: String) { val n = name.trim().take(6); if (n.isNotEmpty()) rt.update { it?.copy(name = n) } }

    override fun setPassengerNames(names: List<String>) {
        val new = names.map { it.trim().take(6) }.filter { it.isNotEmpty() }.distinct().take(6)
        val removed = rt.settings.value.passengerNames.orEmpty() - new.toSet()
        rt.updateSettings { it.copy(passengerNames = new) }
        // 지운 이름이 붙은 동승 기억은 "친구"로 돌린다(F13)
        if (removed.isNotEmpty()) rt.update { p ->
            p?.copy(
                companions = p.companions - removed.toSet(),
                trips = p.trips.map { if (it.companionName in removed) it.copy(companionName = null) else it },
                pendingTrip = p.pendingTrip?.let { if (it.companionName in removed) it.copy(companionName = null) else it },
            )
        }
    }

    override fun updateSettings(settings: AppSettings) {
        val cur = ui.value.settings
        val quietUntil = if (settings.quietToday == cur.quietToday) null else if (settings.quietToday) rt.clock.instant().atZone(kst).let { n -> n.toLocalDate().plusDays(if (n.hour >= Const.DAY_BOUNDARY_HOUR) 1 else 0).atTime(Const.DAY_BOUNDARY_HOUR, 0).atZone(kst).toInstant().toEpochMilli() } else 0L
        rt.updateSettings { it.copy(arriveCard = settings.arriveCard, departGreeting = settings.departGreeting, driveSoundsOff = !settings.driveSounds, sfxOff = !settings.parkedSounds, quietUntilMs = quietUntil ?: it.quietUntilMs) }
        if (!settings.parkedSounds || settings.quietToday) parkedSfx.stop("off")
        if (settings.locationOn != cur.locationOn) rt.update { it?.copy(locationOn = settings.locationOn) }
        if (settings.guestMode != cur.guestMode) rt.update { p -> p?.copy(guestModeUntil = if (settings.guestMode) rt.clock.instant().plus(Duration.ofHours(Const.GUEST_MODE_H.toLong())) else null) }
    }

    override fun clearPlaces() { rt.update { it?.copy(places = emptyMap()) } }
    override fun deleteAllData() { rt.deleteAll() }
    override fun setDemoMode(on: Boolean) { rt.updateSettings { it.copy(demoMode = on) } }
    override fun setLook(look: CarLook) { rt.update { it?.copy(look = look.clamped()) } }
    override fun resetLookToVin() { rt.update { it?.copy(look = null) } }
    override fun sfx(sound: DriveSound, origin: Origin) = parkedSfx.play(listOf(SfxCue(0, sound)), origin)
    override fun runDemo(script: String) { if (script == "levelup") rt.update { p -> p?.let { rt.engine.log(rt.engine.forceStage(it)).state } } else if (script.startsWith("sound:")) Demo.playSound(app, script.removePrefix("sound:")) else Demo.runScript(app, script) }

    /** 움직임 줄이기(모션 대신 정지 프레임)에서는 그 모션의 소리를 첫 변형으로 1번만, 바로 낸다(F15 9번). */
    private fun List<SfxCue>.still() = if (state.value.reduceMotion) take(1).map { it.copy(atMs = 0) } else this

    companion object {
        @Volatile private var inst: RuntimePetController? = null
        fun get(ctx: Context): RuntimePetController = inst ?: synchronized(this) { inst ?: RuntimePetController(ctx.applicationContext).also { inst = it } }
    }
}
