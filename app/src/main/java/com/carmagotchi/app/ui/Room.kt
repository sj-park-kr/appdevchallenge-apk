@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.carmagotchi.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import com.carmagotchi.app.*
import com.carmagotchi.app.domain.*
import com.carmagotchi.app.ui.pet.*
import java.time.ZoneId

private val KST = ZoneId.of("Asia/Seoul")

/** PRD F4 7번: 갈래를 점수 없이 문장으로. */
fun storyOf(pet: PetState): String {
    val t = pet.traits
    val parts = buildList {
        when (t.temperament) { Temperament.CALM -> add("느긋하고"); Temperament.LIVELY -> add("씩씩하고"); else -> {} }
        when (t.roamer) { Roamer.TOWN -> add("동네를 좋아하고"); Roamer.EXPLORER -> add("먼 길을 좋아하고"); else -> {} }
        when (t.social) { Social.SOLO -> add("단둘이 다니는 걸 좋아해요"); Social.FRIENDLY -> add("친구를 반겨요"); else -> {} }
    }
    if (parts.isEmpty()) return "아직 어떤 아이인지 알아 가는 중이에요."
    val last = parts.last()
    return (parts.dropLast(1) + if (last.endsWith("요")) last else last.removeSuffix("고") + "해요").joinToString(", ")
}

/** 메인 3탭의 공통 틀: Page navigation + Tabs(7.4). */
@Composable
fun MainShell(pet: PetState, tab: Int, onTab: (Int) -> Unit, onSettings: () -> Unit, land: Boolean, quiet: Boolean? = null, onQuiet: (Boolean) -> Unit = {}, quietEnabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalChama.current
    Column(Modifier.fillMaxSize().background(c.canvas)) {
        PageNav(pet.name, "${stageName(pet.stage)} · 함께한 지 ${pet.daysTogether}일", onSettings = onSettings, trailing = if (quiet == null) null else { { QuietButton(quiet, quietEnabled, onQuiet) } })
        LineTabs(listOf("방", "도감", "앨범"), listOf(Ic.TAB_ROOM, Ic.TAB_DEX, Ic.TAB_ALBUM), tab, onTab, fitted = !land)
        content()
    }
}

@Composable
private fun ChipPopup(chip: Chip, onDismiss: () -> Unit) {
    val c = LocalChama.current
    SystemPopup(chip.label, onDismiss, large = false) {
        StatusChip(chip)
        Spacer(Modifier.height(16.dp))
        Text(chip.todo ?: "걱정 마. 지금은 괜찮아.", style = Type.body, color = c.textPrimary)
    }
}

@Composable
private fun DecoratePopup(state: AppUiState, pet: PetState, onPlace: (String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalChama.current
    val slots = slotCount(pet.stage)
    val owned = Content.ITEMS.filter { it.id in pet.items }
    SystemPopup("방 꾸미기", onDismiss) {
        Text("칸 ${state.placed.size}/$slots", style = Type.caption, color = c.textSecondary)
        Spacer(Modifier.height(12.dp))
        if (slots == 0) Text("알은 아직 방을 꾸미지 못해요.", style = Type.body, color = c.textPrimary)
        else if (owned.isEmpty()) Text("길에서 물건을 주우면 여기 모여요.", style = Type.body, color = c.textPrimary)
        else LazyVerticalGrid(GridCells.Adaptive(132.dp), Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(owned) { item ->
                val on = item.id in state.placed
                Pressable({ onPlace(item.id) }, Modifier.fillMaxWidth().height(148.dp), desc = "${item.name}, ${if (on) "놓여 있어요. 누르면 치워요" else "누르면 놓아요"}") { _ ->
                    Column(
                        Modifier.fillMaxSize().background(if (on) c.accent.copy(alpha = .16f) else c.sunken, RoundedCornerShape(20.dp))
                            .border(if (on) 3.dp else 1.dp, if (on) c.accent else c.divider, RoundedCornerShape(20.dp)).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Canvas(Modifier.size(72.dp).clearAndSetSemantics { }) { drawItem(item.id) }
                        Text(item.name, style = Type.caption, color = c.textPrimary, maxLines = 2)
                        if (on) Text("놓음", style = Type.caption, color = c.accent)
                    }
                }
            }
        }
    }
}

/** SC-ROOM(14.5). ColumnScope 안에서 남은 높이를 차지한다. */
@Composable
fun ColumnScope.RoomScreen(state: AppUiState, ctl: PetController) {
    val c = LocalChama.current
    val pet = state.pet ?: return
    val spec = pet.spec()
    val mood = pet.moodAt(state.now)
    var popup by remember { mutableStateOf<Chip?>(null) }
    var decorate by remember { mutableStateOf(false) }
    var story by remember { mutableStateOf(false) }
    val guest = state.settings.guestMode
    val chips = state.roomChips
    val bubbleText = when {
        guest -> "손님 모드 중에는 주행을 기록하지 않아요."
        state.bubble != null -> state.bubble.text
        state.pendingEarlierScene != null -> state.pendingEarlierScene.text
        else -> null
    }
    val base = baseLook(spec, mood)
    val reaction = state.bubble?.reaction
    val look = when {
        guest -> base.copy(eyes = Eye.CLOSED, mouth = Mouth.SMILE, fx = setOf(Fx.ZZ), tilt = 4f)
        state.bubble?.annoyed == true -> base.copy(eyes = Eye.SQUEEZE, mouth = Mouth.FROWN, tilt = -6f, motion = Motion.SHIVER) // 귀찮아서 몸을 부르르
        reaction == Interaction.PET -> base.copy(motion = Motion.PET, eyes = Eye.HAPPY, fx = setOf(Fx.HEART))
        reaction == Interaction.SNACK -> base.copy(motion = Motion.SNACK, eyes = Eye.HAPPY)
        pet.affection >= 40 -> base.copy(motion = Motion.HOP)
        else -> base
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(pet.snackPop) { if (pet.snackPop > 0) { delay(1800); ctl.ackSnackPop() } } // "+N" 한 번 보여 주고 끝
    Responsive(Modifier.weight(1f).fillMaxWidth()) { land ->
        val stage: @Composable (Modifier) -> Unit = { m ->
            RoomStage(
                m, state, spec, look, bubbleText, state.bubble?.hint, petDesc(spec, mood, look),
                roamAllowed = !guest, onRub = { ctl.interact(Interaction.PET) }, onVroom = { ctl.sfx(DriveSound.VROOM, Origin.TAP) },
                onRoam = { ctl.roam(it) },
            )
        }
        val buttons = @Composable {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Btn("쓰다듬기", { ctl.interact(Interaction.PET) }, Modifier.weight(1f), primary = false, icon = Ic.PET)
                Box(Modifier.weight(1f)) {
                    Btn("간식 ${pet.snacks}", { ctl.interact(Interaction.SNACK) }, Modifier.fillMaxWidth(), primary = false, icon = Ic.SNACK) // 0개여도 눌린다: 말풍선이 안내한다
                    if (pet.snackPop > 0) SnackPop(pet.snackPop, Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = (-14).dp))
                }
                Btn("방 꾸미기", { decorate = true }, Modifier.weight(1f), primary = false, icon = Ic.DECORATE)
                Btn("레벨업 (시연)", { ctl.runDemo("levelup") }, Modifier.weight(1f), primary = true) // 목업 시연용: 누를 때마다 다음 단계, 단짝 다음엔 알
            }
        }
        if (land) Row(Modifier.fillMaxSize().padding(Dim.margin), horizontalArrangement = Arrangement.spacedBy(Dim.margin)) {
            Column(Modifier.weight(2.6f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) { stage(Modifier.weight(1f).fillMaxWidth()); buttons() }
            TodayColumn(state, pet, chips, { popup = it }, Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
        } else Column(Modifier.fillMaxSize().padding(horizontal = Dim.margin).padding(top = 16.dp, bottom = Dim.margin), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            stage(Modifier.weight(1f).fillMaxWidth())
            GrowthCard(pet, state.now, xpLog = state.xpLog.take(1), tickAt = state.tickAt, tickDriving = state.tickDriving, togetherSec = state.togetherSec) { story = true }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.take(2).forEach { StatusChip(it, onClick = { popup = it }) }
                if (chips.size > 2) TextBtn("외 ${chips.size - 2}", { popup = chips[2] })
            }
            buttons()
        }
        popup?.let { ChipPopup(it) { popup = null } }
        if (decorate) DecoratePopup(state, pet, ctl::placeItem) { decorate = false }
        if (story) SystemPopup("${pet.name} 이야기", { story = false }, large = false) {
            Text(storyOf(pet), style = Type.body, color = c.textPrimary)
            val t = growthTexts(Growth.of(pet, state.now))
            Spacer(Modifier.height(12.dp)); Text(t.growLine, style = Type.caption, color = c.textSecondary)
            Spacer(Modifier.height(4.dp)); Text(t.heartLine, style = Type.caption, color = c.textSecondary)
        }
    }
}

/** 오늘의 간식 "+N": MO-HEART-POP처럼 1 -> 1.25 -> 1로 튀고 잠깐 머문다. 움직임 줄이기면 그냥 보인다. */
@Composable
private fun SnackPop(n: Int, modifier: Modifier) {
    val c = LocalChama.current
    val still = LocalReduceMotion.current
    val k = remember { androidx.compose.animation.core.Animatable(if (still) 1f else .6f) }
    LaunchedEffect(Unit) { if (k.value != 1f) { k.animateTo(1.25f, androidx.compose.animation.core.tween(150)); k.animateTo(1f, androidx.compose.animation.core.tween(150)) } }
    Box(modifier.graphicsLayer { scaleX = k.value; scaleY = k.value }.background(c.accent, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "오늘의 간식 ${n}개를 받았어요" }) {
        Text("+$n", style = Type.label, color = c.onAccent)
    }
}

/** 가로 2칸 오른쪽 칸 "오늘"(7.4): 이야기, 상태, 최근 길동무. 세로의 팝업과 같은 정보(기능 패리티). */
@Composable
private fun TodayColumn(state: AppUiState, pet: PetState, chips: List<Chip>, onChip: (Chip) -> Unit, modifier: Modifier) {
    val c = LocalChama.current
    val recent = pet.visitors.maxByOrNull { it.value.lastMetAt }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GrowthCard(pet, state.now, pressable = false, xpLog = state.xpLog, tickAt = state.tickAt, tickDriving = state.tickDriving, togetherSec = state.togetherSec)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp)) {
                Text("${pet.name} 이야기", style = Type.label, color = c.textSecondary)
                Spacer(Modifier.height(8.dp)); Text(storyOf(pet), style = Type.body, color = c.textPrimary)
            }
        }
        chips.forEach { ch ->
            Pressable({ onChip(ch) }, Modifier.fillMaxWidth(), desc = "${ch.label}, 누르면 할 일을 볼 수 있어요") { _ ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { StatusChip(ch); if (ch.todo != null) Text(ch.todo, style = Type.caption, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp)) }
                        Icon(Ic.CHEVRON, c.textSecondary, 32.dp)
                    }
                }
            }
        }
        recent?.let { (id, r) ->
            val v = Content.VISITORS.first { it.id == id }
            Card(Modifier.fillMaxWidth()) {
                Text("최근 만난 길동무: ${v.name} · ${fmtDate(r.lastMetAt, state.now)}", style = Type.body, color = c.textPrimary, modifier = Modifier.padding(24.dp))
            }
        }
    }
}

/** 10.4: 글자 있는 작은 버튼. 누르면 오늘(04:00까지) 앱 소리를 모두(주행 중·정차 중) 끄고, 다시 누르면 켠다. */
@Composable
private fun QuietButton(quiet: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val c = LocalChama.current
    val label = if (quiet) "오늘 조용히" else "조용히"
    Pressable({ onToggle(!quiet) }, Modifier.padding(end = 8.dp).height(56.dp), enabled, desc = if (quiet) "오늘 조용히, 꺼짐. 두 번 누르면 오늘 앱 소리를 모두 꺼요" else "앱 소리, 켜짐. 누르면 오늘 조용히 해요") { p ->
        Row(
            Modifier.fillMaxHeight().background(if (!enabled) c.divider else if (p) c.secondaryPressed else c.buttonSecondary, RoundedCornerShape(28.dp)).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (quiet) Ic.SOUND_QUIET else Ic.SOUND_ON, c.textPrimary, 28.dp, filled = quiet); Spacer(Modifier.width(8.dp))
            Text(label, style = Type.label, color = c.textPrimary, maxLines = 1)
        }
    }
}
