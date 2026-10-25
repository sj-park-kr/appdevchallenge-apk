package com.carmagotchi.app.ui.pet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 3.11 소품과 머리 위 효과. 좌표는 ADULT 기준이고 호출하는 쪽이 단계 변환을 걸어 둔다. */

private val HAND = Offset(.66f, .72f)
private val HAND_UP = Offset(.71f, .64f)

internal fun Pen.hand(h: Hand, look: PetLook) {
    val piv = if (h == Hand.UMBRELLA || h == Hand.FLAG) HAND_UP else HAND
    tf({ rotate(look.handRot, piv); scale(look.handK, look.handK, piv) }) {
        when (h) {
            Hand.UMBRELLA -> {
                val c = path {
                    moveTo(.58f, .22f); arcTo(Rect(.58f, .10f, .98f, .34f), 180f, 180f, false)
                    var x = .98f
                    repeat(3) { quadraticBezierTo(x - .0667f, .22f - .04f, x - .1333f, .22f); x -= .1333f }
                    close()
                }
                // 얼굴(눈·볼)이 몸 왼쪽~가운데에 있어 손잡이는 오른쪽 가장자리로 내린다
                line(path { moveTo(.78f, .22f); lineTo(.78f, .58f) }, .012f)
                line(quad(.78f, .58f, .78f, .66f, .71f, .64f), .012f)
                fs(c, Color(0xFF8FC6EE), .012f)
            }
            Hand.BOWL_EMPTY, Hand.BOWL_FULL -> {
                val bowl = path { moveTo(.59f, .72f); arcTo(Rect(.59f, .675f, .73f, .765f), 180f, -180f, false); close() }
                fs(bowl, WHITE, .012f)
                fs(oval(.66f, .72f, .07f, .015f), WHITE, .012f)
                if (h == Hand.BOWL_EMPTY) fill(oval(.66f, .72f, .062f, .011f), Color(0xFFE3D9CC))
                else fs(path { moveTo(.60f, .72f); arcTo(Rect(.60f, .685f, .72f, .755f), 180f, 180f, false); close() }, Color(0xFFFFFDF5), .008f)
            }
            Hand.FLAG -> {
                line(path { moveTo(.71f, .64f); lineTo(.75f, .30f) }, .010f)
                for (r in 0 until 2) for (c in 0 until 3) {
                    val x = .75f + c * .04f; val y = .30f + r * .04f
                    fill(rrect(x, y, x + .04f, y + .04f, 0f), if ((r + c) % 2 == 0) INK else WHITE)
                }
                line(rrect(.75f, .30f, .87f, .38f, 0f), .006f)
            }
        }
    }
}

internal fun Pen.top(t: Top, look: PetLook) {
    when (t) {
        Top.RIBBON -> tf({ scale(look.ribbonK, look.ribbonK, Offset(.56f, .36f)) }) {
            val col = Color(0xFFF28C6B)
            fs(poly(.56f, .36f, .51f, .34f, .51f, .38f), col, .010f); fs(poly(.56f, .36f, .61f, .34f, .61f, .38f), col, .010f)
            fs(oval(.56f, .36f, .012f), col, .010f)
        }
        Top.PARTY_HAT -> tf({ translate(0f, look.hatDy) }) {
            fs(poly(.36f, .355f, .50f, .335f, .40f, .20f), Color(0xFFF6A6B2), .012f)
            line(path { moveTo(.368f, .31f); lineTo(.455f, .295f) }, .010f, WHITE); line(path { moveTo(.38f, .255f); lineTo(.435f, .245f) }, .010f, WHITE)
            fs(oval(.40f, .20f, .018f), WHITE, .010f)
        }
        Top.CHERRY -> {
            for (i in 0 until 5) { val a = i * 72f * PI.toFloat() / 180f; fill(oval(.56f + .016f * cos(a), .36f + .016f * sin(a), .014f), Color(0xFFF9D5DF)) }
            fill(oval(.56f, .36f, .008f), AMBER)
        }
        Top.FUR_HAT -> {
            fs(path { moveTo(.34f, .37f); arcTo(Rect(.34f, .28f, .66f, .46f), 180f, 180f, false); close() }, WHITE, .012f)
            fs(rrect(.33f, .355f, .67f, .395f, .02f), Color(0xFFC7CDD8), .012f)
            fs(oval(.50f, .275f, .02f), WHITE, .010f)
        }
    }
}

internal fun Pen.cake(n: Int, flame: Float) {
    fs(rrect(.12f, .79f, .28f, .86f, .008f), Color(0xFFFFF3E0), .012f)
    fill(path { moveTo(.12f, .79f); for (i in 0 until 4) quadraticBezierTo(.12f + .02f + .04f * i, .815f, .12f + .04f * (i + 1), .79f); close() }, WHITE)
    fill(oval(.20f, .778f, .015f), Color(0xFFF07A66))
    val c = n.coerceIn(1, 5)
    for (i in 0 until c) {
        val x = .20f + (i - (c - 1) / 2f) * .026f
        fs(rrect(x - .006f, .74f, x + .006f, .79f, 0f), Color(0xFF8FC6EE), .006f)
        fill(drop(x, .74f - .03f * flame, .03f * flame), Color(0xFFF5D26B))
    }
}

internal val HEART = path {
    moveTo(.5f, .9f); cubicTo(.1f, .6f, 0f, .35f, .2f, .2f); cubicTo(.35f, .1f, .5f, .2f, .5f, .32f)
    cubicTo(.5f, .2f, .65f, .1f, .8f, .2f); cubicTo(1f, .35f, .9f, .6f, .5f, .9f); close()
}

private fun heart(cx: Float, cy: Float, s: Float): Path {
    val p = Path(); p.addPath(HEART)
    val m = androidx.compose.ui.graphics.Matrix(); m.translate(cx - s / 2, cy - s / 2); m.scale(s, s)
    p.transform(m); return p
}

private val RAIN_X = floatArrayOf(.24f, .30f, .36f, .88f, .92f, .96f)

/** MO-GROW-HEART(9.2절): 0~600ms 하트 5개가 반지름 0.42 -> 0.12 원 위에서 모이며 사라지고, 끝에 머리 위에 하트 3개가 퐁 나타난다. */
internal fun Pen.growHearts(look: PetLook) {
    val red = Color(0xFFF07A66)
    if (look.heartRing >= 0f) for (i in 0 until 5) {
        val a = (-90f + 72f * i) * PI.toFloat() / 180f; val r = .42f - .30f * look.heartRing
        val h = heart(.5f + r * cos(a), .6f + r * sin(a), .10f); fill(h, red, 1f - look.heartRing); line(h, .008f, a = 1f - look.heartRing)
    }
    if (look.heartPop >= 0f) for (i in 0 until 3) {
        val k = ((look.heartPop * 400f - 100f * i) / 100f).coerceIn(0f, 1f)
        if (k > 0f) { val h = heart(.40f + .10f * i, .17f, .12f * k); fill(h, red); line(h, .008f) }
    }
}

internal fun Pen.fx(look: PetLook, sec: Color) {
    val f = look.fx
    if (Fx.STAR_ONE in f) fs(star4(.47f, .25f, .025f), AMBER, .008f)
    if (Fx.STARS in f) for (i in 0 until 3) {
        val a = (look.orbit + 120f * i) * PI.toFloat() / 180f
        val x = .47f + .12f * cos(a); val y = .27f + .035f * sin(a)
        tf({ }) { fill(star4(x, y, .025f), AMBER, look.orbitA); line(star4(x, y, .025f), .008f, a = look.orbitA) }
    }
    if (Fx.ZZ in f) for ((i, s) in listOf(Triple(.30f, .36f, .03f), Triple(.25f, .30f, .04f), Triple(.19f, .23f, .05f)).withIndex()) {
        if (i < look.zz) line(poly(s.first, s.second, s.first + s.third, s.second, s.first, s.second + s.third, s.first + s.third, s.second + s.third, close = false), .010f, sec)
    }
    if (Fx.HEART in f) { val h = heart(.70f, .30f, .06f * look.heartK); fill(h, Color(0xFFF07A66)); line(h, .008f) }
    if (Fx.SPARKLE in f) for ((i, s) in listOf(Triple(.30f, .40f, .026f), Triple(.74f, .34f, .022f), Triple(.36f, .27f, .02f)).withIndex()) {
        if (i < look.sparkles) { val st = star4(s.first, s.second, s.third * look.sparkleK); fill(st, if (i % 2 == 0) WHITE else AMBER); line(st, .008f) }
    }
    if (Fx.RAIN in f) for (i in 0 until 6) {
        val y = if (look.rainT < 0f) .26f + .06f * i else .08f + .5f * (((look.rainT - i * 100f) / 600f).mod(1f))
        fs(drop(RAIN_X[i], y, .03f), Color(0xFF7FB8E6), .008f)
    }
    if (Fx.SWEAT in f) fs(drop(.66f, .44f, .035f), Color(0xFF8FC6EE), .008f)
    if (Fx.SHIVER in f) for (side in listOf(-1f, 1f)) for (i in 0 until 3) {
        val x = .5f + side * (.27f + .03f * i)
        line(quad(x, .84f, x + side * .012f, .87f, x, .90f), .008f, sec)
    }
    if (Fx.GRUMBLE in f) for (i in 0 until 2) {
        val y = .73f + .035f * i
        line(path { moveTo(.72f, y); quadraticBezierTo(.74f, y - .02f, .76f, y); quadraticBezierTo(.78f, y + .02f, .80f, y) }, .008f, sec)
    }
}

/** 색종이(MO-PARTY). t = 시작 후 ms(300 이후). 8장이 엇갈려 떨어진다. */
internal fun Pen.confetti(t: Float) {
    val cols = listOf(Color(0xFFF07A66), Color(0xFFF5D26B), Color(0xFF7FB8E6), Color(0xFFA8D58A))
    for (i in 0 until 8) {
        val p = ((t - i * 120f) / 1200f).coerceIn(0f, 1f)
        if (p in 0.001f..0.999f) tf({ rotate(p * 360f, Offset(.2f + i * .08f, .1f + .6f * p)) }) {
            fill(rrect(.2f + i * .08f - .01f, .1f + .6f * p - .006f, .2f + i * .08f + .01f, .1f + .6f * p + .006f, 0f), cols[i % 4])
        }
    }
}
