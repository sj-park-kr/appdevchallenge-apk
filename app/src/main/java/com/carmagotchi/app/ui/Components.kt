package com.carmagotchi.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.clip
import com.carmagotchi.app.ui.pet.spec
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.carmagotchi.app.Chip
import com.carmagotchi.app.ChipKind
import com.carmagotchi.app.Memory
import com.carmagotchi.app.domain.Content
import com.carmagotchi.app.domain.PetState
import com.carmagotchi.app.domain.PlaceRecord
import com.carmagotchi.app.ui.pet.PetCanvas
import com.carmagotchi.app.ui.pet.drawVisitor
import com.carmagotchi.app.ui.pet.petDesc
import com.carmagotchi.app.ui.pet.sceneLook
import com.carmagotchi.app.Scene
import com.carmagotchi.app.SceneKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 8.1 누름 효과: 눌리면 95%(100ms), 놓으면 1(150ms). 손가락이 밖으로 나가면 취소된다(clickable 기본 동작). 움직임 줄이기면 크기는 그대로. */
@Composable
fun Pressable(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, desc: String? = null,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val still = LocalReduceMotion.current
    val k by animateFloatAsState(if (pressed && !still) .95f else 1f, tween(if (pressed) 100 else 150), label = "press")
    Box(
        modifier.defaultMinSize(Dim.touch, Dim.touch).graphicsLayer { scaleX = k; scaleY = k }
            .clickable(src, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (desc != null) Modifier.semantics { contentDescription = desc } else Modifier),
        contentAlignment = Alignment.Center,
    ) { content(pressed && enabled) }
}

@Composable
fun Btn(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, icon: Ic? = null, loading: Boolean = false) {
    val c = LocalChama.current
    Pressable({ if (!loading) onClick() }, modifier.height(Dim.button), enabled) { pressed ->
        val bg = when {
            !enabled -> c.divider
            primary -> if (pressed) c.accentPressed else c.accent
            else -> if (pressed) c.secondaryPressed else c.buttonSecondary
        }
        val fg = if (!enabled) c.textSecondary else if (primary) c.onAccent else c.textPrimary
        Row(
            Modifier.fillMaxSize().background(bg, RoundedCornerShape(16.dp)).padding(horizontal = 24.dp),
            Arrangement.Center, Alignment.CenterVertically,
        ) {
            if (loading) DotLoader(fg) else {
                if (icon != null) { Icon(icon, fg, 32.dp); Spacer(Modifier.width(8.dp)) }
                Text(label, style = Type.label, color = fg, maxLines = 1)
            }
        }
    }
}

@Composable
fun IconBtn(ic: Ic, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalChama.current
    Pressable(onClick, modifier.size(Dim.touch), desc = desc) { p -> Icon(ic, if (p) c.accentPressed else c.textPrimary, 48.dp) }
}

@Composable
fun TextBtn(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalChama.current
    Pressable(onClick, modifier.height(Dim.touch)) { p -> Text(label, style = Type.label, color = if (p) c.accentPressed else c.accent, modifier = Modifier.padding(horizontal = 12.dp)) }
}

/** 8.2 DotLoader: 점 3개, 지름 10dp, 간격 8dp, 900ms 반복. 움직임 줄이기면 멈춘다. */
@Composable
fun DotLoader(color: Color = LocalChama.current.accent) {
    val still = LocalReduceMotion.current
    val t by rememberInfiniteTransition(label = "dots").animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Row(Modifier.semantics { contentDescription = "불러오는 중" }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until 3) {
            val ph = if (still) 1f else ((t + 1f - i / 3f) % 1f)
            val a = .3f + .7f * (1f - kotlin.math.abs(ph * 2f - 1f))
            Box(Modifier.size(10.dp).alpha(a).background(color, CircleShape))
        }
    }
}

/** 7.4 Page navigation: Main(앱 마크, 제목, 부제, 설정) / Depth(뒤로, 제목) */
@Composable
fun PageNav(title: String, sub: String?, onSettings: (() -> Unit)? = null, onBack: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val c = LocalChama.current
    Row(Modifier.fillMaxWidth().height(Dim.nav).padding(horizontal = Dim.margin), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) { IconBtn(Ic.BACK, "뒤로", onBack); Spacer(Modifier.width(8.dp)) }
        else { PeekMark(48.dp); Spacer(Modifier.width(16.dp)) }
        if (sub != null) Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, style = Type.title, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = Type.caption, color = c.textSecondary, maxLines = 1)
        } else Text(title, style = Type.title, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
        trailing?.invoke()
        if (onSettings != null) IconBtn(Ic.SETTINGS, "설정", onSettings)
    }
}

/** 8.1 Tabs Line: 높이 64dp, 선택은 굵은 글씨 + 4dp 밑줄 + 채운 아이콘(색만으로 구분하지 않는다). */
@Composable
fun LineTabs(labels: List<String>, icons: List<Ic>, cur: Int, onSelect: (Int) -> Unit, fitted: Boolean) {
    val c = LocalChama.current
    Row(Modifier.fillMaxWidth().height(Dim.tabs).padding(horizontal = Dim.margin).selectableGroup()) {
        labels.forEachIndexed { i, l ->
            val on = i == cur
            Pressable({ onSelect(i) }, (if (fitted) Modifier.weight(1f) else Modifier.padding(end = 24.dp)).fillMaxHeight(), desc = null) { _ ->
                Column(Modifier.fillMaxHeight().width(IntrinsicSize.Max).padding(horizontal = 16.dp).semantics { selected = on; role = Role.Tab }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icons[i], if (on) c.accent else c.textSecondary, 28.dp, filled = on); Spacer(Modifier.width(8.dp))
                        Text(l, style = Type.label.copy(fontWeight = FontWeight(if (on) 800 else 600)), color = if (on) c.accent else c.textSecondary, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().height(4.dp).background(if (on) c.accent else Color.Transparent))
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
}

/** 8.1 Tabs Box: 높이 56dp 분류 탭 */
@Composable
fun BoxTabs(labels: List<String>, cur: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalChama.current
    Row(modifier.height(Dim.tabsBox).background(c.sunken, RoundedCornerShape(16.dp)).padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, l ->
            val on = i == cur
            Pressable({ onSelect(i) }, Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 48.dp)) { _ ->
                Box(
                    Modifier.fillMaxSize().background(if (on) c.accent else Color.Transparent, RoundedCornerShape(12.dp)).semantics { selected = on; role = Role.Tab },
                    Alignment.Center,
                ) { Text(l, style = Type.label.copy(fontWeight = FontWeight(if (on) 800 else 600)), color = if (on) c.onAccent else c.textSecondary, maxLines = 1) }
            }
        }
    }
}

private fun Modifier.level1(shape: androidx.compose.ui.graphics.Shape) = shadow(6.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))

@Composable
fun Card(modifier: Modifier = Modifier, radius: Dp = 20.dp, content: @Composable BoxScope.() -> Unit) {
    val c = LocalChama.current
    val sh = RoundedCornerShape(radius)
    Box(modifier.level1(sh).background(c.card, sh).then(if (c.dark) Modifier.border(1.dp, c.divider, sh) else Modifier), content = content)
}

/**
 * 8.2 SpeechBubble. Large: 아이콘 + moment 32sp 최대 2줄. Small: body 22sp. 꼬리는 펫 쪽(down = 세로 1칸, left = 가로 2칸).
 * 12절 도착 문장은 liveRegion(Polite).
 */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier, icon: Ic? = null, iconNote: String? = null, large: Boolean = true, tailLeft: Boolean = false, maxWidth: Dp = 520.dp) {
    val c = LocalChama.current
    val sh = RoundedCornerShape(if (large) 28.dp else 22.dp)
    val card: @Composable () -> Unit = {
        Row(
            Modifier.widthIn(max = if (large) maxWidth else 400.dp)
                .shadow(8.dp, sh, ambientColor = Color(0x29000000), spotColor = Color(0x29000000))
                .background(c.card, sh).then(if (c.dark) Modifier.border(1.dp, c.divider, sh) else Modifier)
                .padding(horizontal = if (large) 24.dp else 20.dp, vertical = if (large) 20.dp else 14.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null && large) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, c.accent, 48.dp); if (iconNote != null) Text(iconNote, style = Type.caption, color = c.accent, maxLines = 1) }; Spacer(Modifier.width(16.dp)) }
            Text(text, style = if (large) Type.moment else Type.body, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    val tail: @Composable (Boolean) -> Unit = { left ->
        Canvas(Modifier.size(if (left) 16.dp else 20.dp, if (left) 20.dp else 16.dp)) {
            val p = Path().apply {
                if (left) { moveTo(size.width, 0f); lineTo(0f, size.height / 2); lineTo(size.width, size.height) }
                else { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height) }
                close()
            }
            drawPath(p, c.card)
        }
    }
    if (tailLeft) Row(modifier, verticalAlignment = Alignment.CenterVertically) { tail(true); card() }
    else Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { card(); Box(Modifier.offset(y = (-1).dp)) { tail(false) } }
}

fun chipIcon(k: ChipKind) = when (k) {
    ChipKind.HUNGER -> Ic.HUNGER; ChipKind.TPMS -> Ic.TIRE; ChipKind.WASHER -> Ic.WASHER; ChipKind.OIL -> Ic.OIL
    ChipKind.WARNING -> Ic.WARNING; ChipKind.RECOVERED -> Ic.RECOVERED; ChipKind.PASSENGER -> Ic.PASSENGER; ChipKind.DAYS -> Ic.DAYS
}

/** 8.2 StatusChip: 아이콘 + 글자를 항상 함께. 누를 수 있으면 64dp, 표시용은 48dp. */
@Composable
fun StatusChip(chip: Chip, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = LocalChama.current
    val (fg, bg) = when (chip.kind) {
        ChipKind.HUNGER -> c.hunger to c.hungerBg
        ChipKind.RECOVERED -> c.recover to c.recoverBg
        ChipKind.PASSENGER, ChipKind.DAYS -> c.textPrimary to c.sunken
        else -> c.ailment to c.ailmentBg
    }
    val body: @Composable (Boolean) -> Unit = { p ->
        Row(
            Modifier.height(if (onClick != null) 64.dp else Dim.chipShow).background(if (p) fg.copy(alpha = .18f).compositeOver(bg) else bg, RoundedCornerShape(32.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(chipIcon(chip.kind), fg, 28.dp); Spacer(Modifier.width(8.dp))
            Text(chip.label, style = Type.label, color = fg, maxLines = 1)
            if (onClick != null) { Spacer(Modifier.width(4.dp)); Icon(Ic.CHEVRON, fg, 24.dp) }
        }
    }
    if (onClick != null) Pressable(onClick, modifier, desc = "${chip.label}, 누르면 할 일을 볼 수 있어요") { body(it) }
    else Box(modifier.semantics(mergeDescendants = true) { }) { body(false) }
}


/** 8.1 System popup. 부모 Box 위에 겹쳐 그린다(별도 창을 만들지 않아 멀티 윈도우에서도 같은 크기 기준). */
@Composable
fun SystemPopup(title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, large: Boolean = true, buttons: @Composable (RowScope.() -> Unit)? = null, body: @Composable ColumnScope.() -> Unit) {
    val c = LocalChama.current
    val sh = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxSize().background(c.scrim).clickable(remember { MutableInteractionSource() }, null) { onDismiss() }.semantics { }, Alignment.Center) {
        Column(
            modifier.padding(Dim.margin).widthIn(max = if (large) 640.dp else 560.dp)
                .shadow(20.dp, sh, ambientColor = Color(0x3D000000), spotColor = Color(0x3D000000))
                .background(c.card, sh).then(if (c.dark) Modifier.border(1.dp, c.divider, sh) else Modifier)
                .clickable(remember { MutableInteractionSource() }, null) { }.padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = Type.title, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 2)
                IconBtn(Ic.CLOSE, "닫기", onDismiss)
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f, fill = false), content = body)
            if (buttons != null) { Spacer(Modifier.height(24.dp)); Row(horizontalArrangement = Arrangement.spacedBy(24.dp), content = buttons) }
        }
    }
}

/** 8.1 Switch 행: 높이 72dp, 행 전체가 터치 영역. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LocalChama.current
    Pressable({ onChange(!checked) }, modifier.fillMaxWidth().height(Dim.row), enabled) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = Dim.margin).semantics(mergeDescendants = true) { stateDescription = if (checked) "켜짐" else "꺼짐"; toggleableState = androidx.compose.ui.state.ToggleableState(checked) }, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.body, color = if (enabled) c.textPrimary else c.textSecondary, modifier = Modifier.weight(1f), maxLines = 2)
            Box(Modifier.size(64.dp, 36.dp).background(if (checked) c.accent else c.divider, CircleShape).padding(4.dp), if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
                Box(Modifier.size(28.dp).background(if (checked) c.onAccent else c.card, CircleShape))
            }
        }
    }
}

@Composable
fun NavRow(label: String, value: String? = null, onClick: () -> Unit, danger: Boolean = false, modifier: Modifier = Modifier) {
    val c = LocalChama.current
    Pressable(onClick, modifier.fillMaxWidth().height(Dim.row)) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = Dim.margin), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.body, color = if (danger) c.ailment else c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
            if (value != null) { Text(value, style = Type.body, color = c.textSecondary, maxLines = 1); Spacer(Modifier.width(8.dp)) }
            Icon(Ic.CHEVRON, c.textSecondary, 32.dp)
        }
    }
}

@Composable
fun GroupLabel(text: String) {
    Text(text, style = Type.label, color = LocalChama.current.textSecondary, modifier = Modifier.padding(horizontal = Dim.margin).padding(top = 16.dp, bottom = 4.dp).semantics { heading() })
}

/** 8.1 Text Field Medium: 높이 64dp. maxLen 넘는 입력은 멈춘다. invalid는 테두리 + 문구. */
@Composable
fun ChamaField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, maxLen: Int = 6, invalid: Boolean = false, desc: String = hint, onDone: (() -> Unit)? = null) {
    val c = LocalChama.current
    val sh = RoundedCornerShape(16.dp)
    BasicTextField(
        value, { if (it.length <= maxLen) onChange(it) }, modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = desc },
        singleLine = true, textStyle = Type.body.copy(color = c.textPrimary), cursorBrush = SolidColor(c.accent),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().background(c.card, sh).border(if (invalid) BorderStroke(3.dp, c.ailment) else BorderStroke(2.dp, c.divider), sh).padding(horizontal = 20.dp), Alignment.CenterStart) {
                if (value.isEmpty()) Text(hint, style = Type.body, color = c.textSecondary)
                inner()
            }
        },
    )
}

/** 8.1 Toggle button(칩): 추천 이름, 이름 칩, 꾸미기 물건. */
@Composable
fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, wide: Boolean = false) {
    val c = LocalChama.current
    Pressable(onClick, modifier.height(64.dp), desc = null) { p ->
        Box(
            (if (wide) Modifier.fillMaxSize() else Modifier.fillMaxHeight()).background(if (on) c.accent else if (p) c.secondaryPressed else c.buttonSecondary, RoundedCornerShape(32.dp)).padding(horizontal = 24.dp)
                .semantics { selected = on }, Alignment.Center,
        ) { Text(label, style = Type.label, color = if (on) c.onAccent else c.textPrimary, maxLines = 1) }
    }
}

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

/** 11.5 날짜: 같은 해 "M월 D일", 다른 해 "YYYY년 M월 D일" */
fun fmtDate(t: Instant, now: Instant): String {
    val d = t.atZone(KST).toLocalDate(); val n = now.atZone(KST).toLocalDate()
    return if (d.year == n.year) "${d.monthValue}월 ${d.dayOfMonth}일" else "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일"
}
fun fmtDate(d: LocalDate, now: Instant) = fmtDate(d.atStartOfDay(KST).toInstant(), now)

/** 8.2 VisitorCard: 세로 248×232, 가로 166×208. met / new / locked */
@Composable
fun VisitorCard(id: String, met: Boolean, isNew: Boolean, date: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val c = LocalChama.current
    val v = Content.VISITORS.first { it.id == id }
    val d = if (met) "${v.name}, ${date}에 만났어요${if (isNew) ", 새로 만남" else ""}" else "아직 못 만난 길동무, 힌트: ${v.hint}"
    Pressable(onClick, modifier.then(if (compact) Modifier.height(208.dp) else Modifier.height(232.dp)), desc = d) { p ->
        Column(
            Modifier.fillMaxSize().level1(RoundedCornerShape(20.dp)).background(if (p) c.sunken else c.card, RoundedCornerShape(20.dp))
                .border(if (selected) BorderStroke(3.dp, c.accent) else if (c.dark) BorderStroke(1.dp, c.divider) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(20.dp)).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(if (compact) 104.dp else 128.dp).background(c.sunken, RoundedCornerShape(16.dp)).padding(8.dp)) {
                Canvas(Modifier.fillMaxSize()) { drawVisitor(id, !met, c.sunken, c.textSecondary) }
            }
            Spacer(Modifier.height(8.dp))
            if (met) {
                Text(v.name, style = Type.label, color = c.textPrimary, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isNew) { Box(Modifier.size(8.dp).background(Color(0xFFF2A23A), CircleShape)); Spacer(Modifier.width(6.dp)); Text("새로 만남", style = Type.caption, color = c.hunger, maxLines = 1) }
                    else Text(date ?: "", style = Type.caption, color = c.textSecondary, maxLines = 1)
                }
            } else Text(v.hint, style = Type.caption, color = c.textSecondary, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 8.2 StampCard: 지름 112 도장, 장소 ID 기울기 −6~+6° */
@Composable
fun StampCard(p: PlaceRecord, isNew: Boolean, now: Instant, modifier: Modifier = Modifier, compact: Boolean = false) {
    val c = LocalChama.current
    val tilt = (p.regionKey.hashCode().mod(13) - 6).toFloat()
    val name = if (p.label.length > 10) p.label.take(9) + "…" else p.label
    Column(
        modifier.height(if (compact) 208.dp else 232.dp).level1(RoundedCornerShape(20.dp)).background(c.card, RoundedCornerShape(20.dp))
            .then(if (c.dark) Modifier.border(1.dp, c.divider, RoundedCornerShape(20.dp)) else Modifier).padding(12.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$name 도장, ${fmtDate(p.firstAt, now)}에 처음 갔어요" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(if (compact) 104.dp else 128.dp), Alignment.Center) {
            Canvas(Modifier.size(112.dp).graphicsLayer { rotationZ = tilt }) {
                drawCircle(c.accent, size.minDimension / 2 - 2.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
                drawCircle(c.accent, size.minDimension / 2 - 12.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            }
            Icon(com.carmagotchi.app.ui.placeIcon(p.kind), c.accent, 48.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(name, style = Type.label, color = c.textPrimary, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isNew) { Box(Modifier.size(8.dp).background(Color(0xFFF2A23A), CircleShape)); Spacer(Modifier.width(6.dp)); Text("새로 생김", style = Type.caption, color = c.hunger, maxLines = 1) }
            else Text(fmtDate(p.firstAt, now), style = Type.caption, color = c.textSecondary, maxLines = 1)
        }
    }
}

/** 장면 썸네일/큰 그림: 이벤트 반응(sceneLook)을 정지 프레임으로 그린다. */
@Composable
fun MemoryPicture(pet: PetState, m: Memory, size: Dp, animate: Boolean = false) {
    val spec = pet.spec()
    val look = sceneLook(spec, pet.moodAt(m.at), Scene(SceneKind.ARRIVE, m.event, "", visitor = m.visitor))
    PetCanvas(spec, look, size, animate = animate, desc = petDesc(spec, pet.moodAt(m.at), look, false))
}


/** 8.2 MemoryRow: 높이 112, 썸네일 96 */
@Composable
fun MemoryRow(pet: PetState, m: Memory, now: Instant, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalChama.current
    Pressable(onClick, modifier.fillMaxWidth().height(112.dp), desc = "${m.title}, ${fmtDate(m.at, now)}") { p ->
        Row(
            Modifier.fillMaxSize().background(if (p) c.sunken else if (selected) c.sunken else c.card, RoundedCornerShape(20.dp))
                .border(if (selected) BorderStroke(3.dp, c.accent) else BorderStroke(1.dp, c.divider), RoundedCornerShape(20.dp)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(96.dp).background(c.sunken, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))) { MemoryPicture(pet, m, 96.dp) }
            Spacer(Modifier.width(16.dp))
            Text(m.title, style = Type.label, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(12.dp))
            Text(fmtDate(m.at, now), style = Type.caption, color = c.textSecondary, maxLines = 1)
            Icon(Ic.CHEVRON, c.textSecondary, 32.dp)
        }
    }
}


/** 12절: 색이 아닌 글자로도 읽히도록 텍스트가 있는 빈 상태 */
@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.body, color = LocalChama.current.textSecondary, modifier = modifier.padding(Dim.margin), textAlign = TextAlign.Center)
}

