package com.carmagotchi.app.ui.pet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.withFrameMillis
import com.carmagotchi.app.ui.LocalChama
import com.carmagotchi.app.ui.LocalReduceMotion

/**
 * 펫 한 마리. look은 정지 프레임(= 움직임 줄이기 프레임)이고, animate가 켜져 있으면 look.motion과 숨쉬기 등을 위에 얹는다.
 * animate=false이거나 시스템 움직임 줄이기면 look을 그대로 그린다(9.3, 9.4). 같은 look이라도 motionKey가 바뀌면 모션을 다시 처음부터 재생한다.
 */
@Composable
fun PetCanvas(
    spec: PetSpec, look: PetLook, size: Dp, modifier: Modifier = Modifier,
    animate: Boolean = true, motionKey: Any? = Unit, desc: String? = null, waiting: Boolean = false, onTap: (() -> Unit)? = null, move: PetMove? = null,
) {
    val c = LocalChama.current
    val moving = animate && !LocalReduceMotion.current
    val clock = remember { mutableLongStateOf(0L) }
    val start = remember { mutableLongStateOf(0L) }
    val lastInput = remember { mutableLongStateOf(0L) }
    val touchX = remember { mutableFloatStateOf(0f) }
    val touchUntil = remember { mutableLongStateOf(0L) }
    LaunchedEffect(moving) {
        if (moving) {
            val t0 = withFrameMillis { it }
            while (true) withFrameMillis { clock.longValue = it - t0 }
        } else clock.longValue = 0L
    }
    LaunchedEffect(motionKey) { start.longValue = clock.longValue }
    val tap = Modifier.pointerInput(onTap) {
        detectTapGestures { o ->
            lastInput.longValue = clock.longValue
            touchX.floatValue = if (o.x < this.size.width / 2f) -.008f else .008f
            touchUntil.longValue = clock.longValue + 1500L
            onTap?.invoke()
        }
    }
    Canvas(
        modifier.size(size).then(if (moving) tap else Modifier)
            .then(if (desc != null) Modifier.semantics { contentDescription = desc } else Modifier),
    ) {
        val t = clock.longValue
        val l = if (!moving) look else {
            val m = look.motion?.let { applyMotion(look, spec, it, (t - start.longValue).toFloat()) } ?: look
            val v = applyVisitorIn(m, (t - start.longValue).toFloat())
            val quiet = t - lastInput.longValue > 60_000L
            val idle = withIdle(v, spec, t, quiet, waiting && t - lastInput.longValue > 30_000L, if (t < touchUntil.longValue) touchX.floatValue else 0f)
            move?.applyTo(idle, t) ?: idle
        }
        drawPet(spec, l, c.dark, c.textSecondary)
    }
}

/** 접근성 설명(12절): "{펫이름}, {단계}, 기분 {구간}, {소품·효과}" */
fun petDesc(spec: PetSpec, mood: Int, look: PetLook, withName: Boolean = true): String {
    val stage = when (spec.stage) {
        com.carmagotchi.app.domain.Stage.EGG -> "알"; com.carmagotchi.app.domain.Stage.BABY -> "아기"
        com.carmagotchi.app.domain.Stage.CHILD -> "어린이"; com.carmagotchi.app.domain.Stage.ADULT -> "어른"
        com.carmagotchi.app.domain.Stage.BUDDY -> "단짝"
    }
    val band = when (com.carmagotchi.app.domain.moodBand(mood)) {
        com.carmagotchi.app.domain.MoodBand.LOW -> "시무룩"; com.carmagotchi.app.domain.MoodBand.OK -> "보통"
        com.carmagotchi.app.domain.MoodBand.GOOD -> "좋음"; com.carmagotchi.app.domain.MoodBand.GREAT -> "신남"
    }
    val extra = buildList {
        if (look.hand == Hand.UMBRELLA) add("우산을 쓰고 있어요")
        if (look.hand == Hand.BOWL_EMPTY) add("빈 밥그릇을 들고 있어요")
        if (look.hand == Hand.FLAG) add("깃발을 들고 있어요")
        if (look.bandage) add("이마에 반창고를 붙였어요")
        if (look.top == Top.RIBBON) add("회복 리본을 달았어요")
        if (look.top == Top.PARTY_HAT) add("고깔모자를 썼어요")
        look.visitor?.let { id -> com.carmagotchi.app.domain.Content.VISITORS.firstOrNull { it.id == id }?.let { add("옆에 ${it.name.substringBefore(' ')}가 있어요") } }
    }
    return (listOfNotNull(if (withName) spec.name else null, stage, "기분 $band") + extra).joinToString(", ")
}

@Suppress("unused")
private val keep = Color.Unspecified
