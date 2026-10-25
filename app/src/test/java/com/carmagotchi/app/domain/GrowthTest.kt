package com.carmagotchi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** PRD F4-AC7~AC12(자람 바·하트·하트 가득 성장)와 오늘의 간식(F8 7.8) */
class GrowthTest {
    private val clock = clockAt("2026-10-25T18:00:00")
    private val engine = StateEngine(clock)
    private val yesterday = LocalDate.of(2026, 10, 24)
    private fun pet(stage: Stage = Stage.BABY) = newPet(clock, stage)
    private fun view(p: PetState) = Growth.of(p, clock.now)

    @Test fun babyDay20_barIsTwentyOfThirty() {
        val v = view(pet().copy(daysTogether = 20))
        assertEquals("어린이까지", v.nextLabel)
        assertEquals(20 to 30, v.done to v.total)
        assertEquals("GROW-LINE", v.lineId)
    }

    @Test fun nextDayLineAtThresholdMinusOne() {
        assertEquals("GROW-NEXT-CHILD", view(pet().copy(daysTogether = 29)).lineId)
        assertEquals("GROW-NEXT-BABY", view(pet(Stage.EGG).copy(daysTogether = 6)).lineId)
        assertEquals("GROW-NEXT-ADULT", view(pet(Stage.CHILD).copy(daysTogether = 99)).lineId)
    }

    @Test fun secondTripSameDayDoesNotMoveBar() {
        val p1 = engine.confirmTrip(pet().copy(daysTogether = 20), trip(clock.now.minusSeconds(3600))).state
        val p2 = engine.confirmTrip(p1, trip(clock.now, km = 2.0)).state
        assertEquals(21, view(p1).done)
        assertEquals(21, view(p2).done)
    }

    @Test fun adultAndBuddyUseCarBirthday() {
        val a = newPet(clock, Stage.ADULT, LocalDate.of(2026, 3, 1)).copy(daysTogether = 120)
        val va = view(a)
        assertEquals("단짝까지", va.nextLabel); assertEquals("GROW-BUDDY", va.lineId)
        assertEquals(365, va.total); assertEquals(238, va.done) // 3/1 -> 10/25
        val b = newPet(clock, Stage.BUDDY, LocalDate.of(2024, 11, 1))
        val vb = view(b) // 마지막 차 생일 2025-11-01 -> 다음 2026-11-01 (365일)
        assertEquals("다음 차 생일까지", vb.nextLabel); assertEquals("GROW-ANNIV", vb.lineId)
        assertEquals(365, vb.total); assertEquals(358, vb.done)
    }

    @Test fun heartsMapping() {
        assertEquals(1.0f, Growth.hearts(pet().copy(affection = 7)), 1e-4f) // 5 x 7/35
        assertEquals(5f * 8 / 35, Growth.hearts(pet().copy(affection = 8)), 1e-4f)
        assertEquals(5f, Growth.hearts(pet().copy(affection = 100, affectionOverflow = 50)), 0f)
        assertEquals(2.5f, Growth.hearts(pet(Stage.CHILD).copy(affection = 60, heartBase = 10)), 1e-4f) // v 50 / 100
        assertEquals(0f, Growth.hearts(pet().copy(affection = 3, heartBase = 5)), 0f)
    }

    @Test fun heartsNeverDropWithinStage() {
        var s = pet(Stage.BABY)
        var last = Growth.hearts(s)
        repeat(6) { d ->
            clock.now = clock.now.plusSeconds(86400L)
            s = engine.bond(s, Bond.PET).state
            s = engine.confirmTrip(s, trip(clock.now)).state
            s = engine.startUp(s).state
            val h = Growth.hearts(s)
            assertTrue("day $d", h >= last); last = h
        }
    }

    @Test fun fullHeartsGrowthIsSpecialAndResetsHearts() {
        val s = pet(Stage.BABY).copy(daysTogether = 29, lastCountedDayKey = yesterday, affection = 35, heartBase = 0)
        val r = engine.startUp(s)
        assertEquals(Stage.CHILD, r.state.stage)
        assertTrue("HEARTS_FULL" in r.tags)
        assertEquals(35, r.state.heartBase)
        assertEquals(0f, Growth.hearts(r.state), 0f)
        assertEquals("어른까지", view(r.state).nextLabel)
        assertEquals(29 to 100, view(r.state).let { it.done to it.total })
    }

    @Test fun notFullHeartsIsPlainGrowthButStillResets() {
        val s = pet(Stage.BABY).copy(daysTogether = 29, lastCountedDayKey = yesterday, affection = 20, affectionOverflow = 0, heartBase = 0)
        val r = engine.startUp(s)
        assertTrue("HEARTS_FULL" !in r.tags)
        assertEquals(20, r.state.heartBase)
    }

    @Test fun departLinePicksHeartsVariant() {
        val lines = catalog()
        val sel = DialogueSelector(lines, clock)
        val full = engine.startUp(pet(Stage.BABY).copy(daysTogether = 29, lastCountedDayKey = yesterday, affection = 35))
        assertEquals("DLG-DEPART-STAGE_UP-H2", sel.depart(full)!!.line.id)
        val plain = engine.startUp(pet(Stage.BABY).copy(daysTogether = 29, lastCountedDayKey = yesterday, affection = 3))
        assertTrue(sel.depart(plain)!!.line.id in listOf("DLG-DEPART-STAGE_UP-03", "DLG-DEPART-STAGE_UP-04"))
        val both = engine.startUp(newPet(clock, Stage.CHILD, LocalDate.of(2025, 10, 1)).copy(daysTogether = 99, lastCountedDayKey = yesterday, affection = 100))
        assertEquals(Stage.BUDDY, both.state.stage)
        assertEquals("DLG-DEPART-STAGE_UP-H4", sel.depart(both)!!.line.id)
    }

    // ---- 오늘의 간식 ----
    @Test fun firstSessionOfDayGivesTwo() {
        val r = engine.startUp(pet())
        assertEquals(2, r.state.snacks)
        assertEquals(2, r.state.snackPop)
        assertEquals(LocalDate.of(2026, 10, 25), r.state.lastSnackGrantDay)
    }

    @Test fun secondSessionSameDayGivesNothing() {
        val a = engine.startUp(pet()).state
        val b = engine.startUp(a.copy(snackPop = 0)).state
        assertEquals(2, b.snacks); assertEquals(0, b.snackPop)
    }

    @Test fun pouchCapsAtFive() {
        assertEquals(5, engine.startUp(pet().copy(snacks = 4)).state.snacks)
        val full = engine.startUp(pet().copy(snacks = 5)).state
        assertEquals(5, full.snacks); assertEquals(0, full.snackPop)
    }

    @Test fun guestModeStillGetsDailySnacks() {
        val r = engine.startUp(pet().copy(guestModeUntil = clock.now.plusSeconds(3600)))
        assertEquals(2, r.state.snacks)
    }

    @Test fun newDayGivesAgain() {
        val a = engine.startUp(pet()).state
        clock.now = clock.now.plusSeconds(86400L)
        val b = engine.startUp(a.copy(snacks = 0)).state
        assertEquals(2, b.snacks)
    }
}

/** 쓰다듬기 연타(F8): 3초 안 5번이면 귀찮아함, 기분 -5, 30초 쿨다운 */
class PetSpamTest {
    private fun run(vararg t: Long) = PetSpam().let { s -> t.map { s.hit(it) } }

    @Test fun fourInThreeSecondsDoesNotTrigger() {
        assertTrue(run(0, 700, 1400, 2100).all { it == PetSpam.Result.NONE })
    }

    @Test fun fiveInThreeSecondsTriggers() {
        assertEquals(PetSpam.Result.TRIGGER, run(0, 700, 1400, 2100, 3000).last())
        assertTrue(run(0, 1000, 2000, 3000, 4000).all { it == PetSpam.Result.NONE }) // 창 밖으로 밀려난다
    }

    @Test fun cooldownGivesNoSecondTriggerThenAllowsAgain() {
        val s = PetSpam()
        listOf(0L, 100, 200, 300).forEach { s.hit(it) }
        assertEquals(PetSpam.Result.TRIGGER, s.hit(400))
        assertEquals(PetSpam.Result.COOLDOWN, s.hit(500))
        assertEquals(PetSpam.Result.COOLDOWN, s.hit(30_399))
        repeat(4) { assertEquals(PetSpam.Result.NONE, s.hit(30_400L + it)) }
        assertEquals(PetSpam.Result.TRIGGER, s.hit(30_500))
    }

    @Test fun annoyLowersMoodByFiveOnlyAndRecovers() {
        val clock = clockAt("2026-10-25T18:00:00")
        val e = StateEngine(clock)
        val p = newPet(clock).copy(mood = 60, moodUpdatedAt = clock.now, affection = 10, snacks = 1)
        val a = e.annoy(p)
        assertEquals(55, a.mood)
        assertEquals(10, a.affection); assertEquals(1, a.snacks); assertEquals(p.ledger, a.ledger)
        clock.now = clock.now.plusSeconds(6 * 3600L)
        assertTrue(a.moodAt(clock.now) in 56..60) // 기본값 쪽으로 되돌아온다
    }
}

/** XP 곡선: 쓰다듬기 +50, 단계 오름, 강제 레벨업 순환 */
class XpTest {
    private val clock = clockAt("2026-10-25T18:00:00")
    private val engine = StateEngine(clock)

    @Test fun fiftyPetsHatchToBaby() {
        var s = newPet(clock, Stage.EGG)
        repeat(49) { s = engine.bond(s, Bond.PET).state }
        assertEquals(2450, s.xp); assertEquals(Stage.EGG, s.stage)
        s = engine.bond(s, Bond.PET).state
        assertEquals(Stage.BABY, s.stage)
    }

    @Test fun roamXpScalesCapsAndIgnoresShort() {
        val s = newPet(clock, Stage.EGG)
        assertEquals(0, engine.roam(s, .04f).state.xp)
        assertEquals(60, engine.roam(s, 1.5f).state.xp)
        assertEquals(Const.XP_ROAM_MAX, engine.roam(s, 9f).state.xp)
    }

    @Test fun tickAndDriveXp() {
        val s = newPet(clock, Stage.EGG)
        assertEquals(1, engine.tick(s, false).state.xp)
        assertEquals(2, engine.tick(s, true).state.xp)
    }

    @Test fun forceStageCyclesAndSurvivesStartUp() {
        var s = newPet(clock, Stage.EGG)
        val seen = mutableListOf<Stage>()
        repeat(5) { s = engine.forceStage(s).state; seen += s.stage; s = engine.startUp(s).state; assertEquals(seen.last(), s.stage) }
        assertEquals(listOf(Stage.BABY, Stage.CHILD, Stage.ADULT, Stage.BUDDY, Stage.EGG), seen)
    }
}
