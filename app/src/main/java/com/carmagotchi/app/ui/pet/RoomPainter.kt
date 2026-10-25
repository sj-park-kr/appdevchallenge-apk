package com.carmagotchi.app.ui.pet

import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.carmagotchi.app.domain.Stage

/** 3.13 방 무대. 물건 자리 9곳 중 3곳만 정의돼 있어 4~9번째는 창틀 오른쪽·가운데·벽 쪽으로 정했다. */
private val SLOTS = listOf(
    .18f to .42f, .18f to .74f, .82f to .92f, .82f to .42f, .82f to .74f, .18f to .92f, .30f to .42f, .70f to .42f, .10f to .58f,
)

/** 놓인 물건 i의 상자(px). 방 안 이동의 바닥 탭이 물건 위면 이동이 아니다. */
fun itemRect(i: Int, w: Float, h: Float, petS: Float): androidx.compose.ui.geometry.Rect {
    val (u, v) = SLOTS[i]; val s = petS * .18f
    return androidx.compose.ui.geometry.Rect(u * w - s / 2, v * h - s / 2, u * w + s / 2, v * h + s / 2)
}

fun slotCount(s: Stage) = when (s) { Stage.EGG -> 0; Stage.BABY -> 3; Stage.CHILD -> 5; Stage.ADULT -> 7; Stage.BUDDY -> 9 }

fun skyColor(hour: Int, rain: Boolean) = when {
    hour in 6..16 -> if (rain) Color(0xFFB9C7D3) else Color(0xFFCDE7F7)
    hour in 17..18 -> Color(0xFFF6D2B5)
    else -> Color(0xFF2F3D5C)
}

fun DrawScope.drawRoom(dark: Boolean, hour: Int, rain: Boolean, items: List<String>, petS: Float, sec: Color, divider: Color) {
    val w = size.width; val h = size.height / w
    withTransform({ scale(w, w, Offset.Zero) }) {
        val p = Pen(this, 2.dp.toPx() / w)
        val frame = if (dark) Color(0xFF3A3E48) else INK
        p.fill(rrect(0f, 0f, 1f, h, 0f), if (dark) Color(0xFF1B1E24) else Color(0xFFF4ECE2))
        val win = rrect(.10f, .06f * h, .90f, .42f * h, .06f * h)
        p.fill(win, skyColor(hour, rain))
        p.clip(win) {
            if (hour !in 6..18) for ((x, y) in listOf(.20f to .12f, .35f to .22f, .55f to .10f, .70f to .25f, .82f to .14f)) p.fill(oval(x, y * h, 2.dp.toPx() / w), Color(0xFFFFF3C4))
            if (rain) for (i in 0 until 6) p.line(path { moveTo(.16f + i * .12f, .10f * h); lineTo(.14f + i * .12f, .20f * h) }, .004f, Color(0xFF7FB8E6))
        }
        p.line(win, 2.dp.toPx() / w, frame)
        val back = rrect(.04f, .36f * h, .96f, .80f * h, .08f * h)
        p.fill(back, if (dark) Color(0xFF2E323B) else Color(0xFFE5D6C3))
        for (u in listOf(.36f, .64f)) p.line(path { moveTo(u, .42f * h); lineTo(u, .76f * h) }, 2.dp.toPx() / w, divider)
        p.fill(rrect(.02f, .74f * h, .98f, h, .06f * h), if (dark) Color(0xFF262A33) else Color(0xFFD9C7B1))
        val n = items.size
        for (i in 0 until n.coerceAtMost(SLOTS.size)) {
            val (u, v) = SLOTS[i]; val s = petS / w * .18f
            p.tf({ translate(u - s / 2, v * h - s / 2); scale(s, s, Offset.Zero) }) { Pen(this, p.minW / s, ow = .06f).item(items[i]) }
        }
    }
}

/** 방 물건 한 개를 정사각형 상자에 그린다(꾸미기 목록 썸네일에도 쓴다). */
fun DrawScope.drawItem(id: String) {
    val s = size.minDimension
    withTransform({ scale(s, s, Offset.Zero) }) { Pen(this, 2.dp.toPx() / s, ow = .06f).item(id) }
}

private val CREAM = Color(0xFFF3E2C7)
private val BROWN = Color(0xFFC99A6E)
private val RED = Color(0xFFF07A66)
private val SKY = Color(0xFF8FC6EE)
private val YELLOW = Color(0xFFF5D26B)
private val PINK = Color(0xFFF6A6B2)
private val GREEN = Color(0xFFA8D58A)

private fun Pen.item(id: String) {
    when (id) {
        "ITEM-01" -> {
            line(path { moveTo(.5f, .45f); lineTo(.5f, .85f); lineTo(.62f, .88f) }, ow)
            fs(path { moveTo(.08f, .5f); arcTo(androidx.compose.ui.geometry.Rect(.08f, .1f, .92f, .9f), 180f, 180f, false); close() }, SKY)
        }
        "ITEM-02" -> { line(path { moveTo(.5f, .5f); lineTo(.5f, .85f) }, ow); fs(oval(.5f, .9f, .22f, .07f), CREAM); fs(star4(.5f, .32f, .30f), YELLOW) }
        "ITEM-03" -> { fs(path { moveTo(.1f, .5f); arcTo(androidx.compose.ui.geometry.Rect(.1f, .3f, .9f, .9f), 180f, -180f, false); close() }, Color.White); fs(oval(.5f, .5f, .4f, .1f), Color.White); fill(oval(.5f, .5f, .34f, .07f), Color(0xFFFFFDF5)) }
        "ITEM-04" -> { fs(poly(.5f, .5f, .1f, .25f, .1f, .75f), Color(0xFFF28C6B)); fs(poly(.5f, .5f, .9f, .25f, .9f, .75f), Color(0xFFF28C6B)); fs(oval(.5f, .5f, .1f), Color(0xFFF28C6B)) }
        "ITEM-05" -> { fs(rrect(.08f, .35f, .92f, .85f, .2f), PINK); fs(path { moveTo(.5f, .75f); cubicTo(.3f, .6f, .35f, .45f, .5f, .55f); cubicTo(.65f, .45f, .7f, .6f, .5f, .75f); close() }, Color.White) }
        "ITEM-06" -> {
            fs(path { moveTo(.5f, .88f); quadraticBezierTo(.0f, .55f, .15f, .3f); quadraticBezierTo(.5f, .05f, .85f, .3f); quadraticBezierTo(1f, .55f, .5f, .88f); close() }, CREAM)
            for (x in listOf(.28f, .42f, .58f, .72f)) line(path { moveTo(.5f, .86f); lineTo(x, .22f + kotlin.math.abs(x - .5f) * .6f) }, ow * .6f)
        }
        "ITEM-07" -> { fs(oval(.5f, .55f, .27f, .38f), BROWN); for (y in listOf(.35f, .5f, .65f)) line(quad(.26f, y, .5f, y + .12f, .74f, y), ow * .6f); fs(poly(.45f, .18f, .55f, .18f, .5f, .08f), BROWN) }
        "ITEM-08" -> { fs(oval(.5f, .58f, .42f, .32f), BROWN); for (x in listOf(.3f, .5f, .7f)) line(quad(x, .35f, x + .04f, .58f, x, .82f), ow * .6f) }
        "ITEM-09" -> {
            line(path { moveTo(.2f, .95f); lineTo(.25f, .1f) }, ow)
            for (r in 0 until 2) for (c in 0 until 3) { val x = .25f + c * .2f; val y = .1f + r * .2f; fill(rrect(x, y, x + .2f, y + .2f, 0f), if ((r + c) % 2 == 0) INK else Color.White) }
            line(rrect(.25f, .1f, .85f, .5f, 0f), ow * .6f)
        }
        "ITEM-10" -> { fs(rrect(.1f, .5f, .9f, .9f, .05f), Color(0xFFFFF3E0)); fill(oval(.5f, .46f, .1f), RED); fs(rrect(.46f, .2f, .54f, .5f, 0f), SKY); fill(drop(.5f, .04f, .16f), YELLOW) }
        "ITEM-11" -> { line(path { moveTo(.5f, .62f); lineTo(.5f, .3f) }, ow); for ((x, y) in listOf(.5f to .22f, .3f to .38f, .7f to .38f)) fs(oval(x, y, .13f), Color(0xFFF9D5DF)); fs(rrect(.25f, .62f, .75f, .95f, .06f), Color(0xFFE8823F)) }
        "ITEM-12" -> { fs(rrect(.28f, .08f, .56f, .72f, .06f), YELLOW); fs(rrect(.28f, .58f, .85f, .92f, .12f), YELLOW) }
        "ITEM-13" -> { fs(rrect(.05f, .05f, .95f, .95f, .05f), CREAM); fs(star4(.5f, .5f, .36f, .55f), Color(0xFFE8823F)) }
        "ITEM-14" -> {
            line(path { moveTo(.5f, .02f); lineTo(.5f, .3f) }, ow)
            for (a in listOf(0f, 60f, 120f)) { val r = a * Math.PI.toFloat() / 180f; val dx = .32f * kotlin.math.cos(r); val dy = .32f * kotlin.math.sin(r); val l = path { moveTo(.5f - dx, .62f - dy); lineTo(.5f + dx, .62f + dy) }; line(l, ow * 1.6f); }
            for (a in listOf(0f, 60f, 120f)) { val r = a * Math.PI.toFloat() / 180f; val dx = .32f * kotlin.math.cos(r); val dy = .32f * kotlin.math.sin(r); line(path { moveTo(.5f - dx, .62f - dy); lineTo(.5f + dx, .62f + dy) }, ow * .6f, Color.White) }
        }
        "ITEM-15" -> { fs(poly(.18f, .15f, .82f, .15f, .92f, .88f, .08f, .88f), PINK); line(poly(.38f, .15f, .5f, .5f, .62f, .15f, close = false), ow, Color.White); fs(oval(.55f, .6f, .05f, .2f), RED) }
        "ITEM-16" -> { fs(oval(.5f, .62f, .34f, .3f), RED); fs(rrect(.3f, .22f, .7f, .42f, .08f), RED); line(path { moveTo(.3f, .42f); lineTo(.7f, .42f) }, ow, YELLOW) }
        "ITEM-17" -> { fs(path { moveTo(.06f, .62f); arcTo(androidx.compose.ui.geometry.Rect(.06f, .2f, .94f, .92f), 180f, 180f, false); close() }, GREEN); line(path { moveTo(.12f, .62f); lineTo(.88f, .62f) }, ow * .6f) }
        "ITEM-18" -> { for ((x, y) in listOf(.35f to .35f, .65f to .35f, .35f to .65f, .65f to .65f)) fs(oval(x, y, .2f), GREEN); line(path { moveTo(.5f, .5f); lineTo(.6f, .95f) }, ow) }
        "ITEM-19" -> { fs(oval(.5f, .5f, .4f), Color(0xFFC7CDD8)); line(oval(.5f, .5f, .26f), ow * .6f) }
        "ITEM-20" -> { fs(path { moveTo(.15f, .9f); quadraticBezierTo(.0f, .3f, .8f, .08f); quadraticBezierTo(.9f, .6f, .15f, .9f); close() }, Color(0xFFD7D2E8)); line(path { moveTo(.15f, .9f); lineTo(.7f, .2f) }, ow * .6f) }
    }
}
