package com.carmagotchi.app.ui.pet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.carmagotchi.app.domain.BodyShape
import com.carmagotchi.app.domain.CarLook
import kotlin.math.max
import kotlin.math.min

/** 3.15절 1~3번: 몸 형·길이·차고를 숫자로 푼 것. 그림 없이 계산만 하므로 JVM 테스트로 검사한다. */
internal class BodyGeo(shape: BodyShape, length: Int, ride: Int) {
    constructor(c: CarLook) : this(c.body, c.length, c.ride)

    private val p = when (shape) { // w, h, d, r
        BodyShape.SEDAN -> floatArrayOf(.32f, .48f, .050f, .10f)
        BodyShape.SUV -> floatArrayOf(.30f, .56f, .030f, .07f)
        BodyShape.HATCH -> floatArrayOf(.27f, .46f, .080f, .13f)
        BodyShape.MINI -> floatArrayOf(.25f, .40f, .090f, .12f)
    }
    val w = p[0] * LENGTH_K[length.coerceIn(0, 4)]
    val h = p[1]; val d = p[2]; val r = p[3]
    val height = h + .75f * d                  // H
    val kx = w / .32f
    val ky = height / .5175f
    val typeScale = min(kx, ky).coerceIn(.85f, 1f)
    val typeFace = kx.coerceIn(.82f, 1f)
    val lift = when (ride) { 1 -> .025f; 2 -> -.030f; else -> 0f } // 차고 이동(아래가 +)
    val top get() = B - height
    val faceY = B - .513f * height
    /** 뒷날개 밑동: 지붕 뒤쪽 끝(어깨) = 몸 윗선 위의 점. 지지대 아래 끝은 여기서 .01 안쪽이다. */
    val wingBase = Offset(.5f - w + r, B - h)
    val fin = Offset(.5f + .41f * w, B - h - .2f * d)
    fun x(v: Float) = .5f + (v - .5f) * kx
    fun y(v: Float) = B - (B - v) * ky

    companion object {
        const val B = .86f
        val LENGTH_K = floatArrayOf(.92f, .96f, 1f, 1.04f, 1.08f)
    }
}

private const val OUTLINE = .008f // 외곽선 반 굵기 여유
private const val FACE_HALF_W = .2125f // 볼 바깥 끝(.175 + .0375)
private const val FACE_HALF_H = .075f  // 눈 윗끝 ~ 입 아랫끝

/** 옆모습에서 얼굴이 쏠리는 양. 몸 옆면에 닿지 않게 남는 폭 안으로 줄인다. */
internal fun BodyGeo.sideShift(ss: StageSpec) = min(.06f, max(0f, ss.sx * w - OUTLINE - .001f - FACE_HALF_W * ss.face * typeFace))

/** 단계 변환 뒤 얼굴(눈·볼·입)이 차지하는 상자. 차고는 몸과 같이 움직이므로 뺀다. */
internal fun BodyGeo.faceBox(ss: StageSpec, side: Boolean): Rect {
    val k = ss.face * typeFace
    val cx = .5f - if (side) sideShift(ss) else 0f; val cy = ss.y(faceY)
    return Rect(cx - FACE_HALF_W * k, cy - FACE_HALF_H * k, cx + FACE_HALF_W * k, cy + FACE_HALF_H * k)
}

/** 단계 변환 뒤 몸 상자에서 외곽선 폭을 뺀 안쪽. */
internal fun BodyGeo.bodyInner(ss: StageSpec) =
    Rect(ss.x(.5f - w) + OUTLINE, ss.y(top) + OUTLINE, ss.x(.5f + w) - OUTLINE, ss.y(BodyGeo.B) - OUTLINE)
