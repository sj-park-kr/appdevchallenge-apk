package com.carmagotchi.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.carmagotchi.app.domain.Const
import com.carmagotchi.app.domain.Growth
import com.carmagotchi.app.domain.GrowthView
import com.carmagotchi.app.domain.PetState
import com.carmagotchi.app.domain.Stage
import com.carmagotchi.app.domain.XpEntry
import com.carmagotchi.app.domain.XpKind
import com.carmagotchi.app.ui.pet.HEART
import com.carmagotchi.app.ui.pet.star4
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** MO-BAR-FILL·MO-HEART-POP이 "마지막으로 보여 준 값"과 비교하려고 앱 프로세스 안에만 두는 값(저장 안 함). */
private object Shown {
    var petId: String? = null
    var stage: Stage? = null
    var total = 0
    var done = -1
    var hearts = -1f
}

/** 하트는 반 개 단위로 읽는다(그림은 부분 채움). */
fun heartsSpoken(h: Float): String {
    val n = (h * 2f).roundToInt() / 2f
    val whole = floor(n).toInt()
    return if (n - whole >= .5f) (if (whole == 0) "반 개" else "${whole}개 반") else "${whole}개"
}

/** 자람 카드가 쓰는 글(DLG-SYS-*). 카드와 "이야기" 팝업이 같은 문장을 쓴다. */
class GrowthTexts(val growLine: String, val heartLine: String)

@Composable
fun growthTexts(g: GrowthView): GrowthTexts {
    val ctx = LocalContext.current
    val fallback = when (g.lineId) {
        "GROW-LINE" -> "1km나 5분 넘게 운전한 날마다 하루씩 자라요."
        "GROW-BUDDY" -> "차 생일(1주년)에 단짝이 돼요."
        "GROW-ANNIV" -> "차 생일마다 같이 축하해요."
        else -> "다음 날 시동을 걸면 자라요."
    }
    return GrowthTexts(Lines.text(ctx, "DLG-SYS-${g.lineId}", fallback), Lines.text(ctx, "DLG-SYS-HEART-LINE", "하트: 운전한 날 · 주유·충전·회복 · 쓰다듬기·간식"))
}

/** DESIGN-GUIDE 12절: 한 덩어리로 읽는다. */
fun growthDesc(pet: PetState, g: GrowthView, t: GrowthTexts, pressable: Boolean) =
    "${xpLine(pet)}. ${t.growLine} 하트 5개 중 ${heartsSpoken(g.hearts)}. 운전한 날, 주유·충전·회복, 쓰다듬기·간식, Gleo 고마워로 하트가 차요." +
        if (pressable) " 두 번 누르면 이야기를 볼 수 있어요." else ""

/** XP 기록 한 줄. 한글 라벨은 여기 한 곳. */
fun xpLabel(x: XpEntry) = when (x.kind) {
    XpKind.PET -> "쓰다듬기 +${x.n}"; XpKind.SNACK -> "간식 +${x.n}"; XpKind.THANKS -> "고마워 +${x.n}"
    XpKind.ROAM -> "돌아다니기 +${x.n}"; XpKind.CARE -> "주유 +${x.n}"; XpKind.TIME -> "함께 있는 시간 +${x.n}"
    XpKind.DRIVE -> "주행 +${x.n}"; XpKind.LEVELUP -> "강제 레벨업"
}

/** "XP 2501 / 8500". 마지막 단계는 "최고 단계 · XP n". */
private fun xpLine(pet: PetState) = Const.xpNext(pet.stage)?.let { "XP ${pet.xp} / $it" } ?: "최고 단계 · XP ${pet.xp}"

/** 8.2 GrowthCard: XP 기준 진행 바(가장 크게) + 함께 있는 시간 줄(10초 바) + 애정 하트 5개 + 한 줄 설명 둘. 날 수는 작은 보조 글씨. */
@Composable
fun GrowthCard(
    pet: PetState, now: java.time.Instant, modifier: Modifier = Modifier, pressable: Boolean = true, xpLog: List<XpEntry> = emptyList(),
    tickAt: java.time.Instant? = null, tickDriving: Boolean = false, togetherSec: Int = 0, onClick: () -> Unit = {},
) {
    val c = LocalChama.current
    val g = Growth.of(pet, now)
    val t = growthTexts(g)
    val desc = growthDesc(pet, g, t, pressable)
    var prev by remember { mutableStateOf(pet.xp) }
    var pop by remember { mutableStateOf(0) }
    var tickPop by remember { mutableStateOf(0) }
    LaunchedEffect(pet.xp) {
        val d = pet.xp - prev; prev = pet.xp
        if (d >= 10) { pop = d; delay(1200); pop = 0 } // 돌봄은 "+50", 시간 XP(+1~2)는 아래 줄에서 따로
        else if (d > 0) { tickPop = d; delay(1200); tickPop = 0 }
    }
    val lo = Const.XP_AT[pet.stage.ordinal]
    val frac = Const.xpNext(pet.stage)?.let { ((pet.xp - lo).toFloat() / (it - lo)).coerceIn(0f, 1f) } ?: 1f
    val per = if (tickDriving) Const.XP_TICK_DRIVE else Const.XP_TICK_OPEN
    val tickBar = remember { Animatable(0f) }
    val still = LocalReduceMotion.current
    LaunchedEffect(tickAt) { // 마지막 틱부터 10초 동안 차오르고, 다음 틱에 다시 0에서 시작
        val left = Const.XP_TICK_SEC * 1000L - (tickAt?.let { System.currentTimeMillis() - it.toEpochMilli() } ?: 0L).coerceIn(0L, Const.XP_TICK_SEC * 1000L)
        tickBar.snapTo(1f - left / (Const.XP_TICK_SEC * 1000f))
        if (!still) tickBar.animateTo(1f, tween(left.toInt(), easing = LinearEasing))
    }
    val body: @Composable () -> Unit = {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (Const.xpNext(pet.stage) == null) "최고 단계" else "다음 단계까지", style = Type.label, color = c.textPrimary, maxLines = 1)
                    Spacer(Modifier.width(12.dp))
                    ProgressBar(frac, Modifier.weight(1f), 16.dp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(xpLine(pet), style = Type.title.copy(fontFeatureSettings = "tnum"), color = c.textPrimary, maxLines = 1)
                    if (pop > 0) Text("  +$pop", style = Type.label, color = c.accent, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("함께 있는 시간", style = Type.caption, color = c.textSecondary, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Canvas(Modifier.width(48.dp).height(4.dp)) {
                        val r = CornerRadius(2.dp.toPx())
                        drawRoundRect(c.divider, size = size, cornerRadius = r)
                        drawRoundRect(c.accent, size = Size(size.width * tickBar.value, size.height), cornerRadius = r)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("켜 두면 ${Const.XP_TICK_SEC}초마다 +$per XP" + if (togetherSec >= 60) " · 오늘 ${togetherSec / 60}분" else "", style = Type.caption, color = c.textSecondary, maxLines = 1)
                    if (tickPop > 0) Text("  +$tickPop", style = Type.caption, color = c.accent, maxLines = 1)
                }
                Text("${g.nextLabel} ${g.done}/${g.total}일 · ${t.growLine}", style = Type.caption, color = c.textSecondary)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HeartRow(pet, g.hearts)
                    Spacer(Modifier.width(12.dp))
                    Text(t.heartLine, style = Type.caption, color = c.textSecondary, modifier = Modifier.weight(1f))
                }
                xpLog.forEachIndexed { i, x -> Text(xpLabel(x), style = Type.caption, color = if (i == 0) c.accent else c.textSecondary, maxLines = 1) }
            }
        }
    }
    if (pressable) Pressable(onClick, modifier.fillMaxWidth().heightIn(min = 64.dp), desc = desc) { body() }
    else Box(modifier.fillMaxWidth().semantics { contentDescription = desc }) { body() }
}

/** 모서리는 높이의 절반. 채움 폭 = XP 비율(0보다 크면 최소 6dp). 값이 바뀌면 300ms 동안 차오르고 끝에 별이 반짝(MO-BAR-FILL). */
@Composable
private fun ProgressBar(target: Float, modifier: Modifier, height: androidx.compose.ui.unit.Dp) {
    val c = LocalChama.current
    val still = LocalReduceMotion.current
    val fill = remember { Animatable(target) }
    val star = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (!still && target > fill.value) {
            fill.animateTo(target, tween(300))
            star.snapTo(0f); star.animateTo(1f, tween(150, easing = LinearEasing)); star.animateTo(0f, tween(150, easing = LinearEasing))
        } else fill.snapTo(target)
    }
    Canvas(modifier.height(height)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(c.divider, size = size, cornerRadius = r)
        val w = if (fill.value > 0f) maxOf(size.width * fill.value, 6.dp.toPx()) else 0f
        if (w > 0f) drawRoundRect(c.accent, size = Size(w, size.height), cornerRadius = r)
        if (star.value > 0f) {
            val s = 6.dp.toPx() * star.value
            withTransform({ translate(w, size.height / 2f); scale(s, s, Offset.Zero) }) { drawPath(star4(0f, 0f, 1f), c.accent, style = Fill) }
        }
    }
}

/** 하트 5개, 각 24dp, 사이 4dp. 부분 채움은 왼쪽부터 폭 비율로 자른다. 늘어나는 하트 하나는 300ms 1 -> 1.25 -> 1(MO-HEART-POP). */
@Composable
private fun HeartRow(pet: PetState, hearts: Float) {
    val c = LocalChama.current
    val still = LocalReduceMotion.current
    val start = if (Shown.petId == pet.petId && Shown.stage == pet.stage && Shown.hearts in 0f..hearts && !still) Shown.hearts else hearts
    val v = remember { Animatable(start) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(pet.petId, pet.stage, hearts) {
        val prev = Shown.hearts
        val same = Shown.petId == pet.petId && Shown.stage == pet.stage
        Shown.hearts = hearts
        if (!still && same && hearts > prev && prev >= 0f) {
            pop.snapTo(1f)
            coroutineScope {
                launch { v.animateTo(hearts, tween(300)) }
                launch { pop.animateTo(1.25f, tween(100)); pop.animateTo(1f, tween(200)) }
            }
        } else { v.snapTo(hearts); pop.snapTo(1f) }
    }
    Canvas(Modifier.size(136.dp, 24.dp)) {
        val box = 24.dp.toPx(); val step = 28.dp.toPx()
        val popIdx = (ceil(v.value) - 1f).toInt().coerceIn(0, 4)
        for (i in 0 until 5) {
            val f = (v.value - i).coerceIn(0f, 1f)
            val k = if (i == popIdx) pop.value else 1f
            withTransform({ translate(i * step, 0f); scale(k, k, Offset(box / 2f, box / 2f)) }) { heartShape(box, f, c.heart, c.textSecondary) }
        }
    }
}

/** 빈 하트는 text_secondary 2dp 외곽선, 채움은 chama_heart(색만이 아니라 채움 모양으로 구분). */
private fun DrawScope.heartShape(box: Float, fill: Float, color: Color, outline: Color) {
    withTransform({ scale(box, box, Offset.Zero) }) {
        if (fill > 0f) clipRect(0f, 0f, fill, 1f) { drawPath(HEART, color, style = Fill) }
        drawPath(HEART, if (fill >= 1f) color else outline, style = Stroke(2.dp.toPx() / box))
    }
}
