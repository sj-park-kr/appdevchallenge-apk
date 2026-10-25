package com.carmagotchi.app.platform

import android.content.Context
import android.util.Log
import com.carmagotchi.app.domain.DriveSound
import com.carmagotchi.app.domain.Gear
import com.carmagotchi.app.domain.Origin
import com.carmagotchi.app.domain.ParkedSoundPolicy
import com.carmagotchi.app.domain.SfxCue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * 정차 중 효과음(F15)의 주인. 화면이 모션의 정해진 순간에 [play]를 부르면 규칙(ParkedSoundPolicy)을 보고 DriveSoundPlayer로 낸다.
 * 소리는 한 번에 하나: 새 요청은 앞 소리와 남은 예약(한 입 소리 등)을 버린다. 막히면 보류 없이 버린다.
 */
class ParkedSfx(ctx: Context, private val rt: PetRuntime, private val scope: CoroutineScope) {
    private val policy = ParkedSoundPolicy()
    private val player = DriveSoundPlayer(ctx, scope) { cues?.cancel() }
    private var cues: Job? = null
    private var petVariant = 0
    /** Activity가 resumed인 동안 true */
    @Volatile var resumed = false

    fun play(list: List<SfxCue>, origin: Origin) {
        val t0 = rt.clock.instant()
        synchronized(this) {
            cues?.cancel()
            cues = scope.launch {
                var at = 0
                for (c in list) {
                    delay((c.atMs - at).toLong()); at = c.atMs
                    emit(c, origin, if (at == 0) t0 else rt.clock.instant())
                }
            }
        }
    }

    private fun emit(c: SfxCue, origin: Origin, now: Instant) = synchronized(this) {
        val pet = rt.pet.value
        val set = rt.settings.value
        val why = policy.block(
            c.sound, origin, now, rt.gear.value == Gear.P && !rt.uxRestricted.value, resumed, !set.sfxOff, now.toEpochMilli() < set.quietUntilMs,
            pet != null && (pet.guestModeUntil?.isAfter(now) == true || pet.serious || pet.crashQuiet(now)),
        )
        if (why != null) { Log.i(TAG, "blocked ${c.sound} $origin reason=$why"); return@synchronized }
        val v = if (c.sound == DriveSound.PET) petVariant else c.variant
        if (player.play(c.sound, v, origin) && c.sound == DriveSound.PET) petVariant++
    }

    /** P를 벗어남, 화면 가려짐·꺼짐, 설정 끔: 소리·예약·포커스를 바로 버린다. */
    fun stop(why: String) { synchronized(this) { cues?.cancel(); cues = null }; player.stop(why) }

    private companion object { const val TAG = "ChamaSound" }
}
