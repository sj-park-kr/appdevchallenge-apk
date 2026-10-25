package com.carmagotchi.app.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

val KST: ZoneId = ZoneId.of("Asia/Seoul")

fun at(iso: String): Instant = LocalDateTime.parse(iso).atZone(KST).toInstant()

class TestClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = KST
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}

fun clockAt(iso: String) = TestClock(at(iso))

/** 시계 시각에 부화한 펫. 기본 단계는 BABY(말을 하는 단계). */
fun newPet(clock: TestClock, stage: Stage = Stage.BABY, birthday: LocalDate = LocalDate.of(2026, 1, 1)): PetState =
    StateEngine(clock).hatch("콩이", "vin-seed-hash", 0.0, birthday).copy(stage = stage)

var tripSeq = 0

/** 인정 주행(15분, 5km) 기본값. */
fun trip(
    end: Instant,
    driveSec: Int = 900,
    km: Double = 5.0,
    endOdo: Double = km,
    hb: Int = 0,
    st: Int = 0,
    rain: Boolean = false,
    snow: Boolean = false,
    night: Boolean = false,
    passengers: Int = 0,
    rear: Boolean = false,
    place: PlaceInfo? = null,
    name: String? = null,
) = TripSummary(
    tripId = "t${tripSeq++}", startAt = end.minusSeconds(driveSec.toLong()), endAt = end,
    startHour = end.minusSeconds(driveSec.toLong()).atZone(KST).hour,
    driveSec = driveSec, distanceKm = km, endOdoKm = endOdo,
    hardBrakes = hb, sharpTurns = st, rain = rain, snow = snow, night = night,
    passengers = passengers, rearPassenger = rear, place = place, companionName = name,
)

fun sample(
    t: Instant, gear: Gear, odo: Double = 0.0,
    speed: Double? = null, brake: Boolean? = null, brakePct: Double? = null, steer: Double? = null,
    wiper: Boolean? = null, fuel: Double? = null, soc: Double? = null, refill: Double? = null,
    warnings: Set<AilmentType> = emptySet(), occupied: Set<Seat>? = null, belted: Set<Seat>? = null,
) = VehicleSample(
    at = t, gear = gear, odometerKm = odo, speedKmh = speed, brakePressed = brake, brakePct = brakePct,
    steeringDeg = steer, wiperOn = wiper, fuelPct = fuel, evSoc = soc, tripSinceRefillKm = refill,
    warnings = warnings, occupied = occupied, belted = belted,
)

fun ids(events: List<Event>) = events.map { it.id }

fun catalog(): List<Line> =
    Dialogue.parse(object {}.javaClass.classLoader.getResourceAsStream("dialogue.tsv")!!)
