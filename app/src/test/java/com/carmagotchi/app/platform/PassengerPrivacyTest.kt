package com.carmagotchi.app.platform

import com.carmagotchi.app.data.Settings
import com.carmagotchi.app.data.Snapshot
import com.carmagotchi.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class PassengerPrivacyTest {
    @Test fun removedNamesAreAnonymizedAcrossHistoryAndPendingTrip() {
        val clock = clockAt("2026-10-25T09:00:00")
        val trip = trip(clock.now, passengers = 1, name = "서윤")
        val pet = newPet(clock).copy(
            trips = listOf(trip),
            companions = mapOf("서윤" to CompanionRecord("서윤", clock.now, 10)),
            memory = listOf(MemoryEntry(clock.now, "COMPANION", "서윤:10"), MemoryEntry(clock.now, "COMPANION", "지우:1")),
        )
        val s = PetSession(Snapshot(pet, Settings(passengerNames = listOf("서윤", "지우"))), StateEngine(clock), clock)
        s.replace(s.pet.copy(pendingTrip = trip))
        s.companion = "서윤"
        s.setPassengerNames(listOf(" 지우 ", "지우", ""))
        assertEquals(listOf("지우"), s.settings.passengerNames)
        assertNull(s.companion)
        assertNull(s.pet.pendingTrip!!.companionName)
        assertNull(s.pet.trips.single().companionName)
        assertFalse(s.pet.companions.containsKey("서윤"))
        assertEquals(listOf("친구:10", "지우:1"), s.pet.memory.map { it.ref })
        assertTrue(s.takeDirty())
        assertEquals(10, pet.companions["서윤"]!!.rideCount)
    }

    @Test fun unchangedNamesPreserveMemoriesAndCurrentCompanion() {
        val clock = clockAt("2026-10-25T09:00:00")
        val pet = newPet(clock).copy(memory = listOf(MemoryEntry(clock.now, "COMPANION", "서윤:1")))
        val s = PetSession(Snapshot(pet, Settings(passengerNames = listOf("서윤"))), StateEngine(clock), clock)
        s.companion = "서윤"
        s.setPassengerNames(listOf("서윤", "지우"))
        assertEquals(pet, s.pet)
        assertEquals("서윤", s.companion)
    }
}
