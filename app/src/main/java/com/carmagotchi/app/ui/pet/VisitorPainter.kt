package com.carmagotchi.app.ui.pet

import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform

/** 3.12 길동무 12종. 좌표는 길동무 상자(0~1). 도형 최대 4개 + 점 눈. 그림 정의가 없던 5종(04·05·10·11·12)은 같은 규칙으로 정했다. */
private val CREAM = Color(0xFFF3E2C7)
private val CARAMEL = Color(0xFFE7B98A)
private val SKY = Color(0xFF8FC6EE)
private val YELLOW = Color(0xFFF5D26B)
private val GRAY = Color(0xFFC7CDD8)
private val ORANGE = Color(0xFFE8823F)
private val BROWN = Color(0xFFC99A6E)
private val LILAC = Color(0xFFD7D2E8)

val FLYERS = setOf("VIS-03", "VIS-06", "VIS-12")

private fun Pen.dot(x: Float, y: Float) { if (!locked) fill(oval(x, y, .06f), INK) }

/** 겹친 도형을 한 덩어리처럼: 굵은 선을 먼저 깔고 채움으로 안쪽 선을 덮는다. */
private fun Pen.union(paths: List<androidx.compose.ui.graphics.Path>, c: Color) {
    if (locked) { paths.forEach { fs(it, c) }; return }
    paths.forEach { line(it, ow * 2) }; paths.forEach { fill(it, c) }
}

internal fun Pen.visitor(id: String) {
    when (id) {
        "VIS-01" -> {
            fs(rrect(.10f, .70f, .90f, .86f, .08f), CREAM)
            fs(oval(.55f, .50f, .28f), CARAMEL)
            line(path { arcTo(Rect(.41f, .36f, .69f, .64f), 0f, 270f, true) }, ow * .8f)
            line(path { arcTo(Rect(.48f, .43f, .62f, .57f), 0f, 270f, true) }, ow * .8f)
            fs(oval(.18f, .62f, .12f), CREAM)
            line(path { moveTo(.14f, .52f); lineTo(.10f, .34f) }, ow); line(path { moveTo(.22f, .52f); lineTo(.24f, .34f) }, ow)
            dot(.10f, .34f); dot(.24f, .34f)
        }
        "VIS-02" -> {
            fs(poly(.24f, .50f, .04f, .38f, .08f, .64f), BROWN)
            fs(oval(.50f, .56f, .30f), BROWN)
            fill(oval(.52f, .66f, .18f, .14f), CREAM)
            fs(poly(.78f, .50f, .94f, .55f, .78f, .60f), ORANGE)
            dot(.64f, .46f)
        }
        "VIS-03" -> {
            fill(oval(.72f, .55f, .22f), YELLOW, .35f)
            fs(oval(.40f, .32f, .12f, .08f), Color.White); fs(oval(.55f, .32f, .12f, .08f), Color.White)
            fs(oval(.45f, .50f, .22f, .16f), LILAC)
            fs(oval(.72f, .55f, .14f), YELLOW)
            dot(.32f, .48f)
        }
        "VIS-04" -> {
            fs(poly(.12f, .64f, .16f, .40f, .24f, .52f, .30f, .30f, .38f, .46f, .46f, .26f, .54f, .44f, .62f, .32f, .66f, .52f, .72f, .64f), CARAMEL)
            fs(oval(.46f, .64f, .34f, .24f), BROWN)
            fs(oval(.78f, .68f, .14f, .12f), CREAM)
            dot(.78f, .62f); if (!locked) fill(oval(.90f, .68f, .03f), INK)
        }
        "VIS-05" -> {
            fs(oval(.20f, .56f, .09f, .05f), CARAMEL)
            fs(oval(.48f, .64f, .28f, .18f), CARAMEL)
            fs(oval(.74f, .48f, .17f), CARAMEL)
            fs(poly(.66f, .38f, .46f, .26f, .58f, .50f), BROWN)
            dot(.80f, .46f); if (!locked) line(path { moveTo(.86f, .58f); lineTo(.92f, .56f) }, ow * .6f)
        }
        "VIS-06" -> {
            fs(oval(.45f, .28f, .12f, .08f), Color.White); fs(oval(.60f, .28f, .12f, .08f), Color.White)
            fs(oval(.50f, .52f, .24f, .17f), YELLOW)
            if (!locked) { line(path { moveTo(.46f, .40f); lineTo(.46f, .64f) }, ow); line(path { moveTo(.58f, .40f); lineTo(.58f, .64f) }, ow) }
            fill(oval(.34f, .58f, .035f), ORANGE)
            dot(.34f, .48f)
        }
        "VIS-07" -> {
            fs(oval(.50f, .58f, .30f, .20f), Color.White)
            fs(path { moveTo(.28f, .56f); quadraticBezierTo(.46f, .22f, .66f, .50f); lineTo(.60f, .58f); quadraticBezierTo(.46f, .36f, .34f, .60f); close() }, GRAY)
            fs(poly(.78f, .52f, .94f, .58f, .78f, .62f), Color(0xFFF2A23A))
            fs(rrect(.62f, .64f, .80f, .72f, .03f), SKY)
            dot(.70f, .48f)
        }
        "VIS-08" -> {
            fs(oval(.72f, .42f, .25f), CARAMEL)
            fs(oval(.42f, .62f, .20f, .24f), BROWN)
            fill(oval(.46f, .68f, .11f, .15f), CREAM)
            fs(path { moveTo(.28f, .42f); arcTo(Rect(.28f, .31f, .56f, .47f), 180f, 180f, false); close() }, CARAMEL)
            dot(.50f, .54f)
        }
        "VIS-09" -> {
            val s = { u: Float, v: Float -> Offset(.08f + .84f * u, v) }
            fun p(u1: Float, v1: Float, u2: Float, v2: Float, u3: Float, v3: Float) =
                floatArrayOf(s(u1, v1).x, s(u1, v1).y, s(u2, v2).x, s(u2, v2).y, s(u3, v3).x, s(u3, v3).y)
            val body = path {
                moveTo(s(.5f, 0f).x, 0f)
                for (c in listOf(p(.78f, 0f, 1f, .318f, 1f, .606f), p(1f, .848f, .78f, 1f, .5f, 1f), p(.22f, 1f, 0f, .848f, 0f, .606f), p(0f, .318f, .22f, 0f, .5f, 0f)))
                    cubicTo(c[0], c[1], c[2], c[3], c[4], c[5])
                close()
            }
            fs(poly(.18f, .06f, .34f, .10f, .22f, .26f), BROWN); fs(poly(.82f, .06f, .66f, .10f, .78f, .26f), BROWN)
            fs(body, BROWN)
            fill(oval(.50f, .78f, .20f, .14f), CREAM)
            fs(oval(.34f, .38f, .13f), Color.White); fs(oval(.66f, .38f, .13f), Color.White)
            dot(.34f, .38f); dot(.66f, .38f)
            fs(poly(.46f, .50f, .54f, .50f, .50f, .58f), ORANGE)
        }
        "VIS-10" -> {
            union(listOf(oval(.30f, .64f, .17f), oval(.50f, .54f, .22f), oval(.70f, .64f, .17f), rrect(.14f, .64f, .86f, .82f, .09f)), LILAC)
            fs(poly(.36f, .40f, .38f, .18f, .52f, .34f), LILAC); fs(poly(.64f, .40f, .62f, .18f, .48f, .34f), LILAC)
            dot(.42f, .56f); dot(.58f, .56f); if (!locked) line(path { moveTo(.47f, .64f); lineTo(.53f, .64f) }, ow * .6f)
        }
        "VIS-11" -> {
            fs(oval(.50f, .64f, .26f), Color.White)
            if (!locked) for (a in listOf(0f, 60f, 120f)) {
                val r = a * Math.PI.toFloat() / 180f; val dx = .13f * kotlin.math.cos(r); val dy = .13f * kotlin.math.sin(r)
                val l = path { moveTo(.5f - dx, .24f - dy); lineTo(.5f + dx, .24f + dy) }
                line(l, ow * 1.9f);
            }
            if (!locked) for (a in listOf(0f, 60f, 120f)) {
                val r = a * Math.PI.toFloat() / 180f; val dx = .13f * kotlin.math.cos(r); val dy = .13f * kotlin.math.sin(r)
                line(path { moveTo(.5f - dx, .24f - dy); lineTo(.5f + dx, .24f + dy) }, ow * .9f, SKY)
            }
            dot(.40f, .62f); dot(.60f, .62f); if (!locked) line(path { moveTo(.47f, .72f); lineTo(.53f, .72f) }, ow * .6f)
        }
        "VIS-12" -> {
            fs(poly(.44f, .40f, .02f, .12f, .40f, .60f), Color.White)
            fs(star4(.64f, .52f, .32f, .5f), YELLOW)
            dot(.58f, .50f); dot(.70f, .50f)
        }
    }
}

/** 펫 캔버스 위에 길동무를 얹는다(3.12절 1번: 상자 한 변 = 캔버스 × 0.24). */
internal fun Pen.visitorOn(id: String, look: PetLook) {
    val x = (if (id in FLYERS) .06f else .04f) + look.visitorDx
    val y = (if (id in FLYERS) .14f else .68f) + look.visitorDy
    d.withTransform({ translate(x, y); scale(.24f, .24f, Offset.Zero) }) {
        Pen(d, minW / .24f, ow = .0533f).visitor(id)
    }
}

/** 도감 카드·상세용: 길동무 한 마리를 정사각형 상자에 가득 그린다. */
fun DrawScope.drawVisitor(id: String, locked: Boolean, sunken: Color, secondary: Color) {
    val s = size.minDimension
    val minW = 2.dp.toPx() / s
    withTransform({ scale(s, s, Offset.Zero) }) {
        val pen = if (locked) Pen(this, minW, 1.dp.toPx() / s, .04f, sunken, secondary) else Pen(this, minW, ow = .04f)
        pen.visitor(id)
    }
}

