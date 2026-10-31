package com.carmagotchi.app.platform

import com.carmagotchi.app.data.Snapshot
import com.carmagotchi.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class VehicleRecoveryTest {
    private val clock = clockAt("2026-10-25T09:00:00")
    private val t0 = clock.now
    private val engine = StateEngine(clock)
    private fun session() = PetSession(Snapshot(newPet(clock)), engine, clock)
    private fun feed(s: PetSession, sec: Long, gear: Gear, odo: Double, speed: Double? = null) {
        clock.now = t0.plusSeconds(sec)
        s.onSample(sample(clock.now, gear, odo, speed))
    }

    @Test fun deniedOrMissingSpeedDoesNotBecomeAnEmulatorOrCreateHarshDriving() {
        val s = session()
        feed(s, 0, Gear.P, 0.0)
        for (m in 1..5) feed(s, m * 60L, Gear.D, m.toDouble())
        feed(s, 360, Gear.P, 6.0)
        feed(s, 490, Gear.P, 6.0)
        val t = s.pet.trips.single()
        assertEquals(SignalProfile.REAL, s.pet.signalProfile)
        assertEquals(0, t.hardBrakes)
        assertEquals(0, t.sharpTurns)
        assertTrue(t.counted)
    }

    @Test fun speedSignalCanDisappearAndRecoverWithoutFalseBraking() {
        val s = session()
        feed(s, 0, Gear.D, 0.0, 60.0)
        feed(s, 60, Gear.D, 1.0)
        feed(s, 120, Gear.D, 2.0)
        feed(s, 180, Gear.D, 3.0, 30.0)
        feed(s, 240, Gear.P, 4.0)
        feed(s, 370, Gear.P, 4.0)
        assertEquals(0, s.pet.trips.single().hardBrakes)
        assertEquals(SignalProfile.REAL, s.pet.signalProfile)
    }

    @Test fun restartFromCheckpointConfirmsOnceAndCanResumeRecording() {
        val s = session()
        feed(s, 0, Gear.D, 0.0)
        feed(s, 60, Gear.D, 1.0)
        feed(s, 120, Gear.D, 2.0)
        val recovered = PetSession(s.snapshot(), engine, clock)
        assertEquals(1, recovered.pet.trips.size)
        assertNull(recovered.checkpoint)
        val reopened = PetSession(recovered.snapshot(), engine, clock)
        assertEquals(recovered.pet, reopened.pet)
        feed(reopened, 180, Gear.D, 3.0)
        feed(reopened, 240, Gear.P, 4.0)
        feed(reopened, 370, Gear.P, 4.0)
        assertEquals(2, reopened.pet.trips.size)
        assertEquals(2, reopened.pet.trips.map { it.tripId }.distinct().size)
    }

    @Test fun briefParkThenDriveKeepsOneTripAndIgnitionOffDoesNotDuplicateIt() {
        val s = session()
        feed(s, 0, Gear.D, 0.0)
        feed(s, 60, Gear.P, 1.0)
        feed(s, 100, Gear.D, 1.0)
        assertNull(s.pet.pendingTrip)
        feed(s, 160, Gear.P, 2.0)
        s.onSessionEnd(clock.now)
        s.onSessionEnd(clock.now)
        assertEquals(1, s.pet.trips.size)
        assertEquals(2.0, s.pet.trips.single().distanceKm, .001)
    }
}
