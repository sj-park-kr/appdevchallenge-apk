package com.carmagotchi.app.platform

import com.carmagotchi.app.data.Snapshot
import com.carmagotchi.app.domain.EventId
import com.carmagotchi.app.domain.Gear
import com.carmagotchi.app.domain.StateEngine
import com.carmagotchi.app.domain.clockAt
import com.carmagotchi.app.domain.newPet
import com.carmagotchi.app.domain.sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetSessionTest {
    private val clock = clockAt("2026-10-25T09:00:00")
    private val t0 = clock.now
    private fun session() = PetSession(Snapshot(newPet(clock)), StateEngine(clock), clock)

    /** 70분 주행 뒤 P. 이후 정차 stopMin분, 다시 D. */
    private fun longTripThenStop(stopMin: Int): PetSession {
        val s = session()
        var odo = 0.0
        fun feed(sec: Long, gear: Gear, speed: Double? = null) {
            clock.now = t0.plusSeconds(sec)
            s.onSample(sample(clock.now, gear, odo, speed))
        }
        feed(0, Gear.P)
        for (m in 1..70) { odo += 1.4; feed(m * 60L, Gear.D, 80.0) }
        val park = 70 * 60L
        feed(park, Gear.P)
        for (sec in 10..(stopMin * 60) step 10) feed(park + sec, Gear.P)
        feed(park + stopMin * 60L + 1, Gear.D, 30.0)
        return s
    }

    @Test fun arrivalCardShowsImmediatelyThenRestStopIsCreditedAtDeparture() {
        val s = session()
        var odo = 0.0
        clock.now = t0; s.onSample(sample(t0, Gear.P))
        for (m in 1..70) { odo += 1.4; clock.now = t0.plusSeconds(m * 60L); s.onSample(sample(clock.now, Gear.D, odo, 80.0)) }
        clock.now = t0.plusSeconds(70 * 60L)
        s.onSample(sample(clock.now, Gear.P, odo))
        assertNotNull(s.arrival) // 도착 카드는 바로
        assertNotNull(s.pet.pendingTrip)
    }

    @Test fun restOfFiveMinutesCreditsRestStopOnNextStartup() {
        val s = longTripThenStop(6)
        s.onSessionStart()
        assertTrue(EventId.REST_STOP in s.depart!!.extraEvents)
        assertEquals(0, s.pet.driveSinceRestMin)
    }

    @Test fun shortStopDoesNotCredit() {
        val s = longTripThenStop(3)
        s.onSessionStart()
        assertFalse(EventId.REST_STOP in s.depart!!.extraEvents)
    }

    @Test fun screenOffDuringStopCancelsRetroCredit() {
        val s = session()
        var odo = 0.0
        clock.now = t0; s.onSample(sample(t0, Gear.P))
        for (m in 1..70) { odo += 1.4; clock.now = t0.plusSeconds(m * 60L); s.onSample(sample(clock.now, Gear.D, odo, 80.0)) }
        val park = t0.plusSeconds(70 * 60L)
        clock.now = park; s.onSample(sample(park, Gear.P, odo))
        clock.now = park.plusSeconds(60); s.onSessionEnd(clock.now)
        clock.now = park.plusSeconds(400); s.onSample(sample(clock.now, Gear.D, odo, 30.0))
        s.onSessionStart()
        assertFalse(EventId.REST_STOP in s.depart!!.extraEvents)
    }

    @Test fun checkpointIsConfirmedAfterProcessDeath() {
        val s = session()
        var odo = 0.0
        clock.now = t0; s.onSample(sample(t0, Gear.P))
        for (m in 1..12) { odo += 1.0; clock.now = t0.plusSeconds(m * 60L); s.onSample(sample(clock.now, Gear.D, odo, 60.0)) }
        val snap = s.snapshot()
        assertNotNull(snap.checkpoint)
        val revived = PetSession(snap, StateEngine(clock), clock)
        assertNull(revived.checkpoint)
        assertEquals(1, revived.pet.trips.size)
        assertTrue(revived.arrival?.missed == true)
    }
}
