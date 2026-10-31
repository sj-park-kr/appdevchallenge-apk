package com.carmagotchi.app.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.tan

/** 재시작 때 저장된 값으로 감지기를 이어 시작한다(밥·경고 판정은 "마지막 P 값"과 비교). */
data class DetectorSeed(
    val fuelPct: Double? = null,
    val soc: Double? = null,
    val refillKm: Double? = null,
    val warnings: Set<AilmentType> = emptySet(),
)

/**
 * 샘플 시계열 -> 순간 이벤트(PRD 8).
 * 여기서 만드는 것: TRIP_START, TRIP_END(확정, 요약 포함), HARD_BRAKE, SHARP_TURN, PASSENGER_ON,
 * REFUEL, RECHARGE, LOW_FUEL, LOW_BATTERY, WARN_*, WARN_CLEARED.
 * 저장 상태가 필요한 이벤트(NEW_PLACE, REST_STOP, MILESTONE_KM, LONG_ABSENCE, FIRST_OF_DAY, BIRTHDAY,
 * SEASON_EVENT, STAGE_UP)와 주행 요약에서 나오는 SMOOTH_TRIP/RAIN/NIGHT는 StateEngine이 만든다.
 */
class EventDetector(
    private val zone: ZoneId,
    var profile: SignalProfile = SignalProfile.REAL,
    private val energy: EnergyProfile = EnergyProfile.FUEL,
    seed: DetectorSeed = DetectorSeed(),
) {
    private class Acc(val startAt: Instant, val startOdo: Double, val startHour: Int, val startMonth: Int) {
        var endAt = startAt
        var endOdo = startOdo
        var driveSec = 0.0
        var hardBrakes = 0
        var sharpTurns = 0
        var wiperSec = 0.0
        var wiperSince: Instant? = null
        var tempSum = 0.0
        var tempN = 0
        var lowBeamSec = 0.0
        var fogSec = 0.0
        var breezeSec = 0.0
        val seats = mutableSetOf<Seat>()
        var place: PlaceInfo? = null
        var windowSupported = false
        var fogSupported = false
        var tempSupported = false
    }

    private var prev: VehicleSample? = null
    private var trip: Acc? = null
    private var parkedSince: Instant? = null

    private var lastHardBrake: Instant? = null
    private var lastSharpTurn: Instant? = null
    private var lastBrakeOn: Instant? = null
    private var brakeSeen = false
    private var turnHold: Instant? = null
    private val speedBuf = ArrayDeque<Pair<Instant, Double>>()
    private val steerBuf = ArrayDeque<Pair<Instant, Double>>()
    private var zeroSince: Instant? = null
    private var speedEverPositive = false

    private val seatSince = mutableMapOf<Seat, Instant>()
    private val seatOn = mutableSetOf<Seat>()
    private var passengerFired = false

    private var parkStart: Instant? = null
    private val parkBuf = ArrayDeque<VehicleSample>()
    private var curFuel = seed.fuelPct
    private var curSoc = seed.soc
    private var curRefill = seed.refillKm
    private var baseFuel = seed.fuelPct
    private var baseSoc = seed.soc
    private var baseRefill = seed.refillKm
    private var lastRefuel: Instant? = null
    private var lastRecharge: Instant? = null
    private var hungry = false

    private val warnOn = seed.warnings.toMutableSet()
    private val offSince = mutableMapOf<AilmentType, Instant>()

    /** 저장해 둘 마지막 P 값. */
    val baseline get() = DetectorSeed(curFuel, curSoc, curRefill, warnOn.toSet())

    fun clearPlaces() {
        trip?.place = null
        prev = prev?.copy(place = null)
        for (i in parkBuf.indices) parkBuf[i] = parkBuf[i].copy(place = null)
    }

    private fun sec(a: Instant, b: Instant) = Duration.between(a, b).toMillis() / 1000.0
    private fun ready(last: Instant?, now: Instant, minSec: Double) = last == null || sec(last, now) >= minSec

    fun feed(s: VehicleSample): List<Event> {
        val out = mutableListOf<Event>()
        val p = prev
        prev = s
        val dt = if (p == null) 0.0 else maxOf(0.0, sec(p.at, s.at))

        parkedSince?.let { if (sec(it, s.at) >= Const.TRIP_MERGE_SEC) confirm(out) }

        if (s.gear != Gear.P) {
            if (trip == null) startTrip(s, out) else parkedSince = null
        }
        trip?.let { a ->
            if (p != null && p.gear != Gear.P) accumulate(a, p, dt)
            if (s.windowOpenPct != null) a.windowSupported = true
            if (s.fogLightOn != null) a.fogSupported = true
            if (s.outsideTempC != null) a.tempSupported = true
            wiper(a, s)
            a.place = s.place ?: a.place
            if (s.gear == Gear.P) {
                if (parkedSince == null) {
                    parkedSince = s.at
                    a.endAt = s.at
                    a.endOdo = s.odometerKm
                }
            } else {
                a.endAt = s.at
                a.endOdo = s.odometerKm
            }
        }

        seats(s, out)
        if (s.gear != Gear.P) driving(s, p, out) else {
            turnHold = null
            speedBuf.clear()
            steerBuf.clear()
            zeroSince = null
        }
        if (s.gear == Gear.P) parked(s, out) else {
            parkStart = null
            parkBuf.clear()
        }
        warnings(s, out)
        return out
    }

    /** 시동 OFF: 대기 중 주행을 바로 확정한다. */
    fun ignitionOff(at: Instant): List<Event> {
        val out = mutableListOf<Event>()
        trip?.let { a ->
            if (parkedSince == null) {
                a.endAt = at
                parkedSince = at
            }
            confirm(out)
        }
        return out
    }

    private fun startTrip(s: VehicleSample, out: MutableList<Event>) {
        val t = s.at.atZone(zone)
        trip = Acc(s.at, s.odometerKm, t.hour, t.monthValue).also { it.seats += seatOn }
        parkedSince = null
        passengerFired = false
        baseFuel = curFuel ?: baseFuel
        baseSoc = curSoc ?: baseSoc
        baseRefill = curRefill ?: baseRefill
        out += Event(EventId.TRIP_START, s.at)
    }

    private fun accumulate(a: Acc, p: VehicleSample, dt: Double) {
        a.driveSec += dt
        if (p.lowBeamOn == true) a.lowBeamSec += dt
        if (p.fogLightOn == true) a.fogSec += dt
        if ((p.windowOpenPct ?: 0.0) >= Const.BREEZE_WINDOW_PCT) a.breezeSec += dt
    }

    private fun wiper(a: Acc, s: VehicleSample) {
        if (s.gear != Gear.P && s.wiperOn == true) {
            if (a.wiperSince == null) a.wiperSince = s.at
            s.outsideTempC?.let { a.tempSum += it; a.tempN++ }
        } else a.wiperSince?.let {
            val len = sec(it, s.at)
            if (len >= Const.RAIN_SEGMENT_MIN_SEC) a.wiperSec += len // 워셔 한 번 정도의 짧은 구간은 뺀다
            a.wiperSince = null
        }
    }

    /** 지금까지의 주행 요약(주행 중 체크포인트, P에서 "대기 중 주행"). 주행이 없으면 null. 상태를 바꾸지 않는다. */
    fun snapshot(): TripSummary? = trip?.let { summarize(it) }

    private fun summarize(a: Acc): TripSummary {
        val rain = a.wiperSec >= Const.RAIN_MIN_SEC
        val snow = rain && a.tempSupported && a.tempN > 0 && a.tempSum / a.tempN <= Const.SNOW_TEMP_C
        fun inNightWindow(h: Int) = h >= Const.NIGHT_WINDOW_FROM_HOUR || h < Const.NIGHT_WINDOW_TO_HOUR
        val night = a.lowBeamSec >= Const.NIGHT_MIN_SEC && a.lowBeamSec >= Const.NIGHT_RATIO * a.driveSec &&
            (inNightWindow(a.startHour) || inNightWindow(a.endAt.atZone(zone).hour))
        return TripSummary(
            tripId = a.startAt.epochSecond.toString(),
            startAt = a.startAt, endAt = a.endAt, startHour = a.startHour,
            driveSec = a.driveSec.toInt(),
            distanceKm = maxOf(0.0, a.endOdo - a.startOdo),
            endOdoKm = a.endOdo,
            hardBrakes = a.hardBrakes, sharpTurns = a.sharpTurns,
            rain = rain, snow = snow,
            fog = a.fogSec >= Const.FOG_MIN_SEC,
            night = night,
            breeze = a.breezeSec >= Const.BREEZE_MIN_SEC && a.startMonth in 4..10,
            lowBeamSec = a.lowBeamSec.toInt(),
            passengers = a.seats.size,
            rearPassenger = a.seats.any { it.name.startsWith("REAR") },
            place = a.place,
            windowSupported = a.windowSupported, fogSupported = a.fogSupported, tempSupported = a.tempSupported,
        )
    }

    private fun confirm(out: MutableList<Event>) {
        val a = trip ?: return
        out += Event(EventId.TRIP_END, a.endAt, trip = summarize(a))
        trip = null
        parkedSince = null
    }

    private fun brakeOn(s: VehicleSample): Boolean? = s.brakePressed ?: s.brakePct?.let { it > 0 }

    private fun steerDelta(): Double =
        if (steerBuf.size < 2) 0.0 else steerBuf.maxOf { it.second } - steerBuf.minOf { it.second }

    private fun driving(s: VehicleSample, p: VehicleSample?, out: MutableList<Event>) {
        val a = trip
        s.speedKmh?.let {
            speedBuf.addLast(s.at to it)
            if (it > 0) speedEverPositive = true
        }
        s.steeringDeg?.let { steerBuf.addLast(s.at to it) }
        while (speedBuf.isNotEmpty() && sec(speedBuf.first().first, s.at) > Const.HB_WINDOW_SEC) speedBuf.removeFirst()
        while (steerBuf.isNotEmpty() && sec(steerBuf.first().first, s.at) > Const.EMU_STEER_WINDOW_SEC) steerBuf.removeFirst()
        if (brakeOn(s) != null) brakeSeen = true
        if (brakeOn(s) == true) lastBrakeOn = s.at

        // PRD 확인 필요: 실차 신호대기 60초도 "속도 0"이라, 속도가 한 번도 0 초과였던 적이 없을 때만 EMULATOR로 바꾼다.
        if (s.gear == Gear.D && s.speedKmh == 0.0 && !speedEverPositive) {
            zeroSince = zeroSince ?: s.at
            if (sec(zeroSince!!, s.at) >= 60) profile = SignalProfile.EMULATOR
        } else zeroSince = null

        // LOW_SPEED_IGNORE_KMH(5)는 REAL의 20km/h 시작 속도 조건이 이미 포함한다.
        var hb = false
        if (profile == SignalProfile.REAL && s.speedKmh != null) {
            val peak = speedBuf.maxOf { it.second }
            val brakeOk = !brakeSeen || (lastBrakeOn != null && sec(lastBrakeOn!!, s.at) <= Const.HB_WINDOW_SEC)
            hb = peak >= Const.HB_MIN_SPEED_KMH && peak - s.speedKmh >= Const.HB_DROP_KMH && brakeOk
        } else if (profile == SignalProfile.EMULATOR) {
            hb = if (s.brakePct != null) s.brakePct >= Const.EMU_BRAKE_PCT && (p?.brakePct ?: 0.0) < Const.EMU_BRAKE_PCT
            else s.brakePressed == true && steerDelta() >= Const.EMU_STEER_DELTA_DEG
        }
        if (hb && ready(lastHardBrake, s.at, Const.HB_DEBOUNCE_SEC.toDouble())) {
            lastHardBrake = s.at
            a?.let { it.hardBrakes++ }
            out += Event(EventId.HARD_BRAKE, s.at)
        }

        // PRD 확인 필요: "HARD_BRAKE ±2초 생략"의 이후 2초는 샘플 지연이 필요해 이전 2초만 적용한다.
        var st = false
        if (profile == SignalProfile.REAL && s.speedKmh != null && s.steeringDeg != null) {
            val v = s.speedKmh / 3.6
            val angle = abs(s.steeringDeg)
            val delta = Math.toRadians(if (angle > 45) angle / Const.STEERING_RATIO else angle)
            if (s.speedKmh >= Const.ST_MIN_SPEED_KMH && v * v * tan(delta) / Const.WHEELBASE_M >= Const.ST_LAT_ACC_MS2) {
                turnHold = turnHold ?: s.at
                if (sec(turnHold!!, s.at) >= Const.ST_HOLD_SEC) st = true
            } else turnHold = null
        } else if (profile == SignalProfile.EMULATOR) {
            st = brakeOn(s) != true && steerDelta() >= Const.EMU_STEER_DELTA_DEG
        }
        if (st && ready(lastSharpTurn, s.at, Const.ST_DEBOUNCE_SEC.toDouble()) && ready(lastHardBrake, s.at, 2.0)) {
            lastSharpTurn = s.at
            turnHold = null
            a?.let { it.sharpTurns++ }
            out += Event(EventId.SHARP_TURN, s.at)
        }
    }

    private fun seatPresent(s: VehicleSample, seat: Seat): Boolean {
        val occ = s.occupied
        val belt = s.belted
        // 탑승 신호가 없으면 벨트만으로 판정, 벨트 신호가 없으면 탑승만으로 판정
        return if (occ == null) belt?.contains(seat) == true else seat in occ && (belt == null || seat in belt)
    }

    private fun seats(s: VehicleSample, out: MutableList<Event>) {
        var added = false
        for (seat in Seat.values()) {
            if (seat == Seat.DRIVER) continue
            if (seatPresent(s, seat)) {
                val since = seatSince.getOrPut(seat) { s.at }
                if (sec(since, s.at) >= Const.PASSENGER_STABLE_SEC && seatOn.add(seat)) added = true
            } else {
                seatSince.remove(seat)
                seatOn.remove(seat)
            }
        }
        trip?.seats?.addAll(seatOn)
        if (added && !passengerFired) {
            passengerFired = true
            out += Event(EventId.PASSENGER_ON, s.at)
        }
    }

    private fun median(v: List<Double>): Double? =
        if (v.isEmpty()) null else v.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }

    private fun parked(s: VehicleSample, out: MutableList<Event>) {
        if (parkStart == null) parkStart = s.at
        parkBuf.addLast(s)
        while (sec(parkBuf.first().at, s.at) > Const.ENERGY_MEDIAN_SEC) parkBuf.removeFirst()
        if (sec(parkStart!!, s.at) < Const.ENERGY_MEDIAN_SEC) return // 연료·SOC는 P에서 10초 중앙값만 쓴다

        val fuel = median(parkBuf.mapNotNull { it.fuelPct })
        val soc = median(parkBuf.mapNotNull { it.evSoc })
        val refill = parkBuf.mapNotNull { it.tripSinceRefillKm }.lastOrNull()
        curFuel = fuel ?: curFuel
        curSoc = soc ?: curSoc
        curRefill = refill ?: curRefill
        val debounce = Const.ENERGY_DEBOUNCE_MIN * 60.0

        if (energy != EnergyProfile.EV && fuel != null) {
            val rise = baseFuel?.let { fuel - it >= Const.REFUEL_PP } ?: false
            val reset = baseRefill?.let { it >= Const.REFILL_RESET_FROM_KM && refill != null && refill < Const.REFILL_RESET_TO_KM } ?: false
            if ((rise || reset) && ready(lastRefuel, s.at, debounce)) {
                lastRefuel = s.at
                out += Event(EventId.REFUEL, s.at)
                baseFuel = fuel
                baseRefill = refill
            }
            baseFuel = baseFuel ?: fuel
            baseRefill = baseRefill ?: refill
        }
        // HEV(energy=FUEL)의 구동 배터리 SOC는 밥이 아니다. EMULATOR는 SOC를 밥으로 쓴다.
        if ((energy != EnergyProfile.FUEL || profile == SignalProfile.EMULATOR) && soc != null) {
            val rise = baseSoc?.let { soc - it >= Const.RECHARGE_PP } ?: false
            if (rise && ready(lastRecharge, s.at, debounce)) {
                lastRecharge = s.at
                out += Event(EventId.RECHARGE, s.at)
                baseSoc = soc
            }
            baseSoc = baseSoc ?: soc
        }

        val ev = energy == EnergyProfile.EV || (fuel == null && soc != null && profile == SignalProfile.EMULATOR)
        val h = hungerOf(if (ev) soc else fuel, ev, hungry)
        if (h == Hunger.HUNGRY && !hungry) out += Event(if (ev) EventId.LOW_BATTERY else EventId.LOW_FUEL, s.at)
        hungry = h == Hunger.HUNGRY
    }

    private fun warnEvent(t: AilmentType) = when (t) {
        AilmentType.TPMS -> EventId.WARN_TPMS
        AilmentType.WASHER -> EventId.WARN_WASHER
        AilmentType.OIL -> EventId.WARN_OIL
        else -> EventId.WARN_OTHER
    }

    private fun warnings(s: VehicleSample, out: MutableList<Event>) {
        for (t in s.warnings) {
            offSince.remove(t)
            if (warnOn.add(t)) out += Event(warnEvent(t), s.at, t.name)
        }
        // 회복은 P에서만 판정한다. 주행 중 꺼졌으면 다음 P에서 30초를 센다. CRASH는 회복 이벤트가 없다.
        for (t in warnOn.toList()) {
            if (t in s.warnings || t == AilmentType.CRASH) continue
            if (s.gear != Gear.P) { offSince.remove(t); continue }
            val since = offSince.getOrPut(t) { s.at }
            if (sec(since, s.at) >= Const.WARN_CLEAR_SEC) {
                warnOn.remove(t)
                offSince.remove(t)
                out += Event(EventId.WARN_CLEARED, s.at, t.name)
            }
        }
    }
}
