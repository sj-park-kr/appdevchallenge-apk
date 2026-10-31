package com.carmagotchi.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.carmagotchi.app.Memory
import com.carmagotchi.app.PetController
import com.carmagotchi.app.domain.EventId
import kotlinx.coroutines.delay

val LocalToast = staticCompositionLocalOf<(String) -> Unit> { {} }
/** 부화 동의 뒤 CAR_SPEED 실행 중 허용을 요청하고, 응답(허용·거부 모두)이 오면 다음 단계로. 기본은 바로 진행(테스트·미리보기). */
val LocalRequestSpeed = staticCompositionLocalOf<(() -> Unit) -> Unit> { { it() } }

@Composable
fun AppRoot(ctl: PetController, dark: Boolean = isSystemInDarkTheme(), startTab: Int = 0, startOverlay: String? = null, startHatchStep: Int = 0) {
    val st by ctl.state.collectAsState()
    ChamaTheme(dark, st.reduceMotion) { AppContent(ctl, startTab, startOverlay, startHatchStep) }
}

/** 14.1 화면 전환. 주행 중(isParked=false)이면 바탕만 남긴다(9.4). */
@Composable
fun AppContent(ctl: PetController, startTab: Int = 0, startOverlay: String? = null, startHatchStep: Int = 0) {
    val c = LocalChama.current
    val st by ctl.state.collectAsState()
    val pet = st.pet
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var overlay by rememberSaveable { mutableStateOf(startOverlay) }
    var hatchStep by rememberSaveable { mutableIntStateOf(startHatchStep) }
    var hatching by rememberSaveable { mutableStateOf(pet == null || startHatchStep > 0) }
    var hadPet by remember { mutableStateOf(pet != null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pet == null) {
        if (pet == null && hadPet) { hatching = true; hatchStep = 0; overlay = null }
        hadPet = pet != null
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2000); toast = null } }
    val onRoom = !hatching && st.scene == null && overlay == null && tab == 0
    LaunchedEffect(st.pendingEarlierScene, onRoom) { if (onRoom && st.pendingEarlierScene != null) { delay(8000); ctl.ackEarlierScene() } }
    LaunchedEffect(st.isParked) { if (!st.isParked && overlay == "look") overlay = null } // 주행 중엔 꾸미기를 닫고 저장 안 한 변경을 버린다
    BackHandler(overlay != null) { overlay = null }

    fun memory(id: String): Memory? = when (id) {
        "weekly" -> weeklyMemory(st)
        "birth" -> pet?.let { Memory("birth", "${it.name}가 태어난 날", it.hatchedAt, EventId.TRIP_START) }
        else -> st.memories.firstOrNull { it.id == id }
    }

    CompositionLocalProvider(LocalToast provides { toast = it }) {
        Box(Modifier.fillMaxSize().background(c.canvas)) {
            when {
                !st.isParked -> {}
                hatching && overlay?.startsWith("share:") == true -> memory(overlay!!.removePrefix("share:"))?.let { ShareScreen(st, it) { overlay = null } }
                hatching -> HatchScreen(st, hatchStep, { hatchStep = it }, ctl, { overlay = "share:birth" }) { hatching = false; tab = 0 }
                st.scene != null && overlay == null -> SceneScreen(st, ctl::dismissScene, ctl::chooseCompanion)
                else -> Main(st, ctl, tab, { tab = it }, overlay, { overlay = it })
            }
            if (st.demoMode) DemoChip(Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            if (st.isParked && st.saveFailed) SaveFailureNotice(ctl::retrySave, Modifier.align(Alignment.BottomCenter).padding(24.dp))
            toast?.let { Toast(it, Modifier.align(Alignment.BottomCenter).padding(24.dp)) }
        }
    }
}

@Composable
fun SaveFailureNotice(retry: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalChama.current
    Column(modifier.widthIn(max = 680.dp).background(c.ailmentBg, RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("아직 저장하지 못했어요", style = Type.title, color = c.textPrimary)
        Text("최신 변경이 저장되지 않았어요. 자동으로 다시 시도할게요. 저장될 때까지 앱을 종료하지 마세요.", style = Type.body, color = c.textPrimary)
        Btn("저장 다시 시도", retry, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Main(st: com.carmagotchi.app.AppUiState, ctl: PetController, tab: Int, onTab: (Int) -> Unit, overlay: String?, setOverlay: (String?) -> Unit) {
    val pet = st.pet ?: return
    val still = LocalReduceMotion.current
    val toast = LocalToast.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Responsive { land ->
        AnimatedContent(
            overlay,
            transitionSpec = {
                if (still) EnterTransition.None togetherWith ExitTransition.None
                else (fadeIn(tween(300)) + slideInHorizontally(tween(300)) { 24 }) togetherWith fadeOut(tween(300))
            }, label = "push",
        ) { o ->
            when {
                o == "settings" -> SettingsScreen(st, ctl, { setOverlay("look") }) { setOverlay(null) }
                o == "look" -> LookScreen(st, ctl) { setOverlay(null) }
                o?.startsWith("share:") == true -> {
                    val m = when (val id = o.removePrefix("share:")) { "weekly" -> weeklyMemory(st); else -> st.memories.firstOrNull { it.id == id } }
                    if (m != null) ShareScreen(st, m) { setOverlay(null) }
                }
                else -> MainShell(pet, tab, onTab, { setOverlay("settings") }, land, quiet = if (st.settings.driveSounds || st.settings.parkedSounds) st.settings.quietToday else null, quietEnabled = !st.settings.guestMode && !pet.serious, onQuiet = { v -> ctl.updateSettings(st.settings.copy(quietToday = v)); toast(Lines.text(ctx, if (v) "DLG-SYS-QUIET-ON" else "DLG-SYS-QUIET-OFF", if (v) "오늘은 조용히 있을게." else "다시 같이 흥얼거릴게.")) }) {
                    Box(Modifier.weight(1f)) {
                        Crossfade(tab, animationSpec = tween(if (still) 0 else 150), label = "tab") { t ->
                            Column(Modifier.fillMaxSize()) {
                                when (t) {
                                    0 -> RoomScreen(st, ctl)
                                    1 -> DexScreen(st)
                                    else -> AlbumScreen(st) { setOverlay("share:${it.id}") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 시연 모드 켜짐 표시(글자 + 점) */
@Composable
fun DemoChip(modifier: Modifier = Modifier) {
    val c = LocalChama.current
    Row(modifier.background(c.hungerBg, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(c.hunger, androidx.compose.foundation.shape.CircleShape)); Spacer(Modifier.width(8.dp))
        Text("시연 모드", style = Type.caption, color = c.hunger)
    }
}

@Composable
fun Toast(text: String, modifier: Modifier = Modifier) {
    val sh = RoundedCornerShape(32.dp)
    Box(modifier.widthIn(max = 792.dp).shadow(8.dp, sh).background(Color(0xFF201C26), sh).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(text, style = Type.body, color = Color(0xFFFFF8F0), maxLines = 1)
    }
}

