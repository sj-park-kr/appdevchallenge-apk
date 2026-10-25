package com.carmagotchi.app.ui

import com.carmagotchi.app.domain.BodyShape
import com.carmagotchi.app.domain.Stage
import com.carmagotchi.app.ui.pet.BodyGeo
import com.carmagotchi.app.ui.pet.bodyInner
import com.carmagotchi.app.ui.pet.faceBox
import com.carmagotchi.app.ui.pet.stageSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3.15절: 몸 형 4 x 길이 5 x 단계에서 얼굴(눈·볼·입) 상자가 몸 안쪽 상자 안에 든다(48dp 아이콘도 같은 비율). */
class LookGeometryTest {
    @Test fun faceStaysInsideBody() {
        for (shape in BodyShape.entries) for (len in 0..4) for (stage in Stage.entries.filter { it != Stage.EGG }) for (side in listOf(false, true)) {
            val g = BodyGeo(shape, len, 0); val ss = stageSpec(stage)
            val f = g.faceBox(ss, side); val b = g.bodyInner(ss)
            val tag = "$shape/$len/$stage/side=$side"
            assertTrue("$tag left", f.left >= b.left); assertTrue("$tag right", f.right <= b.right)
            assertTrue("$tag top", f.top >= b.top); assertTrue("$tag bottom", f.bottom <= b.bottom)
        }
    }

    @Test fun wingPostTouchesBody() {
        for (shape in BodyShape.entries) for (len in 0..4) {
            val g = BodyGeo(shape, len, 0); val b = g.wingBase; val bottom = b.y + .01f
            assertEquals("$shape/$len on roof line", BodyGeo.B - g.h, b.y, 1e-6f)
            assertTrue("$shape/$len x", b.x > .5f - g.w && b.x < .5f)
            assertTrue("$shape/$len bottom inside", bottom > g.top && bottom < BodyGeo.B)
        }
    }

    @Test fun sedanNormalIsOldBody() {
        val g = BodyGeo(BodyShape.SEDAN, 2, 0)
        assertEquals(1f, g.kx, 1e-6f); assertEquals(1f, g.ky, 1e-6f)
        assertEquals(.63f, g.fin.x, .002f); assertEquals(.37f, g.fin.y, .002f)
    }
}
