package com.carmagotchi.app.ui

import com.carmagotchi.app.ui.pet.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PRD F8 10번 제스처 표: 문지르기 / 끌기 / 탭을 400ms·8dp로 가른다. */
class PetGestureTest {
    private fun s(t: Long, x: Float, y: Float = 0f) = PtrSample(t, x, y)

    @Test fun shortStillReleaseIsTap() {
        assertEquals(PetGesture.TAP, classifyPetGesture(listOf(s(0, 0f), s(120, 3f, 2f)), ended = true))
        assertEquals(PetGesture.PENDING, classifyPetGesture(listOf(s(0, 0f), s(120, 3f, 2f)), ended = false))
    }

    @Test fun movingWithin400msIsRub() {
        assertEquals(PetGesture.RUB, classifyPetGesture(listOf(s(0, 0f), s(100, 20f)), false))
        assertEquals(PetGesture.RUB, classifyPetGesture(listOf(s(0, 0f), s(399, 9f)), true)) // 399ms, 9dp
        assertEquals(PetGesture.RUB, classifyPetGesture(listOf(s(0, 0f), s(150, 0f, 9f)), false)) // y로 움직여도 거리로 본다
    }

    @Test fun stayingStillFor400msThenMovingIsDrag() {
        assertEquals(PetGesture.DRAG, classifyPetGesture(listOf(s(0, 0f), s(200, 4f), s(400, 5f)), false)) // 움직이기 전에도 끌기 시작
        assertEquals(PetGesture.DRAG, classifyPetGesture(listOf(s(0, 0f), s(400, 2f), s(450, 40f)), false))
        assertEquals(PetGesture.DRAG, classifyPetGesture(listOf(s(0, 0f), s(400, 8f)), true)) // 정확히 8dp는 안
        assertEquals(PetGesture.DRAG, classifyPetGesture(listOf(s(0, 0f), s(800, 0f)), true)) // 길게 누르고 뗌
    }

    @Test fun slopIsStrictlyMoreThanEightDp() {
        assertEquals(PetGesture.TAP, classifyPetGesture(listOf(s(0, 0f), s(100, 8f)), true))
        assertEquals(PetGesture.RUB, classifyPetGesture(listOf(s(0, 0f), s(100, 8.1f)), true))
    }

    @Test fun rubCountsAfterOneSecondOfRubbing() {
        val rub = listOf(s(0, 0f), s(100, 30f), s(300, -30f), s(600, 30f), s(999, -30f))
        assertFalse(rubCounts(rub))
        assertTrue(rubCounts(rub + s(1000, 30f)))
        // 400ms 넘게 가만히 있다 움직이면 끌기라 쓰다듬기가 아니다
        assertFalse(rubCounts(listOf(s(0, 0f), s(500, 0f), s(600, 30f), s(1500, -30f))))
    }

    @Test fun footClampKeepsPetInsideFloor() {
        val w = 360f; val c = 200f
        assertEquals(.02f * w + 100f, clampFoot(-50f, w, c), 1e-3f)
        assertEquals(.98f * w - 100f, clampFoot(999f, w, c), 1e-3f)
        assertEquals(180f, clampFoot(180f, w, c), 1e-3f)
        assertEquals(100f, clampFoot(5f, 200f, 300f), 1e-3f) // 무대가 펫보다 좁으면 가운데
    }

    @Test fun driveTimeIsDistanceOverSpeedClamped() {
        assertEquals(300, driveMs(0f, 10f))
        assertEquals(500, driveMs(0f, 180f))
        assertEquals(1200, driveMs(0f, 900f))
    }
}
