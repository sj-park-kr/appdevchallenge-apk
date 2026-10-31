package com.carmagotchi.app.ui

import com.carmagotchi.app.Memory
import com.carmagotchi.app.domain.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test

class ShareTest {
    private val clock = clockAt("2026-10-25T09:00:00")
    private val pet = newPet(clock)
    private val memory = Memory("m", "서윤이랑 강릉시에 처음 갔어", clock.now, EventId.NEW_PLACE, with = "서윤", place = "강릉시")

    @Test fun privateFieldsAreHiddenInTitleAndQrByDefault() {
        val text = qrText(pet, memory, false, false)
        assertFalse(text.contains("서윤"))
        assertFalse(text.contains("강릉"))
        assertEquals("새로운 곳에 처음 갔어", shareTitle(memory, false, false))
    }

    @Test fun nameAndPlaceCanBeSharedIndependently() {
        val nameOnly = qrText(pet, memory, true, false)
        assertTrue(nameOnly.contains("서윤"))
        assertFalse(nameOnly.contains("강릉"))
        val placeOnly = qrText(pet, memory, false, true)
        assertFalse(placeOnly.contains("서윤"))
        assertTrue(placeOnly.contains("강릉시"))
        assertTrue(qrText(pet, memory, true, true).contains(memory.title))
    }

    @Test fun companionNameIsAlsoRemovedFromNonLocationTitle() {
        assertEquals("친구랑 10번째 나들이", shareTitle(memory.copy(event = EventId.PASSENGER_ON, title = "서윤이랑 10번째 나들이", place = null), false, false))
    }

    @Test fun longUnicodeFieldsStayWithinByteLimitAndRemainValidUtf8() {
        for (s in listOf("한글", "🚗🌈", "abc", "")) {
            val text = qrText(pet.copy(name = s.repeat(150)), memory.copy(title = s.repeat(200), place = s.repeat(200), with = s.repeat(200)), true, true)
            val bytes = text.toByteArray(Charsets.UTF_8)
            assertTrue("${bytes.size} bytes", bytes.size <= 300)
            assertEquals(text, bytes.toString(Charsets.UTF_8))
            assertTrue(QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")).width > 0)
        }
    }
}
