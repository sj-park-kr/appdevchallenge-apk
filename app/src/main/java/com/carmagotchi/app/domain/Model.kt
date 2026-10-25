package com.carmagotchi.app.domain

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Stage { EGG, BABY, CHILD, ADULT, BUDDY }
enum class Temperament { UNDECIDED, CALM, LIVELY }
enum class Roamer { UNDECIDED, TOWN, EXPLORER }
enum class Social { UNDECIDED, SOLO, FRIENDLY }
data class Traits(
    val temperament: Temperament = Temperament.UNDECIDED,
    val roamer: Roamer = Roamer.UNDECIDED,
    val social: Social = Social.UNDECIDED,
)

enum class Hunger { FULL, OK, PECKISH, HUNGRY }
enum class MoodBand { LOW, OK, GOOD, GREAT }
enum class EnergyProfile { FUEL, EV, PHEV }
enum class SignalProfile { REAL, EMULATOR }
enum class Severity { MILD, MODERATE, SERIOUS, CRITICAL }

enum class AilmentType(val severity: Severity) {
    TPMS(Severity.MODERATE), WASHER(Severity.MILD), OIL(Severity.MODERATE),
    BRAKE_FLUID(Severity.SERIOUS), MISSION_OIL(Severity.SERIOUS), POWER_DOWN(Severity.SERIOUS),
    AIRBAG(Severity.SERIOUS), CRASH(Severity.CRITICAL),
}

data class AilmentRecord(val type: AilmentType, val since: Instant, val lastNotifiedAt: Instant? = null)

enum class PlaceKind { HOME_REGION, REGION, SEA, MOUNTAIN, REST_AREA }

/** 좌표는 담지 않는다(PRD 9.2). label은 목적지 이름 또는 시·군·구 이름. */
data class PlaceInfo(
    val label: String,
    val regionKey: String,
    val provinceKey: String,
    val kind: PlaceKind = PlaceKind.REGION,
    val remainKm: Double? = null,
)

enum class EventId {
    TRIP_START, TRIP_END, HARD_BRAKE, SHARP_TURN, SMOOTH_TRIP, RAIN, NIGHT, PASSENGER_ON,
    REFUEL, RECHARGE, LOW_FUEL, LOW_BATTERY,
    WARN_TPMS, WARN_WASHER, WARN_OIL, WARN_OTHER, WARN_CLEARED,
    NEW_PLACE, REST_STOP, LONG_ABSENCE, MILESTONE_KM, FIRST_OF_DAY, BIRTHDAY, SEASON_EVENT, STAGE_UP,
}

data class Event(
    val id: EventId,
    val at: Instant,
    val detail: String? = null, // 경고 종류, 장소 이름, 계절 ID, 목표 단계 등
    val trip: TripSummary? = null, // TRIP_END만
)

enum class Gear { P, R, N, D }
enum class Seat { DRIVER, FRONT, REAR_LEFT, REAR_MIDDLE, REAR_RIGHT }

/** 플랫폼 계층이 채우는 한 시점의 차량 값. null은 "그 신호를 지원하지 않음". */
data class VehicleSample(
    val at: Instant,
    val gear: Gear,
    val odometerKm: Double,
    val speedKmh: Double? = null,
    val brakePressed: Boolean? = null,
    val brakePct: Double? = null,
    val steeringDeg: Double? = null,
    val wiperOn: Boolean? = null,
    val lowBeamOn: Boolean? = null,
    val fogLightOn: Boolean? = null,
    val outsideTempC: Double? = null,
    val windowOpenPct: Double? = null,
    val occupied: Set<Seat>? = null,
    val belted: Set<Seat>? = null,
    val fuelPct: Double? = null,
    val evSoc: Double? = null,
    val tripSinceRefillKm: Double? = null,
    val warnings: Set<AilmentType> = emptySet(),
    val place: PlaceInfo? = null,
)

/** 주행 한 건의 요약. 속도·거리는 판정 입력일 뿐 보상에 쓰지 않는다. */
data class TripSummary(
    val tripId: String,
    val startAt: Instant,
    val endAt: Instant,
    val startHour: Int,
    val driveSec: Int,
    val distanceKm: Double,
    val endOdoKm: Double,
    val hardBrakes: Int = 0,
    val sharpTurns: Int = 0,
    val rain: Boolean = false,
    val snow: Boolean = false,
    val fog: Boolean = false,
    val night: Boolean = false,
    val breeze: Boolean = false,
    val lowBeamSec: Int = 0,
    val passengers: Int = 0,
    val rearPassenger: Boolean = false,
    val companionName: String? = null, // 사용자가 칩을 눌러 붙인 이름만
    val place: PlaceInfo? = null,
    val windowSupported: Boolean = true,
    val fogSupported: Boolean = true,
    val tempSupported: Boolean = true,
) {
    val micro get() = distanceKm < Const.MICRO_TRIP_KM && driveSec < Const.MICRO_TRIP_SEC
    val counted get() = distanceKm >= Const.COUNTED_TRIP_KM || driveSec >= Const.COUNTED_TRIP_SEC
    val smooth get() = driveSec >= Const.SMOOTH_MIN_SEC && hardBrakes == 0 && sharpTurns == 0
    val long get() = driveSec >= Const.LONG_TRIP_MIN * 60 || distanceKm >= Const.LONG_TRIP_KM
}

data class PlaceRecord(
    val label: String, val kind: PlaceKind, val regionKey: String, val provinceKey: String,
    val firstAt: Instant, val count: Int, val visitors: Set<String> = emptySet(),
)
data class VisitorRecord(val firstMetAt: Instant, val lastMetAt: Instant, val count: Int)
data class CompanionRecord(val name: String, val firstRideAt: Instant, val rideCount: Int)
data class MemoryEntry(val at: Instant, val kind: String, val ref: String? = null)
data class DialogueRecord(val lineId: String, val shownAt: Instant)
data class Appearance(val palette: Int, val pattern: Int, val eyes: Int, val quirk: Int)

data class PetState(
    val schemaVersion: Int = 1,
    val petId: String,
    val name: String,
    val vinSeedHash: String,
    val appearance: Appearance,
    val hatchedAt: Instant,
    val carBirthday: LocalDate,
    val odoAtHatchKm: Double,
    val mood: Int = Const.MOOD_BASE,
    val moodUpdatedAt: Instant,
    val affection: Int = 0,
    val affectionOverflow: Int = 0,
    val xp: Int = 0, // 누적 경험치(Const.XP_AT 곡선). 단계는 날 수와 XP 중 빠른 쪽으로 오른다
    val daysTogether: Int = 0,
    val lastCountedDayKey: LocalDate? = null,
    val lastStartDayKey: LocalDate? = null,
    val stage: Stage = Stage.EGG,
    val lockedLook: Traits = Traits(),
    val traits: Traits = Traits(),
    val ailments: List<AilmentRecord> = emptyList(),
    val energyProfile: EnergyProfile = EnergyProfile.FUEL,
    val signalProfile: SignalProfile = SignalProfile.REAL,
    val lastFuelPct: Double? = null,
    val lastSoc: Double? = null,
    val lastRefillKm: Double? = null,
    val lastTripEndAt: Instant? = null,
    val snacks: Int = 0,
    val lastSnackGrantDay: LocalDate? = null, // 오늘의 간식을 받은 하루 키
    val snackPop: Int = 0, // 방을 열 때 보여 줄 "+N"(아직 못 본 오늘의 간식). 보여 주면 0
    val heartBase: Int = 0, // 마지막 STAGE_UP(처음엔 부화) 때의 affection + affectionOverflow(7.12)
    val driveSinceRestMin: Int = 0,
    val milestoneIndex: Int = 0,
    val guestModeUntil: Instant? = null,
    val crashQuietUntil: Instant? = null,
    val pendingTrip: TripSummary? = null,
    val locationOn: Boolean = true,
    val trips: List<TripSummary> = emptyList(),
    val visitors: Map<String, VisitorRecord> = emptyMap(),
    val places: Map<String, PlaceRecord> = emptyMap(),
    val items: Map<String, Instant> = emptyMap(),
    val companions: Map<String, CompanionRecord> = emptyMap(),
    val memory: List<MemoryEntry> = emptyList(),
    val ledger: Set<String> = emptySet(), // "하루키|보상키". 평생 1번은 하루키 "EVER"
    val dialogueHistory: List<DialogueRecord> = emptyList(),
    val look: CarLook? = null, // 차주가 바꾼 모습(F16). null = VIN 처음 모습. 옛 저장분엔 없어 null로 읽힌다
) {
    val serious get() = ailments.any { it.type.severity == Severity.SERIOUS }
    fun crashQuiet(now: Instant) = crashQuietUntil?.isAfter(now) == true
    fun moodAt(now: Instant) = moodAt(mood, moodUpdatedAt, now)
}

/** 7.2 읽을 때마다 계산하는 표류 기분. 0.5는 올림. */
fun moodAt(m0: Int, t0: Instant, t: Instant): Int {
    val h = (t.toEpochMilli() - t0.toEpochMilli()) / 3_600_000.0
    val v = Const.MOOD_BASE + (m0 - Const.MOOD_BASE) * Math.pow(Const.MOOD_DRIFT_PER_HOUR, h)
    return Math.floor(v + 0.5).toInt().coerceIn(Const.MOOD_MIN, Const.MOOD_MAX)
}

fun moodBand(m: Int) = when {
    m >= 85 -> MoodBand.GREAT
    m >= 65 -> MoodBand.GOOD
    m >= 40 -> MoodBand.OK
    else -> MoodBand.LOW
}

/** 하루는 새벽 4시에 바뀐다. */
fun dayKey(t: Instant, zone: ZoneId): LocalDate =
    t.atZone(zone).minusHours(Const.DAY_BOUNDARY_HOUR.toLong()).toLocalDate()

/** 7.10 씨앗 난수: 같은 차·같은 키면 같은 값. 0 이상 1 미만. */
fun seededRandom(vinSeedHash: String, key: String, purpose: String): Double {
    val h = MessageDigest.getInstance("SHA-256").digest("$vinSeedHash:$key:$purpose".toByteArray())
    var v = 0L
    for (i in 0 until 8) v = (v shl 8) or (h[i].toLong() and 0xff)
    return v.toULong().toDouble() / 18446744073709551616.0
}

/** 7.3 배고픔. wasHungry면 기준 +5%p 이상이어야 벗어난다. 값이 없으면 OK. */
fun hungerOf(pct: Double?, ev: Boolean, wasHungry: Boolean = false): Hunger {
    if (pct == null) return Hunger.OK
    val hungryMax = if (ev) 20.0 else 15.0
    return when {
        pct <= hungryMax || (wasHungry && pct < hungryMax + Const.HUNGER_EXIT_MARGIN_PP) -> Hunger.HUNGRY
        pct < 30 -> Hunger.PECKISH
        pct < 60 -> Hunger.OK
        else -> Hunger.FULL
    }
}
