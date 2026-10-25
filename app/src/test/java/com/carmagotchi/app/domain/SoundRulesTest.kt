package com.carmagotchi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.carmagotchi.app.ui.pet.Motion
import kotlin.math.abs

class SoundRulesTest {
    private val t0 = at("2026-10-25T10:00:00")
    private val p = DriveSoundPolicy(KST)
    private val heard = mutableListOf<Pair<Long, DriveSound>>()

    private fun feed(sec: Long, gear: Gear = Gear.D, events: List<EventId> = emptyList(), wiper: Boolean? = null, on: Boolean = true, base: java.time.Instant = t0) {
        val t = base.plusSeconds(sec)
        p.onSample(sample(t, gear, wiper = wiper), events.map { Event(it, t) }, on)?.let { heard += sec to it }
    }

    /** D로 출발해 sec초까지 1초마다 샘플을 낸다. */
    private fun drive(from: Long, to: Long, wiper: Boolean? = null) { for (s in from..to) feed(s, wiper = wiper) }

    @Test fun departSoundOnceAfterThirtySeconds() {
        feed(-5, Gear.P); drive(0, 120)
        assertEquals(listOf(30L to DriveSound.DEPART), heard)
    }

    @Test fun hardBrakeBlocksTenSecondsAndDelaysDepart() {
        feed(-5, Gear.P); drive(0, 24); feed(25, events = listOf(EventId.HARD_BRAKE)); drive(26, 120)
        assertEquals(listOf(35L to DriveSound.DEPART), heard)
    }

    @Test fun departSkippedWhenWindowPasses() {
        feed(-5, Gear.P); drive(0, 20); feed(21, events = listOf(EventId.SHARP_TURN)); feed(115, events = listOf(EventId.HARD_BRAKE)); drive(116, 140)
        assertTrue(heard.isEmpty())
    }

    @Test fun reverseGearIsSilent() {
        feed(-5, Gear.P); for (s in 0..40L) feed(s, Gear.R)
        assertTrue(heard.isEmpty())
        feed(41); assertEquals(DriveSound.DEPART, heard.single().second)
    }

    @Test fun resumedTripWithin120SecondsHasNoDepartSound() {
        feed(-5, Gear.P); drive(0, 40); heard.clear()
        feed(41, Gear.P); feed(100, Gear.P); drive(101, 200)
        assertTrue(heard.isEmpty())
    }

    @Test fun maxThreePerTripWithFiveMinuteGaps() {
        feed(-5, Gear.P)
        for (s in 0..7200L) feed(s, wiper = true, events = if (s == 100L) listOf(EventId.PASSENGER_ON) else emptyList())
        assertEquals(3, heard.size)
        assertTrue(heard.zipWithNext().all { (a, b) -> b.first - a.first >= 300 })
    }

    @Test fun passengerBeforeStartReplacesDepart() {
        feed(-30, Gear.P, events = listOf(EventId.PASSENGER_ON)); drive(0, 60)
        assertEquals(listOf(30L to DriveSound.FRIEND), heard)
    }

    @Test fun rainAfterSixtySecondsOfWipersIsHeldUntilGapPasses() {
        feed(-5, Gear.P); drive(0, 40, wiper = true); drive(41, 400, wiper = true)
        // 출발 30초, 비는 60초 때 보류되었다가 출발 소리로부터 300초 뒤
        assertEquals(listOf(30L to DriveSound.DEPART, 330L to DriveSound.RAIN), heard)
    }

    @Test fun calmEveryThirtyMinutes() {
        feed(-5, Gear.P)
        for (s in 0..3000L) feed(s)
        assertEquals(listOf(DriveSound.DEPART, DriveSound.CALM), heard.map { it.second })
        assertEquals(1800L, heard[1].first)
    }

    @Test fun disabledSettingGivesNoSound() {
        feed(-5, Gear.P); for (s in 0..300L) feed(s, wiper = true, on = false)
        assertTrue(heard.isEmpty())
    }

    @Test fun nightIsSilent() {
        val night = at("2026-10-25T23:00:00")
        feed(-5, Gear.P, base = night); for (s in 0..120L) feed(s, base = night)
        assertTrue(heard.isEmpty())
    }

    @Test fun nightBoundaries() {
        fun heardAt(iso: String, secs: Long = 60): Boolean { val q = DriveSoundPolicy(KST); val b = at(iso); var got = false
            q.onSample(sample(b.minusSeconds(5), Gear.P), emptyList(), true)
            for (s in 0..secs) if (q.onSample(sample(b.plusSeconds(s), Gear.D), emptyList(), true) != null) got = true
            return got }
        assertTrue(!heardAt("2026-10-25T23:00:00"))
        assertTrue(!heardAt("2026-10-25T05:59:00", 50)) // 05:59:50까지는 밤
        assertTrue(heardAt("2026-10-25T06:00:00"))
    }

    @Test fun cancelLastFreesTheSlot() {
        feed(-5, Gear.P); drive(0, 30, wiper = true); assertEquals(1, heard.size)
        p.cancelLast(); drive(31, 70, wiper = true)
        assertEquals(listOf(30L, 60L), heard.map { it.first }) // 취소하지 않았다면 비 소리는 330초에야 난다
    }

    @Test fun synthShapesAreShortQuietAndSmooth() {
        for (s in DriveSound.entries) {
            val pcm = DriveSounds.synth(s)
            assertTrue("$s length", pcm.size * 1000 / DriveSounds.RATE <= SoundConst.MAX_MS)
            assertTrue(abs(DriveSounds.durationMs(s) - pcm.size * 1000L / DriveSounds.RATE) <= 2)
            assertTrue("$s peak", pcm.maxOf { abs(it.toInt()) } < 0.4 * Short.MAX_VALUE)
            assertTrue("$s edges", abs(pcm.first().toInt()) < 300 && abs(pcm.last().toInt()) < 300)
        }
    }

    @Test fun blockedByCallIsHeldAndRetriedThenExpires() {
        feed(-5, Gear.P); drive(0, 330, wiper = true) // 출발 30초, 비는 60초에 간격 규칙에 막혀 보류됐다가 330초에 나감
        assertEquals(listOf(30L to DriveSound.DEPART, 330L to DriveSound.RAIN), heard)
        p.cancelLast(); heard.clear() // 통화 중이라 플레이어가 못 냈다
        feed(331, wiper = true)
        assertEquals(listOf(331L to DriveSound.RAIN), heard)
        p.cancelLast(); heard.clear(); feed(700, wiper = true) // 원래 트리거(60초)로부터 600초가 지났다
        assertTrue(heard.isEmpty())
    }

    @Test fun departBlockedByCallIsNotHeld() {
        feed(-5, Gear.P); drive(0, 30); assertEquals(1, heard.size)
        p.cancelLast(); heard.clear(); drive(31, 200)
        assertTrue(heard.isEmpty())
    }

    // ---- F15 정차 중 효과음 ----

    private val pp = ParkedSoundPolicy(KST)
    private fun block(s: DriveSound, o: Origin, now: java.time.Instant = t0, sfxOn: Boolean = true, quiet: Boolean = false, parked: Boolean = true, resumed: Boolean = true, state: Boolean = false) =
        pp.block(s, o, now, parked, resumed, sfxOn, quiet, state)

    @Test fun parkedSoundsFitLengthAndLoudness() {
        val db = mapOf(DriveSound.HATCH to -12.0, DriveSound.GREET to -12.0, DriveSound.MUNCH to -18.0, DriveSound.RECOVER to -12.0, DriveSound.PET to -14.0, DriveSound.VISITOR to -14.0, DriveSound.ARRIVE to -20.0)
        val ms = mapOf(DriveSound.HATCH to 800, DriveSound.GREET to 360, DriveSound.MUNCH to 90, DriveSound.RECOVER to 600, DriveSound.PET to 170, DriveSound.VISITOR to 440, DriveSound.ARRIVE to 320)
        for ((snd, d) in db) for (v in 0..3) {
            val pcm = DriveSounds.synth(snd, v)
            assertTrue("$snd length", abs(ms.getValue(snd) - pcm.size * 1000L / DriveSounds.RATE) <= 2)
            assertTrue("$snd length", pcm.size * 1000 / DriveSounds.RATE <= SoundConst.MAX_MS)
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("$snd#$v peak $peak", peak <= Math.pow(10.0, d / 20) * Short.MAX_VALUE + 1)
            assertTrue("$snd#$v loud enough", peak > 0.7 * Math.pow(10.0, d / 20) * Short.MAX_VALUE)
            assertTrue("$snd#$v edges", abs(pcm.first().toInt()) < 300 && abs(pcm.last().toInt()) < 300)
        }
        assertTrue(!DriveSounds.synth(DriveSound.PET, 0).contentEquals(DriveSounds.synth(DriveSound.PET, 1)))
        assertTrue(!DriveSounds.synth(DriveSound.MUNCH, 0).contentEquals(DriveSounds.synth(DriveSound.MUNCH, 1)))
    }

    @Test fun petThrottle300ms() {
        assertNull(block(DriveSound.PET, Origin.TAP))
        assertEquals("gap", block(DriveSound.PET, Origin.TAP, t0.plusMillis(299)))
        assertNull(block(DriveSound.PET, Origin.TAP, t0.plusMillis(300)))
        assertNull(block(DriveSound.MUNCH, Origin.TAP, t0.plusMillis(301))) // 간격은 SND-PET끼리만
    }

    @Test fun vroomThrottle1500ms() {
        assertNull(block(DriveSound.VROOM, Origin.TAP))
        assertEquals("gap", block(DriveSound.VROOM, Origin.TAP, t0.plusMillis(1499)))
        assertNull(block(DriveSound.VROOM, Origin.TAP, t0.plusMillis(1500)))
        assertNull(block(DriveSound.VROOM, Origin.TAP, at("2026-10-25T23:30:00"))) // 직접 소리라 밤에도
    }

    @Test fun nightSkipsAutoOnly() {
        for ((iso, auto) in listOf("2026-10-25T22:00:00" to false, "2026-10-25T05:59:00" to false, "2026-10-25T06:00:00" to true, "2026-10-25T21:59:00" to true)) {
            assertEquals(iso, if (auto) null else "night", block(DriveSound.ARRIVE, Origin.AUTO, at(iso)))
            assertNull(iso, ParkedSoundPolicy(KST).block(DriveSound.PET, Origin.TAP, at(iso), true, true, true, false, false)) // 직접 소리는 밤에도
        }
        assertEquals("night", block(DriveSound.VISITOR, Origin.AUTO, at("2026-10-25T23:00:00")))
    }

    @Test fun switchOffOrQuietGivesNoPlayRequest() {
        assertEquals("off", block(DriveSound.PET, Origin.TAP, sfxOn = false))
        assertEquals("off", block(DriveSound.HATCH, Origin.AUTO, sfxOn = false))
        assertEquals("quiet", block(DriveSound.MUNCH, Origin.TAP, quiet = true))
        assertEquals("quiet", block(DriveSound.ARRIVE, Origin.AUTO, quiet = true))
    }

    @Test fun onlyWhenParkedAndResumedAndCalmState() {
        assertEquals("gear", block(DriveSound.PET, Origin.TAP, parked = false))
        assertEquals("notResumed", block(DriveSound.PET, Origin.TAP, resumed = false))
        assertEquals("state", block(DriveSound.PET, Origin.TAP, state = true))
        assertNull(block(DriveSound.PET, Origin.TAP)) // 막힌 요청은 PET 시각을 남기지 않는다
    }

    @Test fun sceneCuesFollowMotion() {
        fun c(k: com.carmagotchi.app.SceneKind, v: Boolean, m: Motion?) = com.carmagotchi.app.ui.pet.sceneCues(k, v, m).map { it.sound }
        val A = com.carmagotchi.app.SceneKind.ARRIVE; val D = com.carmagotchi.app.SceneKind.DEPART
        assertEquals(listOf(DriveSound.VISITOR), c(A, true, Motion.EAT))
        assertEquals(listOf(DriveSound.ARRIVE), c(A, false, Motion.DOZE))
        assertEquals(List(4) { DriveSound.MUNCH }, c(A, false, Motion.EAT))
        assertEquals(listOf(DriveSound.RECOVER), c(D, false, Motion.RECOVER))
        assertEquals(listOf(DriveSound.GREET), c(D, false, Motion.WAVE))
        assertTrue(c(D, false, Motion.STRETCH).isEmpty())
    }
}
