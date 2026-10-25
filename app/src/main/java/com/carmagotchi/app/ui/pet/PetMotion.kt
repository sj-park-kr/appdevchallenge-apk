package com.carmagotchi.app.ui.pet

import androidx.compose.animation.core.FastOutSlowInEasing
import com.carmagotchi.app.SceneKind
import com.carmagotchi.app.domain.DriveSound
import com.carmagotchi.app.domain.SfxCue
import com.carmagotchi.app.domain.Stage
import com.carmagotchi.app.domain.Temperament
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** 9.2 모션 목록(MO-*). 정지 프레임(base)에서 시간 t(ms)의 모습을 계산한다. t가 길이를 넘으면 base 그대로(= 축소 모드 프레임). */
fun motionMs(m: Motion) = when (m) {
    Motion.HATCH, Motion.GROW, Motion.GROW_HEART, Motion.WOBBLE_DIZZY -> 3000
    Motion.GREET, Motion.WAVE, Motion.OUTFIT -> 1200
    Motion.DOZE -> 2000
    Motion.WOBBLE, Motion.LOOK, Motion.ROLLIN -> 1600
    Motion.DIZZY -> 1400
    Motion.SWAY, Motion.STRETCH -> 1800
    Motion.UMBRELLA -> 2200
    Motion.HOP -> 400
    Motion.EAT -> 2400
    Motion.HUNGRY, Motion.VISITOR_IN -> 900
    Motion.SHIVER, Motion.PET -> 600
    Motion.RECOVER -> 2000
    Motion.FLAG -> 1500
    Motion.PARTY -> 2500
    Motion.SNACK -> 1100
    Motion.BLINK2 -> 640
    Motion.CHEEK -> 800
}

/** F15 1번: 장면 하나의 자동 소리. 길동무 등장·밥·회복 모션이 소리를 가지면 도착 소리(SND-ARRIVE)를 대신한다. 밥 입 모양은 MO-EAT의 300ms 간격 4번. */
fun sceneCues(kind: SceneKind, visitor: Boolean, m: Motion?): List<SfxCue> = when {
    kind == SceneKind.ARRIVE && visitor -> listOf(SfxCue(0, DriveSound.VISITOR))
    m == Motion.GREET || m == Motion.WAVE -> listOf(SfxCue(0, DriveSound.GREET))
    m == Motion.EAT -> List(4) { SfxCue(200 + 300 * it, DriveSound.MUNCH, it) }
    m == Motion.RECOVER -> listOf(SfxCue(500, DriveSound.RECOVER))
    kind == SceneKind.ARRIVE -> listOf(SfxCue(0, DriveSound.ARRIVE))
    else -> emptyList()
}

private val ease = FastOutSlowInEasing
private fun kf(t: Float, vararg k: Pair<Float, Float>): Float {
    if (t <= k.first().first) return k.first().second
    for (i in 1 until k.size) if (t <= k[i].first) {
        val (t0, v0) = k[i - 1]; val (t1, v1) = k[i]
        return v0 + (v1 - v0) * ease.transform((t - t0) / (t1 - t0))
    }
    return k.last().second
}
private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
/** chamaSpring(감쇠 0.55) 근사: 0에서 1로, 한 번 넘쳤다가 안착 */
private fun spring(f: Float) = if (f >= 1f) 1f else 1f - exp(-5f * f) * cos(8f * f)
private fun sinp(f: Float) = sin(PI.toFloat() * f)
private fun tri(t: Float, a: Float, b: Float) = if (t in a..b) 1f - abs((t - a) / (b - a) * 2f - 1f) else 0f

fun applyMotion(b: PetLook, spec: PetSpec, m: Motion, t: Float): PetLook {
    if (t < 0f || t >= motionMs(m)) return b
    val twoPi = 2f * PI.toFloat()
    return when (m) {
        Motion.HATCH -> b.copy(
            tilt = if (t < 1200f) -6f * sin(twoPi * 3f * t / 1200f) else 0f,
            windowT = seg(t, 1200f, 1500f), shardT = if (t in 1500f..1800f) seg(t, 1500f, 1800f) else -1f,
            faceK = kf(t, 1500f to .6f, 1800f to .6f, 2100f to 1f),
            blink = 1f - .9f * tri(t, 2100f, 2400f), dy = -.02f * sinp(seg(t, 2400f, 3000f)),
        )
        Motion.GROW -> {
            val from = b.growFrom
            val eggStep = from == Stage.EGG
            val swap = if (eggStep) 600f else 1400f
            b.copy(
                stageOverride = if (from != null && t < swap) from else null,
                squashY = if (t < 1600f) kf(t, 600f to 1f, 1000f to .90f, 1400f to 1.12f) else 1.12f - .12f * spring(seg(t, 1600f, 2600f)),
                glowR = .30f + .10f * seg(t, 0f, 600f), glowA = if (t < 600f) .6f * (1f - seg(t, 0f, 600f)) else 0f,
                shellTop = if (eggStep && t in 600f..1200f) seg(t, 600f, 1200f) else -1f,
                fx = if (t < 2600f) b.fx - Fx.SPARKLE else b.fx, sparkles = 3,
            )
        }
        Motion.GROW_HEART -> applyMotion(b, spec, Motion.GROW, t).let { g ->
            g.copy(
                glowA = 0f, heartRing = if (t < 600f) seg(t, 0f, 600f) else -1f, heartPop = if (t >= 2600f) seg(t, 2600f, 3000f) else -1f,
                cheekAlpha = if (t >= 2600f) .65f + .35f * cos(twoPi * (t - 2600f) / 400f) else g.cheekAlpha,
            )
        }
        Motion.GREET -> b.copy(arms = Arms.WAVE, wave = 15f * sin(twoPi * 2f * t / 1200f), dy = -.04f * sinp((t % 600f) / 600f))
        Motion.DOZE -> b.copy(
            eyes = if (t < 800f) Eye.HALF else Eye.CLOSED, tilt = b.tilt * seg(t, 400f, 800f),
            fx = b.fx + Fx.ZZ, zz = if (t < 800f) 0 else ((t - 800f) / 300f).toInt() + 1,
        )
        Motion.WOBBLE -> wobble(b, t)
        Motion.DIZZY -> dizzy(b, t)
        Motion.WOBBLE_DIZZY -> if (t < 1600f) wobble(b, t) else dizzy(b, t - 1600f)
        Motion.SWAY -> b.copy(tilt = kf(t, 0f to 0f, 600f to 3f, 1200f to -3f, 1800f to 0f))
        Motion.UMBRELLA -> b.copy(
            handK = spring(seg(t, 0f, 300f)), rainT = if (t >= 300f) t - 300f else -1f,
            fx = if (t < 300f) b.fx - Fx.RAIN else b.fx,
        )
        Motion.WAVE -> b.copy(wave = 15f * sin(twoPi * 2f * t / 1200f), dy = -.03f * sinp(seg(t, 0f, 400f)), heartK = spring(seg(t, 0f, 300f)))
        Motion.HOP -> b.copy(dy = -.03f * sinp(t / 400f))
        Motion.EAT -> b.copy(
            hand = if (t < 2000f) Hand.BOWL_FULL else null,
            handK = if (t < 200f) seg(t, 0f, 200f) else if (t in 1800f..2000f) 1f - seg(t, 1800f, 2000f) else 1f,
            arms = if (t < 1400f) Arms.HOLD else Arms.BELLY, eyes = if (t < 1400f) Eye.OPEN else Eye.HAPPY,
            mouth = if (t in 200f..1400f) Mouth.MUNCH else Mouth.SMILE, munch = ((t - 200f) / 150f).toInt() % 2 == 0,
            fx = if (t >= 2000f) b.fx + Fx.SPARKLE else b.fx, sparkles = 1,
        )
        Motion.HUNGRY -> b.copy(
            handK = seg(t, 0f, 300f), fx = if (t < 300f) b.fx - Fx.GRUMBLE else b.fx, tilt = 3f * sinp(seg(t, 300f, 900f)),
        )
        Motion.SHIVER -> b.copy(dx = if ((t / 40f).toInt() % 2 == 0) .004f else -.004f)
        Motion.RECOVER -> b.copy(
            bandage = t < 500f, bandageT = seg(t, 0f, 500f),
            top = if (t >= 500f) Top.RIBBON else null, ribbonK = kf(t, 500f to 0f, 700f to 1.2f, 900f to 1f),
            fx = if (t in 900f..1500f) b.fx + Fx.SPARKLE else b.fx - Fx.SPARKLE, sparkles = 3,
            arms = if (t >= 1500f) Arms.WAVE else Arms.REST, wave = if (t >= 1500f) 15f * sin(twoPi * seg(t, 1500f, 2000f)) else 0f,
        )
        Motion.LOOK -> b.copy(
            lookX = kf(t, 0f to 0f, 400f to -.008f, 900f to .008f, 1200f to 0f),
            mouth = if (t < 1200f) Mouth.O else Mouth.SMILE,
        )
        Motion.STRETCH -> b.copy(
            arms = if (t < 1200f) Arms.STRETCH else Arms.REST,
            squashY = b.squashY * kf(t, 0f to 1f, 600f to 1.08f, 1200f to 1.08f, 1800f to 1f),
            eyes = if (t in 600f..1200f) Eye.CLOSED else b.eyes, mouth = if (t in 600f..1200f) Mouth.YAWN else Mouth.SMILE,
            fx = if (t >= 1200f) b.fx else b.fx - Fx.SPARKLE,
        )
        Motion.ROLLIN -> {
            val p = ease.transform(seg(t, 0f, 900f))
            b.copy(
                dx = .6f * (1f - p), wheelRad = 4f * PI.toFloat() * (1f - p), dy = -.03f * sinp(seg(t, 900f, 1200f)),
                heartK = spring(seg(t, 1200f, 1500f)), fx = if (t >= 1200f) b.fx else b.fx - Fx.HEART,
            )
        }
        Motion.FLAG -> b.copy(
            arms = if (t < 400f) Arms.REST else Arms.HOLD_UP, handK = seg(t, 0f, 400f),
            handRot = if (t >= 400f) 8f * sin(twoPi * 2f * seg(t, 400f, 1500f)) else 0f,
        )
        Motion.PARTY -> b.copy(
            hatDy = -.2f * (1f - spring(seg(t, 0f, 500f))),
            flame = if (t in 500f..2000f) 1f + .1f * sin(twoPi * t / 400f) else 1f,
            confettiT = if (t in 300f..1800f) t - 300f else -1f,
        )
        Motion.OUTFIT ->
            if (t < 300f) b.copy(neck = null, boots = false, top = null, squashY = .95f)
            else b.copy(fx = if (t in 300f..800f) b.fx + Fx.SPARKLE else b.fx, arms = if (t >= 800f) Arms.WAVE else b.arms, wave = if (t >= 800f) 15f * sin(twoPi * seg(t, 800f, 1200f)) else 0f)
        Motion.VISITOR_IN -> b
        Motion.PET -> b.copy(squashY = kf(t, 0f to 1f, 120f to .95f, 420f to 1f), heartK = spring(seg(t, 0f, 300f)))
        Motion.SNACK -> b.copy(
            snackT = if (t < 500f) seg(t, 0f, 500f) else -1f,
            mouth = if (t >= 500f) Mouth.MUNCH else b.mouth, munch = ((t - 500f) / 150f).toInt() % 2 == 0,
            eyes = if (t < 500f) Eye.OPEN else Eye.HAPPY,
        )
        Motion.BLINK2 -> b.copy(blink = 1f - .9f * maxOf(tri(t, 0f, 160f), tri(t, 320f, 480f)))
        Motion.CHEEK -> b.copy(cheekAlpha = .65f + .35f * cos(twoPi * t / 400f))
    }
}

private fun wobble(b: PetLook, t: Float) = b.copy(
    tilt = kf(t, 0f to 0f, 200f to -12f, 450f to 9f, 700f to -6f, 950f to 3f, 1200f to 0f) + b.tilt * seg(t, 1200f, 1600f),
    wheelRad = kf(t, 0f to 0f, 300f to .9f, 1200f to 0f),
    eyes = if (t < 700f) Eye.SQUEEZE else Eye.OPEN, mouth = if (t < 1200f) Mouth.WAVY else Mouth.SMILE,
    fx = if (t < 1300f) b.fx - Fx.SWEAT else b.fx,
)

private fun dizzy(b: PetLook, t: Float) = b.copy(
    fx = b.fx + Fx.STARS - Fx.SWEAT, orbit = 360f * t / 1200f, orbitA = 1f - seg(t, 1200f, 1400f),
)

/** 길동무 등장(MO-VISITOR-IN): 땅은 왼쪽 밖에서 걸어 들어오고, 나는 길동무는 사인 곡선으로 날아든다. */
fun applyVisitorIn(b: PetLook, t: Float): PetLook {
    val id = b.visitor ?: return b
    if (t < 0f || t >= 900f) return b
    val p = seg(t, 0f, 900f); val e = 1f - (1f - p) * (1f - p) * (1f - p)
    return b.copy(visitorDx = -.3f * (1f - e), visitorDy = if (id in FLYERS) .03f * sin(4f * PI.toFloat() * p) * (1f - p) else 0f)
}

/** 3.14 살아 있다는 신호. quiet(입력 없이 60초)이면 깜빡임만 남긴다. waiting(30초)이면 창밖을 보거나 존다. */
fun withIdle(l: PetLook, spec: PetSpec, t: Long, quiet: Boolean, waiting: Boolean, touchLookX: Float): PetLook {
    var r = l
    val tf = t.toFloat()
    val lively = spec.look.temperament == Temperament.LIVELY && spec.stage >= Stage.CHILD
    val period = when (spec.look.temperament) { Temperament.CALM -> 3200f; Temperament.LIVELY -> 2400f; else -> 2800f }
    if (!quiet) r = r.copy(squashY = r.squashY * (1f + .01f * (1f - cos(2f * PI.toFloat() * tf / period))))
    val w = t / 4500
    val dt = tf - (w * 4500 + 800 + (w * 2654435761L + spec.quirk * 40503L).mod(3000L))
    if (dt in 0f..160f) r = r.copy(blink = minOf(r.blink, 1f - .9f * (1f - abs(dt - 80f) / 80f)))
    if (!quiet) {
        val k = t / 30000
        val q = tf - (k * 30000 + 10000 + (k * 7919).mod(10000L))
        when (spec.quirk) {
            1 -> if (q in 0f..500f) r = r.copy(wheelRad = r.wheelRad + PI.toFloat() * q / 500f)
            2 -> if (q in 0f..300f) r = r.copy(cheekAlpha = 1f - .7f * sinp(q / 300f))
            else -> if (q in 0f..400f) r = r.copy(finTilt = r.finTilt + 6f * sin(2f * PI.toFloat() * q / 400f))
        }
        if (lively && t % 10000 < 400) r = r.copy(dy = r.dy - .03f * sinp((t % 10000) / 400f))
    }
    if (waiting) r = if (spec.quirk % 2 == 0) r.copy(eyes = Eye.CLOSED, tilt = 4f, fx = r.fx + Fx.ZZ) else r.copy(lookX = -.008f, lookY = -.006f)
    if (touchLookX != 0f) r = r.copy(lookX = touchLookX)
    return r
}
