package com.carmagotchi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class DialogueTest {
    private val clock = clockAt("2026-10-25T18:00:00")
    private val lines = catalog()
    private val sel = DialogueSelector(lines, clock)
    private val engine = StateEngine(clock)
    private fun pet(stage: Stage = Stage.BABY) = newPet(clock, stage)

    private fun pick(s: PetState, moment: String, key: String, tags: Set<String> = emptySet(), vars: Map<String, String> = emptyMap(), seed: String = "k") =
        sel.pick(s, moment, key, tags, vars + mapOf("펫이름" to "콩이"), seed)

    // 알 단계 대사 형식: **의성어.** (알이 왜 그러는지 한 줄 설명)
    private val EGG_LINE = Regex("""^\*\*[^*]+\*\* \(.+\)$""")

    @Test fun catalogParsed() {
        assertEquals(346, lines.size)
        assertEquals(lines.size, lines.map { it.id }.toSet().size)
        assertTrue(lines.all { it.moment in setOf("HATCH", "DEPART", "ARRIVE", "ROOM", "GLEO", "SYS") })
    }

    @Test fun noBannedWordsInCatalog() {
        val bad = lines.mapNotNull { l -> Dialogue.bannedIn(l.text)?.let { l.id to it } }
        assertTrue("금지어: $bad", bad.isEmpty())
    }

    @Test fun josaRules() {
        assertEquals("서윤아", Dialogue.fill("{동승자|아/야}", mapOf("동승자" to "서윤")))
        assertEquals("지아야", Dialogue.fill("{동승자|아/야}", mapOf("동승자" to "지아")))
        assertEquals("콩이가 별은", Dialogue.fill("{a|이/가} {b|은/는}", mapOf("a" to "콩이", "b" to "별")))
        assertEquals("별로 곰으로", Dialogue.fill("{a|으로/로} {b|으로/로}", mapOf("a" to "별", "b" to "곰")))
        assertEquals("서윤이랑 지아랑", Dialogue.fill("{a|이랑/랑} {b|이랑/랑}", mapOf("a" to "서윤", "b" to "지아")))
        assertEquals("10이 3이 2가", Dialogue.fill("{a|이/가} {b|이/가} {c|이/가}", mapOf("a" to "10", "b" to "3", "c" to "2")))
        assertNull(Dialogue.fill("{장소}, 처음이야!", mapOf("장소" to "아주아주긴장소이름입니다")))
        assertNull(Dialogue.fill("{동승자}", emptyMap()))
    }

    @Test fun priorityPicksHighestOnly() {
        val ev = setOf(EventId.HARD_BRAKE, EventId.RAIN, EventId.SHARP_TURN)
        assertEquals("HARD_BRAKE", sel.arriveKey(ev, emptySet()).key)
        assertEquals("WARN_OTHER", sel.arriveKey(ev, setOf("SERIOUS")).key)
        assertEquals("VISITOR", sel.arriveKey(ev, setOf("FIRST:VISITOR")).key)
        assertEquals("RAIN", sel.arriveKey(setOf(EventId.RAIN, EventId.NIGHT), emptySet()).key)
        assertEquals("DEFAULT", sel.arriveKey(emptySet(), emptySet()).key)
        assertEquals("STAGE_UP", sel.departKey(setOf(EventId.STAGE_UP, EventId.FIRST_OF_DAY), emptySet()).key)
        assertEquals("AGAIN", sel.departKey(emptySet(), emptySet()).key)
        assertEquals("LOW_BATTERY", sel.departKey(emptySet(), setOf("NOTICE_HUNGER", "PROFILE:EV")).key)
    }

    @Test fun f2ac3_firstRainSnail() {
        val t = trip(clock.now, rain = true)
        val step = engine.confirmTrip(pet(), t)
        val p = sel.arrive(step, t)!!
        assertEquals("비 오는 길에 달팽이를 만났어!", p.text)
    }

    @Test fun example42_repeatSkipsRecentAndPicksMemoryFreeLine() {
        // DIALOGUE 4.2: 비 + 급제동 + CALM + CHILD, -11은 3일 전에 보였다
        val s = pet(Stage.CHILD).copy(dialogueHistory = listOf(DialogueRecord("DLG-ARRIVE-HARD_BRAKE-11", clock.now.minus(Duration.ofDays(3)))))
        val p = pick(s, "ARRIVE", "HARD_BRAKE", setOf("RAIN", "CALM"))!!
        assertEquals("DLG-ARRIVE-HARD_BRAKE-09", p.line.id)
        assertEquals("깜짝했지만, 숨 한 번 후~", p.text)
    }

    @Test fun noRepeatWithin7DaysThenOldestFirst() {
        var s = pet()
        val picks = mutableListOf<String>()
        repeat(6) {
            val p = pick(s, "ARRIVE", "DEFAULT", setOf("LEN:SHORT"), seed = "trip$it")!!
            picks += p.line.id
            s = sel.shown(s, p)
            clock.now = clock.now.plusSeconds(3600)
        }
        assertEquals(5, picks.take(5).toSet().size) // BABY의 짧은 주행 후보 5개를 다 쓸 때까지 반복 없음
        assertEquals(picks[0], picks[5]) // 다 썼으면 가장 오래전 것
    }

    @Test fun threeDaysAgoLineIsNotChosenAgain() {
        val s0 = pet()
        val first = pick(s0, "ARRIVE", "DEFAULT", setOf("LEN:MID"))!!
        val s1 = s0.copy(dialogueHistory = listOf(DialogueRecord(first.line.id, clock.now.minus(Duration.ofDays(3)))))
        assertNotEquals(first.line.id, pick(s1, "ARRIVE", "DEFAULT", setOf("LEN:MID"))!!.line.id)
        val s2 = s0.copy(dialogueHistory = listOf(DialogueRecord(first.line.id, clock.now.minus(Duration.ofDays(8)))))
        assertNotNull(pick(s2, "ARRIVE", "DEFAULT", setOf("LEN:MID")))
    }

    @Test fun sameInputSameLine() {
        val a = pick(pet(), "ARRIVE", "DEFAULT", setOf("LEN:MID"), seed = "x")!!.line.id
        val b = pick(pet(), "ARRIVE", "DEFAULT", setOf("LEN:MID"), seed = "x")!!.line.id
        assertEquals(a, b)
    }

    @Test fun arriveLineNeverOver20AfterFill() {
        val places = listOf(null, PlaceInfo("경포해수욕장", "강릉시", "강원", PlaceKind.SEA), PlaceInfo("서울", "서울", "서울"))
        var checked = 0
        for (stage in Stage.values()) for (i in 0 until 60) {
            val t = trip(
                clock.now, driveSec = 300 + i * 120, hb = i % 3, st = i % 2, rain = i % 4 == 0, night = i % 5 == 0,
                passengers = i % 3, rear = i % 2 == 0, place = places[i % 3], name = if (i % 7 == 0) "서윤" else null,
                endOdo = if (i % 11 == 0) 1002.0 else 3.0,
            )
            val base = pet(stage).copy(companions = mapOf("서윤" to CompanionRecord("서윤", clock.now, if (i % 2 == 0) 9 else 49)))
            val step = engine.confirmTrip(base, t)
            val p = sel.arrive(step, t) ?: continue
            if (p.line.stage == "E") {
                assertTrue(p.text, EGG_LINE.matches(p.text) && p.text.substringAfter("(").length <= 24)
            } else assertTrue("${p.line.id} ${p.text}", p.text.length <= 20 || p.line.moment != "ARRIVE")
            checked++
        }
        assertTrue(checked > 200)
    }

    @Test fun eggIsWordless() {
        val egg = pet(Stage.EGG)
        val keys = lines.map { it.moment to it.key }.toSet()
        var checked = 0
        for ((m, k) in keys) {
            val p = pick(egg, m, k, vars = mapOf("동승자" to "친구", "분" to "10", "km" to "1,000", "일수" to "3", "장소" to "바다",
                "길동무" to "느릿이", "km.이번" to "1", "km.오늘" to "1", "km.함께" to "1", "km.남음" to "1")) ?: continue
            checked++
            if (p.line.stage == "E") {
                assertTrue(p.text, EGG_LINE.matches(p.text))
            } else assertEquals(p.line.id, "*", p.line.stage) // E가 없으면 전 단계 안전 문장·시스템 문장만
        }
        assertTrue(checked > 20)
        // 말하는 단계의 대사는 EGG에서 나오지 않는다
        assertTrue(lines.filter { it.stage == "B+" }.none { l -> l.stageOk(Stage.EGG) })
    }

    @Test fun crashLineAllowedForEgg() {
        val s = pet(Stage.EGG)
        val p = pick(s, "DEPART", "WARN_OTHER", setOf("CRASH"))!!
        assertEquals("괜찮아? 몸부터 살펴.", p.text)
    }

    @Test fun placeOver8CharsDropsLine() {
        val long = pick(pet(), "ARRIVE", "NEW_PLACE", vars = mapOf("장소" to "경포해수욕장앞바다쉼터"))
        assertTrue(long == null || long.line.id != "DLG-ARRIVE-NEW_PLACE-08")
        val ok = Dialogue.fill("{장소}, 처음이야!", mapOf("장소" to "강릉"))
        assertEquals("강릉, 처음이야!", ok)
    }

    @Test fun f7ac2_anonymousPassengerIsFriend() {
        val t = trip(clock.now, passengers = 1)
        val p = sel.arrive(engine.confirmTrip(pet(), t), t)!!
        assertTrue(p.text, "친구" in p.text)
    }

    @Test fun f7ac3_tenthRideLine() {
        // 단풍철(10/15~11/15)이면 계절 문장(우선순위 10)이 먼저라 8월로 옮긴다. 첫 동승은 이미 지났다(우선순위 3)
        val c = clockAt("2026-08-20T18:00:00")
        val s = newPet(c).copy(companions = mapOf("서윤" to CompanionRecord("서윤", c.now, 9)), ledger = setOf("EVER|FIRST:PASSENGER"))
        val t = trip(c.now, passengers = 1, name = "서윤")
        val p = DialogueSelector(lines, c).arrive(StateEngine(c).confirmTrip(s, t), t)!!
        assertEquals("서윤이랑 열 번째 드라이브!", p.text)
    }

    @Test fun f12ac3_milestoneLine() {
        val t = trip(clock.now, km = 4.0, endOdo = 1003.0)
        val p = sel.arrive(engine.confirmTrip(pet(), t), t)!!
        assertTrue(p.text in listOf("같이 1,000km 왔어. 고마워!", "우리 벌써 1,000km래!")) // 둘 중 씨앗이 고른다(trip 번호가 테스트 순서에 따라 달라짐)
    }

    @Test fun f3_stageUpAndLongAbsenceDepartLines() {
        val c = clockAt("2026-10-25T07:40:00")
        val e = StateEngine(c)
        val s = newPet(c, Stage.EGG).copy(daysTogether = 6, lastCountedDayKey = java.time.LocalDate.of(2026, 10, 24))
        val step = e.startUp(s)
        val p = DialogueSelector(lines, c).depart(step)!!
        assertEquals("STAGE_UP", p.line.key)
        assertTrue(p.text, p.text == "일주일 동안 고마워." || p.text == "껍질 벗었다! 나 어때?")

        val c2 = clockAt("2026-10-05T09:00:00")
        val s2 = newPet(c2, Stage.CHILD).copy(lastTripEndAt = at("2026-10-01T18:00:00"), daysTogether = 40)
        val p2 = DialogueSelector(lines, c2).depart(StateEngine(c2).startUp(s2))!!
        assertEquals("LONG_ABSENCE", p2.line.key)
        assertTrue(p2.text, p2.text.length <= 24)
    }

    @Test fun f3_morningGreeting() {
        val c = clockAt("2026-10-27T07:40:00")
        val s = newPet(c)
        val p = DialogueSelector(lines, c).depart(StateEngine(c).startUp(s))!!
        assertEquals("FIRST_OF_DAY", p.line.key)
        assertTrue(p.line.cond.contains("TIME:MORNING") || p.line.cond.isEmpty())
    }

    @Test fun hungerNoticeLineHasNoPressureWords() {
        val c = clockAt("2026-10-27T07:40:00")
        val step = StateEngine(c).startUp(newPet(c))
        val p = DialogueSelector(lines, c).depart(step, extraTags = setOf("NOTICE_HUNGER"))!!
        assertEquals("LOW_FUEL", p.line.key)
        assertNull(Dialogue.bannedIn(p.text))
    }
}
