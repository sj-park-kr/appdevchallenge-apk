package com.carmagotchi.app.ui.pet

import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.DrawTransform
import com.carmagotchi.app.domain.Antenna
import com.carmagotchi.app.domain.CarLook
import com.carmagotchi.app.domain.CheekColor
import com.carmagotchi.app.domain.EyeShape
import com.carmagotchi.app.domain.Mirror
import com.carmagotchi.app.domain.Rim
import com.carmagotchi.app.domain.Stage
import com.carmagotchi.app.domain.clamped
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** 정적 색(테마와 무관). 3.9, 4.2 */
val INK = Color(0xFF2A2530)
val AMBER = Color(0xFFF2A23A)
val WHITE = Color.White

class Pal(val body: Color, val shade: Color)

/** P0~P11(3.4절). 볼 색은 팔레트와 따로 carLook.cheek로 고른다. */
val PALETTES = listOf(
    Pal(Color(0xFFF6EBD9), Color(0xFFD9BD94)), Pal(Color(0xFFCFE3BF), Color(0xFF9FC086)),
    Pal(Color(0xFFC3E0F4), Color(0xFF8BBBE0)), Pal(Color(0xFFDCD2F2), Color(0xFFB3A0E0)),
    Pal(Color(0xFFF9D3B8), Color(0xFFE8A479)), Pal(Color(0xFFC2EBE0), Color(0xFF86CDB9)),
    Pal(Color(0xFFD9BEA4), Color(0xFFB48E70)), Pal(Color(0xFFCBD2DD), Color(0xFF9BA7BA)),
    Pal(Color(0xFFDDE0E4), Color(0xFFAEB5BE)), Pal(Color(0xFFF5F4EF), Color(0xFFCFCCC1)),
    Pal(Color(0xFFDCD6CE), Color(0xFFB5ACA0)), Pal(Color(0xFFAEDFE8), Color(0xFF74BCCB)),
)
internal fun cheekColor(c: CheekColor) = when (c) {
    CheekColor.AMBER -> AMBER; CheekColor.ORANGE -> Color(0xFFE8823F); CheekColor.CORAL -> Color(0xFFF07A66); CheekColor.ICE -> Color(0xFF8FC6EE)
}
val PALETTE_NAMES = listOf("크림", "세이지", "스카이", "라벤더", "피치", "민트", "코코아", "슬레이트")
val PATTERN_NAMES = listOf("민무늬", "물방울", "레이싱 줄", "배 무늬", "옆 줄무늬", "모자 무늬")

// ---- 정규화 좌표(0~1) 도형 도구. 모든 그리기는 한 변 S px 정사각형을 0~1로 본다(3.3절). ----
internal fun path(f: Path.() -> Unit) = Path().apply(f)
internal fun oval(cx: Float, cy: Float, rx: Float, ry: Float = rx) = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }
internal fun rrect(l: Float, t: Float, r: Float, b: Float, rad: Float) = Path().apply { addRoundRect(RoundRect(l, t, r, b, rad, rad)) }
internal fun poly(vararg v: Float, close: Boolean = true) = path {
    moveTo(v[0], v[1]); for (i in 2 until v.size step 2) lineTo(v[i], v[i + 1]); if (close) close()
}
internal fun quad(x1: Float, y1: Float, cx: Float, cy: Float, x2: Float, y2: Float) = path { moveTo(x1, y1); quadraticBezierTo(cx, cy, x2, y2) }
internal fun star4(cx: Float, cy: Float, r: Float, inner: Float = .38f) = path {
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4f; val rr = if (i % 2 == 0) r else r * inner
        val x = cx + rr * cos(a); val y = cy + rr * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
/** 물방울(상자 0~1 경로를 높이 h로) */
internal fun drop(cx: Float, top: Float, h: Float) = path {
    val w = h; val x = { u: Float -> cx + (u - .5f) * w }; val y = { v: Float -> top + v * h }
    moveTo(x(.5f), y(0f)); cubicTo(x(.5f), y(0f), x(.9f), y(.5f), x(.9f), y(.68f))
    cubicTo(x(.9f), y(.9f), x(.72f), y(1f), x(.5f), y(1f)); cubicTo(x(.28f), y(1f), x(.1f), y(.9f), x(.1f), y(.68f))
    cubicTo(x(.1f), y(.5f), x(.5f), y(0f), x(.5f), y(0f)); close()
}

/** 칠 + 잉크 외곽선 도구. locked면 같은 경로를 잠금 모양(채움 sunken, 점선 외곽선)으로 그린다(3.12절 6번). */
internal class Pen(
    val d: DrawScope, val minW: Float, val dp: Float = 0f, val ow: Float = .016f,
    val lockFill: Color? = null, val lockLine: Color? = null,
) {
    val locked get() = lockFill != null
    fun fill(p: Path, c: Color, a: Float = 1f) { if (lockFill == null) d.drawPath(p, c, alpha = a) }
    fun line(p: Path, w: Float, c: Color = INK, a: Float = 1f) { if (lockFill == null)
        d.drawPath(p, c, alpha = a, style = Stroke(max(w, minW), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
    fun fs(p: Path, c: Color, w: Float = ow) {
        if (lockFill != null) {
            d.drawPath(p, lockFill)
            d.drawPath(p, lockLine!!, style = Stroke(max(w, minW), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * dp, 4 * dp))))
        } else { fill(p, c); line(p, w) }
    }
    fun tf(t: androidx.compose.ui.graphics.drawscope.DrawTransform.() -> Unit, b: () -> Unit) = d.withTransform(t) { b() }
    fun clip(p: Path, b: () -> Unit) = d.clipPath(p) { b() }
}

internal class StageSpec(val sx: Float, val sy: Float, val face: Float, val fin: Float, val arm: Float, val wheel: Float) {
    fun x(v: Float) = .5f + (v - .5f) * sx
    fun y(v: Float) = .92f + (v - .92f) * sy
}
internal fun stageSpec(s: Stage) = when (s) {
    Stage.EGG -> StageSpec(.8f, 1f, .85f, 1f, 1f, 0f)
    Stage.BABY -> StageSpec(.80f, .72f, .90f, .45f, .7f, .05f)
    Stage.CHILD -> StageSpec(.90f, .86f, .95f, .75f, .85f, .048f)
    Stage.ADULT -> StageSpec(1f, 1f, 1f, 1f, 1f, .055f)
    Stage.BUDDY -> StageSpec(1.04f, 1.04f, 1f, 1.05f, 1f, .057f)
}

internal val P0 = Offset(.5f, .92f)
internal val F0 = Offset(.63f, .37f)
internal val EGG_SHELL = Color(0xFFFFF9F0)

/** 3.15절 1번 매개변수 몸 경로. 세단형·길이 보통이면 이전 몸과 같다. */
internal fun bodyPath(g: BodyGeo) = path {
    val b = BodyGeo.B; val l = .5f - g.w; val rt = .5f + g.w; val r = g.r; val h = g.h; val d = g.d
    moveTo(l + r, b); lineTo(rt - r, b)
    cubicTo(rt - .4f * r, b, rt, b - .3f * r, rt, b - .9f * r)
    cubicTo(rt, b - .54f * h, rt - .2f * r, b - .875f * h, rt - r, b - h)
    cubicTo(rt - 1.8f * r, b - h - d, l + 1.8f * r, b - h - d, l + r, b - h)
    cubicTo(l + .2f * r, b - .875f * h, l, b - .54f * h, l, b - .9f * r)
    cubicTo(l, b - .3f * r, l + .4f * r, b, l + r, b); close()
}
internal fun finPath() = path {
    moveTo(.55f, .37f); cubicTo(.57f, .31f, .62f, .255f, .70f, .24f); cubicTo(.69f, .28f, .69f, .33f, .71f, .40f); close()
}
internal fun eggPath() = path {
    moveTo(.50f, .26f); cubicTo(.64f, .26f, .75f, .47f, .75f, .66f); cubicTo(.75f, .82f, .64f, .92f, .50f, .92f)
    cubicTo(.36f, .92f, .25f, .82f, .25f, .66f); cubicTo(.25f, .47f, .36f, .26f, .50f, .26f); close()
}
/** 알 얼굴 창: 꼭짓점 16개, 반지름 1.00/0.80 번갈아 */
internal fun windowPath() = path {
    for (i in 0 until 16) {
        val a = i * 2f * PI.toFloat() / 16f; val k = if (i % 2 == 0) 1f else .8f
        val x = .5f + .16f * k * cos(a); val y = .60f + .10f * k * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
/** BABY 껍데기 톱니 선(y = 0.78 ± 0.022, x 0.22~0.78, 이 8개) */
private fun jag(lower: Boolean) = path {
    moveTo(.22f, .78f)
    for (i in 1..16) lineTo(.22f + .56f * i / 16f, .78f + if (i % 2 == 1) -.022f else .022f)
    if (lower) { lineTo(.78f, 1f); lineTo(.22f, 1f) } else { lineTo(.78f, 0f); lineTo(.22f, 0f) }
    close()
}

private fun Pen.pattern(i: Int, c: Color, egg: Boolean) {
    when (i) {
        1 -> { fill(oval(.30f, .45f, .030f), c); fill(oval(.72f, .50f, .026f), c); fill(oval(.27f, .76f, .022f), c) }
        2 -> { fill(rrect(.455f, .30f, .485f, .50f, 0f), c); fill(rrect(.515f, .30f, .545f, .50f, 0f), c) }
        3 -> fill(oval(.50f, .80f, .17f, .075f), if (egg) c else WHITE, .6f)
        4 -> for (y in listOf(.50f, .555f, .61f)) {
            fill(rrect(.18f, y - .011f, .25f, y + .011f, .011f), c); fill(rrect(.75f, y - .011f, .82f, y + .011f, .011f), c)
        }
        5 -> fill(path { moveTo(.18f, .48f); quadraticBezierTo(.50f, .40f, .82f, .48f); lineTo(.82f, .30f); lineTo(.18f, .30f); close() }, c)
    }
}

fun DrawScope.drawPet(spec: PetSpec, look: PetLook, dark: Boolean, secondary: Color) {
    val s = size.minDimension
    val stage = look.stageOverride ?: spec.stage
    val ss = stageSpec(stage)
    // 5단계 보이는 부위(3.15절 11번): BABY는 바퀴·차고·뒷날개를 기본값으로 둔다
    val cl = spec.carLook.clamped().let { if (stage < Stage.CHILD) it.copy(wheelSize = 1, rim = Rim.DOT, ride = 0, wing = 0) else it }
    val g = BodyGeo(cl)
    val pal = PALETTES[cl.color]
    val minW = 2.dp.toPx() / s
    withTransform({ scale(s, s, Offset.Zero) }) {
        val p = Pen(this, minW)
        if (look.starry) p.starryBg()
        if (look.glowA > 0f) p.line(oval(.5f, .6f, look.glowR, look.glowR), .02f, AMBER, look.glowA)
        if (look.heartRing >= 0f || look.heartPop >= 0f) p.growHearts(look)
        // 1 바닥 그림자
        p.fill(oval(.5f, .925f, .27f * ss.sx * if (stage == Stage.EGG) 1f else g.kx, .03f), Color.Black, if (dark) .4f else .1f)
        if (look.cushion) p.cushion()
        p.tf({ translate(look.dx, look.dy); rotate(look.tilt, P0); scale(1f / sqrt(look.squashY), look.squashY, P0) }) {
            if (stage == Stage.EGG) p.egg(cl, look, pal, secondary)
            else p.pet(cl, g, look, stage, ss, pal, secondary)
        }
        if (look.cake > 0) p.cake(look.cake, look.flame)
        look.visitor?.let { p.visitorOn(it, look) }
        if (look.confettiT >= 0f) p.confetti(look.confettiT)
        if (look.snackT >= 0f) p.fill(oval(.12f + (.50f - .12f) * look.snackT, .95f - .35f * look.snackT - .2f * sin(PI.toFloat() * look.snackT), .02f), Color(0xFFE7B98A))
    }
}

private fun Pen.starryBg() {
    fill(oval(.5f, .52f, .47f), Color(0xFF2F3D5C), .9f)
    for ((x, y) in listOf(.18f to .30f, .30f to .16f, .62f to .14f, .80f to .26f, .86f to .50f, .12f to .56f)) fill(oval(x, y, .008f), Color(0xFFFFF3C4))
}

private fun Pen.cushion() {
    fill(rrect(.72f, .85f, .95f, .925f, .035f), Color(0xFFF6A6B2)); line(rrect(.72f, .85f, .95f, .925f, .035f), .012f)
    fill(oval(.835f, .8875f, .008f), WHITE)
}

private fun Pen.pet(cl: CarLook, g: BodyGeo, look: PetLook, stage: Stage, ss: StageSpec, pal: Pal, sec: Color) {
    val baby = stage == Stage.BABY
    // 변환 순서(3.15절 2번): 몸 형(tt) → 차고 → 단계 → 자세. st = 차고+단계, bt = 거기에 몸 형까지.
    val tt: DrawTransform.() -> Unit = { scale(g.kx, g.ky, Offset(.5f, BodyGeo.B)) }
    val st: DrawTransform.() -> Unit = { scale(ss.sx, ss.sy, P0); translate(0f, g.lift) }
    val bt: DrawTransform.() -> Unit = { st(); tt() }
    // 2 뒷날개 GT 윙(몸 뒤) → 뒤 소품(배낭)
    if (cl.wing == 2) tf(st) { // 지붕 뒤쪽 끝(어깨, 몸 윗선 위)에 지지대가 닿는다
        val b = g.wingBase
        line(path { moveTo(b.x, b.y + .01f); lineTo(b.x - .01f, b.y - .024f) }, .014f)
        tf({ rotate(-6f, Offset(b.x - .02f, b.y - .040f)) }) { fs(rrect(b.x - .11f, b.y - .056f, b.x + .07f, b.y - .024f, .016f), pal.shade, .012f) }
    }
    if (look.back) tf(bt) {
        fs(rrect(.11f, .56f, .25f, .80f, .03f), Color(0xFFC99A6E), .016f)
        line(path { moveTo(.115f, .62f); lineTo(.245f, .62f) }, .010f)
    }
    // 3 바퀴발 (BABY도 그림: 몸통 아래로 삐져나와 보임). 땅에 붙어 차고와 상관없다.
    fun wheels() {
        val r = ss.wheel * floatArrayOf(.82f, 1f, 1.18f)[cl.wheelSize]; val cy = .92f - r; val rs = r / .055f
        for (sg in listOf(-1, 1)) {
            val x = ss.x(.5f + sg * .4375f * g.w)
            fs(oval(x, cy, r), if (look.boots) Color(0xFFF5D26B) else pal.shade, .016f)
            if (cl.rim == Rim.DOT) {
                fill(oval(x, cy, .020f), INK)
                val hx = .006f * cos(look.wheelRad) + .006f * sin(look.wheelRad)
                val hy = -.006f * cos(look.wheelRad) + .006f * sin(look.wheelRad)
                fill(oval(x + hx, cy + hy, .006f), WHITE)
            } else tf({ rotate(look.wheelRad * 180f / PI.toFloat(), Offset(x, cy)) }) { rim(cl.rim, x, cy, r, rs) }
        }
    }
    if (!baby) wheels()
    // 3.5 사이드미러(바퀴 다음, 몸 뒤 팔 전)
    for (left in listOf(true, false)) mirror(cl.mirror, left, g, st, pal, if (baby) .8f else 1f)
    // 4 몸 뒤 팔
    arm(look.arms.l, look, ss, g, pal, left = true, front = false)
    arm(look.arms.r, look, ss, g, pal, left = false, front = false)
    // 5 안테나 (+ BUDDY 단짝 별). 밑동은 몸 형을 따라 옮기고 크기는 typeScale
    tf({ st(); translate(g.fin.x - F0.x, g.fin.y - F0.y); scale(g.typeScale, g.typeScale, F0) }) {
        tf({ rotate(-look.finTilt, F0); scale(ss.fin, ss.fin, F0) }) { antenna(cl.antenna, pal) }
        if (stage == Stage.BUDDY) {
            val o = when (cl.antenna) { Antenna.FIN -> Offset(.025f, -.05f); Antenna.ROD -> Offset(.055f, -.18f); Antenna.DOME -> Offset(0f, -.085f) }
            fs(star4(F0.x + o.x, F0.y + o.y, .030f), AMBER, .008f)
        }
    }
    // 6 몸 → 무늬 → 립 스포일러 → 껍데기
    tf(st) {
        val b = bodyPath(g)
        fill(b, pal.body)
        clip(b) { tf(tt) { pattern(cl.pattern, pal.shade, false) } }
        line(b, .016f)
        if (cl.wing == 1) { val w = g.wingBase; val r = g.r; fs(poly(w.x, w.y + .005f, w.x - 1.4f * r, w.y - .025f, w.x - .8f * r, w.y + .04f), pal.shade, .012f) }
        // 7 목·가슴, 배지
        neckAndBadge(look, b, g)
    }
    if (baby) {
        shell(look)
        wheels() // 껍데기(y .78~.92)가 덮으므로 위에 다시 그림
        if (look.shellTop in 0f..1f) tf({ translate(0f, -.15f * look.shellTop); rotate(-20f * look.shellTop, Offset(.5f, .5f)) }) {
            clip(jag(false)) { fill(eggPath(), EGG_SHELL, 1f - look.shellTop); line(eggPath(), .016f, a = 1f - look.shellTop) }
        }
    }
    // 8 얼굴: 늘이지 않고 typeFace로 줄인다(3.15절 3번)
    val ax = ss.x(.5f) - if (look.heading == Heading.SIDE) g.sideShift(ss) else 0f; val ay = ss.y(g.faceY + g.lift); val k = ss.face * look.faceK * g.typeFace
    if (look.heading == Heading.BACK) tf(bt) { rear() }
    else face(look, cl, ax - .09f * k, ay - .03f * k, ax + .09f * k, ay - .03f * k, k, ax, ay + .03f * k, k,
        ax - .175f * k, ay + .035f * k, ax + .175f * k, ay + .035f * k, k)
    tf(bt) {
        // 9 이마
        if (look.bandage) bandage(look.bandageT)
        if (look.fx.contains(Fx.SMUDGE)) for ((x, y) in listOf(.36f to .50f, .63f to .49f, .55f to .69f)) fill(oval(x, y, .012f), Color(0xFF9AA3B5), .8f)
    }
    // 10 몸 앞 팔
    arm(look.arms.l, look, ss, g, pal, left = true, front = true)
    arm(look.arms.r, look, ss, g, pal, left = false, front = true)
    tf(bt) {
        look.hand?.let { hand(it, look) }          // 11
        look.top?.let { top(it, look) }            // 12
    }
    tf(st) { fx(look, sec) }                       // 13
    tf(bt) { if (look.lamp) lamp(.19f, .62f, .028f, cheekColor(cl.cheek)) } // 깜빡이: 진행 방향(그림 왼쪽) 앞 모서리
}

private fun Pen.lamp(x: Float, y: Float, r: Float, c: Color) { fill(oval(x, y, r * 2.2f), c, .35f); fs(oval(x, y, r), c, .008f) }

private fun halfOval(cx: Float, cy: Float, rx: Float, ry: Float) = path {
    moveTo(cx - rx, cy); arcTo(Rect(cx - rx, cy - ry, cx + rx, cy + ry), 180f, 180f, false); close()
}

/** 3.15절 5번. 밑동은 F0(세단 좌표)에 두고 호출하는 쪽이 옮긴다. */
private fun Pen.antenna(a: Antenna, pal: Pal) {
    val x = F0.x; val y = F0.y
    when (a) {
        Antenna.FIN -> fs(finPath(), pal.shade)
        Antenna.ROD -> {
            line(path { moveTo(x, y); lineTo(x + .055f, y - .15f) }, .014f)
            fs(oval(x + .055f, y - .15f, .012f), pal.shade, .008f)
            fs(halfOval(x, y, .030f, .018f), pal.shade, .010f)
        }
        Antenna.DOME -> { fs(halfOval(x, y, .055f, .045f), pal.shade); line(path { moveTo(x, y - .04f); lineTo(x, y - .01f) }, .008f) }
    }
}

/** 3.15절 6번 휠 무늬(DOT은 wheels()가 그린다). 중심 (x, y), 바퀴 반지름 r, rs = r / .055 */
private fun Pen.rim(m: Rim, x: Float, y: Float, r: Float, rs: Float) {
    when (m) {
        Rim.DOT -> {}
        Rim.STAR -> {
            for (i in 0 until 5) { val a = (i * 72f - 90f) * PI.toFloat() / 180f; line(path { moveTo(x, y); lineTo(x + .7f * r * cos(a), y + .7f * r * sin(a)) }, .008f * rs) }
            fill(oval(x, y, .010f * rs), INK)
        }
        Rim.FLOWER -> {
            for (i in 0 until 4) { val a = i * PI.toFloat() / 2f; fill(oval(x + .45f * r * cos(a), y + .45f * r * sin(a), .010f * rs), INK) }
            fill(oval(x, y, .007f * rs), INK)
        }
        Rim.RING -> { line(oval(x, y, .62f * r), .008f * rs); fill(oval(x, y, .010f * rs), INK) }
    }
}

/** 3.15절 7번 사이드미러(귀). M = (몸 옆면 바깥 .012, 어깨 높이 .78H). 뾰족한 끝이 몸 쪽을 향한다. */
private fun Pen.mirror(m: Mirror, left: Boolean, g: BodyGeo, st: DrawTransform.() -> Unit, pal: Pal, prop: Float) {
    if (m == Mirror.NONE) return
    val sc = g.typeScale * prop
    val mx = .5f + (if (left) -1f else 1f) * (g.w + .012f); val my = BodyGeo.B - .78f * g.height
    tf({ st(); translate(mx, my); scale(sc, sc, Offset.Zero) }) {
        when (m) {
            Mirror.ROUND -> { fs(oval(0f, 0f, .034f, .028f), pal.shade, .012f); fill(oval(0f, 0f, .020f, .015f), WHITE, .55f) }
            Mirror.SQUARE -> { fs(rrect(-.032f, -.025f, .032f, .025f, .014f), pal.shade, .012f); fill(rrect(-.019f, -.013f, .019f, .013f, .008f), WHITE, .55f) }
            Mirror.TEAR -> tf({ rotate(if (left) 70f else -70f, Offset.Zero) }) {
                fs(drop(0f, -.030f, .056f), pal.shade, .012f); fill(oval(0f, .008f, .016f, .012f), WHITE, .55f)
            }
            Mirror.NONE -> {}
        }
    }
}

/** 뒷모습: 후미등 둘과 뒤 번호판(얼굴 자리) */
private fun Pen.rear() {
    for (x in listOf(.34f, .66f)) fs(oval(x, .70f, .028f), Color(0xFFE5484D), .008f)
    fs(rrect(.43f, .76f, .57f, .815f, .01f), WHITE, .008f)
}

/** 3.15절 4번: 몸 뒤 팔은 몸 옆면을 따라 x가 옮고, 모든 팔은 y가 몸 형·차고를 따른다. */
private fun Pen.arm(a: Arm, look: PetLook, ss: StageSpec, g: BodyGeo, pal: Pal, left: Boolean, front: Boolean) {
    if (a.front != front) return
    val dx = if (a.front) 0f else (g.w - .32f) * if (left) -1f else 1f
    val sk = ss.arm * g.typeScale
    val c = Offset(ss.x(a.x + dx), ss.y(g.y(a.y) + g.lift))
    val waving = !left && look.arms == Arms.WAVE && look.wave != 0f
    val flag = !left && look.hand == Hand.FLAG && look.handRot != 0f
    val piv = if (waving) Offset(ss.x(.80f + (g.w - .32f)), ss.y(g.y(.62f) + g.lift)) else c
    tf({ if (waving) rotate(look.wave, piv) else if (flag) rotate(look.handRot, piv); rotate(a.rot, c) }) {
        val o = oval(c.x, c.y, a.rx * sk, a.ry * sk)
        fs(o, pal.body)
        if (look.neck == Neck.HANBOK) clip(o) {
            for ((i, col) in listOf(Color(0xFFF07A66), Color(0xFFF5D26B), Color(0xFF7FB8E6)).withIndex()) {
                val y = c.y + (i - 1) * a.ry * sk * .55f
                fill(rrect(c.x - a.rx * sk, y - .006f, c.x + a.rx * sk, y + .006f, 0f), col)
            }
        }
    }
}

/** body는 단계·차고 좌표의 실제 몸 경로(클립용), 그리는 것은 몸 형 변환(3.15절 2번)을 건다. */
private fun Pen.neckAndBadge(look: PetLook, body: Path, g: BodyGeo) {
    fun t(b: () -> Unit) = tf({ scale(g.kx, g.ky, Offset(.5f, BodyGeo.B)) }, b)
    when (look.neck) {
        Neck.BANDANA -> t {
            fs(poly(.38f, .70f, .62f, .70f, .50f, .79f), Color(0xFF8FC6EE), .012f)
            for ((x, y) in listOf(.44f to .715f, .56f to .715f, .50f to .76f)) fill(oval(x, y, .007f), WHITE)
        }
        Neck.HANBOK -> {
            clip(body) { t { fill(rrect(.1f, .76f, .9f, .9f, 0f), Color(0xFFF6A6B2)) } }
            t {
                val c = quad(.40f, .68f, .50f, .84f, .60f, .68f)
                line(c, .035f, INK); line(c, .025f, WHITE)
                for (dx in listOf(0f, .018f)) fs(oval(.53f + dx, .78f, .012f, .05f), Color(0xFFF07A66), .008f)
            }
        }
        Neck.SCARF -> {
            clip(body) { t { fill(rrect(.1f, .70f, .9f, .75f, 0f), Color(0xFFF07A66)) } }
            t {
                line(path { moveTo(.18f, .70f); lineTo(.82f, .70f) }, .010f, a = 0f)
                tf({ rotate(10f, Offset(.69f, .78f)) }) {
                    fs(rrect(.665f, .72f, .715f, .84f, .02f), Color(0xFFF07A66), .012f)
                    for (x in listOf(.675f, .69f, .705f)) line(path { moveTo(x, .84f); lineTo(x, .865f) }, .008f)
                }
            }
        }
        null -> {}
    }
    t {
        when (look.badge) {
            Badge.STAR -> { fs(oval(.70f, .77f, .028f), WHITE, .010f); fill(star4(.70f, .77f, .016f), AMBER) }
            Badge.FRIEND -> {
                fs(oval(.70f, .77f, .028f), WHITE, .010f)
                fill(oval(.693f, .77f, .010f), Color(0xFFF07A66)); fill(oval(.707f, .77f, .010f), Color(0xFFF07A66))
            }
            null -> {}
        }
    }
}

private fun Pen.shell(look: PetLook) {
    val egg = path { addPath(eggPath()) }
    tf({ scale(1.06f, 1f, P0) }) {
        clip(jag(true)) { fill(egg, EGG_SHELL); line(egg, .016f) }
    }
    line(path { moveTo(.22f, .78f); for (i in 1..16) lineTo(.22f + .56f * i / 16f, .78f + if (i % 2 == 1) -.022f else .022f) }, .012f)
}

private fun Pen.egg(cl: CarLook, look: PetLook, pal: Pal, sec: Color) {
    val e = eggPath()
    fill(e, EGG_SHELL)
    clip(e) { pattern(cl.pattern, pal.body, true) }
    line(e, .016f)
    if (look.crack) line(poly(.44f, .33f, .47f, .36f, .50f, .32f, .53f, .37f, .58f, .34f, close = false), .010f)
    if (look.lamp) lamp(.30f, .60f, .024f, cheekColor(cl.cheek)) // 깜빡이: 알 껍데기 안쪽, 얼굴 창 옆
    if (look.heading == Heading.BACK) { rear(); return }
    if (look.eggClosed) return
    val w = windowPath()
    if (look.windowT < 1f) {
        val pm = PathMeasure(); pm.setPath(w, true)
        val seg = Path(); pm.getSegment(0f, pm.length * look.windowT, seg, true)
        line(seg, .012f); return
    }
    fill(w, pal.body)
    val k = look.faceK; val sh = if (look.heading == Heading.SIDE) -.05f else 0f
    clip(w) {
        face(look, cl, .5f + sh - .068f * k, .60f - .018f * k, .5f + sh + .068f * k, .60f - .018f * k, .85f * k, .5f + sh, .63f, .85f * k,
            .5f + sh - .11f * k, .635f, .5f + sh + .11f * k, .635f, .8f * k)
    }
    line(w, .012f)
    if (look.shardT in 0f..1f) for (i in 0 until 3) {
        val a = (-70f + i * 70f) * PI.toFloat() / 180f; val t = look.shardT
        val cx = .5f + sin(a) * .18f * t; val cy = .60f - cos(a) * .18f * t
        tf({ rotate(40f * t * (i - 1 + .5f), Offset(cx, cy)) }) { fs(poly(cx - .02f, cy + .015f, cx + .02f, cy + .015f, cx, cy - .02f), EGG_SHELL, .008f) }
    }
}

private fun Pen.bandage(t: Float) {
    tf({ translate(.06f * t, -.06f * t); rotate(-20f + 50f * t, Offset(.40f, .44f)) }) {
        val a = 1f - t
        fill(rrect(.35f, .4225f, .45f, .4575f, .017f), Color(0xFFF3D3B0), a)
        line(rrect(.35f, .4225f, .45f, .4575f, .017f), .008f, a = a)
        fill(rrect(.3825f, .4225f, .4175f, .4575f, 0f), Color(0xFFE6BC92), a)
        for (x in listOf(.37f, .39f, .41f, .43f)) fill(oval(x, .44f, .003f), INK, a)
    }
}

// ---- 얼굴(3.6) ----
private fun Pen.face(
    look: PetLook, cl: CarLook, lx: Float, ly: Float, rx: Float, ry: Float, ke: Float,
    mx: Float, my: Float, km: Float, cx1: Float, cy1: Float, cx2: Float, cy2: Float, kc: Float,
) {
    for ((cx, cy) in listOf(cx1 to cy1, cx2 to cy2)) {
        val r = rrect(cx - .0375f * kc, cy - .016f * kc, cx + .0375f * kc, cy + .016f * kc, .016f * kc)
        fill(r, cheekColor(cl.cheek), look.cheekAlpha)
    }
    eye(look, cl, lx, ly, -1, ke); eye(look, cl, rx, ry, 1, ke)
    mouth(look, mx, my, km)
}

/** 3.15절 10번: OPEN 눈의 바탕 도형만 바꾼다. e = rx, ry, 하이라이트 dx, dy, r */
private fun Pen.openEye(look: PetLook, cl: CarLook, side: Int, cx: Float, cy: Float, k: Float, bigHl: Boolean, sparkle: Boolean) {
    val e = when (cl.eyes) {
        EyeShape.ROUND -> floatArrayOf(.036f, .038f, .011f, -.012f, .012f)
        EyeShape.SQUARE -> floatArrayOf(.029f, .041f, .010f, -.018f, .011f)
        else -> floatArrayOf(.030f, .045f, .009f, -.016f, .011f)
    }
    tf({ if (cl.eyes == EyeShape.SLANT) rotate(side * 10f, Offset(cx, cy)) }) {
        val ry = e[1] * k * look.blink
        fill(if (cl.eyes == EyeShape.SQUARE) rrect(cx - e[0] * k, cy - ry, cx + e[0] * k, cy + ry, .024f * k) else oval(cx, cy, e[0] * k, ry), INK)
        if (look.blink > .5f) {
            if (bigHl) {
                if (sparkle) fill(star4(cx + e[2] * k, cy + e[3] * k, .016f * k, .375f), WHITE)
                else fill(oval(cx + e[2] * k, cy + e[3] * k, e[4] * k * look.hl), WHITE)
            }
            fill(oval(cx - .008f * k, cy + .017f * k, .005f * k), WHITE, .8f)
        }
    }
}

private fun Pen.eye(look: PetLook, cl: CarLook, cx0: Float, cy0: Float, side: Int, k: Float) {
    val cx = cx0 + look.lookX; val cy = cy0 + look.lookY
    when (look.eyes) {
        Eye.OPEN -> openEye(look, cl, side, cx, cy, k, true, false)
        Eye.SPARKLE -> openEye(look, cl, side, cx, cy, k, true, true)
        Eye.SOFT -> {
            d.clipRect(cx - .1f * k, cy - .02f * k, cx + .1f * k, cy + .1f * k) { openEye(look, cl, side, cx, cy, k, true, false) }
            line(quad(cx - .032f * k, cy - .020f * k, cx, cy - .026f * k, cx + .032f * k, cy - .020f * k), .010f * k)
        }
        Eye.HALF -> {
            d.clipRect(cx - .1f * k, cy + .005f * k, cx + .1f * k, cy + .1f * k) { openEye(look, cl, side, cx, cy, k, false, false) }
            line(path { moveTo(cx - .031f * k, cy + .005f * k); lineTo(cx + .031f * k, cy + .005f * k) }, .010f * k)
        }
        Eye.HAPPY -> line(quad(cx - .028f * k, cy + .012f * k, cx, cy - .032f * k, cx + .028f * k, cy + .012f * k), .013f * k)
        Eye.CLOSED -> line(quad(cx - .028f * k, cy, cx, cy + .026f * k, cx + .028f * k, cy), .012f * k)
        Eye.WIDE -> { fill(oval(cx, cy, .040f * k), INK); fill(oval(cx + .012f * k, cy - .012f * k, .013f * k), WHITE) }
        Eye.SQUEEZE -> {
            val f = -side.toFloat() // 왼눈 '>', 오른눈 '<'
            line(poly(cx + f * .024f * k, cy - .024f * k, cx - f * .020f * k, cy, cx + f * .024f * k, cy + .024f * k, close = false), .013f * k)
        }
    }
}

private fun Pen.mouth(look: PetLook, mx: Float, my: Float, k: Float) {
    fun q(a: Float, b: Float, c: Float, dd: Float, e: Float, f: Float) = quad(mx + a * k, my + b * k, mx + c * k, my + dd * k, mx + e * k, my + f * k)
    when (look.mouth) {
        Mouth.SMILE -> line(q(-.025f, -.007f, 0f, .020f, .025f, -.007f), .012f * k)
        Mouth.OPEN -> {
            val m = path { moveTo(mx - .030f * k, my - .006f * k); quadraticBezierTo(mx, my + .044f * k, mx + .030f * k, my - .006f * k); close() }
            fill(m, INK); clip(m) { fill(oval(mx, my + .012f * k, .012f * k, .007f * k), Color(0xFFE98A80)) }
        }
        Mouth.O -> fill(oval(mx, my + .010f * k, .012f * k, .016f * k), INK)
        Mouth.FLAT -> line(path { moveTo(mx - .020f * k, my + .008f * k); lineTo(mx + .020f * k, my + .008f * k) }, .012f * k)
        Mouth.WAVY -> {
            val ys = floatArrayOf(.012f, .002f, .012f, .002f, .012f)
            line(path { for (i in 0..4) { val x = mx + (-.030f + .015f * i) * k; val y = my + ys[i] * k; if (i == 0) moveTo(x, y) else lineTo(x, y) } }, .010f * k)
        }
        Mouth.FROWN -> line(q(-.022f, .016f, 0f, -.002f, .022f, .016f), .012f * k)
        Mouth.YAWN -> fill(oval(mx, my + .014f * k, .018f * k, .024f * k), INK)
        Mouth.MUNCH ->
            if (look.munch) fill(oval(mx, my + .010f * k, .012f * k, .010f * k), INK)
            else line(path { moveTo(mx - .020f * k, my + .008f * k); lineTo(mx + .020f * k, my + .008f * k) }, .012f * k)
    }
}
