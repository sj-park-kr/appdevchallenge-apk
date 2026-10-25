package com.carmagotchi.app.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** DESIGN-GUIDE 10절. 앞 4개는 주행 중 SND-DRV-*(F14), 뒤 7개는 정차 중 SND-*(F15). 순서는 F14 보류 우선순위라 앞 4개는 옮기지 않는다. */
enum class DriveSound { DEPART, FRIEND, RAIN, CALM, HATCH, GREET, MUNCH, RECOVER, PET, VISITOR, ARRIVE, VROOM }

/** 소리를 시작한 쪽: 사용자가 눌러 시작한 모션은 TAP, 화면 등장·이벤트로 시작한 모션은 AUTO(F15 2번). */
enum class Origin { TAP, AUTO }

/** 모션 시작에서 atMs 뒤에 낼 소리(variant = MUNCH 몇 번째 입). */
data class SfxCue(val atMs: Int, val sound: DriveSound, val variant: Int = 0)

/** DESIGN-GUIDE 10.2 모양 그대로 합성한다(녹음 파일 없음). 음이 미끄러지는 사인파 + 약한 2배음, 같은 음 반복 없음. */
object DriveSounds {
    const val RATE = 22050
    /** startMs~endMs 구간, 주파수 점들을 고르게 이어 글라이드, 비브라토(Hz, ±편차), 어택·릴리스(ms). */
    private class Seg(
        val from: Int, val to: Int, val pts: List<Double>, val vibHz: Double = 0.0, val vibDev: Double = 0.0, val attack: Int = 15, val release: Int = 40, val harm: Double = 0.15,
        /** pts가 놓이는 구간 안 위치(0~1). 없으면 고르게 */ val knots: List<Double>? = null, /** 구간 시작 뒤 이 ms부터 비브라토 */ val vibFrom: Int = 0,
    )
    private class Shape(val totalMs: Int, val peakDb: Double, val segs: List<Seg>)

    private val SHAPES = mapOf(
        DriveSound.DEPART to Shape(480, -12.0, listOf(Seg(0, 180, listOf(523.0, 784.0)), Seg(240, 480, listOf(659.0, 1047.0), release = 120))),
        DriveSound.FRIEND to Shape(560, -12.0, listOf(
            Seg(0, 120, listOf(784.0, 1175.0)), Seg(160, 280, listOf(880.0, 1319.0)),
            Seg(320, 560, listOf(1047.0, 1047.0), vibHz = 6.0, vibDev = 30.0, release = 160),
        )),
        DriveSound.RAIN to Shape(520, -12.0, listOf(
            Seg(0, 80, listOf(1397.0, 988.0), release = 25), Seg(110, 190, listOf(1568.0, 1109.0), release = 25),
            Seg(230, 520, listOf(784.0, 1175.0), vibHz = 8.0, vibDev = 20.0, release = 120),
        )),
        DriveSound.CALM to Shape(700, -16.0, listOf(Seg(0, 700, listOf(440.0, 523.0, 494.0), attack = 80, release = 250, harm = 0.25))),
        // F15(10.3절). 이어지는 조각 사이는 어택·릴리스를 0(없음)으로 둔다. 위상이 이어져 딸깍이 없다
        DriveSound.HATCH to Shape(800, -12.0, listOf(
            Seg(0, 70, listOf(1175.0, 880.0)), Seg(130, 560, listOf(523.0, 1047.0), release = 0),
            Seg(560, 800, listOf(1047.0, 988.0), vibHz = 7.0, vibDev = 20.0, attack = 0, release = 160),
        )),
        DriveSound.GREET to Shape(360, -12.0, listOf(
            Seg(0, 120, listOf(587.0, 880.0)), Seg(160, 360, listOf(698.0, 1047.0), vibHz = 8.0, vibDev = 15.0, vibFrom = 120, release = 100),
        )),
        DriveSound.RECOVER to Shape(600, -12.0, listOf(
            Seg(0, 150, listOf(1047.0, 1397.0)), Seg(190, 330, listOf(1175.0, 1568.0), release = 0),
            Seg(330, 600, listOf(1397.0, 1568.0), vibHz = 9.0, vibDev = 25.0, attack = 0, release = 200),
        )),
        DriveSound.VISITOR to Shape(440, -14.0, listOf(
            Seg(0, 90, listOf(659.0, 880.0)), Seg(120, 210, listOf(740.0, 988.0)),
            Seg(240, 440, listOf(880.0, 1175.0), vibHz = 7.0, vibDev = 20.0, release = 120),
        )),
        DriveSound.ARRIVE to Shape(320, -20.0, listOf(Seg(0, 320, listOf(698.0, 932.0, 784.0), attack = 20, release = 120, knots = listOf(0.0, 0.375, 1.0)))),
        // F15 SND-VROOM(10.3절): 바퀴발로 출발하는 "부릉부릉". 2배음 없음
        DriveSound.VROOM to Shape(560, -14.0, listOf(
            Seg(0, 220, listOf(440.0, 523.0), vibHz = 18.0, vibDev = 25.0, harm = 0.0),
            Seg(260, 560, listOf(466.0, 587.0), vibHz = 18.0, vibDev = 30.0, release = 120, harm = 0.0),
        )),
    )

    /** 높이를 차례로 돌려 같은 소리가 반복되지 않게 한다: 쓰다듬기 3개, 한 입마다 4개. */
    private val PET_HZ = listOf(784.0, 880.0, 740.0)
    private val MUNCH_HZ = listOf(740.0, 660.0, 784.0, 698.0)

    private fun shape(s: DriveSound, variant: Int): Shape = when (s) {
        DriveSound.PET -> PET_HZ[variant % PET_HZ.size].let { f ->
            Shape(170, -14.0, listOf(Seg(0, 170, listOf(f, 0.9 * f, 1.2 * f), release = 60, knots = listOf(0.0, 60 / 170.0, 1.0))))
        }
        DriveSound.MUNCH -> MUNCH_HZ[variant % MUNCH_HZ.size].let { f ->
            Shape(90, -18.0, listOf(Seg(0, 90, listOf(f, 0.75 * f, 0.85 * f), harm = 0.25, knots = listOf(0.0, 40 / 90.0, 1.0))))
        }
        else -> SHAPES.getValue(s)
    }

    fun durationMs(s: DriveSound) = shape(s, 0).totalMs

    fun synth(s: DriveSound, variant: Int = 0): ShortArray {
        val sh = shape(s, variant)
        val out = ShortArray(sh.totalMs * RATE / 1000)
        var ph = 0.0
        for (g in sh.segs) {
            val amp = 10.0.pow(sh.peakDb / 20) * Short.MAX_VALUE / (1 + g.harm) // 2배음을 더해도 꼭대기가 목표를 넘지 않게
            val a = g.from * RATE / 1000
            val n = (g.to - g.from) * RATE / 1000
            val kn = g.knots
            for (i in 0 until n) {
                val x = i.toDouble() / n
                val k: Int; val frac: Double
                if (kn == null) { val seg = x * (g.pts.size - 1); k = min(seg.toInt(), g.pts.size - 2); frac = seg - k }
                else { k = kn.indexOfLast { it <= x }.coerceAtMost(kn.size - 2); frac = (x - kn[k]) / (kn[k + 1] - kn[k]) }
                val ms = i * 1000.0 / RATE
                var f = g.pts[k] + (g.pts[k + 1] - g.pts[k]) * frac
                if (g.vibHz > 0 && ms >= g.vibFrom) f += g.vibDev * sin(2 * PI * g.vibHz * i / RATE)
                ph += 2 * PI * f / RATE
                val left = (g.to - g.from) - ms
                val env = min(1.0, min(if (g.attack > 0) ms / g.attack else 1.0, if (g.release > 0) left / g.release else 1.0)).coerceAtLeast(0.0)
                out[a + i] = (amp * env * (sin(ph) + g.harm * sin(2 * ph))).toInt().toShort()
            }
        }
        return out
    }
}

/**
 * PRD F14 규칙으로 "지금 소리를 낼지"만 정하는 순수 클래스(오디오는 모른다). 샘플 시각(s.at)으로만 시간을 잰다.
 * shortcut: 주행당 횟수·마지막 소리 시각은 메모리에만 있다(체크포인트 복원 없음). 프로세스가 죽었다 살아나면 상한이 새로 센다.
 */
class DriveSoundPolicy(private val zone: ZoneId = ZoneId.systemDefault()) {
    private class Pending(val sound: DriveSound, val at: Instant)

    private var lastGear: Gear? = null
    private var lastParkAt: Instant? = null
    private var tripStart: Instant? = null
    private var departDone = true
    private var paxAtStart = false
    private var paxSeen = false
    private var count = 0
    private var lastAt: Instant? = null
    private var prevAt: Instant? = null
    private var lastQuiet: Instant? = null
    private var lastSampleAt: Instant? = null
    private var calmSec = 0.0
    private var wiperSince: Instant? = null
    private var rainDone = false
    private var pending: Pending? = null
    private var fired: Pending? = null // 방금 낸 소리와 원래 트리거 시각(재생 실패하면 보류로 되돌린다). 출발 소리는 null

    private fun sec(a: Instant, b: Instant) = Duration.between(a, b).toMillis() / 1000.0

    /** enabled: 설정 켜짐 + 손님·사고·심각한 아픔 아님. 돌려준 소리는 이미 1번으로 센다(재생 실패하면 cancelLast). */
    fun onSample(s: VehicleSample, events: List<Event>, enabled: Boolean): DriveSound? {
        val now = s.at
        val prev = lastGear
        lastGear = s.gear
        if (events.any { it.id == EventId.PASSENGER_ON } && s.gear == Gear.P) paxSeen = true
        if (s.gear == Gear.P) {
            if (prev != null && prev != Gear.P) lastParkAt = now
            lastSampleAt = null; wiperSince = null; pending = null
            return null
        }
        if (prev == Gear.P) {
            val merged = tripStart != null && lastParkAt?.let { sec(it, now) < Const.TRIP_MERGE_SEC } == true
            if (!merged) {
                tripStart = now; departDone = false; count = 0; lastAt = null; lastQuiet = null
                calmSec = 0.0; rainDone = false; pending = null
                paxAtStart = paxSeen || s.occupied?.any { it != Seat.DRIVER } == true
            }
            paxSeen = false
        }
        val start = tripStart
        val dt = lastSampleAt?.let { min(sec(it, now), 5.0) } ?: 0.0
        lastSampleAt = now
        if (s.gear == Gear.R) return null
        for (e in events) when (e.id) {
            EventId.HARD_BRAKE, EventId.SHARP_TURN -> { lastQuiet = now; calmSec = 0.0 }
            EventId.PASSENGER_ON -> offer(DriveSound.FRIEND, now)
            else -> {}
        }
        calmSec += dt
        if (calmSec >= SoundConst.CALM_EVERY_MIN * 60) { calmSec = 0.0; offer(DriveSound.CALM, now) }
        if (s.wiperOn == true) {
            val since = wiperSince ?: now.also { wiperSince = it }
            if (!rainDone && sec(since, now) >= Const.RAIN_MIN_SEC) { rainDone = true; offer(DriveSound.RAIN, now) }
        } else wiperSince = null
        if (!enabled) { pending = null; return null }
        if (start == null) return null
        pending?.let { if (sec(it.at, now) > SoundConst.PENDING_MAX_SEC) pending = null }
        if (!departDone) {
            if (sec(start, now) > SoundConst.START_WINDOW_SEC) departDone = true
            else if (canPlay(now, start)) {
                departDone = true
                val friend = paxAtStart || pending?.sound == DriveSound.FRIEND
                if (friend) pending = null
                return fire(if (friend) DriveSound.FRIEND else DriveSound.DEPART, now, null)
            }
        }
        pending?.let { if (canPlay(now, start)) { pending = null; return fire(it.sound, now, it) } }
        return null
    }

    /** 재생을 못 했다(포커스 거부, 통화·내비 안내 중): 횟수에 세지 않는다. 출발 소리는 버리고 나머지는 다시 보류한다(PRD F14 4번, 600초까지). */
    fun cancelLast() { if (count > 0) { count--; lastAt = prevAt; fired?.let { offer(it.sound, it.at) }; fired = null } }

    private fun offer(c: DriveSound, at: Instant) {
        val p = pending
        if (p == null || c.ordinal < p.sound.ordinal) pending = Pending(c, at) // FRIEND > RAIN > CALM
    }

    private fun canPlay(now: Instant, start: Instant): Boolean {
        val h = now.atZone(zone).hour
        return count < SoundConst.MAX_PER_TRIP &&
            sec(start, now) >= SoundConst.START_DELAY_SEC &&
            lastQuiet?.let { sec(it, now) >= SoundConst.AFTER_EVENT_QUIET_SEC } != false &&
            lastAt?.let { sec(it, now) >= SoundConst.MIN_GAP_SEC } != false &&
            !(h >= SoundConst.NIGHT_FROM_HOUR || h < SoundConst.NIGHT_TO_HOUR)
    }

    private fun fire(c: DriveSound, now: Instant, from: Pending?): DriveSound { count++; prevAt = lastAt; lastAt = now; fired = from; return c }
}

/**
 * PRD F15 3·4번: 정차 중 소리를 낼지(순수). 막는 이유를 돌려주고(null = 낸다), PET은 낸 시각을 기억한다. 통화·내비 안내·음소거는 플레이어가 본다.
 * state = 손님 모드·사고 모드·SERIOUS 이상 아픔.
 */
class ParkedSoundPolicy(private val zone: ZoneId = ZoneId.systemDefault()) {
    private var lastPetAt: Instant? = null
    private var lastVroomAt: Instant? = null

    fun block(s: DriveSound, origin: Origin, now: Instant, parked: Boolean, resumed: Boolean, sfxOn: Boolean, quiet: Boolean, state: Boolean): String? {
        val h = now.atZone(zone).hour
        val why = when {
            !parked -> "gear"
            !resumed -> "notResumed"
            !sfxOn -> "off"
            quiet -> "quiet"
            state -> "state"
            origin == Origin.AUTO && (h >= SoundConst.NIGHT_FROM_HOUR || h < SoundConst.NIGHT_TO_HOUR) -> "night"
            s == DriveSound.PET && lastPetAt?.let { Duration.between(it, now).toMillis() < SoundConst.SFX_PET_MIN_GAP_MS } == true -> "gap"
            s == DriveSound.VROOM && lastVroomAt?.let { Duration.between(it, now).toMillis() < SoundConst.VROOM_MIN_GAP_MS } == true -> "gap"
            else -> null
        }
        if (why == null && s == DriveSound.PET) lastPetAt = now
        if (why == null && s == DriveSound.VROOM) lastVroomAt = now
        return why
    }
}
