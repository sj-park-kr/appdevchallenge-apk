package com.carmagotchi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PRD 7.14 단위 테스트용 계산 예시 T1~T12. */
class PrdCasesTest {
    private val clock = clockAt("2026-10-25T09:00:00")
    private val engine = StateEngine(clock)

    @Test fun t1_moodDrift() {
        assertEquals(68, moodAt(90, at("2026-10-24T20:00:00"), at("2026-10-25T08:00:00")))
    }

    private fun moodAfter(start: Int, t: TripSummary): Int {
        val s = newPet(clock).copy(mood = start, moodUpdatedAt = clock.now)
        return engine.confirmTrip(s, t).state.mood
    }

    @Test fun t2_rearHardBrakesCapped() {
        val m = moodAfter(60, trip(clock.now, hb = 3, passengers = 1, rear = true))
        assertEquals(38, m)
        assertEquals(MoodBand.LOW, moodBand(m))
    }

    @Test fun t3_totalCap() {
        assertEquals(36, moodAfter(60, trip(clock.now, hb = 5, st = 4)))
    }

    @Test fun t4_floor() {
        assertEquals(25, moodAfter(30, trip(clock.now, hb = 3)))
    }

    @Test fun t5_hungryHysteresis() {
        assertEquals(Hunger.HUNGRY, hungerOf(18.0, ev = false, wasHungry = true))
        assertEquals(Hunger.PECKISH, hungerOf(18.0, ev = false, wasHungry = false))
        assertEquals(Hunger.PECKISH, hungerOf(20.0, ev = false, wasHungry = true)) // 20%부터 벗어난다
    }

    @Test fun t6_refuel() {
        val det = EventDetector(KST, seed = DetectorSeed(fuelPct = 14.0))
        val t0 = at("2026-10-26T08:00:00")
        val ev = (0..12).flatMap { det.feed(sample(t0.plusSeconds(it.toLong()), Gear.P, fuel = 25.0)) }
        assertEquals(listOf(EventId.REFUEL), ids(ev))
        assertEquals(Hunger.PECKISH, hungerOf(25.0, false))
    }

    @Test fun t7_careOnceAffectionPerDay() {
        val s0 = newPet(clock)
        val refuel = engine.apply(s0, Event(EventId.REFUEL, clock.now)).state
        val sick = engine.apply(refuel, Event(EventId.WARN_TPMS, clock.now, "TPMS")).state
        val healed = engine.apply(sick, Event(EventId.WARN_CLEARED, clock.now, "TPMS")).state
        assertEquals(1, refuel.affection)
        assertEquals(1, healed.affection)
        assertEquals(refuel.mood + 10, healed.mood)
    }

    @Test fun t8_stageUpToChild() {
        val s = newPet(clock, Stage.BABY).copy(daysTogether = 29, lastCountedDayKey = yesterday())
        val r = engine.startUp(s)
        assertEquals(Stage.CHILD, r.state.stage)
        assertTrue(EventId.STAGE_UP in ids(r.events))
    }

    private fun yesterday() = java.time.LocalDate.of(2026, 10, 24)

    @Test fun t9_calmFirstDecision() {
        val w = (1..20).map { trip(clock.now, driveSec = 900, hb = if (it <= 13) 0 else 1) } +
            (1..10).map { trip(clock.now, driveSec = 400) }
        assertEquals(30, w.size)
        assertEquals(Temperament.CALM, engine.recomputeTraits(w, Traits()).temperament)
    }

    @Test fun t10_calmHysteresis() {
        val w = (1..20).map { trip(clock.now, driveSec = 900, hb = if (it <= 11) 0 else 1) } +
            (1..10).map { trip(clock.now, driveSec = 400) }
        assertEquals(Temperament.CALM, engine.recomputeTraits(w, Traits(temperament = Temperament.CALM)).temperament)
        assertEquals(Temperament.LIVELY, engine.recomputeTraits(w, Traits()).temperament) // 처음엔 0.55 < 0.6
    }

    @Test fun t11_longAbsence() {
        val c = clockAt("2026-10-05T09:00:00")
        val s = newPet(c).copy(lastTripEndAt = at("2026-10-01T18:00:00"))
        val ev = StateEngine(c).startUp(s).events.first { it.id == EventId.LONG_ABSENCE }
        assertEquals("3", ev.detail)
    }

    @Test fun t12_dayBoundary() {
        assertEquals(java.time.LocalDate.of(2026, 10, 25), dayKey(at("2026-10-26T03:30:00"), KST))
        assertEquals(java.time.LocalDate.of(2026, 10, 25), dayKey(at("2026-10-26T03:50:00"), KST))
        assertEquals(java.time.LocalDate.of(2026, 10, 26), dayKey(at("2026-10-26T04:00:00"), KST))
    }
}
