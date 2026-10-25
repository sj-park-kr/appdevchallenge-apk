package com.carmagotchi.app.platform

import com.carmagotchi.app.data.Settings
import com.carmagotchi.app.data.Snapshot
import com.carmagotchi.app.domain.Bond
import com.carmagotchi.app.domain.Const
import com.carmagotchi.app.domain.DetectorSeed
import com.carmagotchi.app.domain.DriveSound
import com.carmagotchi.app.domain.DriveSoundPolicy
import com.carmagotchi.app.domain.EnergyProfile
import com.carmagotchi.app.domain.Event
import com.carmagotchi.app.domain.EventDetector
import com.carmagotchi.app.domain.EventId
import com.carmagotchi.app.domain.Gear
import com.carmagotchi.app.domain.Hunger
import com.carmagotchi.app.domain.PetState
import com.carmagotchi.app.domain.PlaceInfo
import com.carmagotchi.app.domain.Severity
import com.carmagotchi.app.domain.SignalProfile
import com.carmagotchi.app.domain.StateEngine
import com.carmagotchi.app.domain.Step
import com.carmagotchi.app.domain.TripSummary
import com.carmagotchi.app.domain.VehicleSample
import com.carmagotchi.app.domain.dayKey
import com.carmagotchi.app.domain.hungerOf
import com.carmagotchi.app.gleo.GleoContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 도착 카드 한 장. step은 confirmTrip 결과(미리보기), missed는 프로세스가 죽었다 살아난 뒤의 "아까 장면". */
data class Arrival(val trip: TripSummary, val step: Step, val missed: Boolean = false)

/** 출발 인사에 필요한 재료. 문장은 DialogueSelector.depart(step, extraEvents, extraTags)로 고른다. */
data class DepartInfo(val step: Step, val extraEvents: Set<EventId>, val extraTags: Set<String>)

/**
 * 샘플 -> 감지기 -> 엔진 -> 상태. Android를 모르는 순수 클래스(시계 주입). 스레드 안전하지 않으므로 한 스레드에서만 부른다.
 * 바뀐 것은 takeDirty()가 true일 때 저장한다.
 */
class PetSession(
    snapshot: Snapshot,
    private val engine: StateEngine,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.of("Asia/Seoul"),
) {
    var pet: PetState = snapshot.pet; private set
    var settings: Settings = snapshot.settings
    var arrival: Arrival? = null; private set
    var depart: DepartInfo? = null; private set
    val checkpoint get() = cp

    private var cp: TripSummary? = snapshot.checkpoint
    private var dirty = false
    private var detector = newDetector()
    private var active = false
    private var lastGear: Gear? = null
    private var lastOdo: Double? = null
    private var lastCheckpointAt: Instant? = null
    private var parkAt: Instant? = null
    private var parkPlace: PlaceInfo? = null
    private var sessionBroken = false // 정차 중 시동 OFF(화면 꺼짐)가 있었으면 휴식 사후 판정을 하지 않는다
    private val pendingDepart = mutableSetOf<EventId>()
    private val sounds = DriveSoundPolicy(ZoneId.systemDefault()) // 밤 22~06시는 기기 현지 시각 기준(앱 하루 경계의 KST와 별개)
    private var soundOut: DriveSound? = null
    /** 다음에 낼 동행 소리 한 개(F14). 한 번 꺼내면 비워진다. */
    fun takeSound() = soundOut.also { soundOut = null }
    fun cancelSound() = sounds.cancelLast()
    /** 이번 주행의 동승자 이름 칩(출발 반김). 주행이 확정되면 비운다. */
    var companion: String? = null
    private val rough = ArrayDeque<Instant>()

    private val guest get() = pet.guestModeUntil?.isAfter(clock.instant()) == true

    init {
        // 프로세스가 죽기 전의 대기 중 주행 또는 마지막 체크포인트가 남아 있으면 지금 확정한다(PRD F1 4번, F2 7번)
        (pet.pendingTrip ?: cp)?.let { confirm(it, missed = true) }
    }

    private fun newDetector() = EventDetector(
        zone, pet.signalProfile, pet.energyProfile,
        DetectorSeed(pet.lastFuelPct, pet.lastSoc, pet.lastRefillKm, pet.ailments.map { it.type }.toSet()),
    )

    fun snapshot() = Snapshot(pet, settings, cp)
    fun takeDirty() = dirty.also { dirty = false }

    /** UI 행동(교감, 설정, 동승자 이름 등)으로 바뀐 상태를 반영한다. */
    fun replace(p: PetState) { pet = p; dirty = true }
    fun changed() { dirty = true }

    fun onSample(s: VehicleSample) {
        val prev = lastGear
        lastGear = s.gear
        lastOdo = s.odometerKm
        val events = detector.feed(s)
        handle(events, s)
        sounds.onSample(s, events, !settings.driveSoundsOff && s.at.toEpochMilli() >= settings.quietUntilMs && !guest && !pet.serious && !pet.crashQuiet(s.at))?.let { soundOut = it }
        if (s.gear == Gear.P) {
            if (prev != null && prev != Gear.P) arrivedP(s)
            else if (s.place != parkPlace && pet.pendingTrip != null) {
                parkPlace = s.place
                detector.snapshot()?.let { setPending(it) }
            }
            syncBaseline()
        } else {
            if (prev == Gear.P) departed(s)
            val last = lastCheckpointAt
            if (last == null || Duration.between(last, s.at).seconds >= Const.CHECKPOINT_SEC) {
                lastCheckpointAt = s.at
                cp = detector.snapshot()
                dirty = true
            }
        }
        if (detector.profile != pet.signalProfile) { pet = pet.copy(signalProfile = detector.profile); dirty = true }
    }

    private fun handle(events: List<Event>, s: VehicleSample?) {
        for (e in events) when (e.id) {
            EventId.TRIP_END -> confirm(e.trip!!)
            EventId.REFUEL, EventId.RECHARGE, EventId.WARN_TPMS, EventId.WARN_WASHER, EventId.WARN_OIL,
            EventId.WARN_OTHER, EventId.WARN_CLEARED -> {
                val reward = e.id == EventId.REFUEL || e.id == EventId.RECHARGE
                if (!(guest && reward)) { pet = engine.log(engine.apply(pet, e)).state; dirty = true } // 손님 모드: 경고는 반영, 보상은 없음
                if (reward || e.id == EventId.WARN_CLEARED) pendingDepart += e.id
            }
            EventId.HARD_BRAKE, EventId.SHARP_TURN -> rough += e.at
            EventId.PASSENGER_ON -> if (s?.gear == Gear.P && detector.snapshot() == null) pendingDepart += e.id
            else -> {}
        }
    }

    private fun withCompanion(t: TripSummary) = companion?.takeIf { t.passengers > 0 }?.let { t.copy(companionName = it) } ?: t

    private fun setPending(t0: TripSummary) {
        val t = withCompanion(t0)
        pet = pet.copy(pendingTrip = t)
        cp = null
        arrival = if (guest) null else Arrival(t, engine.confirmTrip(pet, t))
        dirty = true
    }

    private fun arrivedP(s: VehicleSample) {
        sessionBroken = false
        parkAt = s.at
        parkPlace = s.place
        detector.snapshot()?.let { setPending(it) }
    }

    private fun departed(s: VehicleSample) {
        val stopSec = parkAt?.let { Duration.between(it, s.at).seconds } ?: 0
        // 긴 주행 중 5분 이상 쉬었으면 사후에 REST_STOP 판정(출발 인사에서 칭찬). 도착 카드는 이미 나갔다.
        if (!sessionBroken && stopSec >= Const.REST_STOP_MIN * 60 && pet.pendingTrip == null) {
            val step = engine.restStop(pet, stopSec.toInt(), parkPlace, retro = true)
            pet = step.state
            if (step.events.any { it.id == EventId.REST_STOP }) pendingDepart += EventId.REST_STOP
            dirty = true
        }
        if (pet.pendingTrip != null) { pet = pet.copy(pendingTrip = null); dirty = true } // 120초 안에 다시 출발: 같은 주행
        arrival = null
        parkAt = null
        parkPlace = null
    }

    private fun confirm(t0: TripSummary, missed: Boolean = false) {
        val t = withCompanion(t0)
        companion = null
        val step = engine.confirmTrip(pet, t)
        pet = step.state
        cp = null
        dirty = true
        arrival = when {
            guest || t.micro -> null
            !missed && arrival?.trip?.tripId == t.tripId -> arrival
            else -> Arrival(t, step, missed)
        }
        syncBaseline()
    }

    private fun syncBaseline() {
        val b = detector.baseline
        if (b.fuelPct != pet.lastFuelPct || b.soc != pet.lastSoc || b.refillKm != pet.lastRefillKm) {
            pet = pet.copy(lastFuelPct = b.fuelPct, lastSoc = b.soc, lastRefillKm = b.refillKm)
            dirty = true
        }
    }

    /** M1: 시동(화면 켜짐). 이미 시작된 세션이면 아무것도 하지 않는다. */
    fun onSessionStart() {
        if (active) return
        active = true
        val step = engine.startUp(pet)
        pet = step.state
        val tags = mutableSetOf<String>()
        pet.ailments.map { it.type }.firstOrNull { it.severity < Severity.SERIOUS && engine.ailmentNotice(pet, it) }?.let {
            tags += "NOTICE:${it.name}"
            pet = engine.markNotified(pet, it)
        }
        val b = detector.baseline
        val ev = pet.energyProfile == EnergyProfile.EV ||
            (pet.signalProfile == SignalProfile.EMULATOR && b.fuelPct == null && b.soc != null)
        if (hungerOf(if (ev) b.soc else b.fuelPct, ev) == Hunger.HUNGRY) {
            val (p, ok) = engine.noticeOnce(pet, "HUNGER")
            pet = p
            if (ok) tags += "NOTICE_HUNGER"
        }
        depart = DepartInfo(step.copy(state = pet), pendingDepart.toSet(), tags)
        pendingDepart.clear()
        dirty = true
    }

    /** 시동 OFF(화면 꺼짐): 대기 중 주행을 바로 확정하고 저장한다. */
    fun onSessionEnd(at: Instant) {
        active = false
        sessionBroken = true
        handle(detector.ignitionOff(at), null)
        syncBaseline()
        dirty = true
    }

    /** CHAMA_THANKS: 기분 +3(하루 3번), 애정(하루 상한). 손님 모드에서는 기록하지 않는다. */
    fun thanks() { if (!guest) { pet = engine.log(engine.bond(pet, Bond.THANKS)).state; dirty = true } }

    /** Gleo 대답에 쓰는 메모리 누적기 값(PRD 11.3). remainKm은 NaviHelper 목적지에서 온다. */
    fun gleoContext(remainKm: Double?): GleoContext {
        val now = clock.instant()
        val t = detector.snapshot()
        while (rough.isNotEmpty() && Duration.between(rough.first(), now).toMinutes() >= 10) rough.removeFirst()
        val day = dayKey(now, zone)
        val odo = lastOdo ?: pet.trips.lastOrNull()?.endOdoKm ?: pet.odoAtHatchKm
        return GleoContext(
            pet = pet, now = now, guest = guest, crash = pet.crashQuiet(now),
            tripKm = t?.distanceKm,
            todayKm = pet.trips.filter { dayKey(it.endAt, zone) == day }.sumOf { it.distanceKm } + (t?.distanceKm ?: 0.0),
            togetherKm = maxOf(0.0, odo - pet.odoAtHatchKm),
            remainKm = remainKm,
            rough = rough.isNotEmpty(),
            rear = t?.rearPassenger == true,
            passengers = (t?.passengers ?: 0) > 0,
            rain = t?.rain == true,
            night = t?.night == true,
            smooth = t != null && t.driveSec >= 600 && t.hardBrakes == 0 && t.sharpTurns == 0,
        )
    }
}
