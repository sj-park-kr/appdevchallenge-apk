package com.carmagotchi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PRD F1 / F5 수용 기준 중 감지기 수준 */
class DetectorTest {
    private val t0 = at("2026-10-25T08:00:00")
    private fun t(sec: Int) = t0.plusSeconds(sec.toLong())
    private fun count(ev: List<Event>, id: EventId) = ev.count { it.id == id }

    @Test fun f1ac1_tripStartOnce() {
        val d = EventDetector(KST)
        val ev = d.feed(sample(t(0), Gear.P)) + d.feed(sample(t(1), Gear.D, speed = 10.0)) + d.feed(sample(t(2), Gear.D, speed = 20.0))
        assertEquals(1, count(ev, EventId.TRIP_START))
    }

    @Test fun f1ac2_twoHardBrakesSavedInTrip() {
        val d = EventDetector(KST)
        val ev = mutableListOf<Event>()
        ev += d.feed(sample(t(0), Gear.P))
        ev += d.feed(sample(t(1), Gear.D, speed = 60.0, brake = false))
        ev += d.feed(sample(t(2), Gear.D, speed = 60.0, brake = false))
        ev += d.feed(sample(t(3), Gear.D, odo = 0.1, speed = 40.0, brake = true)) // 1초 안 20km/h 감속
        ev += d.feed(sample(t(14), Gear.D, odo = 0.2, speed = 60.0, brake = false))
        ev += d.feed(sample(t(15), Gear.D, odo = 0.3, speed = 40.0, brake = true))
        ev += d.feed(sample(t(20), Gear.P, odo = 0.4))
        ev += d.feed(sample(t(150), Gear.P, odo = 0.4)) // P 120초 이어짐 -> 확정
        assertEquals(2, count(ev, EventId.HARD_BRAKE))
        assertEquals(2, ev.first { it.id == EventId.TRIP_END }.trip!!.hardBrakes)
    }

    @Test fun hardBrakeDebounceAndLowSpeedIgnored() {
        val d = EventDetector(KST)
        val ev = mutableListOf<Event>()
        ev += d.feed(sample(t(0), Gear.P))
        ev += d.feed(sample(t(1), Gear.D, speed = 60.0, brake = false))
        ev += d.feed(sample(t(2), Gear.D, speed = 40.0, brake = true))
        ev += d.feed(sample(t(3), Gear.D, speed = 60.0, brake = false))
        ev += d.feed(sample(t(4), Gear.D, speed = 40.0, brake = true)) // 10초 디바운스 안
        ev += d.feed(sample(t(30), Gear.D, speed = 18.0, brake = true))
        ev += d.feed(sample(t(31), Gear.D, speed = 3.0, brake = true)) // 시작 속도 20 미만: 판정 안 함
        assertEquals(1, count(ev, EventId.HARD_BRAKE))
    }

    @Test fun f1ac3_mergeWithin120sSumsDriveTime() {
        val d = EventDetector(KST)
        val ev = mutableListOf<Event>()
        ev += d.feed(sample(t(0), Gear.P))
        ev += d.feed(sample(t(5), Gear.D, speed = 50.0))
        ev += d.feed(sample(t(305), Gear.D, odo = 4.0, speed = 50.0))
        ev += d.feed(sample(t(310), Gear.P, odo = 4.1))
        ev += d.feed(sample(t(370), Gear.D, odo = 4.1, speed = 50.0)) // P 60초 뒤 다시 D
        ev += d.feed(sample(t(670), Gear.D, odo = 8.0, speed = 50.0))
        ev += d.feed(sample(t(675), Gear.P, odo = 8.1))
        ev += d.feed(sample(t(800), Gear.P, odo = 8.1))
        assertEquals(1, count(ev, EventId.TRIP_START))
        val trip = ev.single { it.id == EventId.TRIP_END }.trip!!
        assertEquals(610, trip.driveSec)
        assertEquals(8.1, trip.distanceKm, 0.001)
    }

    @Test fun f1ac5_microTripHasNoRewards() {
        val d = EventDetector(KST)
        val ev = mutableListOf<Event>()
        ev += d.feed(sample(t(0), Gear.P))
        ev += d.feed(sample(t(1), Gear.D, odo = 10.0, speed = 20.0))
        ev += d.feed(sample(t(121), Gear.D, odo = 10.2, speed = 20.0))
        ev += d.feed(sample(t(122), Gear.P, odo = 10.2))
        ev += d.ignitionOff(t(123))
        val trip = ev.single { it.id == EventId.TRIP_END }.trip!!
        assertTrue(trip.micro)
        val clock = TestClock(t(123))
        val s = newPet(clock)
        val r = StateEngine(clock).confirmTrip(s, trip)
        assertEquals(s.ledger, r.state.ledger)
        assertTrue(r.events.isEmpty())
    }

    @Test fun f1ac6_emulatorProfileAfter60sOfNoSpeed() {
        val d = EventDetector(KST)
        d.feed(sample(t(0), Gear.P))
        for (i in 1..70) d.feed(sample(t(i), Gear.D))
        assertEquals(SignalProfile.EMULATOR, d.profile)
    }

    @Test fun realProfileStaysWhenSpeedWasEverPositive() {
        val d = EventDetector(KST)
        d.feed(sample(t(0), Gear.P))
        d.feed(sample(t(1), Gear.D, speed = 30.0))
        for (i in 2..80) d.feed(sample(t(i), Gear.D, speed = 0.0)) // 신호대기
        assertEquals(SignalProfile.REAL, d.profile)
    }

    @Test fun emulatorHardBrakeAndSharpTurn() {
        val d = EventDetector(KST, profile = SignalProfile.EMULATOR)
        val ev = mutableListOf<Event>()
        ev += d.feed(sample(t(0), Gear.P))
        ev += d.feed(sample(t(1), Gear.D, brake = false, steer = 0.0))
        ev += d.feed(sample(t(2), Gear.D, brake = false, steer = 100.0)) // 브레이크 OFF + 조향 90도 변화 -> 급회전
        ev += d.feed(sample(t(20), Gear.D, brake = true, steer = 0.0))
        ev += d.feed(sample(t(21), Gear.D, brake = true, steer = 100.0)) // 브레이크 ON + 조향 -> 급제동
        ev += d.feed(sample(t(40), Gear.D, brakePct = 10.0))
        ev += d.feed(sample(t(41), Gear.D, brakePct = 90.0)) // 브레이크 %면 80% 이상
        assertEquals(1, count(ev, EventId.SHARP_TURN))
        assertEquals(2, count(ev, EventId.HARD_BRAKE))
    }

    @Test fun carwashSharpTurnIgnoredAtLowSpeed() {
        val d = EventDetector(KST)
        val ev = mutableListOf<Event>()
        ev += d.feed(sample(t(0), Gear.P))
        ev += d.feed(sample(t(1), Gear.N, speed = 3.0, steer = 0.0))
        ev += d.feed(sample(t(2), Gear.N, speed = 3.0, steer = 400.0))
        ev += d.feed(sample(t(3), Gear.N, speed = 3.0, steer = 400.0))
        assertEquals(0, count(ev, EventId.SHARP_TURN) + count(ev, EventId.HARD_BRAKE))
    }

    @Test fun realSharpTurnNeedsHold() {
        val d = EventDetector(KST)
        val ev = mutableListOf<Event>()
        ev += d.feed(sample(t(0), Gear.P))
        ev += d.feed(sample(t(1), Gear.D, speed = 60.0, steer = 0.0))
        ev += d.feed(sample(t(2), Gear.D, speed = 60.0, steer = 200.0))
        assertEquals(0, count(ev, EventId.SHARP_TURN)) // 아직 0.5초가 안 됨
        ev += d.feed(sample(t(3), Gear.D, speed = 60.0, steer = 200.0))
        assertEquals(1, count(ev, EventId.SHARP_TURN))
    }

    @Test fun rainSnowNightSummary() {
        val d = EventDetector(KST)
        val s = at("2026-12-20T20:00:00")
        val ev = mutableListOf<Event>()
        ev += d.feed(VehicleSample(s, Gear.P, 0.0))
        for (i in 1..400) ev += d.feed(VehicleSample(s.plusSeconds(i * 3L), Gear.D, i * 0.05, speedKmh = 50.0,
            wiperOn = i in 20..60, lowBeamOn = true, outsideTempC = -2.0))
        ev += d.feed(VehicleSample(s.plusSeconds(1210), Gear.P, 20.0))
        ev += d.ignitionOff(s.plusSeconds(1211))
        val trip = ev.single { it.id == EventId.TRIP_END }.trip!!
        assertTrue(trip.rain)
        assertTrue(trip.snow)
        assertTrue(trip.night)
        assertFalse(trip.fog)
    }

    @Test fun shortWiperBlipIsNotRain() {
        val d = EventDetector(KST)
        val s = at("2026-06-20T12:00:00")
        val ev = mutableListOf<Event>()
        ev += d.feed(VehicleSample(s, Gear.P, 0.0))
        for (i in 1..200) ev += d.feed(VehicleSample(s.plusSeconds(i * 3L), Gear.D, i * 0.05, speedKmh = 50.0, wiperOn = i in 10..13))
        ev += d.ignitionOff(s.plusSeconds(700))
        assertFalse(ev.single { it.id == EventId.TRIP_END }.trip!!.rain)
    }

    @Test fun passengerNeedsStableAndBelt() {
        val d = EventDetector(KST)
        val rr = setOf(Seat.REAR_RIGHT)
        val ev = mutableListOf<Event>()
        for (i in 0..15) ev += d.feed(sample(t(i), Gear.P, occupied = rr, belted = rr))
        assertEquals(1, count(ev, EventId.PASSENGER_ON))
        val d2 = EventDetector(KST) // 벨트 없는 짐
        val ev2 = mutableListOf<Event>()
        for (i in 0..15) ev2 += d2.feed(sample(t(i), Gear.P, occupied = setOf(Seat.FRONT), belted = emptySet()))
        assertEquals(0, count(ev2, EventId.PASSENGER_ON))
    }

    @Test fun f5ac3_rechargeInEvOnce() {
        val d = EventDetector(KST, energy = EnergyProfile.EV)
        val ev = mutableListOf<Event>()
        for (i in 0..11) ev += d.feed(sample(t(i), Gear.P, soc = 40.0))
        for (i in 12..30) ev += d.feed(sample(t(i), Gear.P, soc = 46.0))
        assertEquals(1, count(ev, EventId.RECHARGE))
    }

    @Test fun f5ac8_hybridNeverRecharges() {
        val d = EventDetector(KST, energy = EnergyProfile.FUEL)
        val ev = mutableListOf<Event>()
        for (i in 0..11) ev += d.feed(sample(t(i), Gear.P, fuel = 50.0, soc = 40.0))
        for (i in 12..30) ev += d.feed(sample(t(i), Gear.P, fuel = 50.0, soc = 60.0))
        assertEquals(0, count(ev, EventId.RECHARGE))
        assertEquals(0, count(ev, EventId.REFUEL))
    }

    @Test fun smallRefuelCountsWhenRefillDistanceResets() {
        val d = EventDetector(KST, seed = DetectorSeed(fuelPct = 40.0, refillKm = 120.0))
        val ev = (0..12).flatMap { d.feed(sample(t(it), Gear.P, fuel = 46.0, refill = 0.2)) }
        assertEquals(1, count(ev, EventId.REFUEL)) // +6%p지만 주유 이후 거리가 초기화됨
    }

    @Test fun lowFuelOnceAtEntryWithHysteresis() {
        val d = EventDetector(KST)
        val ev = mutableListOf<Event>()
        for (i in 0..12) ev += d.feed(sample(t(i), Gear.P, fuel = 14.0))
        for (i in 13..40) ev += d.feed(sample(t(i), Gear.P, fuel = 18.0)) // 아직 HUNGRY
        assertEquals(1, count(ev, EventId.LOW_FUEL))
    }

    @Test fun f5ac4_ac5_warningOnAndCleared() {
        val d = EventDetector(KST)
        val ev = mutableListOf<Event>()
        ev += d.feed(sample(t(0), Gear.P, warnings = setOf(AilmentType.TPMS)))
        assertEquals(EventId.WARN_TPMS, ev.single().id)
        for (i in 1..20) ev += d.feed(sample(t(i), Gear.P)) // 20초: 아직 회복 아님
        assertEquals(0, count(ev, EventId.WARN_CLEARED))
        for (i in 21..40) ev += d.feed(sample(t(i), Gear.P))
        assertEquals(1, count(ev, EventId.WARN_CLEARED))
    }

    @Test fun warningClearedOnlyJudgedInPark() {
        val d = EventDetector(KST)
        d.feed(sample(t(0), Gear.P, warnings = setOf(AilmentType.WASHER)))
        val ev = (1..60).flatMap { d.feed(sample(t(it), Gear.D, speed = 30.0)) }
        assertEquals(0, count(ev, EventId.WARN_CLEARED))
        val ev2 = (61..100).flatMap { d.feed(sample(t(it), Gear.P)) }
        assertEquals(1, count(ev2, EventId.WARN_CLEARED))
    }
}
