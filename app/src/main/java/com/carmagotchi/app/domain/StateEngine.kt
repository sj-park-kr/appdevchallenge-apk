package com.carmagotchi.app.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.floor

/**
 * 결과 한 건. tags는 대사 고르기에 쓰는 사실(FIRST:RAIN, VISITOR:VIS-01, REVISIT, SE-MONSOON, MEM:SAMEPLACE,
 * NEW_PROVINCE 등). 엔진은 순수 함수라 결과를 저장하지 않고 버리면 그대로 "미리보기"(도착 카드용)다.
 */
data class Step(
    val state: PetState,
    val events: List<Event> = emptyList(),
    val rewards: List<String> = emptyList(),
    val tags: Set<String> = emptySet(),
    val visitor: String? = null,
    val xp: XpEntry? = null, // 이 단계에서 얻은 XP와 이유(기록용)
)

enum class XpKind { PET, SNACK, THANKS, ROAM, CARE, TIME, DRIVE, LEVELUP }
data class XpEntry(val kind: XpKind, val n: Int)

enum class Bond { PET, SNACK, THANKS }

/** 시계를 주입받는 결정적 상태 엔진(PRD 7). 같은 상태·입력·시각이면 같은 결과. */
class StateEngine(private val clock: Clock, private val zone: ZoneId = ZoneId.of("Asia/Seoul")) {

    /** 최근 10개 XP 기록(메모리뿐, 저장 안 함). 실제로 반영하는 호출만 log(step)로 넣는다. 같은 종류가 연속이면 합산. */
    val xpLog = kotlinx.coroutines.flow.MutableStateFlow(emptyList<XpEntry>())
    fun log(step: Step): Step {
        val x = step.xp ?: return step
        if (x.kind == XpKind.TIME) return step // 켜 두는 시간 XP는 전용 줄에서 보여 주므로 기록에는 넣지 않는다
        xpLog.value = xpLog.value.let { l -> if (l.firstOrNull()?.kind == x.kind) listOf(XpEntry(x.kind, l[0].n + x.n)) + l.drop(1) else listOf(x) + l }.take(10)
        return step
    }

    private fun later(a: LocalDate, b: LocalDate?) = if (b != null && b.isAfter(a)) b else a

    private inner class Work(var s: PetState, val now: Instant, val day: LocalDate) {
        val events = mutableListOf<Event>()
        val rewards = mutableListOf<String>()
        val tags = mutableSetOf<String>()
        var visitor: String? = null
        var xp: XpEntry? = null

        fun step() = Step(s, events, rewards, tags, visitor, xp)
        fun ev(id: EventId, detail: String? = null) { events += Event(id, now, detail) }

        /** 7.9 보상 원장. 이미 있으면 false. */
        fun once(key: String, d: String = day.toString()): Boolean {
            val k = "$d|$key"
            if (k in s.ledger) return false
            s = s.copy(ledger = s.ledger + k)
            return true
        }
        fun ever(key: String) = once(key, "EVER")

        /** "하루 N번"은 순번을 붙인 키로. */
        fun cap(key: String, max: Int) = (1..max).any { once("$key:$it") }

        fun mood(delta: Int) {
            s = s.copy(mood = (s.moodAt(now) + delta).coerceIn(Const.MOOD_MIN, Const.MOOD_MAX), moodUpdatedAt = now)
        }
        fun moodAtLeast(v: Int) { s = s.copy(mood = maxOf(s.moodAt(now), v), moodUpdatedAt = now) }
        fun moodCapped(key: String, max: Int, delta: Int) { if (cap(key, max)) mood(delta) }

        fun affect(src: String) {
            if (s.ledger.count { it.startsWith("$day|AFF:") } >= Const.AFFECTION_DAILY_CAP || !once("AFF:$src")) return
            s = if (s.affection < 100) s.copy(affection = s.affection + 1) else s.copy(affectionOverflow = s.affectionOverflow + 1)
        }

        fun album(kind: String, ref: String? = null) {
            if (cap("ALBUM", Const.ALBUM_DAILY_MAX)) s = s.copy(memory = s.memory + MemoryEntry(now, kind, ref))
        }

        fun give(id: String) {
            if (id !in s.items) s = s.copy(items = s.items + (id to now))
            rewards += id
        }
        fun item(id: String): Boolean { if (!ever("ITEM:$id")) return false; give(id); return true }
        fun seasonItem(id: String, year: Int): Boolean {
            if (!ever("ITEM:$id:$year")) return false
            if (id !in s.items) give(id)
            album("SEASON", id)
            return true
        }
        fun snack(key: String) {
            if (s.snacks < Const.SNACK_MAX) s = s.copy(snacks = s.snacks + 1) // 가득 차면 버린다
            rewards += "SNACK:$key"
        }
        fun first(flag: String, item: String?) {
            if (!ever("FIRST:$flag")) return
            tags += "FIRST:$flag"
            item?.let { item(it) }
            album("FIRST_$flag")
        }
    }

    fun hatch(name: String, vinSeedHash: String, odoKm: Double, carBirthday: LocalDate, energyProfile: EnergyProfile = EnergyProfile.FUEL): PetState {
        require(name.length in 1..6)
        val now = clock.instant()
        fun r(p: String, n: Int) = floor(seededRandom(vinSeedHash, "appearance", p) * n).toInt()
        return PetState(
            petId = UUID.nameUUIDFromBytes("$vinSeedHash:${now.toEpochMilli()}".toByteArray()).toString(),
            name = name, vinSeedHash = vinSeedHash,
            appearance = Appearance(r("palette", 8), r("pattern", 6), r("eyes", 4), r("quirk", 4)),
            hatchedAt = now, carBirthday = carBirthday, odoAtHatchKm = odoKm,
            moodUpdatedAt = now, energyProfile = energyProfile,
            memory = listOf(MemoryEntry(now, "BIRTH")),
        )
    }

    /** 7.13 주행 확정. 손님 모드면 버리고, 짧은 이동이면 기록만 한다. */
    fun confirmTrip(state: PetState, trip: TripSummary): Step {
        val now = clock.instant()
        if (state.guestModeUntil?.isAfter(now) == true) return Step(state.copy(pendingTrip = null))
        val recorded = state.copy(trips = (state.trips + if (state.locationOn) trip else trip.copy(place = null)).takeLast(Const.TRIPS_KEPT), pendingTrip = null)
        if (trip.micro) return Step(recorded)

        // 시계가 뒤로 가도 하루 키는 줄지 않는다.
        val day = later(later(dayKey(trip.endAt, zone), state.lastCountedDayKey), state.lastStartDayKey)
        val w = Work(recorded, now, day)
        val quiet = state.crashQuiet(now) // 사고 모드: 보상·길동무·앨범 정지
        val s0 = state

        // 3. 주행 이벤트
        if (trip.hardBrakes > 0) w.ev(EventId.HARD_BRAKE, trip.hardBrakes.toString())
        if (trip.sharpTurns > 0) w.ev(EventId.SHARP_TURN, trip.sharpTurns.toString())
        if (trip.smooth) w.ev(EventId.SMOOTH_TRIP)
        if (trip.rain || trip.fog) w.ev(EventId.RAIN, if (trip.snow) "SNOW" else if (trip.rain) "RAIN" else "FOG")
        if (trip.night) w.ev(EventId.NIGHT)
        if (trip.passengers > 0) w.ev(EventId.PASSENGER_ON)
        val place = if (s0.locationOn) trip.place else null
        val newPlace = place != null && place.label !in s0.places
        if (newPlace) w.ev(EventId.NEW_PLACE, place!!.label)
        val milestone = floor((trip.endOdoKm - s0.odoAtHatchKm) / Const.MILESTONE_KM).toInt()
        if (milestone > s0.milestoneIndex) w.ev(EventId.MILESTONE_KM, (milestone * Const.MILESTONE_KM).toString())

        // 4. 함께한 날, 오늘의 줍기
        val last = s0.lastCountedDayKey
        if (trip.counted && (last == null || day.isAfter(last))) {
            w.s = w.s.copy(daysTogether = w.s.daysTogether + 1, lastCountedDayKey = day)
            if (!quiet) {
                w.affect("DRIVE_DAY")
                if (w.once("FIND")) {
                    val pick = Content.FIND_ITEMS.filter { it !in w.s.items }
                        .minByOrNull { seededRandom(s0.vinSeedHash, "$day:$it", "FIND") }
                    if (pick != null) { w.ever("ITEM:$pick"); w.give(pick) } else w.snack("FIND")
                }
            }
        }

        // 장소 기록과 기억 태그(저장 전 값 기준)
        if (place != null) {
            val old = s0.places[place.label]
            if (old != null && old.count >= 2 && !quiet) w.tags += "MEM:SAMEPLACE"
            val kind = if (s0.places.isEmpty() && place.kind == PlaceKind.REGION) PlaceKind.HOME_REGION else place.kind
            w.s = w.s.copy(places = w.s.places + (place.label to (old?.copy(count = old.count + 1)
                ?: PlaceRecord(place.label, kind, place.regionKey, place.provinceKey, now, 1))))
        }

        if (!quiet) {
            // 5. 기분 (7.2): 감소는 종류별 상한 뒤 합계 상한, 그다음 증가
            val rear = trip.rearPassenger
            val hb = maxOf(-(if (rear) 9 else 6) * trip.hardBrakes, if (rear) -27 else -18)
            val st = maxOf(-3 * trip.sharpTurns, -9)
            val neg = maxOf(hb + st, if (rear) -30 else -24)
            var pos = 0
            if (trip.smooth) pos += 8
            if (trip.passengers > 0) pos += 5
            if (newPlace && w.cap("MOOD:NEWPLACE", 2)) pos += 5
            w.s = w.s.copy(mood = (w.s.moodAt(now) + neg + pos).coerceIn(Const.MOOD_MIN, Const.MOOD_MAX), moodUpdatedAt = now)

            // 7. 처음 있는 일, 물건, 스탬프
            if (trip.rain) w.first("RAIN", "ITEM-01")
            if (trip.snow) w.first("SNOW", null)
            if (trip.night) w.first("NIGHT", "ITEM-02")
            if (trip.passengers > 0) w.first("PASSENGER", "ITEM-05")
            if (place != null) {
                val stamped = w.s.places[place.label]!!.kind
                if (newPlace) w.album("NEW_PLACE", place.label)
                when (stamped) {
                    PlaceKind.SEA -> w.first("SEA", "ITEM-06")
                    PlaceKind.MOUNTAIN -> w.first("MOUNTAIN", "ITEM-07")
                    PlaceKind.REST_AREA -> w.first("REST_AREA", "ITEM-08")
                    else -> {}
                }
                if (s0.places.values.none { it.provinceKey == place.provinceKey } && w.ever("SNACK:REGION:${place.provinceKey}")) {
                    w.snack("REGION:${place.provinceKey}")
                    w.tags += "NEW_PROVINCE"
                }
            }
            if (milestone > s0.milestoneIndex) {
                w.item("ITEM-09")
                w.album("MILESTONE", (milestone * Const.MILESTONE_KM).toString())
            }
            seasonConditions(w, trip, day)

            // 7. 동승 기억: 이름은 사용자가 칩을 눌렀을 때만 온다
            trip.companionName?.takeIf { trip.passengers > 0 }?.let { name ->
                val old = s0.companions[name]
                val n = (old?.rideCount ?: 0) + 1
                w.s = w.s.copy(companions = w.s.companions + (name to CompanionRecord(name, old?.firstRideAt ?: now, n)))
                if (old != null) w.tags += "MEM:PAX_AGAIN"
                if (n == 10 || n == 50) w.tags += "MEM:PAX_COUNT_$n"
                if (n == 1 || n == 10 || n == 50) w.album("COMPANION", "$name:$n")
            }

            // 6. 길동무
            meet(w, trip.tripId, place?.label, VisitCtx(
                day = day, startHour = trip.startHour,
                rain = trip.rain, snow = trip.snow, fog = trip.fog, night = trip.night, breeze = trip.breeze,
                lowBeam = trip.lowBeamSec > 0, passengers = trip.passengers, newPlace = newPlace,
                placeKind = place?.let { w.s.places[it.label]?.kind }, locationOn = s0.locationOn,
                windowSupported = trip.windowSupported, fogSupported = trip.fogSupported, tempSupported = trip.tempSupported,
            ), place?.let { s0.places[it.label]?.visitors } ?: emptySet())
        }

        // 8, 9. 갈래, 휴식 누적, 마지막 주행, 기념 번호
        w.s = w.s.copy(
            traits = recomputeTraits(w.s.trips.filter { it.counted }.takeLast(Const.TRAIT_WINDOW), w.s.traits, w.s.locationOn),
            driveSinceRestMin = w.s.driveSinceRestMin + trip.driveSec / 60,
            lastTripEndAt = trip.endAt,
            milestoneIndex = maxOf(w.s.milestoneIndex, milestone),
        )
        return w.step()
    }

    private fun seasonConditions(w: Work, trip: TripSummary, day: LocalDate) {
        if ("FIRST:RAIN" in w.tags) w.ev(EventId.SEASON_EVENT, "SE-FIRST-RAIN")
        fun season(id: String, item: String, year: Int) {
            if (w.seasonItem(item, year)) { w.ev(EventId.SEASON_EVENT, id); w.tags += id }
        }
        if (trip.rain && Seasons.monsoon(day)) season("SE-MONSOON", "ITEM-12", day.year)
        if (trip.counted && Seasons.cherry(day)) season("SE-CHERRY", "ITEM-11", day.year)
        if (trip.counted && Seasons.autumn(day)) season("SE-AUTUMN", "ITEM-13", day.year)
        if (trip.snow && Seasons.winter(day)) season("SE-FIRST-SNOW", "ITEM-14", if (day.monthValue >= 11) day.year else day.year - 1)
    }

    /** F6: 첫 만남 보장(희귀도 높은 순, ID 순), 다 만났으면 50% 재방문. 하루 2번, 한 주행 1마리. */
    private fun meet(w: Work, tripKey: String, placeLabel: String?, ctx: VisitCtx, metHere: Set<String>) {
        val used = (1..Const.VISITOR_DAILY_MAX).count { "${w.day}|VISIT:$it" in w.s.ledger }
        if (used >= Const.VISITOR_DAILY_MAX) return // PRD 확인 필요: 첫 만남 보장과 하루 2번 상한이 부딪히면 상한이 이긴다
        val cands = Content.VISITORS.filter { it.cond(ctx) }
        val fresh = cands.filter { it.id !in w.s.visitors }
            .sortedWith(compareByDescending<Visitor> { it.rarity }.thenBy { it.id })
        val vin = w.s.vinSeedHash
        val pick = fresh.firstOrNull()
            ?: if (cands.isNotEmpty() && seededRandom(vin, tripKey, "VISIT") < Const.VISITOR_REVISIT_P)
                cands[floor(seededRandom(vin, tripKey, "VISIT_PICK") * cands.size).toInt()]
            else return
        w.cap("VISIT", Const.VISITOR_DAILY_MAX)
        val old = w.s.visitors[pick.id]
        w.s = w.s.copy(visitors = w.s.visitors + (pick.id to VisitorRecord(old?.firstMetAt ?: w.now, w.now, (old?.count ?: 0) + 1)))
        if (placeLabel != null && w.s.places[placeLabel] != null) {
            val p = w.s.places[placeLabel]!!
            w.s = w.s.copy(places = w.s.places + (placeLabel to p.copy(visitors = p.visitors + pick.id)))
        }
        w.visitor = pick.id
        if (old == null) {
            w.tags += "FIRST:VISITOR"
            w.tags += "VISITOR:${pick.id}"
            w.album("VISITOR", pick.id)
        } else {
            w.tags += "REVISIT"
            if (pick.id in metHere) w.tags += "MEM:VISITOR_${pick.id}"
        }
        w.moodCapped("MOOD:VISIT", Const.VISITOR_DAILY_MAX, 4)
    }

    /** 7.5 갈래. 창은 최근 인정 주행. 처음 정하는 기준과 바뀌는 기준(히스테리시스)이 다르다. */
    fun recomputeTraits(window: List<TripSummary>, old: Traits, locationOn: Boolean = true): Traits {
        if (window.size < Const.TRAIT_MIN_TRIPS) return Traits()
        val ten = window.filter { it.driveSec >= Const.SMOOTH_MIN_SEC }
        val temperament = if (ten.size < 5) old.temperament else {
            val r = ten.count { it.smooth } / ten.size.toDouble()
            when (old.temperament) {
                Temperament.UNDECIDED -> if (r >= 0.6) Temperament.CALM else Temperament.LIVELY
                Temperament.CALM -> if (r < 0.5) Temperament.LIVELY else Temperament.CALM
                Temperament.LIVELY -> if (r >= 0.7) Temperament.CALM else Temperament.LIVELY
            }
        }
        val longR = window.count { it.long } / window.size.toDouble()
        val regions = if (locationOn) window.mapNotNull { it.place?.regionKey }.toSet().size else 0
        val roamer = when (old.roamer) {
            Roamer.UNDECIDED -> if (longR >= 0.25 || regions >= 6) Roamer.EXPLORER else Roamer.TOWN
            Roamer.TOWN -> if (longR >= 0.3 || regions >= 7) Roamer.EXPLORER else Roamer.TOWN
            Roamer.EXPLORER -> if (longR < 0.15 && regions <= 4) Roamer.TOWN else Roamer.EXPLORER
        }
        val paxR = window.count { it.passengers > 0 } / window.size.toDouble()
        val social = when (old.social) {
            Social.UNDECIDED -> if (paxR >= 0.3) Social.FRIENDLY else Social.SOLO
            Social.SOLO -> if (paxR >= 0.4) Social.FRIENDLY else Social.SOLO
            Social.FRIENDLY -> if (paxR < 0.2) Social.SOLO else Social.FRIENDLY
        }
        return Traits(temperament, roamer, social)
    }

    /** 단계를 올린다(내려가지 않음). 시동 때의 날 수 성장과 XP 성장이 같이 쓴다. */
    private fun promote(w: Work, target: Stage) {
        val state = w.s
        if (target <= state.stage) return
        val t = state.traits
        val look = when {
            state.stage >= Stage.ADULT -> state.lockedLook
            target >= Stage.ADULT -> t
            target == Stage.CHILD -> Traits(temperament = t.temperament)
            else -> state.lockedLook
        }
        // 하트 가득 성장(F4 11번): 떠나는 단계의 목표를 채웠으면 특별판. 한 번에 두 단계 올라도 떠나는 단계 기준
        val total = state.affection + state.affectionOverflow
        if (total - state.heartBase >= Const.heartGoal(state.stage)) w.tags += "HEARTS_FULL"
        w.s = state.copy(stage = target, lockedLook = look, heartBase = total)
        val passed = Stage.values().filter { it > state.stage && it <= target }
        w.ev(EventId.STAGE_UP, passed.joinToString(",")) // 한 번에 여러 단계면 쉼표로
        w.moodAtLeast(85)
        passed.forEach { w.album("STAGE", it.name) }
    }

    private fun gain(w: Work, n: Int, why: XpKind) {
        w.xp = XpEntry(why, n)
        w.s = w.s.copy(xp = w.s.xp + n)
        promote(w, Const.stageOfXp(w.s.xp))
    }

    /** 함께 보낸 시간 XP(앱을 켜 둔 동안, 주행 중이면 더 많이). */
    fun tick(state: PetState, driving: Boolean): Step {
        val now = clock.instant()
        val w = Work(state, now, later(dayKey(now, zone), state.lastStartDayKey))
        if (driving) gain(w, Const.XP_TICK_DRIVE, XpKind.DRIVE) else gain(w, Const.XP_TICK_OPEN, XpKind.TIME)
        return w.step()
    }

    /** 목업용: 다음 단계로 바로 올린다. BUDDY면 알로 되돌린다. */
    fun forceStage(state: PetState): Step {
        val now = clock.instant()
        val w = Work(state, now, later(dayKey(now, zone), state.lastStartDayKey))
        val next = Stage.values().getOrNull(state.stage.ordinal + 1) ?: Stage.EGG
        val days = listOf(0, Const.BABY_DAYS, Const.CHILD_DAYS, Const.ADULT_DAYS, Const.ADULT_DAYS)[next.ordinal]
        w.s = state.copy(xp = Const.XP_AT[next.ordinal], daysTogether = days)
        if (next == Stage.EGG) w.s = w.s.copy(stage = Stage.EGG, lockedLook = Traits(), heartBase = state.affection + state.affectionOverflow)
        else promote(w, next)
        w.xp = XpEntry(XpKind.LEVELUP, 0)
        return w.step()
    }

    /** M1: 시동이 걸릴 때. 오랜만, 하루 첫 인사, 성장, 생일, 날짜형 계절을 판정한다. */
    fun startUp(state: PetState): Step {
        val now = clock.instant()
        val day = later(dayKey(now, zone), state.lastStartDayKey)
        val w = Work(state, now, day)
        val first = state.lastStartDayKey == null || day.isAfter(state.lastStartDayKey)
        w.s = w.s.copy(lastStartDayKey = day)
        if (state.crashQuiet(now)) return w.step()
        if (first) w.ev(EventId.FIRST_OF_DAY)

        // 오늘의 간식: 하루 첫 세션에 SNACK_DAILY개, 주머니는 SNACK_MAX까지. 같은 날 다시 시작해도 한 번뿐
        if (state.lastSnackGrantDay?.isBefore(day) != false) {
            val add = minOf(Const.SNACK_DAILY, Const.SNACK_MAX - w.s.snacks).coerceAtLeast(0)
            w.s = w.s.copy(snacks = w.s.snacks + add, lastSnackGrantDay = day, snackPop = if (add > 0) add else w.s.snackPop)
        }

        // 7.11 오랜만: 기분만 올린다. 애정·간식·물건·성장은 그대로
        state.lastTripEndAt?.let { last ->
            val gap = Duration.between(last, now)
            if (gap.toHours() >= Const.LONG_ABSENCE_H && w.ever("LONGABS:${last.epochSecond}")) {
                w.ev(EventId.LONG_ABSENCE, gap.toDays().toString())
                w.moodAtLeast(75)
            }
        }

        // 7.7 성장. 한 번에 여러 단계가 오를 수 있고 내려가지 않는다
        val expected = state.daysTogether + if (state.lastCountedDayKey == day) 0 else 1
        var target = when {
            expected >= Const.ADULT_DAYS -> Stage.ADULT
            expected >= Const.CHILD_DAYS -> Stage.CHILD
            expected >= Const.BABY_DAYS -> Stage.BABY
            else -> Stage.EGG
        }
        if (target == Stage.ADULT && !day.isBefore(state.carBirthday.plusYears(1))) target = Stage.BUDDY
        target = maxOf(target, Const.stageOfXp(state.xp), state.stage)
        promote(w, target)

        // 생일: 당일 또는 7일 안 첫 시동(늦은 축하)
        for (y in listOf(day.year, day.year - 1)) {
            val n = y - state.carBirthday.year
            if (n < 1) continue
            val ann = state.carBirthday.plusYears(n.toLong())
            val late = day.isAfter(ann) && !day.isAfter(ann.plusDays(7))
            if ((day == ann || late) && w.ever("BIRTHDAY:$n")) {
                w.ev(EventId.BIRTHDAY, if (late) "LATE:$n" else "$n")
                if (w.once("MOOD:DATE")) w.mood(10)
                w.item("ITEM-10")
                w.album("BIRTHDAY", n.toString())
                break
            }
        }

        // 날짜형 계절
        for ((id, items) in Seasons.dateEvents(day, dayKey(state.hatchedAt, zone))) {
            if (!w.once("SE:$id")) continue
            w.ev(EventId.SEASON_EVENT, id)
            w.tags += id
            if (w.once("MOOD:DATE")) w.mood(10)
            items.forEach { w.seasonItem(it, day.year) }
            w.album("SEASON", id)
        }
        return w.step()
    }

    /** 감지기가 낸 순간 이벤트 중 상태가 바뀌는 것(밥, 경고, 회복)을 적용한다. */
    fun apply(state: PetState, e: Event): Step {
        val now = clock.instant()
        val w = Work(state, now, later(dayKey(now, zone), state.lastStartDayKey))
        val quiet = state.crashQuiet(now)
        when (e.id) {
            EventId.REFUEL, EventId.RECHARGE -> if (!quiet) {
                w.moodCapped("MOOD:${e.id}", 1, 10)
                w.affect("CARE")
                gain(w, Const.XP_CARE, XpKind.CARE)
                if (w.item("ITEM-03")) { w.tags += "FIRST:MEAL"; w.album("FIRST_MEAL") }
            }
            EventId.WARN_TPMS, EventId.WARN_WASHER, EventId.WARN_OIL, EventId.WARN_OTHER -> {
                val t = AilmentType.valueOf(e.detail ?: AilmentType.TPMS.name)
                if (w.s.ailments.none { it.type == t }) w.s = w.s.copy(ailments = w.s.ailments + AilmentRecord(t, e.at))
                if (t == AilmentType.CRASH) w.s = w.s.copy(crashQuietUntil = e.at.plus(Const.CRASH_QUIET_H.toLong(), ChronoUnit.HOURS))
            }
            EventId.WARN_CLEARED -> {
                val t = AilmentType.valueOf(e.detail ?: return Step(state))
                w.s = w.s.copy(ailments = w.s.ailments.filter { it.type != t })
                if (!quiet) {
                    w.moodCapped("MOOD:CLEARED:${t.name}", 1, 10)
                    w.affect("CARE")
                    gain(w, Const.XP_CARE, XpKind.CARE)
                    if (w.item("ITEM-04")) { w.tags += "FIRST:RECOVERY"; w.album("FIRST_RECOVERY") }
                }
            }
            else -> {}
        }
        return w.step()
    }

    /** 쓰다듬기 연타(PetSpam.TRIGGER): 기분만 내린다. 저장된 기분에서 계산하니 시간이 지나면 저절로 돌아온다. 애정·하트·BOND는 건드리지 않는다. */
    fun annoy(state: PetState): PetState {
        val now = clock.instant()
        return state.copy(mood = (state.moodAt(now) + Const.PET_SPAM_MOOD).coerceIn(Const.MOOD_MIN, Const.MOOD_MAX), moodUpdatedAt = now)
    }

    /** 도로 월드에서 이동한 거리(화면 폭 단위)만큼 XP. 너무 짧은 이동은 0, 한 번에 XP_ROAM_MAX까지. 애정·기분은 그대로. */
    fun roam(state: PetState, screens: Float): Step {
        val now = clock.instant()
        val w = Work(state, now, later(dayKey(now, zone), state.lastStartDayKey))
        if (screens >= Const.ROAM_MIN_SCREEN) gain(w, minOf((screens * Const.XP_ROAM_PER_SCREEN).toInt(), Const.XP_ROAM_MAX), XpKind.ROAM)
        return w.step()
    }

    /** F8 교감. 애정 BOND는 무엇이든 하루 +1. */
    fun bond(state: PetState, kind: Bond): Step {
        val now = clock.instant()
        val w = Work(state, now, later(dayKey(now, zone), state.lastStartDayKey))
        if (state.crashQuiet(now)) return w.step()
        when (kind) {
            Bond.PET -> { w.moodCapped("MOOD:PET", 5, 2); gain(w, Const.XP_PET, XpKind.PET) } // 기분은 하루 +10, XP는 제한 없음
            Bond.SNACK -> {
                if (w.s.snacks == 0 || !w.cap("SNACK_USE", 2)) return w.step()
                w.s = w.s.copy(snacks = w.s.snacks - 1)
                w.mood(5)
                gain(w, Const.XP_SNACK, XpKind.SNACK)
            }
            Bond.THANKS -> { w.moodCapped("MOOD:THANKS", 3, 3); gain(w, Const.XP_THANKS, XpKind.THANKS) }
        }
        w.affect("BOND")
        return w.step()
    }

    /**
     * 정차 시간이 REST_STOP_MIN 이상이면 휴식 판정(8.1). 경로·이름이 없으면 retro(다시 출발할 때 사후 판정)일 때만.
     * 5분 이상 정차하면 휴식 판정과 상관없이 누적 운전 시간을 0으로 돌린다.
     */
    fun restStop(state: PetState, stopSec: Int, place: PlaceInfo?, retro: Boolean = false): Step {
        val now = clock.instant()
        val w = Work(state, now, later(dayKey(now, zone), state.lastStartDayKey))
        if (stopSec < Const.REST_STOP_MIN * 60) return w.step()
        val enough = state.driveSinceRestMin >= Const.REST_DRIVE_MIN
        w.s = w.s.copy(driveSinceRestMin = 0)
        val remain = place?.remainKm
        val named = place != null && (place.kind == PlaceKind.REST_AREA || Content.classifyPlace(place.label) == PlaceKind.REST_AREA)
        val atDest = remain != null && remain <= Const.REST_DEST_REMAIN_KM
        val away = named || (remain != null && remain > Const.REST_DEST_REMAIN_KM)
        val ok = enough && !atDest && (away || (retro && stopSec <= Const.REST_RETRO_MAX_MIN * 60))
        if (!ok || state.crashQuiet(now)) return w.step()
        w.ev(EventId.REST_STOP)
        w.moodCapped("MOOD:REST", Const.REST_DAILY_MAX, 6)
        if (w.once("SNACK:REST")) w.snack("REST")
        w.first("REST", null)
        val t = state.trips.lastOrNull()
        meet(w, t?.tripId ?: w.day.toString(), null, VisitCtx(day = w.day, startHour = now.atZone(zone).hour, rest = true, locationOn = state.locationOn), emptySet())
        return w.step()
    }

    /** 7.6 안내 빈도. SERIOUS는 시동마다, 그 밖은 7일까지 하루 1번, 8일째부터 3일에 1번. */
    fun ailmentNotice(state: PetState, type: AilmentType): Boolean {
        val a = state.ailments.firstOrNull { it.type == type } ?: return false
        if (type.severity == Severity.SERIOUS) return true
        val last = a.lastNotifiedAt ?: return true
        val today = dayKey(clock.instant(), zone)
        val ageDays = ChronoUnit.DAYS.between(dayKey(a.since, zone), today) + 1
        val sinceLast = ChronoUnit.DAYS.between(dayKey(last, zone), today)
        return if (ageDays <= Const.AILMENT_DAILY_DAYS) sinceLast >= 1 else sinceLast >= Const.AILMENT_SPARSE_EVERY_D
    }

    fun markNotified(state: PetState, type: AilmentType): PetState = state.copy(
        ailments = state.ailments.map { if (it.type == type) it.copy(lastNotifiedAt = clock.instant()) else it },
    )

    /** "하루 1번" 안내(배고픔 등). 오늘 이미 했으면 false. */
    fun noticeOnce(state: PetState, key: String): Pair<PetState, Boolean> {
        val now = clock.instant()
        val w = Work(state, now, later(dayKey(now, zone), state.lastStartDayKey))
        val ok = w.once("NOTICE:$key")
        return w.s to ok
    }
}
