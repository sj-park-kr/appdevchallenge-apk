package com.carmagotchi.app.platform

import com.carmagotchi.app.data.*
import com.carmagotchi.app.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

class PlacePrivacyTest {
    @get:Rule val tmp = TemporaryFolder()
    private val clock = clockAt("2026-10-25T09:00:00")
    private val place = PlaceInfo("경포해수욕장", "강릉시", "강원", PlaceKind.SEA)
    private val engine = StateEngine(clock)

    @Test fun deletionRemovesAllNamedLocationsAndSurvivesEncryptedReload() {
        val t = trip(clock.now, place = place, name = "친구", passengers = 1)
        val pet = engine.confirmTrip(newPet(clock), t).state
        val s = PetSession(Snapshot(pet), engine, clock)
        s.replace(s.pet.copy(pendingTrip = t))
        s.clearPlaces()
        val clean = s.pet
        assertFalse(clean.locationOn)
        assertTrue(clean.places.isEmpty())
        assertTrue(clean.trips.all { it.place == null })
        assertNull(clean.pendingTrip!!.place)
        assertTrue(clean.memory.none { it.kind == "NEW_PLACE" })
        assertTrue(clean.ledger.none { it.contains("강원") })
        assertEquals(pet.xp, clean.xp)
        assertEquals(pet.items, clean.items)
        assertEquals(pet.companions, clean.companions)
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = PetStore(tmp.root, AesGcmCrypto({ key }, {}))
        store.save(s.snapshot())
        assertEquals(s.snapshot(), PetStore(tmp.root, AesGcmCrypto({ key }, {})).load())
    }

    @Test fun lateLocationCallbacksCannotRestoreDeletedDataDuringTrip() {
        val s = PetSession(Snapshot(newPet(clock)), engine, clock)
        fun feed(sec: Long, gear: Gear, odo: Double) {
            clock.now = at("2026-10-25T09:00:00").plusSeconds(sec)
            s.onSample(sample(clock.now, gear, odo).copy(place = place))
        }
        feed(0, Gear.D, 0.0)
        feed(60, Gear.D, 1.0)
        assertNotNull(s.checkpoint!!.place)
        s.clearPlaces()
        assertNull(s.checkpoint!!.place)
        feed(120, Gear.P, 2.0)
        assertNull(s.pet.pendingTrip!!.place)
        feed(250, Gear.P, 2.0)
        assertTrue(s.pet.trips.isNotEmpty())
        assertTrue(s.pet.trips.all { it.place == null })
        assertTrue(s.pet.places.isEmpty())
        assertTrue(s.pet.memory.none { it.kind == "NEW_PLACE" })
    }

    @Test fun locationOffDoesNotPersistLocationEvenForMicroTrips() {
        val p = newPet(clock).copy(locationOn = false)
        val result = engine.confirmTrip(p, trip(clock.now, driveSec = 5, km = .01, place = place)).state
        assertNull(result.trips.single().place)
    }
}
