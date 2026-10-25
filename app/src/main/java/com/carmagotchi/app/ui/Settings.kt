package com.carmagotchi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.carmagotchi.app.AppUiState
import com.carmagotchi.app.PetController
import com.carmagotchi.app.domain.Dialogue
import kotlinx.coroutines.delay

private enum class Group(val label: String) { PET("펫"), PASSENGER("동승자"), DISPLAY("화면·소리·기록"), DATA("데이터·개인정보"), INFO("정보"), DEMO("시연") }

private enum class Pop { NONE, RENAME, PASSENGERS, LOC_OFF, DELETE, PRIVACY, GLEO, LICENSES }

/** 2초 길게 눌러야 실행되는 위험 버튼(PRD F13). 시간은 프레임 시각으로 잰다(시스템 애니메이션 배율 0에서도 2초). */
@Composable
private fun HoldButton(label: String, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalChama.current
    var holding by remember { mutableStateOf(false) }
    var prog by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(holding) {
        if (holding) {
            val t0 = androidx.compose.runtime.withFrameMillis { it }
            while (prog < 1f) { prog = ((androidx.compose.runtime.withFrameMillis { it } - t0) / 2000f).coerceAtMost(1f) }
            holding = false; prog = 0f; onDone()
        } else prog = 0f
    }
    Box(
        modifier.height(Dim.button).clip(RoundedCornerShape(16.dp)).background(c.ailmentBg)
            .semantics { onLongClick("모두 지우기") { onDone(); true } }
            .pointerInput(Unit) { detectTapGestures(onPress = { holding = true; tryAwaitRelease(); holding = false }) },
        Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(prog).background(c.ailment.copy(alpha = .35f)))
        Text(if (holding) "계속 누르세요…" else label, style = Type.label, color = c.ailment, maxLines = 1)
    }
}

@Composable
private fun NamesEditor(initial: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val names = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    SystemPopup("동승자 이름", onDismiss, buttons = { Btn("저장", { onSave(names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()); onDismiss() }, Modifier.weight(1f)) }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            names.forEachIndexed { i, n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChamaField(n, { names[i] = it }, "이름 (6자)", Modifier.weight(1f), desc = "동승자 이름 ${i + 1}")
                    IconBtn(Ic.CLOSE, "${n.ifBlank { "이름" }} 지우기", { names.removeAt(i) })
                }
            }
            if (names.size < 6) TextBtn("+ 이름 추가", { names.add("") })
        }
    }
}

@Composable
private fun TextPopup(title: String, text: String, onDismiss: () -> Unit) {
    SystemPopup(title, onDismiss) { Text(text, style = Type.body, color = LocalChama.current.textPrimary, modifier = Modifier.verticalScroll(rememberScrollState())) }
}

/** SC-SETTINGS(14.9). 세로는 한 줄 목록, 가로는 왼쪽 그룹 + 오른쪽 항목. */
@Composable
fun SettingsScreen(state: AppUiState, ctl: PetController, onLook: () -> Unit = {}, onBack: () -> Unit) {
    val c = LocalChama.current
    val pet = state.pet
    val ctx = LocalContext.current
    val set = state.settings
    val toast = LocalToast.current
    var pop by rememberSaveable { mutableStateOf(Pop.NONE) }
    var group by rememberSaveable { mutableStateOf(Group.PET) }
    var taps by remember { mutableIntStateOf(0) }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0.0" }
    val name = pet?.name ?: "펫"

    @Composable
    fun items(g: Group) { when (g) {
        Group.DEMO -> DemoPanel(ctl, toast)
        Group.PET -> {
            NavRow("펫 이름", name, { pop = Pop.RENAME })
            if (pet != null) NavRow("모습 꾸미기", null, onLook)
            if (set.vinFallback) Text("다시 설치하면 모습이 달라질 수 있어요", style = Type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = Dim.margin))
        }
        Group.PASSENGER -> NavRow("동승자 이름", "${state.passengerNames.size}명 등록", { pop = Pop.PASSENGERS })
        Group.DISPLAY -> {
            SwitchRow("도착 카드", set.arriveCard, { ctl.updateSettings(set.copy(arriveCard = it)) })
            SwitchRow("출발 인사", set.departGreeting, { ctl.updateSettings(set.copy(departGreeting = it)) })
            SwitchRow("주행 중 소리", set.driveSounds, { ctl.updateSettings(set.copy(driveSounds = it)) })
            Text("주행 중에 1초쯤 작게, 한 번 주행에 3번까지 소리를 내요.", style = Type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = Dim.margin))
            SwitchRow("정차 중 효과음", set.parkedSounds, { ctl.updateSettings(set.copy(parkedSounds = it)) })
            Text("차가 서 있을 때 쓰다듬기·간식 같은 순간에 작은 소리를 내요.", style = Type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = Dim.margin))
            SwitchRow("손님 모드 (24시간 기록 안 함)", set.guestMode, { ctl.updateSettings(set.copy(guestMode = it)) })
            if (set.guestMode) Text("손님 모드 중에는 주행을 기록하지 않아요.", style = Type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = Dim.margin))
        }
        Group.DATA -> {
            SwitchRow("위치 사용 (장소 도장)", set.locationOn, { if (it) ctl.updateSettings(set.copy(locationOn = true)) else { ctl.updateSettings(set.copy(locationOn = false)); pop = Pop.LOC_OFF } })
            Text("켜면 처음 간 동네가 도장으로 남아요. 위치는 차 안에만 저장돼요.", style = Type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = Dim.margin))
            NavRow("데이터 모두 지우기", null, { pop = Pop.DELETE }, danger = true)
            NavRow("개인정보 안내", null, { pop = Pop.PRIVACY })
        }
        Group.INFO -> {
            if (set.gleoPresent) NavRow("Gleo 도움말", null, { pop = Pop.GLEO })
            NavRow("오픈소스 라이선스", null, { pop = Pop.LICENSES })
            // 숨은 시연 모드: 버전 글자를 7번 누르면 켜고 끈다(릴리스 포함)
            Pressable({ if (++taps >= 7) { taps = 0; ctl.setDemoMode(!state.demoMode); toast(if (state.demoMode) "시연 모드를 껐어요" else "시연 모드를 켰어요") } }, Modifier.fillMaxWidth().height(Dim.row)) { _ ->
                Text("버전 $version", style = Type.caption, color = c.textSecondary, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = Dim.margin))
            }
        }
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        PageNav("설정", null, onBack = onBack)
        Responsive(Modifier.weight(1f).fillMaxWidth()) { land ->
            if (land) Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(280.dp).padding(start = Dim.margin, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Group.entries.filter { it != Group.DEMO || state.demoMode }.forEach { g -> ToggleChip(g.label, g == group, { group = g }, Modifier.fillMaxWidth(), wide = true) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = Dim.margin)) { items(group) }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Group.entries.filter { it != Group.DEMO || state.demoMode }.forEach { g -> GroupLabel(g.label); items(g) }
                Spacer(Modifier.height(24.dp))
            }
            when (pop) {
                Pop.NONE -> {}
                Pop.RENAME -> {
                    var n by remember { mutableStateOf(name) }
                    SystemPopup("펫 이름", { pop = Pop.NONE }, large = false, buttons = { Btn("저장", { if (n.isNotBlank()) { ctl.renamePet(n.trim()); toast("이름을 바꿨어"); pop = Pop.NONE } }, Modifier.weight(1f), enabled = n.isNotBlank()) }) {
                        ChamaField(n, { n = it }, "이름 (1~6자)", desc = "펫 이름")
                    }
                }
                Pop.PASSENGERS -> NamesEditor(state.passengerNames, ctl::setPassengerNames) { pop = Pop.NONE }
                Pop.LOC_OFF -> SystemPopup("장소 도장을 지울까요?", { pop = Pop.NONE }, large = false, buttons = {
                    Btn("남겨 두기", { pop = Pop.NONE }, Modifier.weight(1f), primary = false)
                    Btn("지우기", { ctl.clearPlaces(); pop = Pop.NONE }, Modifier.weight(1f))
                }) { Text("위치 사용을 껐어요. 지금까지 모은 장소 도장은 남겨 둘 수도 있어요.", style = Type.body, color = c.textPrimary) }
                Pop.DELETE -> SystemPopup("모든 기억을 지울까요?", { pop = Pop.NONE }, large = false, buttons = {
                    Btn("취소", { pop = Pop.NONE }, Modifier.weight(1f), primary = false)
                    HoldButton("모두 지우기", { pop = Pop.NONE; ctl.deleteAllData() }, Modifier.weight(1f))
                }) {
                    Text(Dialogue.fill("지우면 되돌릴 수 없어요. {펫이름|은/는} 알로 돌아가요.", mapOf("펫이름" to name)) ?: "", style = Type.body, color = c.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("[모두 지우기]를 2초 동안 길게 눌러요.", style = Type.caption, color = c.textSecondary)
                }
                Pop.PRIVACY -> TextPopup("개인정보 안내", "• 모든 기억은 이 차 안에만 저장돼요.\n• 차대번호(VIN)는 저장하지 않고, 모습을 정하는 데만 써요.\n• 위치는 켠 경우에만 쓰고, 좌표는 저장하지 않아요.\n• 동승자 이름은 누른 경우에만 기억하고, 카드에는 기본으로 넣지 않아요.\n• 언제든 [데이터 모두 지우기]로 지울 수 있어요.") { pop = Pop.NONE }
                Pop.GLEO -> TextPopup("Gleo 도움말", Dialogue.fill("운전 중에 \"차마 {펫이름} 기분 어때?\"라고 물어보세요.", mapOf("펫이름" to name)) ?: "") { pop = Pop.NONE }
                Pop.LICENSES -> {
                    val txt = remember { listOf("licenses/AstaSans-OFL.txt", "licenses/zxing-Apache-2.0.txt").joinToString("\n\n— — —\n\n") { f -> runCatching { ctx.assets.open(f).bufferedReader().readText() }.getOrDefault(f) } }
                    TextPopup("오픈소스 라이선스", txt) { pop = Pop.NONE }
                }
            }
        }
    }
}

/** 시연 모드에서만 보이는 패널: adb 없이 PT 시나리오를 돌린다(차가 켜져 서비스가 돌 때). */
@Composable
private fun DemoPanel(ctl: PetController, toast: (String) -> Unit) {
    val c = LocalChama.current
    val acts = listOf(
        "동승자 태우고 한 바퀴" to "passenger", "비 오는 길" to "rain", "타이어 경고 켜기" to "tpms_on", "타이어 경고 끄기" to "tpms_off",
        "충전·주유 (밥)" to "soc", "7일 지나기 (성장)" to "grow", "강제 레벨업 (BUDDY 다음엔 알로)" to "levelup", "차 생일" to "birthday",
    )
    Text("차가 켜져 있을 때 눌러요. 눌러도 차는 움직이지 않아요.", style = Type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = Dim.margin, vertical = 8.dp))
    acts.forEach { (label, script) -> NavRow(label, null, { ctl.runDemo(script); toast("실행했어요: $label") }) }
    Text("소리 미리 듣기 (정차 중에만)", style = Type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = Dim.margin, vertical = 8.dp))
    listOf(
        listOf("출발" to "DEPART", "친구" to "FRIEND", "비" to "RAIN", "평온" to "CALM"),
        listOf("부화" to "HATCH", "인사" to "GREET", "냠" to "MUNCH", "회복" to "RECOVER"),
        listOf("쓰다듬기" to "PET", "길동무" to "VISITOR", "도착" to "ARRIVE"),
    ).forEach { row ->
        Row(Modifier.padding(horizontal = Dim.margin).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { (label, id) -> Btn(label, { ctl.runDemo("sound:$id"); toast("$label 소리") }, Modifier.weight(1f), primary = false) }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
