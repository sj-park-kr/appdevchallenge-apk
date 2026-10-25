@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.carmagotchi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.carmagotchi.app.AppUiState
import com.carmagotchi.app.PetController
import com.carmagotchi.app.domain.*
import com.carmagotchi.app.ui.pet.*

private val TABS = listOf("몸", "바퀴", "미러·안테나", "얼굴", "튜닝")

/** SC-LOOK(F16): 모습 꾸미기. 주행 중에는 AppContent가 화면을 통째로 내려 draft가 버려진다. */
@Composable
fun LookScreen(state: AppUiState, ctl: PetController, onClose: () -> Unit) {
    val c = LocalChama.current
    val pet = state.pet ?: return
    val saved = pet.carLook()
    var draft by remember { mutableStateOf(saved) }
    var tab by remember { mutableIntStateOf(0) }
    var grown by remember { mutableStateOf(false) }
    val spec0 = pet.spec()
    val spec = spec0.copy(carLook = draft, stage = if (grown) Stage.ADULT else spec0.stage)

    @Composable
    fun Part(title: String, content: @Composable () -> Unit) {
        Text(title, style = Type.label, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
    @Composable
    fun Opts(labels: List<String>, cur: Int, set: (Int) -> Unit) = labels.forEachIndexed { i, l -> ToggleChip(l, i == cur, { set(i) }) }
    @Composable
    fun <T : Enum<T>> Pick(values: List<T>, label: (T) -> String, cur: T, set: (T) -> Unit) = Opts(values.map(label), values.indexOf(cur)) { set(values[it]) }

    val preview = @Composable { m: Modifier, size: androidx.compose.ui.unit.Dp ->
        Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                val side = (minOf(maxWidth, maxHeight) * .8f).coerceAtMost(size) // size = 상한(440dp)
                // 눈 모양 칩이 보이도록 뜬 눈으로 고정
                PetCanvas(spec, baseLook(spec, pet.moodAt(state.now)).copy(eyes = com.carmagotchi.app.ui.pet.Eye.OPEN), side, desc = "미리보기, ${petDesc(spec, pet.moodAt(state.now), PetLook(), false)}")
            }
            SwitchRow("자란 모습 보기", grown, { grown = it })
        }
    }
    val pickers = @Composable { m: Modifier ->
        Column(m) {
            BoxTabs(TABS, tab, { tab = it }, Modifier.fillMaxWidth())
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when (tab) {
                    0 -> {
                        Part("몸 형") { Pick(BodyShape.entries, { it.label }, draft.body) { draft = draft.copy(body = it) } }
                        Part("길이") { Opts(CarLook.LENGTH_LABELS, draft.length) { draft = draft.copy(length = it) } }
                        Part("색") {
                            CarLook.COLOR_NAMES.forEachIndexed { i, n ->
                                val on = i == draft.color
                                Pressable({ draft = draft.copy(color = i) }, Modifier.size(64.dp), desc = "$n, ${if (on) "고름" else "누르면 골라요"}") { _ ->
                                    Box(
                                        Modifier.size(52.dp).background(PALETTES.getOrNull(i)?.body ?: c.sunken, CircleShape)
                                            .border(if (on) 4.dp else 1.dp, if (on) c.accent else c.divider, CircleShape).semantics { selected = on },
                                    )
                                }
                            }
                        }
                        Part("무늬") { Opts(PATTERN_NAMES, draft.pattern) { draft = draft.copy(pattern = it) } }
                    }
                    1 -> {
                        Part("바퀴발 크기") { Opts(CarLook.WHEEL_LABELS, draft.wheelSize) { draft = draft.copy(wheelSize = it) } }
                        Part("휠") { Pick(Rim.entries, { it.label }, draft.rim) { draft = draft.copy(rim = it) } }
                    }
                    2 -> {
                        Part("사이드미러") { Pick(Mirror.entries, { it.label }, draft.mirror) { draft = draft.copy(mirror = it) } }
                        Part("안테나") { Pick(Antenna.entries, { it.label }, draft.antenna) { draft = draft.copy(antenna = it) } }
                    }
                    3 -> {
                        Part("눈") { Pick(EyeShape.entries, { it.label }, draft.eyes) { draft = draft.copy(eyes = it) } }
                        Part("볼 색") { Pick(CheekColor.entries, { it.label }, draft.cheek) { draft = draft.copy(cheek = it) } }
                    }
                    else -> {
                        Part("차고") { Opts(CarLook.RIDE_LABELS, draft.ride) { draft = draft.copy(ride = it) } }
                        Part("뒷날개") { Opts(CarLook.WING_LABELS, draft.wing) { draft = draft.copy(wing = it) } }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Btn("처음 모습으로", { draft = carLookFromVin(pet.vinSeedHash, pet.appearance) }, Modifier.weight(1f), primary = false)
                Btn("취소", onClose, Modifier.weight(1f), primary = false)
                Btn("저장", { ctl.setLook(draft.clamped()); onClose() }, Modifier.weight(1f), enabled = draft != saved)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        PageNav("모습 꾸미기", null, onBack = onClose)
        Responsive(Modifier.weight(1f).fillMaxWidth()) { land ->
            if (land) Row(Modifier.fillMaxSize().padding(Dim.margin), horizontalArrangement = Arrangement.spacedBy(Dim.margin)) {
                preview(Modifier.weight(1f).fillMaxHeight(), 440.dp); pickers(Modifier.weight(1.4f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize().padding(horizontal = Dim.margin).padding(bottom = Dim.margin), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                preview(Modifier.weight(1f).fillMaxWidth(), 440.dp); pickers(Modifier.weight(1.4f).fillMaxWidth())
            }
        }
    }
}
