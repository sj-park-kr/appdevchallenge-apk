package com.carmagotchi.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.carmagotchi.app.AppUiState
import com.carmagotchi.app.Memory
import com.carmagotchi.app.domain.PetState
import com.carmagotchi.app.domain.Stage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.time.ZoneId

private val CARD_BG = Color(0xFFFFF8F0)
private val CARD_INK = Color(0xFF201C26)
private val CARD_SUB = Color(0xFF5A5464)

fun stageName(s: Stage) = when (s) { Stage.EGG -> "알"; Stage.BABY -> "아기"; Stage.CHILD -> "어린이"; Stage.ADULT -> "어른"; Stage.BUDDY -> "단짝" }

/** 8.2 QR 내용: 사람이 읽는 텍스트(호스팅 없음), 300바이트 이하. VIN·좌표·동승자 이름(토글 전)은 넣지 않는다. */
fun qrText(pet: PetState, m: Memory, withName: Boolean, locationOn: Boolean): String {
    val a = pet.appearance
    fun build(title: String) = listOfNotNull(
        "차마고찌 카드", "${pet.name} · ${stageName(pet.stage)}", title, m.at.atZone(ZoneId.of("Asia/Seoul")).toLocalDate().toString(),
        "모습 ${a.palette}-${a.pattern}-${a.eyes}-${a.quirk}", m.place.takeIf { locationOn }, m.with.takeIf { withName }?.let { "함께 $it" }, "#차마고찌 #PleosConnect",
    ).joinToString("\n")
    var title = m.title
    while (build(title).toByteArray().size > 300 && title.isNotEmpty()) title = title.dropLast(1)
    return build(title)
}

/** 모듈을 정수 px로 그려 흐림을 막는다(8.2). 흰 바탕 + 조용한 영역 4모듈. */
@Composable
fun QrView(text: String, size: Dp, modifier: Modifier = Modifier) {
    val bits = remember(text) {
        runCatching {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8"))
        }.getOrNull()
    }
    Canvas(modifier.size(size).semantics { contentDescription = "공유용 QR 코드, 휴대폰 카메라로 찍으세요" }) {
        drawRect(Color.White)
        val b = bits ?: return@Canvas
        val px = (size.toPx() / (b.width + 8)).toInt().coerceAtLeast(1)
        val ox = ((size.toPx() - px * b.width) / 2).toInt().toFloat(); val oy = ((size.toPx() - px * b.height) / 2).toInt().toFloat()
        for (y in 0 until b.height) for (x in 0 until b.width) if (b[x, y]) drawRect(Color.Black, Offset(ox + x * px, oy + y * px), Size(px.toFloat(), px.toFloat()))
    }
}

/** 항상 밝은 카드(테마와 무관). qr = null이면 QR 없이 미리보기. */
@Composable
fun QrShareCard(pet: PetState, m: Memory, state: AppUiState, withName: Boolean, qr: String?, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalChama provides LightColors) {
        Column(modifier.width(512.dp).height(624.dp).shadow(6.dp, RoundedCornerShape(28.dp), ambientColor = Color(0x14000000), spotColor = Color(0x14000000)).background(CARD_BG, RoundedCornerShape(28.dp)).border(1.dp, Color(0xFFE3D9CC), RoundedCornerShape(28.dp)).padding(24.dp)) {
            Box(Modifier.size(464.dp, 256.dp).background(Color(0xFFF4ECE2), RoundedCornerShape(20.dp)), Alignment.Center) { MemoryPicture(pet, m, 240.dp) }
            Spacer(Modifier.height(16.dp))
            Text(m.title, style = Type.headline, color = CARD_INK, maxLines = 1)
            val sub = listOfNotNull("함께한 지 ${pet.daysTogether}일", fmtDate(m.at, state.now), m.with.takeIf { withName }, m.place.takeIf { state.settings.locationOn }).joinToString(" · ")
            Text(sub, style = Type.body, color = CARD_SUB, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    PeekMark(72.dp); Spacer(Modifier.height(8.dp))
                    Text("차마고찌", style = Type.title, color = CARD_INK)
                }
                if (qr != null) Box(Modifier.background(Color.White, RoundedCornerShape(8.dp)).padding(16.dp)) { QrView(qr, 200.dp) }
            }
        }
    }
}

/** SC-SHARE(14.8) */
@Composable
fun ShareScreen(state: AppUiState, m: Memory, onBack: () -> Unit) {
    val c = LocalChama.current
    val pet = state.pet ?: return
    var withName by remember { mutableStateOf(false) }
    var tries by remember { mutableIntStateOf(0) }
    val qr = remember(m, withName, tries, state.settings.locationOn) { qrText(pet, m, withName, state.settings.locationOn) }
    val ok = remember(qr, tries) { runCatching { QRCodeWriter().encode(qr, BarcodeFormat.QR_CODE, 0, 0) }.isSuccess }
    Column(Modifier.fillMaxSize().background(c.canvas)) {
        PageNav("공유 카드", null, onBack = onBack)
        Responsive(Modifier.weight(1f).fillMaxWidth()) { land ->
            val toggle = @Composable { if (m.with != null) SwitchRow("이름 넣기 (${m.with})", withName, { withName = it }) }
            if (land) Row(Modifier.fillMaxSize().padding(Dim.margin), horizontalArrangement = Arrangement.spacedBy(Dim.margin)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) { QrShareCard(pet, m, state, withName, null) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (ok) {
                        Box(Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp)) { QrView(qr, 280.dp) }
                        Spacer(Modifier.height(24.dp))
                        Text("1. 휴대폰 카메라를 켜요", style = Type.body, color = c.textPrimary)
                        Text("2. QR을 비추면 카드를 저장할 수 있어요", style = Type.body, color = c.textPrimary)
                        Text("화면을 사진으로 찍어 가도 돼요", style = Type.caption, color = c.textSecondary)
                    } else FailNote { tries++ }
                    toggle()
                }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (ok) QrShareCard(pet, m, state, withName, qr) else FailNote { tries++ }
                toggle()
                Text("휴대폰 카메라로 QR을 찍으면 카드를 저장할 수 있어요", style = Type.caption, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = Dim.margin, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun FailNote(retry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("카드를 만들지 못했어요", style = Type.body, color = LocalChama.current.textPrimary)
        Btn("다시 시도", retry, Modifier.width(240.dp))
    }
}
