package com.carmagotchi.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.carmagotchi.app.AppUiState
import com.carmagotchi.app.Scene
import com.carmagotchi.app.SceneKind
import com.carmagotchi.app.domain.PetState
import com.carmagotchi.app.domain.Stage
import com.carmagotchi.app.ui.pet.PetCanvas
import com.carmagotchi.app.ui.pet.petDesc
import com.carmagotchi.app.ui.pet.sceneLook
import com.carmagotchi.app.ui.pet.spec

/** 가로 2칸 판단은 폭 > 높이(7.2). */
@Composable
fun Responsive(modifier: Modifier = Modifier, content: @Composable BoxWithConstraintsScope.(landscape: Boolean) -> Unit) =
    BoxWithConstraints(modifier) { content(maxWidth > maxHeight) }

/** 9.2 MO-BUBBLE: 300ms, 불투명도 0 → 1, 위로 16dp → 0. 움직임 줄이기면 바로 표시. */
@Composable
fun Modifier.bubbleIn(key: Any?): Modifier {
    val still = LocalReduceMotion.current
    val a = remember(key) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(key) { if (!still) a.animateTo(1f, tween(300, easing = CubicBezierEasing(0f, 0f, .2f, 1f))) }
    return graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 16.dp.toPx() }
}

/** EGG 대사 "의성어 / 해설: 해설" 또는 "(해설) 해설"을 나눈다. */
fun splitEgg(t: String): Pair<String?, String> = when {
    " / 해설: " in t -> t.substringBefore(" / 해설: ") to t.substringAfter(" / 해설: ")
    t.startsWith("(해설) ") -> null to t.removePrefix("(해설) ")
    else -> null to t
}

/** EGG는 말을 못 한다: 굵은 의성어 + 작은 회색 해설(11.1). */
@Composable
fun EggLine(text: String, modifier: Modifier = Modifier) {
    val c = LocalChama.current
    val (sound, note) = splitEgg(text)
    Column(modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (sound != null) Text(sound, style = Type.moment.copy(fontWeight = FontWeight.ExtraBold), color = c.textPrimary, textAlign = TextAlign.Center)
        Text(note, style = Type.caption, color = c.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun PetScene(pet: PetState, scene: Scene, size: Dp, state: AppUiState) {
    val spec = pet.spec()
    val mood = pet.moodAt(state.now)
    val look = sceneLook(spec, mood, scene)
    PetCanvas(spec, look, size, animate = state.isParked, motionKey = scene, desc = petDesc(spec, mood, look))
}

/** SC-DEPART / SC-ARRIVE(14.3, 14.4). 탭하면 방으로. 버튼·닫기 없음. */
@Composable
fun SceneScreen(state: AppUiState, onTap: () -> Unit, onCompanion: (String?) -> Unit) {
    val c = LocalChama.current
    val pet = state.pet ?: return
    val sc = state.scene ?: return
    val egg = pet.stage == Stage.EGG || " / 해설: " in sc.text || sc.text.startsWith("(해설) ") // 알일 때 만든 장면이 단계가 오른 뒤에 떠도 문장은 해설 모양 그대로
    val icon = eventIcon(sc.event)
    val depart = sc.kind == SceneKind.DEPART
    Responsive(Modifier.fillMaxSize().background(c.canvas).clickable(remember { MutableInteractionSource() }, null, onClick = onTap)) { land ->
        val pad = Dim.margin
        // 펫: 세로 360, 가로 440이 기준이고 창이 크면 빈 높이·칸 폭만큼 키운다(최대 560).
        val petSize = if (land) minOf(maxWidth / 2 - pad * 2, maxHeight - pad * 2, 560.dp) else minOf(maxWidth - pad * 2, maxHeight * .42f, 480.dp)
        val text: @Composable (tailLeft: Boolean) -> Unit = { left ->
            Column(Modifier.bubbleIn(sc), horizontalAlignment = if (left) Alignment.Start else Alignment.CenterHorizontally) {
                if (egg) EggLine(sc.text, Modifier.padding(vertical = 16.dp))
                else SpeechBubble(sc.text, icon = icon, tailLeft = left, iconNote = if (sc.picked) "주웠다" else null)
                if (depart && sc.chips.isNotEmpty()) Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { sc.chips.take(2).forEach { StatusChip(it) } }
                if (depart && sc.todo != null) Text(sc.todo, style = Type.body, color = c.textPrimary, modifier = Modifier.padding(top = 12.dp).widthIn(max = 640.dp), textAlign = if (left) TextAlign.Start else TextAlign.Center)
                sc.greetNames?.let { names ->
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        names.forEach { ToggleChip(it, false, { onCompanion(it) }) }
                        ToggleChip("말 안 할래", false, { onCompanion(null) })
                    }
                }
            }
        }
        if (land) Row(Modifier.fillMaxSize().padding(pad), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), Alignment.Center) { PetScene(pet, sc, petSize, state) }
            Box(Modifier.weight(1f).padding(start = 8.dp), Alignment.CenterStart) { text(true) }
        } else Column(Modifier.fillMaxSize().padding(pad), Arrangement.Center, Alignment.CenterHorizontally) {
            text(false)
            Spacer(Modifier.height(16.dp))
            PetScene(pet, sc, petSize, state)
        }
    }
}
