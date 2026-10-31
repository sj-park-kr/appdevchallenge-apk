package com.carmagotchi.app

import com.carmagotchi.app.domain.Appearance
import com.carmagotchi.app.domain.CarLook
import com.carmagotchi.app.domain.carLook
import com.carmagotchi.app.domain.DriveSound
import com.carmagotchi.app.domain.EventId
import com.carmagotchi.app.domain.Origin
import com.carmagotchi.app.domain.PetState
import com.carmagotchi.app.domain.Stage
import com.carmagotchi.app.domain.XpEntry
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate

/*
 * UI와 플랫폼 계층 사이의 약속. UI는 이 파일의 타입만 보고 그린다.
 * 플랫폼 계층(다른 담당)은 PetController를 구현하고 MainActivity의 FakePetController를 바꿔 끼운다.
 */

enum class SceneKind { DEPART, ARRIVE }

/** 도착·출발 카드가 보여 줄 한 장면. 문장은 DialogueSelector가 고른 것을 그대로 담는다. */
data class Scene(
    val kind: SceneKind,
    val event: EventId,                     // 포즈·아이콘을 정하는 이벤트(3.10 표)
    val text: String,                       // EGG 단계는 "의성어 / 해설: 해설" 원문 그대로
    val visitor: String? = null,            // VIS-01~12, 길동무가 같이 보일 때
    val cues: Set<Cue> = emptySet(),        // 겹침 그림(PRD F2 4번)
    val hardBrakes: Int = 0,
    val chips: List<Chip> = emptyList(),    // 출발: 상태 칩 최대 2개
    val todo: String? = null,               // 출발: 할 일 한 줄(해요체)
    val greetNames: List<String>? = null,   // 출발 반김: 이름 칩 후보. null이면 칩 없음
    val picked: Boolean = false,            // 도착: 그날 첫 확정 주행 "주웠다" 표시
    val fromStage: Stage? = null,           // STAGE_UP: 바로 이전 단계
    val heartsFull: Boolean = false,        // STAGE_UP: 하트 가득 특별판(MO-GROW-HEART, F4 11번)
    val rewards: List<String> = emptyList(),
    val at: Instant? = null,
)

/** 문장과 따로 항상 보이는 겹침 그림 + 계절 옷. */
enum class Cue { STAR, TILT, UMBRELLA, FUR_HAT, STARRY, CUSHION, BANDAGE, EMPTY_BOWL, HANBOK, SCARF, BOOTS, CHERRY, CRITICAL }

enum class ChipKind { HUNGER, TPMS, WASHER, OIL, WARNING, RECOVERED, PASSENGER, DAYS }
data class Chip(val kind: ChipKind, val label: String, val todo: String? = null)

enum class Interaction { PET, SNACK }

/** 방 말풍선. reaction이 있으면 방이 그 모션을 한 번 재생한다. nonce가 바뀔 때마다 새로 재생. */
data class RoomBubble(val text: String, val reaction: Interaction? = null, val nonce: Int = 0, val hint: String? = null, val annoyed: Boolean = false)

data class AppSettings(
    val arriveCard: Boolean = true,
    val departGreeting: Boolean = true,
    val guestMode: Boolean = false,
    val locationOn: Boolean = false,
    val gleoPresent: Boolean = false,   // Gleo가 있는 차에서만 도움말을 보인다
    val vinFallback: Boolean = false,   // VIN을 못 읽어 무작위 씨앗을 썼다
    val driveSounds: Boolean = true,    // 주행 중 동행 소리(F14), 기본 켜짐
    val parkedSounds: Boolean = true,   // 정차 중 효과음(F15), 기본 켜짐
    val quietToday: Boolean = false,    // "조용히": 오늘(04:00까지) 앱 소리(F14·F15) 모두 끔
)

/** 앨범 한 장. 그림은 열 때 event·visitor로 그린다(PRD F9 5번). */
data class Memory(
    val id: String, val title: String, val at: Instant, val event: EventId,
    val visitor: String? = null, val with: String? = null, val place: String? = null,
)

/** 월드 연출이 읽는 차량 환경(읽기 전용). tempC는 외기 온도. */
data class WorldEnv(val tempC: Double? = null, val windowOpenPct: Double = 0.0, val lowBeam: Boolean = false, val fog: Boolean = false, val energyPct: Double? = null)

data class AppUiState(
    val pet: PetState? = null,
    val scene: Scene? = null,                   // 지금 보여 줄 출발·도착 장면(P단일 때만 그린다)
    val pendingEarlierScene: Scene? = null,     // "아까 장면"(12시간 보관). 방에서 말풍선으로 1번
    val bubble: RoomBubble? = null,             // 방 말풍선(교감 반응)
    val isParked: Boolean = true,               // false면 화면에 펫을 그리지 않는다(M0)
    val reduceMotion: Boolean = false,          // animator_duration_scale == 0
    val demoMode: Boolean = false,
    val saveFailed: Boolean = false,           // 최신 변경이 아직 기기에 저장되지 않음
    val settings: AppSettings = AppSettings(),
    val passengerNames: List<String> = emptyList(),
    val placed: List<String> = emptyList(),     // 방 칸에 놓은 물건 ID(앞에서부터 칸 1, 2, …)
    val now: Instant = Instant.now(),
    val odometerKm: Double = 0.0,
    val rain: Boolean = false,                  // 방 창문 날씨
    val env: WorldEnv = WorldEnv(),             // 도로 월드 환경 연출
    val roomChips: List<Chip> = emptyList(),    // 방 상태 칩(배고픔·아픔), 중요한 순
    val memories: List<Memory> = emptyList(),   // 최근 순
    val weekly: List<Memory>? = null,           // 이번 주 앨범 장면(최대 4). null = 아직 없음, 빈 목록 = 조용한 한 주
    val hatchPreview: Appearance = Appearance(0, 0, 0, 0), // 부화 전 알에 비치는 VIN 색·무늬
    val xpLog: List<XpEntry> = emptyList(),     // 최근 XP 획득 10개, 최신순(저장 안 함)
    val tickAt: Instant? = null,                // 함께 있는 시간 XP를 마지막으로 준 시각(없으면 바를 그리지 않고 문구만)
    val tickDriving: Boolean = false,           // 주행 중이면 틱당 +2
    val togetherSec: Int = 0,                   // 앱을 켠 뒤 누적 함께 한 초(저장 안 함)
) {
    val carLook get() = pet?.carLook()          // 현재 모습(F16). 펫이 없으면 null
}

interface PetController {
    val state: StateFlow<AppUiState>
    fun hatch(name: String, locationOn: Boolean, carBirthday: LocalDate)
    fun interact(action: Interaction)
    fun placeItem(itemId: String)                // 이미 놓았으면 치우고, 아니면 첫 빈 칸에 놓는다
    fun dismissScene()
    fun roam(screens: Float) {}                  // 펫이 화면 폭 몇 개만큼 이동했다(XP)
    fun ackSnackPop() {}                        // 오늘의 간식 "+N"을 보여 줬다(한 번뿐)
    fun ackEarlierScene()
    fun chooseCompanion(name: String?)           // 반김 이름 칩. null = "말 안 할래"
    fun renamePet(name: String)
    fun setPassengerNames(names: List<String>)   // 최대 6개, 이름 6자
    fun updateSettings(settings: AppSettings)
    fun clearPlaces()                            // 위치 사용을 끌 때 장소 도장 지우기
    fun retrySave() {}
    fun deleteAllData()
    fun setDemoMode(on: Boolean)
    fun setLook(look: CarLook) {}                // 모습 꾸미기 저장(F16)
    fun resetLookToVin() {}                      // "처음 모습으로": look = null
    fun sfx(sound: DriveSound, origin: Origin) {}   // 정차 중 효과음 요청(F15). 화면이 모션의 정해진 순간에 부른다
    fun runDemo(script: String)                  // 시연 모드 패널: passenger, rain, tpms_on, tpms_off, soc, grow, birthday, levelup
}
