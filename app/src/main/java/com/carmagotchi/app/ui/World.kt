package com.carmagotchi.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.carmagotchi.app.WorldEnv
import com.carmagotchi.app.ui.pet.INK
import kotlin.math.sin

/** 월드 크기는 기본 줌(WORLD_ZOOM)일 때 보이는 영역의 가로 2배 x 세로 1.5배(dp). 사용자 줌과 상관없이 고정. 왼쪽 30%는 산, 72%부터는 바다, 가운데(x 50%)에 세로 길과 휴게소. */
const val WORLD_W = 2f
const val WORLD_H = 1.5f
/** 기본 카메라 줌(1이면 확대 없음). 작을수록 월드가 더 많이 보이고 펫이 작아진다. 사용자는 ZOOM_MIN..ZOOM_MAX 안에서 바꾼다. */
const val WORLD_ZOOM = .5f
const val ZOOM_DEFAULT = .3f // 화면에 "x1.0"으로 보이는 기본 배율. 월드 크기는 WORLD_ZOOM 기준이라 이 값과 무관
const val ZOOM_MIN = ZOOM_DEFAULT
const val ZOOM_MAX = 1.5f
/** 깜빡이는 가로 이동이 보이는 폭의 이 비율 이상이고 세로 이동보다 클 때만 켠다. */
const val BLINK_FAR = .35f
const val BLINK_TURN = .08f // 달리는 중 꺾을 때(보이는 폭 기준 가로 성분)
private const val SEA_X = .72f
private const val TREES = 26
const val COLD_C = 5.0; const val DEEP_COLD_C = -5.0; const val HOT_C = 28.0; const val WIND_PCT = 10.0 // 환경 연출 임계(외기 온도 °C, 창 열림 %)
private val ICE = Color(0xFFE4F4FF); private val FROST = Color(0xFFF4FAFF); private val WARM = Color(0xFFFFB347); private val STORM = Color(0xFF5B6470)
private val RAIN = Color(0xFFCFE3F5); private val PETAL =Color(0xFFF7B6C8)
/** 에너지 잔량(%)이 낮을수록 느려지는 이동 속도 계수(≤5% .35, ≤10% .45, ≤20% .65, 그 외·모름 1) */
fun energySpeed(pct: Double?) = when { pct == null || pct > 20.0 -> 1f; pct > 10.0 -> .65f; pct > 5.0 -> .45f; else -> .35f }

private fun WorldEnv.cold() = tempC != null && tempC <= COLD_C
private fun WorldEnv.hot() = tempC != null && tempC >= HOT_C
private fun WorldEnv.wind() = if (windowOpenPct > WIND_PCT) (windowOpenPct / 100.0).toFloat().coerceIn(.1f, 1f) else 0f
/** 매 프레임 움직이는 연출이 하나라도 켜졌는가(꺼져 있으면 프레임을 구독하지 않는다). */
fun WorldEnv.animated(rain: Boolean) = cold() || hot() || wind() > 0f || rain

/** 펫 발(dp)을 월드 안으로: 바다 앞 모래밭까지, 상자 윗변이 월드 밖으로 안 나가게. */
fun clampWorld(x: Float, y: Float, w: Float, h: Float, ps: Float) =
    Offset(x.coerceIn(.02f * w + ps / 2f, SEA_X * w), y.coerceIn(ps, .97f * h))

private val GROUND = Color(0xFFE6EFD0); private val FOREST = Color(0xFFA8D58A); private val ROCK = Color(0xFFB9C4BC); private val ROCK_D = Color(0xFF98A59D)
private val SAND = Color(0xFFF3E2C7); private val SEA = Color(0xFF8FC6EE); private val SEA_L = Color(0xFFB4DDF5); private val ROAD = Color(0xFF8A8F9A); private val PLAZA = Color(0xFFB4B8C0)
private val ROOF = Color(0xFFF07A66); private val WALL = Color(0xFFF6EBD9); private val WOOD = Color(0xFFC99A6E); private val SUN = Color(0xFFF5D26B)
private val PINE = Color(0xFF4F9B5A); private val PINE_L = Color(0xFF6FAF6A); private val GLASS = Color(0xFFBFE3F5)
private val MOUNTS = listOf(Triple(.07f, .16f, 1.2f), Triple(.21f, .14f, 1f), Triple(.11f, .42f, 1.4f), Triple(.25f, .40f, .9f), Triple(.06f, .70f, 1.1f), Triple(.20f, .74f, 1.3f), Triple(.12f, .94f, 1f))

/** 그리는 조각 하나: 채움(+선택 테두리) 또는 선. 좌표는 월드 dp. */
internal class Piece(val p: Path, val fill: Color?, val line: Color?, val sw: Float)

private fun Path.rc(x: Float, y: Float, w: Float, h: Float) = addRect(Rect(x, y, x + w, y + h))
private fun Path.poly(vararg v: Float) { moveTo(v[0], v[1]); for (i in 2 until v.size step 2) lineTo(v[i], v[i + 1]); close() }

/** 지형 한 번 계산해 두는 그림(월드 크기·펫 크기가 같으면 재사용). 매 프레임에는 만들어 둔 Path를 그리기만 한다. */
class WorldArt(val w: Float, val h: Float, val u: Float) {
    internal val parts = ArrayList<Piece>()
    internal var treeA = 0; internal var treeB = 0 // parts 중 소나무 조각 범위(바람 불 때 나무마다 따로 그린다)
    internal val tx = FloatArray(TREES); internal val ty = FloatArray(TREES); internal val tp = Array(3 * TREES) { Path() }
    val size = Size(w, h)
    val roadW = 1.4f * u
    private fun fill(c: Color, ink: Boolean = false, f: Path.() -> Unit) { parts += Piece(Path().apply(f), c, if (ink) INK else null, 2f) }
    private fun line(c: Color, sw: Float, f: Path.() -> Unit) { parts += Piece(Path().apply(f), null, c, sw) }
    private fun boat(bx: Float, by: Float, k: Float) {
        val s = u * k
        fill(ROOF, true) { poly(bx - .5f * s, by, bx + .8f * s, by, bx + .55f * s, by + .3f * s, bx - .3f * s, by + .3f * s) }
        line(INK, 2f) { moveTo(bx + .1f * s, by); lineTo(bx + .1f * s, by - 1.2f * s) }
        fill(Color.White, true) { poly(bx + .15f * s, by - 1.15f * s, bx + .15f * s, by - .1f * s, bx + .7f * s, by - .1f * s) }
        fill(Color.White, true) { poly(bx + .05f * s, by - 1f * s, bx + .05f * s, by - .1f * s, bx - .4f * s, by - .1f * s) }
        line(Color.White, 2.5f) { moveTo(bx - .3f * s, by + .38f * s); lineTo(bx - .9f * s, by + .38f * s); moveTo(bx - .2f * s, by + .5f * s); lineTo(bx - .6f * s, by + .5f * s) } // 물살
    }

    init {
        fill(GROUND) { rc(0f, 0f, w, h) }
        // 산 쪽: 숲 바닥, 소나무(가지 두 단), 봉우리 셋짜리 산 + 그늘 + 눈 덮인 꼭대기
        fill(FOREST) { rc(0f, 0f, .3f * w, h) }
        for (i in 0 until TREES) {
            val x = w * (.01f + .28f * ((i * 37) % 100) / 100f); val y = h * (.05f + .93f * ((i * 53) % 100) / 100f)
            tx[i] = x; ty[i] = y
            tp[3 * i].rc(x - .03f * u, y - .1f * u, .06f * u, .12f * u)
            tp[3 * i + 1].poly(x, y - .5f * u, x - .24f * u, y - .08f * u, x + .24f * u, y - .08f * u)
            tp[3 * i + 2].poly(x, y - .7f * u, x - .17f * u, y - .35f * u, x + .17f * u, y - .35f * u)
        }
        treeA = parts.size
        fill(WOOD) { for (i in 0 until TREES) addPath(tp[3 * i]) }; fill(PINE, true) { for (i in 0 until TREES) addPath(tp[3 * i + 1]) }; fill(PINE_L, true) { for (i in 0 until TREES) addPath(tp[3 * i + 2]) }
        treeB = parts.size
        for ((mx, my, s) in MOUNTS) {
            val x = mx * w; val y = my * h; val bw = 1.2f * u * s; val mh = 1.8f * u * s
            fill(ROCK, true) { poly(x - bw * 1.4f, y, x - bw * .9f, y - mh * .55f, x - bw * .55f, y - mh * .4f, x, y - mh, x + bw * .4f, y - mh * .5f, x + bw * .75f, y - mh * .7f, x + bw * 1.3f, y) }
            fill(ROCK_D) { poly(x, y - mh, x + bw * .4f, y - mh * .5f, x + bw * .75f, y - mh * .7f, x + bw * 1.3f, y, x + bw * .1f, y) }
            fill(Color.White, true) { poly(x, y - mh, x - bw * .35f, y - mh * .62f, x - bw * .17f, y - mh * .7f, x, y - mh * .58f, x + bw * .15f, y - mh * .68f, x + bw * .3f, y - mh * .62f) }
            fill(Color.White) { poly(x + bw * .75f, y - mh * .7f, x + bw * .6f, y - mh * .55f, x + bw * .75f, y - mh * .58f, x + bw * .9f, y - mh * .55f) } // 오른쪽 작은 봉우리 눈
        }
        // 바다 쪽: 모래밭, 얕은 물, 바다, 물결, 부두(널빤지·기둥), 배 두 척
        fill(SAND) { rc(SEA_X * w, 0f, .04f * w, h) }
        fill(SEA) { rc((SEA_X + .04f) * w, 0f, (1f - SEA_X - .04f) * w, h) }
        fill(SEA_L) { rc((SEA_X + .04f) * w, 0f, .03f * w, h) }
        line(Color.White, 2.5f) {
            for (i in 0 until 24) {
                val x = w * (.79f + .19f * ((i * 41) % 100) / 100f); val y = h * (.04f + .92f * ((i * 29) % 100) / 100f)
                moveTo(x, y); quadraticTo(x + .12f * u, y - .12f * u, x + .25f * u, y); quadraticTo(x + .38f * u, y + .12f * u, x + .5f * u, y)
            }
        }
        val py = h * .5f - .25f * u
        fill(WOOD) { rc((SEA_X + .13f) * w, py + .5f * u, .08f * u, .35f * u); rc((SEA_X + .13f) * w, py - .35f * u, .08f * u, .35f * u) } // 기둥
        fill(WOOD, true) { rc(SEA_X * w, py, .14f * w, .5f * u) }
        line(INK, 1.2f) { var d = .3f * u; while (d < .14f * w) { moveTo(SEA_X * w + d, py); lineTo(SEA_X * w + d, py + .5f * u); d += .3f * u } }
        boat(.9f * w, .26f * h, 1f); boat(.84f * w, .74f * h, .7f)
        // 길: 가로 길은 산 앞에서 바다 앞까지, 세로 길은 월드 끝까지. 노란 점선 가운데줄
        val rw = 1.4f * u; val cx = .5f * w; val cy = .5f * h
        fill(ROAD) { rc(.3f * w, cy - rw / 2, (SEA_X - .3f) * w, rw); rc(cx - rw / 2, 0f, rw, h) }
        line(SUN, 3f) {
            var d = 0f
            while (d < h) { moveTo(cx, d); lineTo(cx, d + .3f * u); d += .6f * u }
            d = .3f * w
            while (d < SEA_X * w) { moveTo(d, cy); lineTo(d + .3f * u, cy); d += .6f * u }
        }
        // 휴게소: 주차장(주차선) + 건물(지붕·창·문·간판) + 주유소(지붕·기둥·주유기) + 가로등
        val px = cx + rw / 2; val py2 = cy + rw / 2
        fill(PLAZA, true) { rc(px, py2, 3.4f * u, 1.6f * u) }
        line(Color.White, 3f) { for (i in 0..4) { moveTo(px + (.2f + .75f * i) * u, py2 + .2f * u); lineTo(px + (.2f + .75f * i) * u, py2 + .9f * u) } }
        val by = cy - rw / 2
        fill(WALL, true) { rc(px + .3f * u, by - 1.6f * u, 2.4f * u, 1.1f * u) }
        fill(ROOF, true) { poly(px + .1f * u, by - 1.6f * u, px + .6f * u, by - 2.3f * u, px + 2.4f * u, by - 2.3f * u, px + 2.9f * u, by - 1.6f * u) }
        fill(GLASS, true) { rc(px + .5f * u, by - 1.4f * u, .5f * u, .4f * u); rc(px + 2f * u, by - 1.4f * u, .5f * u, .4f * u) }
        fill(WOOD, true) { rc(px + 1.35f * u, by - 1.4f * u, .5f * u, .9f * u) }
        fill(SUN, true) { rc(px + .8f * u, by - 2.1f * u, 1.4f * u, .3f * u) }
        val gx = cx - rw / 2 - 2.8f * u
        fill(WALL) { rc(gx + .25f * u, by - .45f * u, .1f * u, .45f * u); rc(gx + 1.85f * u, by - .45f * u, .1f * u, .45f * u) } // 기둥
        fill(ROOF, true) { rc(gx, by - 1.15f * u, 2.2f * u, .7f * u) }
        fill(ROOF, true) { rc(gx + .55f * u, by - .55f * u, .3f * u, .5f * u); rc(gx + 1.35f * u, by - .55f * u, .3f * u, .5f * u) } // 주유기
        line(INK, 3f) { moveTo(cx - rw / 2 - .4f * u, by - 1.5f * u); lineTo(cx - rw / 2 - .4f * u, by - .3f * u) }
        fill(SUN) { addOval(Rect(Offset(cx - rw / 2 - .4f * u, by - 1.55f * u), .12f * u)) }
    }
}

/** 월드 지형을 dp 좌표로 그린다(호출하는 쪽이 카메라 이동·밀도 배율을 이미 걸어 둠). 이미지 없이 도형만. */
fun DrawScope.drawWorld(a: WorldArt, shade: Float, env: WorldEnv = WorldEnv(), rain: Boolean = false, t: Long = 0L) {
    val sw = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val parts = a.parts
    val wind = env.wind(); val anim = t != 0L; val sec = t / 1000f // t=0이면 정지 색조만(움직임 줄이기)
    for (i in parts.indices) {
        if (wind > 0f && anim && i in a.treeA until a.treeB) continue
        val p = parts[i]
        if (p.fill != null) drawPath(p.p, p.fill)
        if (p.line != null) drawPath(p.p, p.line, style = if (p.fill == null) Stroke(p.sw, cap = StrokeCap.Round) else sw)
    }
    val w = a.w; val h = a.h; val u = a.u; val seaX = (SEA_X + .04f) * w
    val road = a.roadW; val cx = .5f * w; val cy = .5f * h
    if (wind > 0f && anim) { // 나무 살랑(밑동 기준 좌우 기울기) + 부두 끝 깃발
        for (i in 0 until TREES) withTransform({ rotate(sin(sec * 2.5f + i) * 5f * wind, Offset(a.tx[i], a.ty[i])) }) {
            drawPath(a.tp[3 * i], WOOD); drawPath(a.tp[3 * i + 1], PINE); drawPath(a.tp[3 * i + 1], INK, style = sw)
            drawPath(a.tp[3 * i + 2], PINE_L); drawPath(a.tp[3 * i + 2], INK, style = sw)
        }
        val fx = (SEA_X + .14f) * w; val fy = h * .5f - .25f * u
        drawLine(INK, Offset(fx, fy), Offset(fx, fy - 1.1f * u), 2f)
        drawLine(ROOF, Offset(fx, fy - .95f * u), Offset(fx + (.55f + .15f * sin(sec * 6f)) * u * (.5f + wind), fy - (.9f - .1f * sin(sec * 7f)) * u), .3f * u)
    }
    if (env.cold()) { // 서리 + 얼어붙은 바다(하이라이트 줄이 천천히 흐름) + 길가 눈
        val deep = env.tempC!! <= DEEP_COLD_C
        drawRect(ICE, Offset(seaX, 0f), Size(w - seaX, h), alpha = if (deep) .8f else .6f)
        if (anim) for (i in 0 until 9) {
            val x = seaX + (w - seaX) * ((i * .137f + sec * .02f) % 1f)
            drawLine(Color.White, Offset(x, 0f), Offset(x - .5f * h, h), 3f + i % 3, alpha = (.3f + .3f * sin(sec * 1.5f + i)).coerceIn(0f, 1f))
        }
        drawRect(FROST, Offset.Zero, a.size, alpha = if (deep) .26f else .14f)
        val sn = .22f * u; val rl = (SEA_X - .3f) * w
        drawRect(Color.White, Offset(.3f * w, cy - road / 2 - sn), Size(rl, sn), alpha = .85f); drawRect(Color.White, Offset(.3f * w, cy + road / 2), Size(rl, sn), alpha = .85f)
        drawRect(Color.White, Offset(cx - road / 2 - sn, 0f), Size(sn, h), alpha = .85f); drawRect(Color.White, Offset(cx + road / 2, 0f), Size(sn, h), alpha = .85f)
    }
    if (env.hot()) { // 따뜻한 색조 + 바다 윤슬 + 길 위 아지랑이
        drawRect(WARM, Offset.Zero, a.size, alpha = .09f)
        if (anim) {
            for (i in 0 until 12) {
                val x = seaX + (w - seaX) * ((i * 41) % 100) / 100f; val y = h * ((i * 29) % 100) / 100f
                drawCircle(Color.White, .06f * u, Offset(x, y), alpha = (.5f * sin(sec * 3f + i * 1.7f)).coerceIn(0f, .8f))
            }
            for (i in 0 until 8) {
                val x = .3f * w + (SEA_X - .3f) * w * (i + .5f) / 8f; val y = cy - road / 2
                for (j in 0 until 3) {
                    val d = .08f * u * sin(sec * 4f + i + j * 1.3f); val d2 = .08f * u * sin(sec * 4f + i + (j + 1) * 1.3f)
                    drawLine(Color.White, Offset(x + d, y - j * .35f * u), Offset(x + d2, y - (j + 1) * .35f * u), 2f, alpha = .35f)
                }
            }
        }
    }
    if (rain) { // 회색조 + 빗줄 + 물웅덩이 파문
        drawRect(STORM, Offset.Zero, a.size, alpha = .14f)
        if (anim) {
            for (i in 0 until 90) {
                val x = w * ((i * 37) % 100) / 100f; val y = (h * ((i * 53) % 100) / 100f + sec * (1.6f + (i % 3) * .3f) * u * 3f) % h
                drawLine(RAIN, Offset(x, y), Offset(x - .06f * u, y + .3f * u), 2f, alpha = .6f)
            }
            for (i in 0 until 6) {
                val k = (sec * .8f + i * .37f) % 1f
                drawCircle(Color.White, (.1f + .25f * k) * u, Offset(cx + (if (i % 2 == 0) -1f else 1f) * (road / 2 + .3f * u), h * (.1f + .15f * i)), alpha = .5f * (1f - k), style = Stroke(1.5f))
            }
        }
    }
    if (wind > 0f && anim) { // 바람 줄 + 꽃잎: 열린 정도에 비례해 개수·속도
        val n = (6 + 24 * wind).toInt(); val v = (.15f + .5f * wind) * w
        for (i in 0 until n) {
            val len = (1f + (i % 3) * .5f) * u; val x = (((i * 37) % 100) / 100f * w + sec * v * (1f + (i % 4) * .15f)) % (w + len) - len; val y = h * ((i * 53) % 100) / 100f
            drawLine(Color.White, Offset(x, y), Offset(x + len, y), 1.5f, alpha = .5f)
            if (i % 3 == 0) drawCircle(PETAL, .06f * u, Offset(x + len * .5f, y + .15f * u * sin(sec * 5f + i)), alpha = .9f)
        }
    }
    if (env.fog) drawRect(Color.White, Offset.Zero, a.size, alpha = .32f) // 안개: 흐린 흰 막
    // 밤·비·다크: 얇은 어둠 막
    if (shade > 0f) drawRect(Color(0xFF14161B), Offset.Zero, a.size, alpha = shade)
}

/** 미니맵: 월드 전체를 단순 색 덩어리로(호출하는 쪽이 월드 dp -> 미니맵 px 배율을 걸어 둠). */
fun DrawScope.drawMini(a: WorldArt) {
    val w = a.w; val h = a.h; val u = a.u
    drawRect(GROUND, Offset.Zero, a.size)
    drawRect(FOREST, Offset.Zero, Size(.3f * w, h))
    for ((mx, my, s) in MOUNTS) drawRect(ROCK_D, Offset(mx * w - 1.2f * u * s, my * h - 1.8f * u * s), Size(2.4f * u * s, 1.8f * u * s))
    drawRect(SAND, Offset(SEA_X * w, 0f), Size(.04f * w, h))
    drawRect(SEA, Offset((SEA_X + .04f) * w, 0f), Size((1f - SEA_X - .04f) * w, h))
    val rw = 1.4f * u; val cx = .5f * w; val cy = .5f * h
    drawRect(ROAD, Offset(.3f * w, cy - rw / 2), Size((SEA_X - .3f) * w, rw))
    drawRect(ROAD, Offset(cx - rw / 2, 0f), Size(rw, h))
    drawRect(ROOF, Offset(cx + rw / 2, cy - rw / 2 - 2.3f * u), Size(3.4f * u, 4.3f * u))
}
