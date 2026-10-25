package com.carmagotchi.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import com.carmagotchi.app.domain.AilmentType
import com.carmagotchi.app.domain.DriveSound
import com.carmagotchi.app.domain.Gear
import com.carmagotchi.app.domain.PlaceInfo
import com.carmagotchi.app.domain.PlaceKind
import com.carmagotchi.app.domain.Seat
import com.carmagotchi.app.domain.dayKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 시연(PT)·개발용 신호 주입. 서비스가 돌고 있어야 한다. 실제 신호와 같은 경로(원시 값 -> 샘플 -> 감지기 -> 엔진)로 들어간다.
 * 시간이 걸리는 기준(와이퍼 60초, 정차 120초 등)은 시계를 앞으로 보내 건너뛴다: 단계마다 clock.offsetMs를 늘린 뒤 샘플을 낸다.
 *
 * adb 예 (-a com.carmagotchi.app.debug.INJECT):
 *   --es cmd script --es name passenger|rain|tpms|soc|fuel|grow|birthday|confirm
 *   --es cmd set [--es gear P|D|R|N] [--ef speed 60] [--ez brake true] [--ef steer 30] [--ez wiper true]
 *       [--es seat FRONT|REAR_LEFT|REAR_MIDDLE|REAR_RIGHT --ez on true] [--es warn TPMS|WASHER|OIL|... --ez on true]
 *       [--ef soc 40] [--ef fuel 40] [--ef odoAdd 2] [--ef temp -2] [--ef window 50]
 *       [--es placeLabel 경포해수욕장 --es regionKey "강원 강릉시" --es provinceKey 강원 --es kind SEA --ef remainKm 0.1]
 *   --es cmd dump   (현재 원시 값·펫 요약을 ChamaDemo 로그로)
 *   --es cmd skip --ei minutes 60
 *   --es cmd sound --es name DEPART|FRIEND|RAIN|CALM|HATCH|GREET|MUNCH|RECOVER|PET|VISITOR|ARRIVE   (소리 미리 듣기. P에서만)
 *   --es cmd hatch|start|stop|demo|wipe [--es name 차마] [--ez on true]   (debug 빌드만. 서비스 없이도 받는다)
 *   adb shell am broadcast -n com.carmagotchi.app/.platform.DemoReceiver -a com.carmagotchi.app.debug.INJECT --es cmd ...
 */
object Demo {
    const val ACTION = "com.carmagotchi.app.debug.INJECT"
    private const val TAG = "ChamaDemo"

    fun handle(ctx: Context, i: Intent) {
        val rt = PetRuntime.get(ctx)
        val debuggable = ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable && !rt.settings.value.demoMode) return // release에서는 숨은 시연 모드가 켜져 있을 때만
        val cmd = i.getStringExtra("cmd")
        if (cmd in setOf("hatch", "start", "stop", "demo", "wipe")) { // 서비스 없이도 되는 개발용 명령(debug 빌드만)
            if (!debuggable) return
            when (cmd) {
                "hatch" -> rt.scope.launch { rt.hatch(i.getStringExtra("name") ?: "차마", true, LocalDate.now()) }
                "start" -> PetService.start(ctx)
                "wipe" -> rt.deleteAll()
                "demo" -> rt.updateSettings { it.copy(demoMode = i.getBooleanExtra("on", true)) }
                else -> ctx.stopService(Intent(ctx, PetService::class.java))
            }
            return
        }
        val src = rt.source
        if (src == null) { Log.w(TAG, "service not running"); return }
        src.fastWarn = true // 시연: 경고 디바운스 2초
        rt.scope.launch {
            try {
                when (i.getStringExtra("cmd")) {
                    "sound" -> playSound(ctx, i.getStringExtra("name") ?: "")
                "script" -> Script(rt, src).run(i.getStringExtra("name") ?: "")
                    "set" -> set(src, i)
                    "dump" -> Log.i(TAG, "raw=${src.current()} pet=${rt.pet.value?.let { "stage=${it.stage} mood=${it.moodAt(rt.clock.instant())} ailments=${it.ailments.map { a -> a.type }} trips=${it.trips.size} last=${(it.pendingTrip ?: it.trips.lastOrNull())?.let { t -> "hb=${t.hardBrakes} st=${t.sharpTurns} pax=${t.passengers} rear=${t.rearPassenger} sec=${t.driveSec} km=${t.distanceKm} rain=${t.rain}" }} snacks=${it.snacks} items=${it.items.size}" }} ux=${rt.uxRestricted.value} arrival=${rt.arrival.value?.trip?.tripId} depart=${rt.depart.value?.extraEvents}")
                    "skip" -> { rt.clock.offsetMs += i.getIntExtra("minutes", 0) * 60_000L; src.update { it } }
                    else -> Log.w(TAG, "unknown cmd")
                }
            } catch (e: Exception) { Log.w(TAG, "inject failed: ${e.javaClass.simpleName}") }
        }
    }

    /** 소리 미리 듣기(시연 패널·adb). P에서만, 그 소리의 스위치("주행 중 소리" 또는 "정차 중 효과음")가 꺼져 있으면 안 낸다. 주행 규칙·밤 규칙·횟수는 무시한다. */
    fun playSound(ctx: Context, name: String) {
        val rt = PetRuntime.get(ctx)
        if (!rt.settings.value.demoMode && ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val s = runCatching { DriveSound.valueOf(name) }.getOrNull() ?: return
        if (rt.source == null || rt.gear.value != Gear.P || rt.settings.value.let { if (s >= DriveSound.HATCH) it.sfxOff else it.driveSoundsOff }) { Log.w(TAG, "sound preview blocked"); return }
        rt.previews.tryEmit(s)
    }

    /** 시연 모드 패널(앱 안)에서 PT 스크립트를 돌린다. 서비스가 돌고 있어야 한다. release에서는 시연 모드가 켜진 때만. */
    fun runScript(ctx: Context, name: String) {
        val rt = PetRuntime.get(ctx)
        if (!rt.settings.value.demoMode && ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val src = rt.source ?: return
        src.fastWarn = true
        rt.scope.launch { try { Script(rt, src).run(name) } catch (e: Exception) { Log.w(TAG, "script failed: ${e.javaClass.simpleName}") } }
    }

    private fun set(src: VehicleSignalSource, i: Intent) {
        fun f(k: String) = if (i.hasExtra(k)) i.getFloatExtra(k, 0f).toDouble() else null
        i.getStringExtra("gear")?.let { g -> src.injectGear(Gear.valueOf(g)) }
        src.update { r ->
            var n = r
            f("speed")?.let { n = n.copy(speedKmh = it) }
            if (i.hasExtra("brake")) n = n.copy(brake = i.getBooleanExtra("brake", false))
            f("steer")?.let { n = n.copy(steer = it) }
            if (i.hasExtra("wiper")) n = n.copy(wiper = i.getBooleanExtra("wiper", false))
            f("soc")?.let { n = n.copy(soc = it) }
            f("fuel")?.let { n = n.copy(fuel = it) }
            f("odoAdd")?.let { n = n.copy(odo = n.odo + it) }
            f("temp")?.let { n = n.copy(tempC = it) }
            f("window")?.let { n = n.copy(windows = mapOf("demo" to it.toInt())) }
            i.getStringExtra("seat")?.let { s ->
                val on = i.getBooleanExtra("on", true)
                n = n.copy(occ = n.occ + (Seat.valueOf(s) to on), belt = n.belt + (Seat.valueOf(s) to on))
            }
            i.getStringExtra("warn")?.let { w ->
                val t = AilmentType.valueOf(w)
                n = n.copy(warns = n.warns + ("demo" to if (i.getBooleanExtra("on", true)) setOf(t) else emptySet()))
            }
            i.getStringExtra("placeLabel")?.let { label ->
                n = n.copy(placeOverride = PlaceInfo(
                    label, i.getStringExtra("regionKey") ?: label, i.getStringExtra("provinceKey") ?: "기타",
                    i.getStringExtra("kind")?.let { PlaceKind.valueOf(it) } ?: PlaceKind.REGION, f("remainKm"),
                ))
            }
            n
        }
    }

    /** PT 시연 순서. 모두 정차(P)에서 시작하고 끝난다. */
    private class Script(val rt: PetRuntime, val src: VehicleSignalSource) {
        suspend fun wait(sec: Double) { rt.clock.offsetMs += (sec * 1000).toLong(); src.update { it }; delay(40) }
        suspend fun raw(f: (VehicleSignalSource.Raw) -> VehicleSignalSource.Raw, after: Double = 0.0) { src.update(f); wait(after) }

        suspend fun drive(steps: Int = 6, wiper: Boolean? = null) {
            src.injectGear(Gear.D)
            repeat(steps) { raw({ it.copy(odo = it.odo + 0.4, speedKmh = 50.0, brake = false, steer = 0.0, wiper = wiper) }, 20.0) }
        }

        suspend fun park() {
            raw({ it.copy(speedKmh = null, brake = false, steer = null) })
            src.injectGear(Gear.P)
            wait(1.0)
        }

        // 급제동: 속도 60 -> 45 + 브레이크, 이어서 조향 흔들림(EMULATOR 규칙도 만족)
        suspend fun hardBrake() {
            raw({ it.copy(speedKmh = 60.0, brake = false, steer = 0.0) }, 0.2)
            raw({ it.copy(speedKmh = 45.0, brake = true) }, 0.2)
            raw({ it.copy(steer = 60.0) }, 0.2)
            raw({ it.copy(steer = -60.0) }, 0.2)
            raw({ it.copy(brake = false, steer = 0.0, speedKmh = 50.0) }, 3.0)
        }

        // 급회전: 속도 60에서 조향 +50을 0.5초 이상 유지하다 반대로
        suspend fun sharpTurn() {
            raw({ it.copy(speedKmh = 60.0, brake = false, steer = 0.0) }, 0.2)
            raw({ it.copy(steer = 50.0) }, 0.3)
            raw({ it.copy(steer = 50.0) }, 0.3)
            raw({ it.copy(steer = -50.0) }, 0.3)
            raw({ it.copy(steer = 0.0, speedKmh = 50.0) }, 3.0)
        }

        suspend fun run(name: String) {
            when (name) {
                "passenger" -> { // 동승자 탑승 -> D -> 급제동 + 급회전 -> P(도착 카드)
                    raw({ it.copy(occ = it.occ + (Seat.REAR_RIGHT to true), belt = it.belt + (Seat.REAR_RIGHT to true)) }, 12.0)
                    drive(3); hardBrake(); sharpTurn(); drive(2); park()
                }
                "rain" -> { drive(5, wiper = true); raw({ it.copy(wiper = false) }); park() }
                "tpms" -> {
                    raw({ it.copy(warns = it.warns + ("demo" to setOf(AilmentType.TPMS))) }, 3.0)
                    wait(5.0)
                    raw({ it.copy(warns = it.warns + ("demo" to emptySet())) }, 31.0) // 정차에서 30초 꺼져 있으면 회복
                }
                "tpms_on" -> { raw({ it.copy(warns = it.warns + ("demo" to setOf(AilmentType.TPMS))) }, 3.0); wait(5.0) }
                "tpms_off" -> raw({ it.copy(warns = it.warns + ("demo" to emptySet())) }, 31.0)
                "soc" -> energy { r, v -> r.copy(soc = v) }
                "fuel" -> energy { r, v -> r.copy(fuel = v) }
                "confirm" -> wait(125.0) // 정차 120초 -> 주행 확정
                "grow" -> { repeat(7) { oneDay() }; restart() } // 7일 -> 아기 단계
                "birthday" -> {
                    rt.update { p -> p?.copy(carBirthday = dayKey(rt.clock.instant(), ZoneId.of("Asia/Seoul")).minusYears(1)) }
                    delay(100); restart()
                }
                else -> Log.w(TAG, "unknown script")
            }
        }

        private suspend fun energy(set: (VehicleSignalSource.Raw, Double) -> VehicleSignalSource.Raw) {
            raw({ set(it, 30.0) }, 12.0)
            raw({ set(it, 45.0) }, 12.0) // P에서 10초 중앙값이 +5%p 이상 오르면 밥
        }

        private suspend fun oneDay() {
            rt.clock.offsetMs += 24 * 3_600_000L
            drive(2); raw({ it.copy(odo = it.odo + 0.4) }, 160.0)
            park(); wait(125.0)
        }

        private suspend fun restart() { rt.sessionEnd(); delay(100); rt.sessionStart() } // 시동 OFF -> ON: 출발 인사(성장·생일)
    }
}

/** adb broadcast 진입점. 서비스가 죽어 있어도 받는다. */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Demo.handle(context, intent)
}
