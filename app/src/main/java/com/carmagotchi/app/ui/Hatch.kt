@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.carmagotchi.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.carmagotchi.app.AppUiState
import com.carmagotchi.app.PetController
import com.carmagotchi.app.domain.Dialogue
import com.carmagotchi.app.domain.DriveSound
import com.carmagotchi.app.domain.Origin
import com.carmagotchi.app.domain.Stage
import com.carmagotchi.app.domain.Traits
import com.carmagotchi.app.ui.pet.*
import kotlinx.coroutines.delay
import java.time.LocalDate

val NAME_PICKS = listOf("콩이", "모모", "토리", "보리", "두부")
private val BIRTHDAY_PICKS = listOf("오늘" to 0L, "어제" to 1L, "지난주" to 7L, "지난달" to 30L, "1년 전" to 365L)

/** SC-HATCH(14.2). 단계 H1~H6. step은 위에서 들고 있어 회전·크기 변화에도 이어진다. */
@Composable
fun HatchScreen(state: AppUiState, step: Int, onStep: (Int) -> Unit, ctl: PetController, onShare: () -> Unit, onFinish: () -> Unit) {
    val c = LocalChama.current
    var name by rememberSaveable { mutableStateOf("") }
    var loc by rememberSaveable { mutableStateOf(false) }
    var back by rememberSaveable { mutableIntStateOf(0) }
    val pv = state.hatchPreview
    val egg = PetSpec(Stage.EGG, pv.palette, pv.pattern, pv.quirk, Traits(), "", 0)
    val used = state.odometerKm > 1000
    val sound = if (used) "뾰옥! / 해설: 이 차에 새로 이사 왔어요." else "뾰옥! / 해설: 알이 깨어났어요! 오늘부터 이 차에 살아요."
    val today = remember { LocalDate.now() }

    // H1: 3초 기다리면 깨어난다(탭 3번은 EggStep에서). H2: 연출 3초 뒤 이름 짓기.
    LaunchedEffect(step) { if (step == 0) { delay(3000); onStep(1) } else if (step == 1) { delay(3000); onStep(2) } }

    Responsive(Modifier.fillMaxSize().background(c.canvas).imePadding()) { land ->
        val pet: @Composable (Dp, PetLook, Any?, Boolean) -> Unit = { s, look, key, anim ->
            PetCanvas(egg, look, s, animate = state.isParked && anim, motionKey = key, desc = "알")
        }
        val big = if (land) minOf(maxWidth / 2 - 48.dp, maxHeight - 48.dp, 560.dp) else minOf(maxWidth - 48.dp, maxHeight * .45f, 400.dp)
        val small = if (land) big else minOf(maxWidth - 48.dp, maxHeight * .3f, 240.dp)
        val open = PetLook()
        val sway by rememberInfiniteTransition(label = "sway").animateFloat(-3f, 3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
        val still = LocalReduceMotion.current
        var taps by remember { mutableIntStateOf(0) }
        // MO-HATCH 1,500ms(껍데기 조각이 튕기는 순간)에 SND-HATCH. 알을 3번 눌러 깨웠으면 직접, 기다렸으면 자동
        LaunchedEffect(step) { if (step == 1) { delay(1500); ctl.sfx(DriveSound.HATCH, if (taps >= 3) Origin.TAP else Origin.AUTO) } }
        val stageLook = when (step) {
            0 -> PetLook(eggClosed = true, tilt = if (still || !state.isParked) 0f else sway, crack = taps >= 2)
            1 -> PetLook(motion = Motion.HATCH)
            else -> open
        }
        val side: @Composable ColumnScope.() -> Unit = {
            val pad = Modifier.padding(horizontal = if (land) 0.dp else 24.dp)
            when (step) {
                0 -> EggLine("톡… 톡톡. / 해설: 알 속에서 누가 두드려요.", pad)
                1 -> EggLine(sound, pad)
                2 -> NameStep(name, { name = it }, { if (name.isNotBlank()) onStep(3) }, pad)
                3 -> { val ask = LocalRequestSpeed.current; ConsentStep(name.trim().ifBlank { "콩이" }, loc, { loc = it }, { ask { onStep(4) } }, pad) }
                4 -> {
                    Text("이 차를 받은 날은?", style = Type.headline, color = c.textPrimary, modifier = pad)
                    Spacer(Modifier.height(16.dp))
                    Text(fmtDate(today.minusDays(BIRTHDAY_PICKS[back].second), state.now), style = Type.title, color = c.accent)
                    Spacer(Modifier.height(16.dp))
                    FlowRowSimple {
                        BIRTHDAY_PICKS.forEachIndexed { i, (l, _) -> ToggleChip(l, back == i, { back = i }) }
                    }
                    Spacer(Modifier.height(24.dp))
                    Btn("다음", { ctl.hatch(name.trim().ifBlank { "콩이" }, loc, today.minusDays(BIRTHDAY_PICKS[back].second)); onStep(5) }, Modifier.fillMaxWidth().padding(horizontal = if (land) 0.dp else 24.dp))
                }
                else -> {
                    val p = state.pet
                    if (p == null) DotLoader() else {
                        Text(Dialogue.fill("{펫이름} · 태어난 날 · {km}km", mapOf("펫이름" to p.name, "km" to "%,d".format(state.odometerKm.toInt())))!!.replace("태어난 날", fmtDate(p.hatchedAt, state.now)), style = Type.title, color = c.textPrimary, textAlign = TextAlign.Center, modifier = pad)
                        Text(if (used) "이 차에 새로 이사 왔어요." else "오늘부터 이 차에 살아요.", style = Type.body, color = c.textSecondary)
                        Spacer(Modifier.height(24.dp))
                        Row(Modifier.padding(horizontal = if (land) 0.dp else 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Btn("QR로 간직하기", onShare, Modifier.weight(1f), primary = false, icon = Ic.SHARE)
                            Btn("시작하기", onFinish, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        val picture: @Composable () -> Unit = {
            val s = if (step == 2 || step == 3 || step == 4) small else big
            if (step == 5 && state.pet != null) {
                val sp = state.pet.spec()
                PetCanvas(sp, baseLook(sp, state.pet.moodAt(state.now)), s, animate = state.isParked, desc = petDesc(sp, state.pet.moodAt(state.now), baseLook(sp, 70)))
            } else Box(Modifier.clickable(remember { MutableInteractionSource() }, null, enabled = step == 0) { if (++taps >= 3) onStep(1) }) { pet(s, stageLook, if (step == 1) "hatch" else step, true) }
        }
        if (land) Row(Modifier.fillMaxSize().padding(24.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight(), Alignment.Center) { picture() }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), Arrangement.Center, Alignment.CenterHorizontally) { side() }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            picture(); Spacer(Modifier.height(16.dp)); side()
        }
    }
}

@Composable
private fun FlowRowSimple(content: @Composable () -> Unit) =
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, onDone: () -> Unit, pad: Modifier) {
    val c = LocalChama.current
    Text("이름을 지어 주세요.", style = Type.headline, color = c.textPrimary, modifier = pad)
    Spacer(Modifier.height(16.dp))
    ChamaField(name, onName, "이름 (1~6자)", pad, invalid = name.isNotEmpty() && name.isBlank(), desc = "펫 이름, 1에서 6자", onDone = onDone)
    if (name.isNotEmpty() && name.isBlank()) Text(Lines.text(LocalContext.current, "DLG-HATCH-NAME-S3", "이름을 한 글자 이상 써 주세요."), style = Type.caption, color = c.ailment, modifier = pad)
    else Text("이름은 언제든 바꿀 수 있어요.", style = Type.caption, color = c.textSecondary, modifier = pad)
    Spacer(Modifier.height(16.dp))
    FlowRowSimple { NAME_PICKS.forEach { ToggleChip(it, name == it, { onName(it) }) } }
    Spacer(Modifier.height(24.dp))
    Btn("이걸로 할래", onDone, pad.fillMaxWidth(), enabled = name.isNotBlank())
}

@Composable
private fun ConsentStep(name: String, loc: Boolean, onLoc: (Boolean) -> Unit, onOk: () -> Unit, pad: Modifier) {
    val c = LocalChama.current
    Text(Dialogue.fill("{펫이름|이/가} 본 것은 이 차 안에만 저장돼요.", mapOf("펫이름" to name)) ?: "", style = Type.headline, color = c.textPrimary, modifier = pad)
    Spacer(Modifier.height(12.dp))
    Text("차량 신호(기어, 연료, 경고 등)를 읽어요. 위치와 이름은 차 밖으로 나가지 않아요.", style = Type.body, color = c.textSecondary, modifier = pad)
    Spacer(Modifier.height(8.dp))
    SwitchRow("위치 사용 (장소 도장)", loc, onLoc)
    Text("켜면 처음 간 동네가 도장으로 남아요. 위치는 차 안에만 저장돼요.", style = Type.caption, color = c.textSecondary, modifier = pad)
    Spacer(Modifier.height(24.dp))
    Btn("확인", onOk, pad.fillMaxWidth())
}
