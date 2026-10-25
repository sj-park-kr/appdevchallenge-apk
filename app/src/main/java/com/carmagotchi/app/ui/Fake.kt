package com.carmagotchi.app.ui

import com.carmagotchi.app.*
import com.carmagotchi.app.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 화면 확인용 예시 상태. 실제 연결 전까지 MainActivity가 쓴다. 시각은 고정(낮 3시)이라 그림이 늘 같다. */
object Samples {
    val NOW: Instant = Instant.parse("2026-10-25T06:00:00Z")
    private val KST = ZoneId.of("Asia/Seoul")
    private fun ago(days: Long, h: Long = 0) = NOW.minusSeconds(days * 86400 + h * 3600)
    val CALM_TOWN_FRIEND = Traits(Temperament.CALM, Roamer.TOWN, Social.FRIENDLY)
    val LIVELY_EXPLORER_SOLO = Traits(Temperament.LIVELY, Roamer.EXPLORER, Social.SOLO)

    fun pet(stage: Stage = Stage.ADULT, palette: Int = 0, pattern: Int = 0, traits: Traits = CALM_TOWN_FRIEND, name: String = "콩이", mood: Int = 70, quirk: Int = 0): PetState {
        val base = StateEngine(Clock.fixed(NOW, KST)).hatch(name, "sample", 12.0, LocalDate.of(2026, 1, 1))
        val full = stage >= Stage.BABY
        return base.copy(
            appearance = Appearance(palette, pattern, 0, quirk), stage = stage, mood = mood, affection = 45, snacks = 3,
            daysTogether = when (stage) { Stage.EGG -> 3; Stage.BABY -> 12; Stage.CHILD -> 40; Stage.ADULT -> 120; Stage.BUDDY -> 400 },
            lockedLook = if (stage >= Stage.CHILD) traits else Traits(), traits = traits, hatchedAt = ago(23),
            visitors = if (!full) emptyMap() else listOf("VIS-01", "VIS-02", "VIS-03", "VIS-05", "VIS-06", "VIS-07", "VIS-09").mapIndexed { i, id -> id to VisitorRecord(ago(30L - i * 3), ago(i.toLong() * 2), 1 + i % 3) }.toMap(),
            places = if (!full) emptyMap() else listOf(
                PlaceRecord("우리 동네", PlaceKind.HOME_REGION, "seoul-gn", "서울", ago(23), 9),
                PlaceRecord("강릉시", PlaceKind.REGION, "gangneung", "강원", ago(9), 1),
                PlaceRecord("경포해수욕장", PlaceKind.SEA, "gyeongpo", "강원", ago(9), 1),
                PlaceRecord("설악산", PlaceKind.MOUNTAIN, "seorak", "강원", ago(8), 1),
                PlaceRecord("안성휴게소", PlaceKind.REST_AREA, "anseong", "경기", ago(1), 2),
            ).associateBy { it.regionKey },
            items = if (!full) emptyMap() else Content.ITEMS.take(9).associate { it.id to ago(5) },
        )
    }

    private fun mem(id: String, t: String, d: Long, e: EventId, v: String? = null, with: String? = null, place: String? = null) = Memory(id, t, ago(d), e, v, with, place)
    val MEMORIES = listOf(
        mem("m1", "첫 비, 달팽이를 만났어", 3, EventId.RAIN, "VIS-01"),
        mem("m2", "강릉, 첫 바다야!", 9, EventId.NEW_PLACE, "VIS-07", "서윤", "경포해수욕장"),
        mem("m3", "아기가 된 날", 16, EventId.STAGE_UP),
        mem("m4", "부엉박사랑 쉬어 갔어", 12, EventId.REST_STOP, "VIS-09"),
        mem("m5", "첫 밤길", 14, EventId.NIGHT),
        mem("m6", "태어난 날", 23, EventId.TRIP_START),
    )
    private val HUNGRY = Chip(ChipKind.HUNGER, "배고파", "주유하면 콩이가 든든해져요.")
    private val TIRE = Chip(ChipKind.TPMS, "발이 시려", "가까운 주유소 공기 주입기나 정비소에서 타이어 공기압을 확인해 주세요.")
    private val WASH = Chip(ChipKind.WASHER, "눈이 따가워", "워셔액을 채워 주세요. 주유소나 마트에서 살 수 있어요.")

    fun room(stage: Stage = Stage.ADULT, traits: Traits = CALM_TOWN_FRIEND) = AppUiState(
        pet = pet(stage, traits = traits), now = NOW, odometerKm = 12.0, roomChips = listOf(HUNGRY, TIRE, WASH),
        passengerNames = listOf("서윤", "도윤"), placed = listOf("ITEM-01", "ITEM-02", "ITEM-03"), memories = MEMORIES, weekly = MEMORIES.take(4),
        pendingEarlierScene = scene("비 오는 날 좋아", EventId.RAIN),
        settings = AppSettings(gleoPresent = true, locationOn = true),
    )

    fun scene(text: String, e: EventId) = Scene(SceneKind.ARRIVE, e, text)
    val ARRIVE_RAIN = Scene(SceneKind.ARRIVE, EventId.RAIN, "비 오는 길에 달팽이를 만났어!", visitor = "VIS-01", picked = true)
    val ARRIVE_BRAKE = Scene(SceneKind.ARRIVE, EventId.HARD_BRAKE, "아까 깜짝 놀랐어. 그래도 잘 왔다!", cues = setOf(Cue.STAR), hardBrakes = 2)
    val DEPART_TPMS = Scene(SceneKind.DEPART, EventId.WARN_TPMS, "발이 시려… 같이 가 줘서 고마워.", cues = setOf(Cue.BANDAGE), chips = listOf(TIRE, HUNGRY), todo = TIRE.todo)
    val DEPART_GREET = Scene(SceneKind.DEPART, EventId.PASSENGER_ON, "새 친구다! 안녕!", cues = setOf(Cue.CUSHION), greetNames = listOf("서윤", "친구"))
    val DEPART_MORNING = Scene(SceneKind.DEPART, EventId.FIRST_OF_DAY, "좋은 아침! 오늘도 잘 부탁해.")
    val DEPART_GROW = Scene(SceneKind.DEPART, EventId.STAGE_UP, "일주일 동안 고마워.", fromStage = Stage.EGG)
    val ARRIVE_EGG = Scene(SceneKind.ARRIVE, EventId.TRIP_END, "꼬물꼬물. / 해설: 창밖이 궁금했나 봐요.")

    /** 디버그 실행에서 sample 이름으로 고른다. */
    fun named(n: String): AppUiState = when (n) {
        "egg" -> room(Stage.EGG).copy(pet = pet(Stage.EGG), scene = null, memories = emptyList(), roomChips = emptyList(), placed = emptyList(), pendingEarlierScene = null)
        "baby" -> room(Stage.BABY)
        "child" -> room(Stage.CHILD, LIVELY_EXPLORER_SOLO)
        "buddy" -> room(Stage.BUDDY)
        "empty" -> room().copy(pet = pet(Stage.BABY).copy(visitors = emptyMap(), places = emptyMap(), items = emptyMap()), memories = emptyList(), weekly = null, roomChips = emptyList(), placed = emptyList(), pendingEarlierScene = null)
        "guest" -> room().copy(settings = AppSettings(guestMode = true))
        "arrive" -> room().copy(scene = ARRIVE_RAIN)
        "arrive2" -> room().copy(scene = ARRIVE_BRAKE)
        "arrive-egg" -> room(Stage.EGG).copy(scene = ARRIVE_EGG)
        "depart" -> room().copy(scene = DEPART_TPMS)
        "greet" -> room().copy(scene = DEPART_GREET)
        "morning" -> room().copy(scene = DEPART_MORNING)
        "grow" -> room(Stage.BABY).copy(scene = DEPART_GROW)
        "hatch" -> AppUiState(now = NOW, odometerKm = 12.0, hatchPreview = Appearance(2, 1, 0, 1))
        "driving" -> room().copy(isParked = false)
        else -> room()
    }
}

class FakePetController(initial: AppUiState = Samples.room()) : PetController {
    private val s = MutableStateFlow(initial)
    override val state: StateFlow<AppUiState> = s.asStateFlow()
    private var nonce = 0

    override fun hatch(name: String, locationOn: Boolean, carBirthday: LocalDate) = s.update {
        it.copy(pet = Samples.pet(Stage.EGG, it.hatchPreview.palette, it.hatchPreview.pattern, name = name, mood = 70, quirk = it.hatchPreview.quirk).copy(hatchedAt = it.now, daysTogether = 0, odoAtHatchKm = it.odometerKm),
            settings = it.settings.copy(locationOn = locationOn), memories = emptyList(), roomChips = emptyList(), placed = emptyList())
    }
    override fun interact(action: Interaction) = s.update {
        val p = it.pet ?: return@update it
        when (action) {
            Interaction.PET -> it.copy(bubble = RoomBubble("기분 좋아!", Interaction.PET, ++nonce))
            Interaction.SNACK -> if (p.snacks > 0) it.copy(pet = p.copy(snacks = p.snacks - 1), bubble = RoomBubble("냠냠! 맛있다.", Interaction.SNACK, ++nonce)) else it
        }
    }
    override fun placeItem(itemId: String) = s.update { it.copy(placed = if (itemId in it.placed) it.placed - itemId else it.placed + itemId) }
    override fun dismissScene() = s.update { it.copy(scene = null) }
    override fun ackEarlierScene() = s.update { it.copy(pendingEarlierScene = null) }
    override fun chooseCompanion(name: String?) = s.update { it.copy(scene = null) }
    override fun renamePet(name: String) = s.update { it.copy(pet = it.pet?.copy(name = name)) }
    override fun setPassengerNames(names: List<String>) = s.update { it.copy(passengerNames = names.take(6)) }
    override fun updateSettings(settings: AppSettings) = s.update { it.copy(settings = settings) }
    override fun clearPlaces() = s.update { it.copy(pet = it.pet?.copy(places = emptyMap())) }
    override fun deleteAllData() = s.update { AppUiState(now = it.now, odometerKm = it.odometerKm, hatchPreview = it.hatchPreview, demoMode = it.demoMode) }
    override fun setDemoMode(on: Boolean) = s.update { it.copy(demoMode = on) }
    override fun runDemo(script: String) {}
}
