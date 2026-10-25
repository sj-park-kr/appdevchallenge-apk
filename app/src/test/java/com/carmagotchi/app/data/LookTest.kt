package com.carmagotchi.app.data

import com.carmagotchi.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

class LookTest {
    @get:Rule val tmp = TemporaryFolder()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val crypto = AesGcmCrypto({ key }, {})
    private fun pet() = newPet(clockAt("2026-10-25T09:00:00"))

    @Test fun sameVinSameLook() {
        val a = Appearance(4, 3, 1, 2)
        assertEquals(carLookFromVin("abc", a), carLookFromVin("abc", a))
        val l = carLookFromVin("abc", a)
        assertEquals(4, l.color); assertEquals(3, l.pattern); assertEquals(CheekColor.ORANGE, l.cheek)
        assertTrue(l.mirror != Mirror.NONE)
    }

    @Test fun roundTripAndOldFileCompat() {
        val store = PetStore(tmp.root, crypto)
        val old = pet() // look=null: Gson이 필드를 쓰지 않으므로 옛 저장분과 같은 모양
        store.save(Snapshot(old))
        val loaded = PetStore(tmp.root, crypto).load()!!.pet
        assertNull(loaded.look)
        assertEquals(carLookFromVin(old.vinSeedHash, old.appearance), loaded.carLook())
        val mine = CarLook(body = BodyShape.MINI, length = 4, color = 9, wing = 2, eyes = EyeShape.SLANT)
        store.save(Snapshot(old.copy(look = mine)))
        assertEquals(mine, PetStore(tmp.root, crypto).load()!!.pet.look)
    }

    @Test fun resetGivesNull() {
        assertNull(pet().copy(look = CarLook()).copy(look = null).look)
        assertEquals(CarLook(length = 4, color = 11, pattern = 5, wheelSize = 2, ride = 2, wing = 2), CarLook(length = 9, color = 99, pattern = 9, wheelSize = 9, ride = 9, wing = 9).clamped())
    }
}
