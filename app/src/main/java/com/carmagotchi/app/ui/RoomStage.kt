package com.carmagotchi.app.ui

import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.carmagotchi.app.AppUiState
import com.carmagotchi.app.ui.pet.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

private val KST_STAGE = ZoneId.of("Asia/Seoul")

/**
 * SC-ROOM 무대: 방 + 말풍선 + 펫 + 방 안 이동(PRD F8 10번, BUILD-GUIDE 9.11). 바닥 탭은 달려가기, 펫을 400ms 누르고 끌면 따라오기,
 * 400ms 안에 움직여 1초 문지르면 쓰다듬기(onRub). 자리는 이 composable 안에서만 기억해서 방을 다시 열거나 장면이 뜨면 집 자리다.
 */
@Composable
fun RoomStage(
    modifier: Modifier, state: AppUiState, spec: PetSpec, look: PetLook, bubbleText: String?, hint: String?, desc: String,
    roamAllowed: Boolean, onRub: () -> Unit, onVroom: () -> Unit, onRoam: (Float) -> Unit = {},
) {
    val c = LocalChama.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val reduce = LocalReduceMotion.current || state.reduceMotion
    val roam by rememberUpdatedState(roamAllowed)
    val rub by rememberUpdatedState(onRub)
    val vroom by rememberUpdatedState(onVroom)
    val roamXp by rememberUpdatedState(onRoam)
    val placed by rememberUpdatedState(state.placed)
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp))) {
        val ph = maxHeight
        var z by rememberSaveable { mutableFloatStateOf(ZOOM_DEFAULT) } // 사용자가 정하는 배율(ZOOM_MIN..ZOOM_MAX). 펫 크기·터치 변환·카메라가 모두 이 값을 쓴다
        var showMini by rememberSaveable { mutableStateOf(true) }
        val psDp = minOf(maxWidth * .75f, ph * .7f).value // 월드 dp 단위 펫 크기. 화면에는 z배로 그려진다
        val wDp = maxWidth.value / z; val hDp = ph.value / z // 보이는 영역(월드 dp)
        val worldW = maxWidth.value / WORLD_ZOOM * WORLD_W; val worldH = ph.value / WORLD_ZOOM * WORLD_H // 월드는 줌과 무관하게 고정. 카메라가 펫을 가운데에 두고 따라간다(월드 끝에서는 멈춤)
        val mv = remember(worldW, worldH, psDp) { PetMove(worldW / 2f, worldH * .62f) }
        val art = remember(worldW, worldH, psDp) { WorldArt(worldW, worldH, psDp) }
        mv.rwDp = max(stageSpec(spec.stage).wheel, .05f) * psDp
        val frame = remember { mutableLongStateOf(0L) }
        fun camX() = (mv.foot.value - wDp / 2f).coerceIn(0f, max(0f, worldW - wDp))
        fun camY() = (mv.footY.value - psDp / 2f - hDp / 2f).coerceIn(0f, max(0f, worldH - hDp))
        val fx = !reduce && state.env.animated(state.rain)
        LaunchedEffect(fx) { while (fx) withFrameMillis { frame.longValue = it } } // 환경 연출이 움직일 때만 프레임 구독
        LaunchedEffect(mv.puffs.isNotEmpty()) { while (mv.puffs.isNotEmpty()) withFrameMillis { frame.longValue = it; mv.puffs = mv.puffs.filter { p -> it - p.startMs < 400L } } }

        fun stopNow() {
            mv.job?.cancel(); mv.job = null; mv.mode = PetMove.Mode.IDLE; mv.blink = 0; mv.heading = Heading.FRONT
            scope.launch { mv.lean.animateTo(0f, tween(150)) }
        }
        // 가는 쪽을 본다: 위=뒷모습, 아래=정면, 좌우=옆. 깜빡이는 가로로 멀리(BLINK_FAR) 갈 때만. 움직임 줄이기에서는 깜빡임을 안 쓴다
        fun face(tx: Float, ty: Float) {
            val dx = tx - mv.foot.value; val dy = ty - mv.footY.value
            // 달리는 중에 옆으로 꺾는 경우: 직진(위·아래)이었거나 좌우가 바뀌면 남은 이동이 대각선이어도 깜빡이
            val turn = mv.mode == PetMove.Mode.DRIVE && abs(dx) > BLINK_TURN * wDp && (mv.heading != Heading.SIDE || (if (dx > 0) 1 else -1) != mv.facing)
            val far = abs(dx) > BLINK_FAR * wDp && abs(dx) > abs(dy)
            if (abs(dx) > 2f) mv.facing = if (dx > 0) 1 else -1
            if (abs(dx) > 2f || abs(dy) > 2f) mv.heading = if (!turn && abs(dy) > abs(dx)) (if (dy < 0) Heading.BACK else Heading.FRONT) else Heading.SIDE
            mv.blink = if ((far || turn) && !reduce) mv.facing else 0
        }
        // 퐁 구름은 뒤쪽 바퀴 뒤(펫이 보는 쪽의 반대)
        fun puff() { if (mv.tired && mv.puffs.isNotEmpty()) return; mv.puffs = mv.puffs + PetMove.Puff(mv.foot.value - mv.facing * .2f * psDp, SystemClock.uptimeMillis(), mv.footY.value - .03f * psDp) }
        fun driveTo(x: Float, y: Float) {
            val t = clampWorld(x, y, worldW, worldH, psDp)
            mv.job?.cancel()
            face(t.x, t.y)
            vroom()
            val screens = hypot(t.x - mv.foot.value, t.y - mv.footY.value) / (worldW / WORLD_W) // 화면 폭 단위 이동 거리. 도착했을 때만 XP
            if (reduce) { mv.job = scope.launch { mv.foot.snapTo(t.x); mv.footY.snapTo(t.y); roamXp(screens) }; return }
            // 월드가 넓어 최대 시간을 3초로 늘렸다(RoomMove.DRIVE_MAX_MS는 방 안 이동용 1.2초)
            val k = energySpeed(state.env.energyPct); mv.tired = k < 1f
            val ms = (hypot(t.x - mv.foot.value, t.y - mv.footY.value) / (RoomMove.DRIVE_SPEED_DP_PER_SEC * k) * 1000f).toInt().coerceIn(RoomMove.DRIVE_MIN_MS, (3000 / k).toInt())
            mv.mode = PetMove.Mode.DRIVE
            mv.job = scope.launch {
                launch { mv.lean.animateTo(-4f, tween(150)) }
                puff()
                if (ms > 600) launch { delay(if (mv.tired) 1200 else 600); if (mv.mode == PetMove.Mode.DRIVE) puff() }
                launch { mv.footY.animateTo(t.y, tween(ms, easing = FastOutSlowInEasing)) }
                mv.foot.animateTo(t.x, tween(ms, easing = FastOutSlowInEasing))
                roamXp(screens)
                mv.mode = PetMove.Mode.IDLE; mv.blink = 0; mv.heading = Heading.FRONT
                launch { mv.lean.animateTo(0f, tween(150)) }
                mv.settleBounce = false; mv.settle.snapTo(0f); mv.settle.animateTo(300f, tween(300, easing = LinearEasing))
            }
        }
        fun dragStart() {
            mv.job?.cancel(); vroom()
            if (!reduce) { mv.mode = PetMove.Mode.DRAG; scope.launch { mv.lean.animateTo(-3f, tween(150)) } }
        }
        fun dragTo(x: Float, y: Float) {
            val t = clampWorld(x, y, worldW, worldH, psDp)
            face(t.x, t.y)
            scope.launch { if (reduce) mv.foot.snapTo(t.x) else mv.foot.animateTo(t.x, spring(dampingRatio = .8f, stiffness = 400f)) }
            scope.launch { if (reduce) mv.footY.snapTo(t.y) else mv.footY.animateTo(t.y, spring(dampingRatio = .8f, stiffness = 400f)) }
        }
        fun dragEnd() {
            mv.mode = PetMove.Mode.IDLE; mv.blink = 0; mv.heading = Heading.FRONT
            if (reduce) return
            scope.launch { mv.lean.animateTo(0f, tween(150)) }
            scope.launch { mv.settleBounce = true; mv.settle.snapTo(0f); mv.settle.animateTo(300f, tween(300, easing = LinearEasing)) }
        }

        // 무대 전체를 Initial 패스에서 본다: 펫 상자 안은 펫 제스처, 그 밖은 탭한 월드 자리로 이동. 아래 PetCanvas의 "그쪽을 본다" 탭은 그대로 남는다
        val gestures = Modifier.pointerInput(wDp, hDp, psDp) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val p = down.position
                // 펫 상자 가운데 몸통(가로 .18~.82)만 펫이다. 상자 양옆은 이동 탭을 받는다
                val sc = density * z // 화면 px per 월드 dp
                val pet = Rect(Offset((mv.foot.value - camX() - psDp * .32f) * sc, (mv.footY.value - camY() - psDp) * sc), Size(psDp * .64f * sc, psDp * sc))
                if (pet.contains(p)) {
                    stopNow()
                    val ox = mv.foot.value - (p.x / sc + camX()); val oy = mv.footY.value - (p.y / sc + camY()) // 잡은 자리 차이를 유지해 튀지 않게
                    val t0 = SystemClock.uptimeMillis()
                    val s = mutableListOf(PtrSample(0, p.x / density, p.y / density))
                    var rubbed = false; var dragging = false
                    while (true) {
                        val g = classifyPetGesture(s, false)
                        val target = when { g == PetGesture.PENDING -> RoomMove.DRAG_HOLD_MS; g == PetGesture.RUB && !rubbed -> RoomMove.RUB_MS; else -> -1L }
                        val ev = if (target < 0) awaitPointerEvent(PointerEventPass.Initial)
                        else withTimeoutOrNull(max(1L, target - (SystemClock.uptimeMillis() - t0))) { awaitPointerEvent(PointerEventPass.Initial) }
                        if (ev == null) s += s.last().copy(tMs = target)
                        else {
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) { if (dragging || rubbed || g != PetGesture.TAP && g != PetGesture.PENDING) ch.consume(); break }
                            s += PtrSample(ch.uptimeMillis - down.uptimeMillis, ch.position.x / density, ch.position.y / density)
                            if (classifyPetGesture(s, false).let { it == PetGesture.RUB || it == PetGesture.DRAG }) ch.consume()
                        }
                        when (classifyPetGesture(s, false)) {
                            PetGesture.RUB -> if (!rubbed && rubCounts(s)) { rubbed = true; rub() }
                            PetGesture.DRAG -> {
                                if (!dragging && roam) { dragging = true; dragStart() }
                                if (dragging) dragTo(s.last().xDp / z + camX() + ox, s.last().yDp / z + camY() + oy)
                            }
                            else -> {}
                        }
                    }
                    if (dragging) dragEnd()
                } else if (roam) {
                    var moved = false
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (ev.changes.size > 1) moved = true // 두 손가락(핀치)은 이동 탭이 아니다
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) { if (!moved) driveTo(p.x / sc + camX(), p.y / sc + camY()); break }
                        if (hypot(ch.position.x - p.x, ch.position.y - p.y) > slop) moved = true
                    }
                }
            }
        }
        val night = state.now.atZone(KST_STAGE).hour !in 6..18
        val pinch = Modifier.pointerInput(Unit) { // 두 손가락 핀치로 배율 조절
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                do {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val k = if (ev.changes.size > 1) ev.calculateZoom() else 1f
                    if (k != 1f) { z = (z * k).coerceIn(ZOOM_MIN, ZOOM_MAX); ev.changes.forEach { it.consume() } }
                } while (ev.changes.any { it.pressed })
            }
        }
        Box(Modifier.fillMaxSize().then(pinch).then(gestures)) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                val cx = camX(); val cy = camY()
                withTransform({ scale(density * z, density * z, Offset.Zero); translate(-cx, -cy) }) {
                    drawWorld(art, if (c.dark || night || state.env.lowBeam) .4f else if (state.rain) .12f else 0f, state.env, state.rain, if (fx) frame.longValue.coerceAtLeast(1L) else 0L)
                    for (pf in mv.puffs) { // 퐁 구름(바닥 그림자 층): 반지름 .020 -> .040, 불투명도 .6 -> 0, 400ms
                        val k = ((frame.longValue - pf.startMs) / 400f).coerceIn(0f, 1f)
                        drawCircle(c.textSecondary, radius = (.020f + .020f * k) * psDp, center = Offset(pf.xDp, pf.yDp), alpha = (if (mv.tired) .3f else .6f) * (1f - k))
                    }
                }
                // 방 꾸미기 물건: 휴게소 주차장 앞 길가에 줄 세운다
                val side = psDp * z * density * .4f
                placed.take(9).forEachIndexed { i, id ->
                    val x = ((worldW / 2f + psDp * (.9f + .5f * (i % 5))) - cx) * density * z; val y = ((worldH / 2f + psDp * (1.6f + .5f * (i / 5))) - cy) * density * z
                    inset(x, y, size.width - x - side, size.height - y - side) { drawItem(id) }
                }
            }
            Column(Modifier.align(Alignment.TopCenter).padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bubbleText != null) SpeechBubble(bubbleText, Modifier.bubbleIn(bubbleText + (state.bubble?.nonce ?: 0)), large = false)
                if (hint != null) Text(hint, style = Type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = 24.dp))
            }
            PetCanvas(
                spec, if (mv.tired && mv.mode == PetMove.Mode.DRIVE) look.copy(eyes = Eye.HALF, mouth = Mouth.FLAT) else look, (psDp * z).dp,
                Modifier.align(Alignment.TopStart)
                    .graphicsLayer {
                        translationX = (mv.foot.value - camX() - psDp / 2f) * density * z; translationY = (mv.footY.value - camY() - psDp) * density * z
                        scaleX = if (mv.facing > 0) -1f else 1f
                    }
                    .semantics {
                        if (roam) customActions = listOf(
                            CustomAccessibilityAction("왼쪽으로 가기") { driveTo(mv.foot.value - wDp / 3f, mv.footY.value); true },
                            CustomAccessibilityAction("오른쪽으로 가기") { driveTo(mv.foot.value + wDp / 3f, mv.footY.value); true },
                            CustomAccessibilityAction("위로 가기") { driveTo(mv.foot.value, mv.footY.value - hDp / 3f); true },
                            CustomAccessibilityAction("아래로 가기") { driveTo(mv.foot.value, mv.footY.value + hDp / 3f); true },
                        )
                    },
                animate = state.isParked, motionKey = state.bubble?.nonce ?: look.motion, desc = desc, move = mv,
            )
        }
        // 오버레이는 제스처 상자 밖에 둬서 버튼·미니맵 탭이 월드 이동으로 새지 않는다
        Row(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            MapBtn("지도", if (showMini) "미니맵 끄기" else "미니맵 켜기") { showMini = !showMini }
            MapBtn("−", "축소") { z = (z / 1.25f).coerceIn(ZOOM_MIN, ZOOM_MAX) }
            Text("x" + "%.1f".format(z / ZOOM_DEFAULT), style = Type.caption, color = c.textPrimary, modifier = Modifier.background(c.canvas.copy(alpha = .8f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
            MapBtn("+", "확대") { z = (z * 1.25f).coerceIn(ZOOM_MIN, ZOOM_MAX) }
        }
        if (showMini) {
            val mw = minOf(120.dp, maxWidth * .35f); val mh = mw * (worldH / worldW)
            Canvas(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(mw, mh).clip(RoundedCornerShape(8.dp)).border(2.dp, c.textPrimary, RoundedCornerShape(8.dp))
                    .pointerInput(worldW, roam) { detectTapGestures { if (roam) driveTo(it.x / size.width * worldW, it.y / size.height * worldH) } } // 탭한 곳으로 이동
                    .clearAndSetSemantics { },
            ) {
                val k = size.width / worldW
                withTransform({ scale(k, k, Offset.Zero) }) { drawMini(art) }
                val cx = camX(); val cy = camY()
                drawRect(Color.White, Offset(cx * k, cy * k), Size(minOf(wDp, worldW) * k, minOf(hDp, worldH) * k), style = Stroke(3f))
                drawCircle(Color(0xFFE5484D), 4.dp.toPx(), Offset(mv.foot.value * k, (mv.footY.value - psDp / 2f) * k))
            }
        }
    }
}

@Composable
private fun MapBtn(label: String, desc: String, onClick: () -> Unit) {
    val c = LocalChama.current
    Box(
        Modifier.size(44.dp).background(c.canvas.copy(alpha = .8f), RoundedCornerShape(22.dp)).clickable(onClick = onClick).semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.label, color = c.textPrimary, maxLines = 1) }
}
