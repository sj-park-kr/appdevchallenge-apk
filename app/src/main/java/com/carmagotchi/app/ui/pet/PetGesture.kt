package com.carmagotchi.app.ui.pet

import kotlin.math.hypot

/** BUILD-GUIDE 9.11 UI 상수. 값은 DESIGN-GUIDE 9.2 MO-DRIVE-TO·MO-DRAG와 PRD F8 10번. */
object RoomMove {
    const val DRAG_HOLD_MS = 400L
    const val DRAG_SLOP_DP = 8f
    const val RUB_MS = 1000L
    const val DRIVE_SPEED_DP_PER_SEC = 360f
    const val DRIVE_MIN_MS = 300
    const val DRIVE_MAX_MS = 1200
}

/** 한 손가락 궤적의 한 점. 시간은 ms, 좌표는 dp. */
data class PtrSample(val tMs: Long, val xDp: Float, val yDp: Float)

enum class PetGesture {
    /** 아직 모름: 누른 채 400ms 안이고 8dp 안에 있다 */
    PENDING,
    /** 400ms 전에 떼고 8dp 안: LOOK */
    TAP,
    /** 400ms 안에 8dp 넘게 움직임: 문지르기(쓰다듬기 후보). 자리는 안 옮긴다 */
    RUB,
    /** 400ms 동안 8dp 안에 머문 뒤: 끌기 */
    DRAG,
}

/**
 * F8 10번 제스처 가르기(순수). samples[0]이 누름. ended = 손을 뗐다.
 * 처음 8dp를 넘은 시각이 400ms 전이면 RUB, 그 뒤면 DRAG. 안 넘었으면 400ms를 채운 순간부터 DRAG, 그 전에 뗐으면 TAP.
 */
fun classifyPetGesture(samples: List<PtrSample>, ended: Boolean, holdMs: Long = RoomMove.DRAG_HOLD_MS, slopDp: Float = RoomMove.DRAG_SLOP_DP): PetGesture {
    val d = samples.first()
    val out = samples.firstOrNull { hypot(it.xDp - d.xDp, it.yDp - d.yDp) > slopDp }
    if (out != null) return if (out.tMs - d.tMs < holdMs) PetGesture.RUB else PetGesture.DRAG
    return if (samples.last().tMs - d.tMs >= holdMs) PetGesture.DRAG else if (ended) PetGesture.TAP else PetGesture.PENDING
}

/** 문지르기가 쓰다듬기로 인정되는 시점: 문지르기로 가려진 뒤 누른 지 RUB_MS(1초)가 지났다. */
fun rubCounts(samples: List<PtrSample>, holdMs: Long = RoomMove.DRAG_HOLD_MS, slopDp: Float = RoomMove.DRAG_SLOP_DP) =
    classifyPetGesture(samples, false, holdMs, slopDp) == PetGesture.RUB && samples.last().tMs - samples.first().tMs >= RoomMove.RUB_MS

/** 3.13 걷는 범위: 발 x(dp) = [0.02W + 0.5c, 0.98W − 0.5c]. 무대가 펫보다 좁으면 가운데 한 점. */
fun clampFoot(x: Float, stageW: Float, petC: Float): Float {
    val lo = .02f * stageW + .5f * petC; val hi = .98f * stageW - .5f * petC
    return if (lo >= hi) stageW / 2f else x.coerceIn(lo, hi)
}

fun driveMs(from: Float, to: Float) = (kotlin.math.abs(to - from) / RoomMove.DRIVE_SPEED_DP_PER_SEC * 1000f).toInt().coerceIn(RoomMove.DRIVE_MIN_MS, RoomMove.DRIVE_MAX_MS)
