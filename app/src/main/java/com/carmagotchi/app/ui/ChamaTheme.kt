@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.carmagotchi.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carmagotchi.app.R

/** 4.2 UI 팔레트. 토큰 이름은 DESIGN-GUIDE의 chama_* 와 같다. */
@Immutable
class ChamaColors(
    val dark: Boolean,
    val canvas: Color, val card: Color, val sunken: Color, val textPrimary: Color, val textSecondary: Color,
    val accent: Color, val accentPressed: Color, val onAccent: Color, val buttonSecondary: Color, val divider: Color,
    val hunger: Color, val hungerBg: Color, val ailment: Color, val ailmentBg: Color, val recover: Color, val recoverBg: Color,
    val scrim: Color,
) {
    /** 보조 버튼 눌림(가정): 바탕을 살짝 어둡게/밝게 */
    /** chama_heart(4.2절): 애정 하트 채움 */
    val heart get() = if (dark) Color(0xFFFF9A8C) else Color(0xFFC2453A)
    val secondaryPressed get() = if (dark) Color(0xFF3A3F4A) else Color(0xFFDDD1C2)
}

val LightColors = ChamaColors(
    false, Color(0xFFFFF8F0), Color(0xFFFFFFFF), Color(0xFFF4ECE2), Color(0xFF201C26), Color(0xFF5A5464),
    Color(0xFF23695E), Color(0xFF1A5249), Color(0xFFFFFFFF), Color(0xFFEDE4D8), Color(0xFFE3D9CC),
    Color(0xFF8A5300), Color(0xFFFFF0D9), Color(0xFFA3362B), Color(0xFFFDE8E5), Color(0xFF2F6B2F), Color(0xFFE5F4E3),
    Color(0x66000000),
)
val DarkColors = ChamaColors(
    true, Color(0xFF14161B), Color(0xFF1F2229), Color(0xFF0F1115), Color(0xFFF3F0EA), Color(0xFFB4AFBA),
    Color(0xFF6FD1BE), Color(0xFF57B8A5), Color(0xFF0E2420), Color(0xFF2E323B), Color(0xFF363A44),
    Color(0xFFF5BE62), Color(0xFF3A2C12), Color(0xFFFF9A8C), Color(0xFF3D1E1B), Color(0xFF8FD68A), Color(0xFF1E3320),
    Color(0x99000000),
)

val LocalChama = staticCompositionLocalOf { LightColors }
val LocalReduceMotion = staticCompositionLocalOf { false }

/** 5.1 글꼴: 시스템 글꼴이 Asta Sans인지 확인 전이라 원본 가변 글꼴을 그대로 넣었다(OFL, assets/licenses). */
private fun asta(w: Int) = Font(R.font.asta_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val AstaSans = FontFamily(asta(400), asta(600), asta(700), asta(800))

/** 5.2 글자 단계 */
@Immutable
class ChamaType(
    val moment: TextStyle, val headline: TextStyle, val title: TextStyle, val body: TextStyle, val label: TextStyle, val caption: TextStyle,
)

private fun ts(size: Int, line: Int, w: Int) = TextStyle(fontFamily = AstaSans, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(w))
val Type = ChamaType(ts(32, 48, 700).copy(lineBreak = LineBreak.Heading), ts(28, 42, 700), ts(24, 36, 700), ts(22, 33, 400).copy(lineBreak = LineBreak.Paragraph), ts(20, 30, 600), ts(18, 27, 400).copy(lineBreak = LineBreak.Paragraph))

/** 7.3 크기 토큰(가정, 한 곳에서 고친다) */
object Dim {
    val margin = 24.dp
    val gutter = 16.dp
    val nav = 72.dp
    val tabs = 64.dp
    val tabsBox = 56.dp
    val button = 64.dp
    val row = 72.dp
    val chipShow = 48.dp
    val touch = 64.dp
}

@Composable
fun ChamaTheme(dark: Boolean = isSystemInDarkTheme(), reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalChama provides c, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = lightColorScheme(primary = c.accent, onPrimary = c.onAccent, surface = c.card), content = content)
    }
}
