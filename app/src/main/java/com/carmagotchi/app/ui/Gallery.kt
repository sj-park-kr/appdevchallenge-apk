@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.carmagotchi.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.carmagotchi.app.Scene
import com.carmagotchi.app.SceneKind
import com.carmagotchi.app.domain.*
import com.carmagotchi.app.ui.pet.*

private val PAGES = listOf("알", "아기", "어린이", "어른", "단짝", "팔레트×무늬", "갈래", "눈·입", "모습 꾸미기", "길동무·물건")
private val EXPRESSIONS = EventId.entries.filter { it != EventId.TRIP_START }

@Composable
private fun Cell(label: String, size: Dp, content: @Composable () -> Unit) {
    val c = LocalChama.current
    Column(Modifier.width(size + 8.dp).background(c.card, RoundedCornerShape(12.dp)).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Text(label, style = Type.caption, color = c.textSecondary, maxLines = 1)
    }
}

@Composable
private fun Pet(spec: PetSpec, look: PetLook, size: Dp) = PetCanvas(spec, look, size, animate = false, desc = null)

/** 디버그 전용 QA 갤러리(15절 "디버그 격자"): 단계 × 표정, 8 × 6 색·무늬, 갈래, 눈·입, 길동무·물건. 정지 프레임만 그린다. */
@Composable
fun Gallery(startPage: Int = 0) {
    val c = LocalChama.current
    var page by rememberSaveable { mutableIntStateOf(startPage) }
    Column(Modifier.fillMaxSize().background(c.canvas).padding(Dim.margin)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PAGES.forEachIndexed { i, l -> ToggleChip(l, i == page, { page = i }, Modifier.height(48.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (page) {
                in 0..4 -> {
                    val stage = Stage.entries[page]
                    val spec = PetSpec(stage, 0, 0, 0, Samples.CALM_TOWN_FRIEND, "콩이", 30)
                    EXPRESSIONS.forEach { e -> Cell(e.name, 150.dp) { Pet(spec, sceneLook(spec, 70, Scene(SceneKind.ARRIVE, e, "", visitor = if (e == EventId.NEW_PLACE) "VIS-07" else null, hardBrakes = 2, cues = emptySet())), 150.dp) } }
                    MoodBand.entries.forEach { b -> Cell("기분 ${b.name}", 150.dp) { Pet(spec, baseLook(spec, when (b) { MoodBand.LOW -> 20; MoodBand.OK -> 50; MoodBand.GOOD -> 70; MoodBand.GREAT -> 90 }), 150.dp) } }
                }
                5 -> for (p in 0 until 8) for (q in 0 until 6) {
                    val spec = PetSpec(Stage.ADULT, p, q, q % 4, Traits(), "", 0)
                    Cell("$p-$q ${PALETTE_NAMES[p]}", 128.dp) { Pet(spec, baseLook(spec, 70), 128.dp) }
                }
                6 -> for (t in Temperament.entries) for (r in Roamer.entries) for (s in Social.entries) {
                    val spec = PetSpec(Stage.ADULT, 0, 0, 0, Traits(t, r, s), "", 0)
                    Cell("${t.name.take(2)}·${r.name.take(2)}·${s.name.take(2)}", 136.dp) { Pet(spec, baseLook(spec, 70), 136.dp) }
                }
                7 -> {
                    val spec = PetSpec(Stage.ADULT, 0, 0, 0, Traits(), "", 0)
                    for (e in Eye.entries) for (m in Mouth.entries) Cell("${e.name}/${m.name}", 104.dp) { Pet(spec, PetLook(eyes = e, mouth = m), 104.dp) }
                }
                8 -> for (b in BodyShape.entries) for (len in 0..4) {
                    val n = b.ordinal * 5 + len
                    val cl = CarLook(b, len, n % 12, n % 6, n % 3, Rim.entries[n % 4], Mirror.entries[n % 4], Antenna.entries[n % 3],
                        EyeShape.entries[n % 4], CheekColor.entries[n % 4], n % 3, n % 3)
                    val spec = PetSpec(Stage.ADULT, 0, 0, 0, Traits(), "", 0, cl)
                    Cell("${b.label} $len", 104.dp) { Pet(spec, baseLook(spec, 70), 104.dp) }
                }
                else -> {
                    Content.VISITORS.forEach { v -> Cell(v.name, 112.dp) { Canvas(Modifier.size(112.dp).padding(8.dp)) { drawVisitor(v.id, false, c.sunken, c.textSecondary) } } }
                    Content.VISITORS.take(4).forEach { v -> Cell("잠금", 112.dp) { Canvas(Modifier.size(112.dp).padding(8.dp)) { drawVisitor(v.id, true, c.sunken, c.textSecondary) } } }
                    Content.ITEMS.forEach { i -> Cell(i.name, 112.dp) { Canvas(Modifier.size(112.dp).padding(8.dp)) { drawItem(i.id) } } }
                }
            }
        }
    }
}
