package com.carmagotchi.app.data

import com.carmagotchi.app.domain.PlaceInfo
import com.carmagotchi.app.domain.PlaceKind
import com.carmagotchi.app.domain.StateEngine
import com.carmagotchi.app.domain.clockAt
import com.carmagotchi.app.domain.newPet
import com.carmagotchi.app.domain.trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class PetStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private var destroyed = false
    private val crypto = AesGcmCrypto({ key }, { destroyed = true })

    private fun snapshot(): Snapshot {
        val clock = clockAt("2026-10-25T09:00:00")
        val place = PlaceInfo("경포해수욕장", "강원 강릉시", "강원", PlaceKind.SEA, 0.1)
        val t = trip(clock.now, place = place, passengers = 1, name = "하루")
        val pet = StateEngine(clock).confirmTrip(newPet(clock), t).state
        return Snapshot(pet, Settings(arriveCard = false, demoMode = true), checkpoint = t)
    }

    @Test fun roundTrip() {
        val s = snapshot()
        assertTrue(s.pet.trips.isNotEmpty() && s.pet.places.isNotEmpty())
        val store = PetStore(tmp.root, crypto)
        assertFalse(store.exists())
        store.save(s)
        store.save(s) // 두 번째는 history를 다시 쓰지 않아도 같은 결과
        assertEquals(s, PetStore(tmp.root, crypto).load())
    }

    @Test fun filesAreEncryptedAndNoTempLeft() {
        PetStore(tmp.root, crypto).save(snapshot())
        assertEquals(listOf("core.bin", "history.bin"), tmp.root.listFiles()!!.map { it.name }.sorted())
        assertFalse(String(tmp.root.resolve("core.bin").readBytes()).contains("콩이"))
    }

    @Test fun tamperedFileIsQuarantinedAndStartsOver() {
        val store = PetStore(tmp.root, crypto)
        store.save(snapshot())
        tmp.root.resolve("core.bin").let { f -> f.writeBytes(f.readBytes().also { it[it.size - 1] = (it.last() + 1).toByte() }) }
        assertNull(store.load())
        assertFalse(store.exists())
        assertTrue(tmp.root.resolve("core.bin.corrupt").exists())
    }

    @Test fun deleteAllRemovesEverythingAndKey() {
        val store = PetStore(tmp.root, crypto)
        store.save(snapshot())
        tmp.root.resolve("core.bin.corrupt").writeText("x")
        store.deleteAll()
        assertEquals(0, tmp.root.listFiles()!!.size)
        assertTrue(destroyed)
    }

    @Test fun oldSettingsWithoutSfxFieldKeepEffectsOn() {
        val old = com.google.gson.Gson().fromJson("{\"arriveCard\":false,\"driveSoundsOff\":true}", Settings::class.java)
        assertFalse(old.sfxOff)
        assertTrue(old.driveSoundsOff)
    }

    @Test fun oldBlobWithoutGrowthFieldsLoads() {
        val store = PetStore(tmp.root, crypto)
        store.save(snapshot())
        val aad = "core.bin".toByteArray()
        val f = tmp.root.resolve("core.bin")
        val json = com.google.gson.JsonParser.parseString(String(crypto.open(f.readBytes(), aad), Charsets.UTF_8)).asJsonObject
        json.getAsJsonObject("pet").let { it.remove("heartBase"); it.remove("lastSnackGrantDay"); it.remove("snackPop") }
        f.writeBytes(crypto.seal(json.toString().toByteArray(Charsets.UTF_8), aad))
        val loaded = PetStore(tmp.root, crypto).load()!!.pet
        assertEquals(0, loaded.heartBase)
        assertEquals(0, loaded.snackPop)
        assertNull(loaded.lastSnackGrantDay)
    }
}
