package com.carmagotchi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** PRD F4~F7, F12 수용 기준과 안전 가드 (도메인 수준) */
class EngineTest {
    private val clock = clockAt("2026-10-25T18:00:00")
    private val engine = StateEngine(clock)
    private fun pet(stage: Stage = Stage.BABY) = newPet(clock, stage)
    private fun confirm(s: PetState, t: TripSummary) = engine.confirmTrip(s, t)

    // ---- F4 ----
    @Test fun f4ac1_stageUpToBaby() {
        val s = pet(Stage.EGG).copy(daysTogether = 6, lastCountedDayKey = LocalDate.of(2026, 10, 24))
        val r = engine.startUp(s)
        assertEquals(Stage.BABY, r.state.stage)
        assertTrue(r.state.memory.any { it.kind == "STAGE" && it.ref == "BABY" })
        assertTrue(r.state.mood >= 85)
    }

    @Test fun f4ac2_tenTripsOneDay() {
        var s = pet()
        repeat(10) { s = confirm(s, trip(clock.now.minusSeconds(60L * (10 - it)))).state }
        assertEquals(1, s.daysTogether)
        assertEquals(1, s.affection) // DRIVE_DAY 하루 1번
    }

    @Test fun f4ac3_gapDoesNotReduceDays() {
        var s = confirm(pet(), trip(clock.now)).state
        clock.now = clock.now.plusSeconds(4 * 86400L)
        s = confirm(s, trip(clock.now)).state
        assertEquals(2, s.daysTogether)
    }

    @Test fun f4ac4_adultLocksTemperament() {
        val s = pet(Stage.CHILD).copy(daysTogether = 99, lastCountedDayKey = LocalDate.of(2026, 10, 24),
            traits = Traits(Temperament.CALM, Roamer.TOWN, Social.SOLO))
        val r = engine.startUp(s)
        assertEquals(Stage.ADULT, r.state.stage)
        assertEquals(Traits(Temperament.CALM, Roamer.TOWN, Social.SOLO), r.state.lockedLook)
        // 이후 갈래가 바뀌어도 겉모습은 그대로
        assertEquals(Temperament.CALM, r.state.copy(traits = Traits(Temperament.LIVELY)).lockedLook.temperament)
    }

    @Test fun f4ac5_birthdayThenBuddy() {
        val c = clockAt("2026-10-25T07:00:00")
        val e = StateEngine(c)
        val s = newPet(c, Stage.ADULT, LocalDate.of(2025, 10, 25)).copy(daysTogether = 120, lastCountedDayKey = LocalDate.of(2026, 10, 24))
        val r = e.startUp(s)
        assertTrue(EventId.BIRTHDAY in ids(r.events))
        assertEquals(Stage.BUDDY, r.state.stage)
        assertTrue("ITEM-10" in r.state.items)
    }

    @Test fun birthdayWhileStillChildDoesNotMakeBuddy() {
        val c = clockAt("2026-10-25T07:00:00")
        val s = newPet(c, Stage.CHILD, LocalDate.of(2025, 10, 25)).copy(daysTogether = 40, lastCountedDayKey = LocalDate.of(2026, 10, 24))
        assertEquals(Stage.CHILD, StateEngine(c).startUp(s).state.stage)
    }

    @Test fun doubleStageJumpMakesTwoAlbumPages() {
        val c = clockAt("2026-10-25T07:00:00")
        val s = newPet(c, Stage.CHILD, LocalDate.of(2025, 10, 1)).copy(daysTogether = 99, lastCountedDayKey = LocalDate.of(2026, 10, 24))
        val r = StateEngine(c).startUp(s)
        assertEquals(Stage.BUDDY, r.state.stage)
        assertEquals(1, ids(r.events).count { it == EventId.STAGE_UP })
        assertEquals(2, r.state.memory.count { it.kind == "STAGE" })
    }

    @Test fun lateBirthdayWithinSevenDays() {
        val c = clockAt("2026-10-28T07:00:00")
        val s = newPet(c, Stage.BABY, LocalDate.of(2025, 10, 25))
        val e = StateEngine(c).startUp(s).events.first { it.id == EventId.BIRTHDAY }
        assertEquals("LATE:1", e.detail)
    }

    @Test fun traitsUndecidedUnderTenTrips() {
        val w = (1..9).map { trip(clock.now) }
        assertEquals(Traits(), engine.recomputeTraits(w, Traits()))
    }

    // ---- F5 ----
    @Test fun f5ac2_refuelRewards() {
        val r = engine.apply(pet(), Event(EventId.REFUEL, clock.now))
        assertEquals(70, r.state.mood)
        assertEquals(1, r.state.affection)
        assertTrue("ITEM-03" in r.state.items)
    }

    @Test fun f5ac4_tpmsDoesNotHurt() {
        val s = pet()
        val r = engine.apply(s, Event(EventId.WARN_TPMS, clock.now, "TPMS"))
        assertEquals(listOf(AilmentType.TPMS), r.state.ailments.map { it.type })
        assertEquals(s.mood, r.state.mood)
        assertEquals(s.affection, r.state.affection)
        assertEquals(s.stage, r.state.stage)
    }

    @Test fun f5ac6_thirdClearGivesNoMore() {
        var s = pet()
        val gains = mutableListOf<Triple<Int, Int, Boolean>>()
        repeat(3) {
            s = engine.apply(s, Event(EventId.WARN_TPMS, clock.now, "TPMS")).state
            val m = s.mood
            val r = engine.apply(s, Event(EventId.WARN_CLEARED, clock.now, "TPMS"))
            s = r.state
            gains += Triple(s.mood - m, s.affection, "ITEM-04" in r.rewards)
        }
        assertEquals(Triple(10, 1, true), gains[0])
        assertEquals(1, gains[1].second)
        assertEquals(1, gains[2].second)
        assertFalse(gains[1].third || gains[2].third)
        assertEquals(0, gains[1].first) // 두 번째 회복부터 기분도 하루 1번
    }

    @Test fun f5_ailmentNoticeFrequency() {
        val c = clockAt("2026-10-25T08:00:00")
        val e = StateEngine(c)
        var s = e.apply(newPet(c), Event(EventId.WARN_WASHER, c.now, "WASHER")).state
        assertTrue(e.ailmentNotice(s, AilmentType.WASHER))
        s = e.markNotified(s, AilmentType.WASHER)
        assertFalse(e.ailmentNotice(s, AilmentType.WASHER)) // 오늘은 1번
        c.now = at("2026-10-26T08:00:00")
        assertTrue(e.ailmentNotice(s, AilmentType.WASHER))
        c.now = at("2026-11-04T08:00:00") // 8일째 이후
        s = s.copy(ailments = s.ailments.map { it.copy(lastNotifiedAt = at("2026-11-03T08:00:00")) })
        assertFalse(e.ailmentNotice(s, AilmentType.WASHER)) // 3일에 1번
        c.now = at("2026-11-06T08:00:00")
        assertTrue(e.ailmentNotice(s, AilmentType.WASHER))
    }

    @Test fun f5ac7_seriousAlwaysNotified() {
        var s = engine.apply(pet(), Event(EventId.WARN_OTHER, clock.now, "BRAKE_FLUID")).state
        s = engine.markNotified(s, AilmentType.BRAKE_FLUID)
        assertTrue(s.serious)
        assertTrue(engine.ailmentNotice(s, AilmentType.BRAKE_FLUID))
    }

    @Test fun hungerNoticeOncePerDay() {
        val (s1, a) = engine.noticeOnce(pet(), "HUNGER")
        val (_, b) = engine.noticeOnce(s1, "HUNGER")
        assertTrue(a)
        assertFalse(b)
    }

    // ---- F6 ----
    @Test fun f6ac1_snailComesOnFirstRain() {
        val r = confirm(pet(), trip(clock.now, rain = true))
        assertEquals("VIS-01", r.visitor)
        assertEquals(1, r.state.visitors.size)
        assertTrue("FIRST:VISITOR" in r.tags)
    }

    @Test fun f6ac2_dailyVisitCap() {
        var s = confirm(pet(), trip(clock.now, rain = true)).state
        s = confirm(s, trip(clock.now, passengers = 2)).state
        assertEquals(2, s.visitors.size)
        val r = confirm(s, trip(clock.now, rain = true))
        assertNull(r.visitor)
    }

    @Test fun f6ac3_sameInputSameResult() {
        val s = pet()
        val t = trip(clock.now, rain = true)
        assertEquals(confirm(s, t), confirm(s, t))
    }

    @Test fun f6_rarestFirstThenId() {
        val place = PlaceInfo("경포해수욕장", "강릉시", "강원", PlaceKind.SEA)
        val r = confirm(pet(), trip(at("2026-12-24T22:00:00").also { clock.now = it }, night = true, passengers = 2, place = place))
        assertEquals("VIS-12", r.visitor) // 밤 + 새 장소: 드묾 우선
    }

    @Test fun f6_revisitIsSeeded() {
        var s = confirm(pet(), trip(clock.now, rain = true)).state // 달팽이 첫 만남
        val results = (0 until 20).map {
            val c2 = TestClock(clock.now.plusSeconds(86400L * (it + 1)))
            val t = trip(c2.now, rain = true)
            StateEngine(c2).confirmTrip(s, t).visitor to StateEngine(c2).confirmTrip(s, t).visitor
        }
        assertTrue(results.all { it.first == it.second })
        assertTrue(results.any { it.first == "VIS-01" })
        assertTrue(results.any { it.first == null }) // 50% 확률
    }

    // ---- F7 ----
    @Test fun f7ac2_noNameRecordedUnlessChipPressed() {
        val r = confirm(pet(), trip(clock.now, passengers = 1))
        assertTrue(r.state.companions.isEmpty())
    }

    @Test fun f7ac3_tenthRide() {
        val s = pet().copy(companions = mapOf("서윤" to CompanionRecord("서윤", clock.now, 9)))
        val r = confirm(s, trip(clock.now, passengers = 1, name = "서윤"))
        assertEquals(10, r.state.companions["서윤"]!!.rideCount)
        assertTrue("MEM:PAX_COUNT_10" in r.tags)
        assertTrue(r.state.memory.any { it.kind == "COMPANION" && it.ref == "서윤:10" })
    }

    @Test fun f7_rearPassengerHardBrakeIsHeavier() {
        val front = confirm(pet(), trip(clock.now, hb = 1, passengers = 1)).state.mood
        val rear = confirm(pet(), trip(clock.now, hb = 1, passengers = 1, rear = true)).state.mood
        assertEquals(60 - 6 + 5, front)
        assertEquals(60 - 9 + 5, rear)
    }

    // ---- F12 ----
    @Test fun f12ac1_seollal() {
        val c = clockAt("2027-02-06T07:00:00")
        val s = newPet(c, Stage.BABY, LocalDate.of(2026, 1, 1))
        val r = StateEngine(c).startUp(s)
        assertEquals("SE-SEOLLAL", r.events.first { it.id == EventId.SEASON_EVENT }.detail)
        assertEquals(70, r.state.mood)
        assertTrue("ITEM-15" in r.state.items && "ITEM-16" in r.state.items)
        // 같은 날 두 번째 시동은 다시 주지 않는다
        val again = StateEngine(c).startUp(r.state)
        assertFalse(EventId.SEASON_EVENT in ids(again.events))
        assertEquals(70, again.state.mood)
    }

    @Test fun f12ac2_monsoonBootsOncePerYear() {
        val c = clockAt("2026-06-25T18:00:00")
        val e = StateEngine(c)
        val s = newPet(c).copy(ledger = setOf("EVER|FIRST:RAIN"))
        val r = e.confirmTrip(s, trip(c.now, rain = true))
        assertTrue("ITEM-12" in r.rewards)
        assertTrue("SE-MONSOON" in r.tags)
        val r2 = e.confirmTrip(r.state, trip(c.now, rain = true))
        assertFalse("ITEM-12" in r2.rewards)
    }

    @Test fun f12ac3_milestone() {
        val r = confirm(pet().copy(milestoneIndex = 0), trip(clock.now, km = 4.0, endOdo = 1003.0))
        val e = r.events.first { it.id == EventId.MILESTONE_KM }
        assertEquals("1000", e.detail)
        assertTrue("ITEM-09" in r.rewards)
        assertEquals(1, r.state.milestoneIndex)
        val again = confirm(r.state, trip(clock.now, km = 4.0, endOdo = 1010.0))
        assertFalse(EventId.MILESTONE_KM in ids(again.events))
    }

    @Test fun firstMeetings() {
        val place = PlaceInfo("경포해수욕장", "강릉시", "강원", PlaceKind.SEA)
        val r = confirm(pet(), trip(clock.now, place = place))
        assertTrue("ITEM-06" in r.rewards)
        assertTrue("FIRST:SEA" in r.tags)
        assertTrue("NEW_PROVINCE" in r.tags)
        assertEquals(1, r.state.snacks)
        val again = confirm(r.state, trip(clock.now, place = place))
        assertFalse(EventId.NEW_PLACE in ids(again.events))
        assertEquals(2, again.state.places["경포해수욕장"]!!.count)
    }

    @Test fun placeClassifier() {
        assertEquals(PlaceKind.SEA, Content.classifyPlace("경포해수욕장"))
        assertEquals(PlaceKind.SEA, Content.classifyPlace("속초항"))
        assertNull(Content.classifyPlace("항동"))
        assertEquals(PlaceKind.MOUNTAIN, Content.classifyPlace("관악산"))
        assertNull(Content.classifyPlace("부산 해운대구"))
        assertEquals(PlaceKind.REST_AREA, Content.classifyPlace("망향휴게소"))
    }

    @Test fun restStopRules() {
        val s = pet().copy(driveSinceRestMin = 70)
        val away = PlaceInfo("어딘가", "x", "y", PlaceKind.REGION, remainKm = 40.0)
        val r = engine.restStop(s, 6 * 60, away)
        assertEquals(listOf(EventId.REST_STOP), ids(r.events))
        assertEquals(0, r.state.driveSinceRestMin)
        assertTrue("SNACK:REST" in r.rewards)
        // 목적지 도착은 휴식이 아니다. 5분 미만도 아니다.
        assertTrue(engine.restStop(s, 6 * 60, away.copy(remainKm = 1.0)).events.isEmpty())
        assertTrue(engine.restStop(s, 4 * 60, away).events.isEmpty())
        // 경로·이름이 없으면 사후 판정에서만
        assertTrue(engine.restStop(s, 10 * 60, null).events.isEmpty())
        assertEquals(listOf(EventId.REST_STOP), ids(engine.restStop(s, 10 * 60, null, retro = true).events))
    }

    // ---- 안전 가드 ----
    @Test fun safety_noKmOrSpeedRewards() {
        val near = confirm(pet(), trip(clock.now, driveSec = 900, km = 2.0, endOdo = 2.0))
        val far = confirm(pet(), trip(clock.now, driveSec = 900, km = 200.0, endOdo = 200.0))
        assertEquals(near.rewards, far.rewards)
        assertEquals(near.state.affection, far.state.affection)
        assertEquals(near.state.mood, far.state.mood)
        assertEquals(near.state.daysTogether, far.state.daysTogether)
        assertEquals(near.state.ledger, far.state.ledger)
        assertEquals(ids(near.events), ids(far.events))
    }

    @Test fun safety_noStreakFields() {
        val names = (PetState::class.java.declaredFields.map { it.name } + TripSummary::class.java.declaredFields.map { it.name })
            .map { it.lowercase() }
        assertTrue(names.none { "streak" in it || "consecutive" in it || "score" in it || "rank" in it })
    }

    @Test fun safety_noDeathNoDecline() {
        var s = engine.apply(pet(Stage.CHILD), Event(EventId.WARN_OIL, clock.now, "OIL")).state
        s = engine.apply(s, Event(EventId.WARN_OTHER, clock.now, "BRAKE_FLUID")).state
        val before = s
        val c = TestClock(clock.now.plusSeconds(86400L * 60))
        val after = StateEngine(c).startUp(s).state
        assertEquals(Stage.CHILD, after.stage)
        assertEquals(before.affection, after.affection)
        assertTrue(after.moodAt(c.now) >= Const.MOOD_MIN)
        assertEquals(2, after.ailments.size) // 오래 앓아도 그대로, 사라지거나 약해지지 않는다
    }

    @Test fun safety_stageNeverDecreases() {
        val s = pet(Stage.ADULT).copy(daysTogether = 3)
        assertEquals(Stage.ADULT, engine.startUp(s).state.stage)
    }

    @Test fun safety_longAbsenceGivesNothing() {
        val s = pet(Stage.CHILD).copy(lastTripEndAt = clock.now.minusSeconds(86400L * 10), mood = 30, moodUpdatedAt = clock.now,
            affection = 12, snacks = 2, daysTogether = 40, lastCountedDayKey = LocalDate.of(2026, 10, 10))
        val r = engine.startUp(s)
        assertTrue(EventId.LONG_ABSENCE in ids(r.events))
        assertTrue(r.state.mood >= 75)
        assertEquals(12, r.state.affection)
        assertEquals(2 + Const.SNACK_DAILY, r.state.snacks) // 오랜만이라 주는 건 없고, 하루 첫 세션의 오늘의 간식만
        assertEquals(s.items, r.state.items)
        assertEquals(40, r.state.daysTogether)
        assertTrue(r.rewards.isEmpty())
    }

    @Test fun safety_seriousAilmentDoesNotChangeNumbers() {
        val s = pet()
        val r = engine.apply(s, Event(EventId.WARN_OTHER, clock.now, "BRAKE_FLUID")).state
        assertEquals(s.mood, r.mood)
        assertEquals(s.affection, r.affection)
    }

    @Test fun safety_crashQuietStopsRewardsFor24h() {
        var s = engine.apply(pet(), Event(EventId.WARN_OTHER, clock.now, "CRASH")).state
        val quiet = confirm(s, trip(clock.now, rain = true))
        assertTrue(quiet.rewards.isEmpty())
        assertNull(quiet.visitor)
        assertEquals(s.affection, quiet.state.affection)
        assertTrue(engine.apply(s, Event(EventId.REFUEL, clock.now)).rewards.isEmpty())
        val c = TestClock(clock.now.plusSeconds(25 * 3600L))
        s = quiet.state
        val after = StateEngine(c).confirmTrip(s, trip(c.now, rain = true))
        assertEquals("VIS-01", after.visitor)
    }

    @Test fun guestModeDropsTrip() {
        val s = pet().copy(guestModeUntil = clock.now.plusSeconds(3600))
        val r = confirm(s, trip(clock.now))
        assertTrue(r.state.trips.isEmpty())
        assertEquals(0, r.state.daysTogether)
    }

    @Test fun clockGoingBackwardsKeepsDayKey() {
        val s = confirm(pet(), trip(clock.now)).state // 10/25 하루 키
        val back = TestClock(clock.now.minusSeconds(3 * 86400L))
        val r = StateEngine(back).confirmTrip(s, trip(back.now))
        assertEquals(1, r.state.daysTogether)
        assertEquals(LocalDate.of(2026, 10, 25), r.state.lastCountedDayKey)
    }

    @Test fun affectionDailyCapAndOverflow() {
        var s = engine.apply(pet(), Event(EventId.REFUEL, clock.now)).state // CARE
        s = confirm(s, trip(clock.now)).state // DRIVE_DAY
        s = engine.bond(s, Bond.PET).state // BOND
        assertEquals(3, s.affection)
        s = engine.bond(s, Bond.THANKS).state
        assertEquals(3, s.affection)
        val over = engine.apply(pet().copy(affection = 100), Event(EventId.RECHARGE, clock.now))
        assertEquals(100, over.state.affection)
        assertNotEquals(0, over.state.affectionOverflow)
    }

    @Test fun bondCaps() {
        var s = pet().copy(snacks = 3)
        repeat(7) { s = engine.bond(s, Bond.PET).state }
        assertEquals(70, s.mood) // 기분 +10/일
        repeat(3) { s = engine.bond(s, Bond.SNACK).state }
        assertEquals(1, s.snacks) // 하루 2번
    }

    @Test fun deterministicHatchAppearance() {
        assertEquals(pet().appearance, pet().appearance)
        assertTrue(pet().appearance.palette in 0..7 && pet().appearance.pattern in 0..5)
    }
}
