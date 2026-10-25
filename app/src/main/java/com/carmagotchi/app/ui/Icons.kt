package com.carmagotchi.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.carmagotchi.app.domain.EventId
import com.carmagotchi.app.domain.PlaceKind
import com.carmagotchi.app.ui.pet.Eye
import com.carmagotchi.app.ui.pet.PetLook
import com.carmagotchi.app.ui.pet.PetSpec
import com.carmagotchi.app.ui.pet.drawPet
import com.carmagotchi.app.domain.Stage
import com.carmagotchi.app.domain.Traits
import kotlin.math.cos
import kotlin.math.sin

/**
 * 6.1 아이콘: 48dp 그리드, 선 3dp, 둥근 끝. 지침은 VectorDrawable이지만 코드로 그린다(파일 수를 줄이려는 선택).
 * 글자 없이 쓰는 아이콘은 뒤로·설정·닫기 셋뿐이다.
 */
enum class Ic {
    TAB_ROOM, TAB_DEX, TAB_ALBUM, SETTINGS, BACK, CLOSE, CHEVRON, PET, SNACK, DECORATE, SHARE,
    HUNGER, TIRE, WASHER, OIL, WARNING, RECOVERED, PASSENGER, DAYS,
    TRIP, SURPRISE, SMOOTH, RAIN, NIGHT, MEAL, PLACE, REST, REUNION, MILESTONE, MORNING, BIRTHDAY, SEASON, GROW,
    P_SEA, P_MOUNTAIN, P_REST_AREA, P_CITY, P_HOME, P_OTHER, SOUND_ON, SOUND_QUIET,
}

fun eventIcon(e: EventId) = when (e) {
    EventId.HARD_BRAKE, EventId.SHARP_TURN -> Ic.SURPRISE
    EventId.SMOOTH_TRIP -> Ic.SMOOTH
    EventId.RAIN -> Ic.RAIN
    EventId.NIGHT -> Ic.NIGHT
    EventId.PASSENGER_ON -> Ic.PASSENGER
    EventId.REFUEL, EventId.RECHARGE -> Ic.MEAL
    EventId.LOW_FUEL, EventId.LOW_BATTERY -> Ic.HUNGER
    EventId.WARN_TPMS -> Ic.TIRE
    EventId.WARN_WASHER -> Ic.WASHER
    EventId.WARN_OIL -> Ic.OIL
    EventId.WARN_OTHER -> Ic.WARNING
    EventId.WARN_CLEARED -> Ic.RECOVERED
    EventId.NEW_PLACE -> Ic.PLACE
    EventId.REST_STOP -> Ic.REST
    EventId.LONG_ABSENCE -> Ic.REUNION
    EventId.MILESTONE_KM -> Ic.MILESTONE
    EventId.FIRST_OF_DAY -> Ic.MORNING
    EventId.BIRTHDAY -> Ic.BIRTHDAY
    EventId.SEASON_EVENT -> Ic.SEASON
    EventId.STAGE_UP -> Ic.GROW
    else -> Ic.TRIP
}

fun placeIcon(k: PlaceKind?) = when (k) {
    PlaceKind.SEA -> Ic.P_SEA; PlaceKind.MOUNTAIN -> Ic.P_MOUNTAIN; PlaceKind.REST_AREA -> Ic.P_REST_AREA
    PlaceKind.REGION -> Ic.P_CITY; PlaceKind.HOME_REGION -> Ic.P_HOME; null -> Ic.P_OTHER
}

private fun p(f: Path.() -> Unit) = Path().apply(f)
private fun circ(cx: Float, cy: Float, r: Float) = p { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
private fun rr(l: Float, t: Float, r: Float, b: Float, rad: Float) = p { addRoundRect(androidx.compose.ui.geometry.RoundRect(l, t, r, b, rad, rad)) }
private fun ln(vararg v: Float, close: Boolean = false) = p { moveTo(v[0], v[1]); for (i in 2 until v.size step 2) lineTo(v[i], v[i + 1]); if (close) close() }

private fun DrawScope.icon(ic: Ic, c: Color, filled: Boolean) {
    val st = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun s(path: Path) = drawPath(path, c, style = st)
    fun f(path: Path) = drawPath(path, c)
    fun sf(path: Path) { if (filled) f(path) else s(path) }
    fun dot(x: Float, y: Float, r: Float = 2f) = f(circ(x, y, r))
    when (ic) {
        Ic.TAB_ROOM, Ic.P_HOME -> { s(ln(6f, 22f, 24f, 8f, 42f, 22f)); sf(rr(11f, 22f, 37f, 40f, 3f)) }
        Ic.TAB_DEX -> { sf(rr(10f, 7f, 38f, 41f, 4f)); if (!filled) s(ln(24f, 7f, 24f, 41f)) }
        Ic.TAB_ALBUM -> { sf(rr(6f, 10f, 42f, 38f, 4f)); drawPath(ln(10f, 34f, 20f, 22f, 28f, 30f, 34f, 24f, 40f, 34f), if (filled) Color.White else c, style = st); dot(32f, 18f, 3f) }
        Ic.SETTINGS -> { s(circ(24f, 24f, 12f)); s(circ(24f, 24f, 4f)); for (i in 0 until 8) { val a = i * Math.PI.toFloat() / 4; s(ln(24f + 12f * cos(a), 24f + 12f * sin(a), 24f + 18f * cos(a), 24f + 18f * sin(a))) } }
        Ic.BACK -> s(ln(30f, 10f, 16f, 24f, 30f, 38f))
        Ic.CLOSE -> { s(ln(12f, 12f, 36f, 36f)); s(ln(36f, 12f, 12f, 36f)) }
        Ic.CHEVRON -> s(ln(18f, 10f, 32f, 24f, 18f, 38f))
        Ic.PET, Ic.REUNION -> sf(p { moveTo(24f, 41f); cubicTo(2f, 26f, 6f, 8f, 18f, 8f); cubicTo(22f, 8f, 24f, 11f, 24f, 14f); cubicTo(24f, 11f, 26f, 8f, 30f, 8f); cubicTo(42f, 8f, 46f, 26f, 24f, 41f); close() })
        Ic.SNACK -> { sf(circ(24f, 24f, 16f)); for ((x, y) in listOf(18f to 18f, 30f to 20f, 22f to 31f, 32f to 31f)) drawCircle(if (filled) Color.White else c, 2f, Offset(x, y)) }
        Ic.DECORATE -> sf(p { moveTo(24f, 5f); lineTo(29f, 19f); lineTo(43f, 24f); lineTo(29f, 29f); lineTo(24f, 43f); lineTo(19f, 29f); lineTo(5f, 24f); lineTo(19f, 19f); close() })
        Ic.SHARE -> { s(rr(6f, 6f, 20f, 20f, 3f)); s(rr(28f, 6f, 42f, 20f, 3f)); s(rr(6f, 28f, 20f, 42f, 3f)); dot(30f, 30f); dot(38f, 30f); dot(30f, 38f); dot(38f, 38f) }
        Ic.HUNGER, Ic.MEAL -> { sf(p { moveTo(7f, 22f); lineTo(41f, 22f); quadraticBezierTo(39f, 40f, 24f, 40f); quadraticBezierTo(9f, 40f, 7f, 22f); close() }); s(ln(18f, 6f, 18f, 14f)); s(ln(30f, 6f, 30f, 14f)) }
        Ic.TIRE -> { s(p { moveTo(8f, 36f); lineTo(8f, 24f); quadraticBezierTo(8f, 8f, 24f, 8f); quadraticBezierTo(40f, 8f, 40f, 24f); lineTo(40f, 36f); lineTo(33f, 36f); lineTo(33f, 28f); lineTo(15f, 28f); lineTo(15f, 36f); close() }); s(ln(24f, 14f, 24f, 22f)); dot(24f, 26f) }
        Ic.WASHER -> { s(p { moveTo(5f, 30f); quadraticBezierTo(24f, 2f, 43f, 30f); close() }); s(ln(16f, 34f, 14f, 42f)); s(ln(24f, 34f, 24f, 42f)); s(ln(32f, 34f, 34f, 42f)) }
        Ic.OIL -> { s(rr(8f, 20f, 30f, 38f, 4f)); s(ln(30f, 26f, 42f, 16f)); s(ln(14f, 20f, 14f, 14f, 24f, 14f, 24f, 20f)); sf(p { moveTo(38f, 30f); quadraticBezierTo(34f, 36f, 38f, 40f); quadraticBezierTo(42f, 36f, 38f, 30f); close() }) }
        Ic.WARNING -> { sf(ln(24f, 6f, 44f, 40f, 4f, 40f, close = true)); drawPath(ln(24f, 18f, 24f, 28f), if (filled) Color.White else c, style = st); drawCircle(if (filled) Color.White else c, 2f, Offset(24f, 34f)) }
        Ic.RECOVERED -> { sf(ln(24f, 24f, 8f, 12f, 8f, 36f, close = true)); sf(ln(24f, 24f, 40f, 12f, 40f, 36f, close = true)); sf(circ(24f, 24f, 4f)) }
        Ic.PASSENGER -> { sf(circ(16f, 15f, 6f)); sf(circ(32f, 15f, 6f)); s(p { moveTo(5f, 40f); quadraticBezierTo(16f, 18f, 27f, 40f) }); s(p { moveTo(21f, 40f); quadraticBezierTo(32f, 18f, 43f, 40f) }) }
        Ic.DAYS -> { sf(rr(8f, 10f, 40f, 40f, 4f)); s(ln(8f, 20f, 40f, 20f)); s(ln(16f, 5f, 16f, 14f)); s(ln(32f, 5f, 32f, 14f)) }
        Ic.TRIP -> { sf(rr(6f, 20f, 42f, 34f, 5f)); s(ln(14f, 20f, 18f, 11f, 30f, 11f, 34f, 20f)); sf(circ(16f, 37f, 4f)); sf(circ(32f, 37f, 4f)) }
        Ic.SURPRISE -> { s(ln(24f, 6f, 24f, 28f)); dot(24f, 38f, 3f); s(ln(10f, 12f, 14f, 16f)); s(ln(38f, 12f, 34f, 16f)) }
        Ic.SMOOTH -> { s(p { moveTo(5f, 24f); quadraticBezierTo(14f, 8f, 24f, 24f); quadraticBezierTo(34f, 40f, 43f, 24f) }); s(p { moveTo(5f, 36f); quadraticBezierTo(14f, 20f, 24f, 36f); quadraticBezierTo(34f, 52f, 43f, 36f) }) }
        Ic.RAIN -> { s(circ(18f, 20f, 8f)); s(circ(30f, 18f, 10f)); s(ln(12f, 28f, 38f, 28f)); s(ln(16f, 34f, 14f, 42f)); s(ln(26f, 34f, 24f, 42f)); s(ln(36f, 34f, 34f, 42f)) }
        Ic.NIGHT -> sf(p { moveTo(32f, 6f); cubicTo(16f, 8f, 10f, 28f, 20f, 38f); cubicTo(28f, 44f, 40f, 38f, 42f, 30f); cubicTo(30f, 34f, 24f, 22f, 32f, 6f); close() })
        Ic.PLACE -> { sf(p { moveTo(24f, 43f); cubicTo(10f, 28f, 10f, 6f, 24f, 6f); cubicTo(38f, 6f, 38f, 28f, 24f, 43f); close() }); drawCircle(if (filled) Color.White else c, 5f, Offset(24f, 19f), style = st) }
        Ic.REST -> { sf(rr(9f, 16f, 33f, 40f, 4f)); s(p { moveTo(33f, 22f); quadraticBezierTo(46f, 22f, 33f, 34f) }); s(ln(16f, 5f, 16f, 11f)); s(ln(25f, 5f, 25f, 11f)) }
        Ic.MILESTONE -> { s(ln(12f, 5f, 12f, 43f)); sf(ln(12f, 8f, 40f, 15f, 12f, 24f, close = true)) }
        Ic.MORNING -> { sf(circ(24f, 24f, 8f)); for (i in 0 until 8) { val a = i * Math.PI.toFloat() / 4; s(ln(24f + 13f * cos(a), 24f + 13f * sin(a), 24f + 18f * cos(a), 24f + 18f * sin(a))) } }
        Ic.BIRTHDAY -> { sf(rr(7f, 24f, 41f, 41f, 4f)); s(ln(24f, 11f, 24f, 22f)); dot(24f, 7f, 3f) }
        Ic.SEASON -> { sf(p { moveTo(8f, 40f); cubicTo(6f, 14f, 22f, 6f, 42f, 8f); cubicTo(42f, 26f, 34f, 40f, 8f, 40f); close() }); drawPath(ln(8f, 40f, 30f, 18f), if (filled) Color.White else c, style = st) }
        Ic.GROW -> { s(ln(24f, 43f, 24f, 20f)); sf(p { moveTo(24f, 30f); quadraticBezierTo(6f, 30f, 8f, 12f); quadraticBezierTo(24f, 12f, 24f, 30f); close() }); sf(p { moveTo(24f, 24f); quadraticBezierTo(42f, 24f, 40f, 8f); quadraticBezierTo(24f, 8f, 24f, 24f); close() }) }
        Ic.P_SEA -> { for (y in listOf(16f, 26f, 36f)) s(p { moveTo(5f, y); quadraticBezierTo(14f, y - 8f, 24f, y); quadraticBezierTo(34f, y + 8f, 43f, y) }) }
        Ic.P_MOUNTAIN -> sf(ln(4f, 40f, 18f, 12f, 28f, 30f, 34f, 22f, 44f, 40f, close = true))
        Ic.P_REST_AREA -> { sf(rr(7f, 7f, 41f, 41f, 7f)); drawPath(p { moveTo(19f, 35f); lineTo(19f, 14f); lineTo(27f, 14f); quadraticBezierTo(33f, 14f, 33f, 21f); quadraticBezierTo(33f, 28f, 27f, 28f); lineTo(19f, 28f) }, if (filled) Color.White else c, style = st) }
        Ic.P_CITY -> { sf(rr(6f, 18f, 20f, 42f, 2f)); sf(rr(24f, 6f, 42f, 42f, 2f)); s(ln(4f, 42f, 44f, 42f)) }
        Ic.SOUND_ON, Ic.SOUND_QUIET -> { sf(ln(8f, 19f, 16f, 19f, 26f, 10f, 26f, 38f, 16f, 29f, 8f, 29f, close = true)); if (ic == Ic.SOUND_ON) { s(p { moveTo(32f, 18f); quadraticBezierTo(37f, 24f, 32f, 30f) }); s(p { moveTo(37f, 12f); quadraticBezierTo(46f, 24f, 37f, 36f) }) } else { s(ln(32f, 18f, 44f, 30f)); s(ln(44f, 18f, 32f, 30f)) } }
        Ic.P_OTHER -> { sf(circ(24f, 24f, 14f)); drawCircle(if (filled) Color.White else c, 4f, Offset(24f, 24f)) }
    }
}

@Composable
fun Icon(ic: Ic, color: Color, size: Dp = 48.dp, filled: Boolean = false) {
    Canvas(Modifier.size(size).clearAndSetSemantics { }) {
        withTransform({ scale(this.size.width / 48f, this.size.height / 48f, Offset.Zero) }) { icon(ic, color, filled) }
    }
}

/** 엿보기 마크(2.2절): ADULT 크림 펫의 y ≤ 0.62 윗부분만 보이게 자른다. width = 마크 폭. */
@Composable
fun PeekMark(width: Dp) {
    val spec = PetSpec(Stage.ADULT, 0, 0, 0, Traits(), "", 0)
    val big = width / .66f
    Box(Modifier.size(width, big * .37f).clipToBounds().clearAndSetSemantics { }) {
        Canvas(Modifier.requiredSize(big).offset(-big * .17f, -big * .25f)) { drawPet(spec, PetLook(eyes = Eye.OPEN), false, Color.Gray) }
    }
}
