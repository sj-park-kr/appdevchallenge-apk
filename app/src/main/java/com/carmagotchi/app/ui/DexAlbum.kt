@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.carmagotchi.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.carmagotchi.app.*
import com.carmagotchi.app.domain.*
import com.carmagotchi.app.ui.pet.*

/** 도감 상세용 한 줄 이야기(UI 문구). 속도·거리·보상 말을 쓰지 않는다. */
private val VISITOR_STORY = mapOf(
    "VIS-01" to "비 오는 날 우산 옆에 붙어 있었어.", "VIS-02" to "차 안이 북적이자 창가에서 짹짹거렸어.",
    "VIS-03" to "여름밤 길가에서 엉덩이 불을 깜빡였어.", "VIS-04" to "가을밤 길가에서 밤송이 등을 보여 줬어.",
    "VIS-05" to "창문 틈 바람을 타고 살랑살랑 왔어.", "VIS-06" to "꽃 핀 길에서 꽃가루 볼로 인사했어.",
    "VIS-07" to "바다 냄새를 따라 끼룩끼룩 왔어.", "VIS-08" to "숲길에서 도토리 모자를 자랑했어.",
    "VIS-09" to "쉬어 가는 차 옆에서 안경을 고쳐 썼어.", "VIS-10" to "뿌연 아침에 구름처럼 슬며시 나타났어.",
    "VIS-11" to "하얀 눈길에서 눈송이 왕관을 흔들었어.", "VIS-12" to "처음 가는 밤길에 반짝 하고 떨어졌어.",
)

private val PROVINCES = Content.REGION_SNACKS.keys.toList()

@Composable
private fun VisitorDetail(pet: PetState, id: String, now: java.time.Instant, picture: Dp) {
    val c = LocalChama.current
    val v = Content.VISITORS.first { it.id == id }
    val rec = pet.visitors[id]
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(picture).background(c.sunken, RoundedCornerShape(24.dp)).padding(12.dp)) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) { drawVisitor(id, rec == null, c.sunken, c.textSecondary) }
        }
        Spacer(Modifier.height(16.dp))
        if (rec != null) {
            Text(v.name, style = Type.title, color = c.textPrimary)
            Text("처음 만난 날 ${fmtDate(rec.firstMetAt, now)}", style = Type.body, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            Text(VISITOR_STORY.getValue(id), style = Type.body, color = c.textPrimary, textAlign = TextAlign.Center)
        } else {
            Text("아직 못 만난 길동무", style = Type.title, color = c.textPrimary)
            Text(v.hint, style = Type.body, color = c.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

/** SC-DEX(14.6) */
@Composable
fun ColumnScope.DexScreen(state: AppUiState) {
    val c = LocalChama.current
    val pet = state.pet ?: return
    var sub by rememberSaveable { mutableIntStateOf(0) }
    var sel by rememberSaveable { mutableStateOf<String?>(null) }
    val order = Content.VISITORS.map { it.id }.sortedWith(compareByDescending<String> { pet.visitors[it]?.lastMetAt }.thenBy { it })
    val met = pet.visitors.size
    val places = pet.places.values.sortedByDescending { it.firstAt }
    Responsive(Modifier.weight(1f).fillMaxWidth()) { land ->
        val shown = sel ?: order.firstOrNull { it in pet.visitors } ?: order.first()
        Row(Modifier.fillMaxSize().padding(horizontal = Dim.margin)) {
            Column(Modifier.weight(1f)) {
                Spacer(Modifier.height(16.dp))
                BoxTabs(listOf("길동무 $met/12", "장소 ${places.size}"), sub, { sub = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                if (sub == 0) {
                    if (met == 0) Text("길에서 만난 친구들이 여기 모여", style = Type.body, color = c.textSecondary, modifier = Modifier.padding(bottom = 12.dp))
                    LazyVerticalGrid(GridCells.Fixed(if (land) 3 else 2), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
                        items(order) { id ->
                            val r = pet.visitors[id]
                            VisitorCard(id, r != null, r != null && r.count == 1 && java.time.Duration.between(r.firstMetAt, state.now).toDays() < 1, r?.let { fmtDate(it.firstMetAt, state.now) }, land && id == shown, { sel = id }, compact = land)
                        }
                    }
                } else PlacesTab(pet, places, state, land, Modifier.weight(1f))
            }
            if (land && sub == 0) Column(Modifier.weight(1f).padding(start = Dim.margin).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp)); VisitorDetail(pet, shown, state.now, 240.dp)
            }
        }
        if (!land && sel != null) SystemPopup("길동무", { sel = null }) { VisitorDetail(pet, sel!!, state.now, 200.dp) }
    }
}

@Composable
private fun PlacesTab(pet: PetState, places: List<PlaceRecord>, state: AppUiState, land: Boolean, modifier: Modifier) {
    val c = LocalChama.current
    Column(modifier) {
        // 17개 시·도 칸(실루엣 지도 대신 이름 칸. 가 본 곳은 채우고 ✓ 글자를 붙인다)
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PROVINCES.forEach { p ->
                val on = places.any { it.provinceKey.startsWith(p) }
                Box(
                    Modifier.size(width = 72.dp, height = 48.dp).background(if (on) c.accent else c.sunken, RoundedCornerShape(12.dp)),
                    Alignment.Center,
                ) { Text(if (on) "$p ✓" else p, style = Type.caption, color = if (on) c.onAccent else c.textSecondary, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (places.isEmpty()) Text("새로운 곳에 가면 도장이 생겨", style = Type.body, color = c.textSecondary)
        else LazyVerticalGrid(GridCells.Fixed(if (land) 3 else 2), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
            items(places) { StampCard(it, java.time.Duration.between(it.firstAt, state.now).toDays() < 1, state.now, compact = land) }
        }
    }
}

/** 장면 한 장(큰 그림 + 문장 + 날짜 + 공유 버튼). 세로는 팝업, 가로는 오른쪽 칸. */
@Composable
private fun MemoryDetail(pet: PetState, m: Memory, now: java.time.Instant, picture: Dp, onShare: () -> Unit) {
    val c = LocalChama.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(picture).background(c.sunken, RoundedCornerShape(24.dp)), Alignment.Center) { MemoryPicture(pet, m, picture) }
        Spacer(Modifier.height(16.dp))
        Text(m.title, style = Type.headline, color = c.textPrimary, textAlign = TextAlign.Center)
        Text(listOfNotNull(fmtDate(m.at, now), m.with?.let { "${Dialogue.fill("{이름|이랑/랑}", mapOf("이름" to it)) ?: it}" }).joinToString(" · "), style = Type.body, color = c.textSecondary)
        Spacer(Modifier.height(16.dp))
        Btn("공유 카드 만들기", onShare, Modifier.fillMaxWidth(), icon = Ic.SHARE)
    }
}

/** 이번 주 카드: 장면 1~4개를 있는 만큼 크게 2×2. 장면이 없으면 조용한 한 주. */
@Composable
private fun WeeklyCard(pet: PetState, scenes: List<Memory>, onShare: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalChama.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Card(Modifier.fillMaxWidth(), radius = 28.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("이번 주 앨범", style = Type.title, color = c.textPrimary)
                Spacer(Modifier.height(16.dp))
                if (scenes.isEmpty()) {
                    val spec = pet.spec()
                    PetCanvas(spec, baseLook(spec, 70).copy(eyes = Eye.HAPPY), 200.dp, animate = false, desc = "${pet.name}, 포근한 한 주")
                    Text("조용하고 포근한 한 주", style = Type.body, color = c.textSecondary)
                } else {
                    val cols = if (scenes.size == 1) 1 else 2
                    val big = if (scenes.size == 1) 280.dp else if (scenes.size == 2) 200.dp else 160.dp
                    scenes.chunked(cols).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            row.forEach { m ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(big)) {
                                    Box(Modifier.size(big).background(c.sunken, RoundedCornerShape(20.dp))) { MemoryPicture(pet, m, big) }
                                    Text(m.title, style = Type.caption, color = c.textPrimary, maxLines = 2, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Btn("공유 카드 만들기", onShare, Modifier.fillMaxWidth(), icon = Ic.SHARE)
    }
}

fun weeklyMemory(state: AppUiState): Memory {
    val w = state.weekly.orEmpty()
    return Memory("weekly", "이번 주 앨범", state.now, w.firstOrNull()?.event ?: EventId.SMOOTH_TRIP, w.firstOrNull()?.visitor)
}

/** SC-ALBUM(14.7) */
@Composable
fun ColumnScope.AlbumScreen(state: AppUiState, onShare: (Memory) -> Unit) {
    val c = LocalChama.current
    val pet = state.pet ?: return
    var sub by rememberSaveable { mutableIntStateOf(0) }
    var sel by rememberSaveable { mutableStateOf<String?>(null) }
    val mem = state.memories
    Responsive(Modifier.weight(1f).fillMaxWidth()) { land ->
        val shown = mem.firstOrNull { it.id == sel } ?: mem.firstOrNull()
        Row(Modifier.fillMaxSize().padding(horizontal = Dim.margin)) {
            Column(Modifier.weight(1f)) {
                Spacer(Modifier.height(16.dp))
                BoxTabs(listOf("추억", "이번 주"), sub, { sub = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                if (sub == 0) {
                    if (mem.isEmpty()) Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                        val spec = pet.spec()
                        PetCanvas(spec, baseLook(spec, 70), 200.dp, animate = false, desc = "${pet.name}, 첫 추억을 기다려요")
                        Text("첫 추억을 기다리는 중이야", style = Type.body, color = c.textSecondary)
                    } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(mem) { m -> MemoryRow(pet, m, state.now, land && m.id == shown?.id, { sel = m.id }) }
                    }
                } else {
                    val w = state.weekly
                    if (w == null) Text("이번 주 앨범은 일요일 저녁에 나와요", style = Type.body, color = c.textSecondary)
                    else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { WeeklyCard(pet, w, { onShare(weeklyMemory(state)) }) }
                }
            }
            if (land && sub == 0 && shown != null) Column(Modifier.weight(1f).padding(start = Dim.margin, top = 16.dp).verticalScroll(rememberScrollState())) {
                MemoryDetail(pet, shown, state.now, 360.dp) { onShare(shown) }
            }
        }
        if (!land && sub == 0) mem.firstOrNull { it.id == sel }?.let { m -> SystemPopup("추억", { sel = null }) { MemoryDetail(pet, m, state.now, 280.dp) { onShare(m) } } }
    }
}
