package com.carmagotchi.app.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.carmagotchi.app.domain.DriveSound
import com.carmagotchi.app.domain.DriveSounds
import com.carmagotchi.app.domain.Origin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * F14 재생 절차: SONIFICATION 속성 + AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK 요청 -> 허락(GRANTED)일 때만 재생 -> 끝나면 즉시 반납.
 * 포커스를 잃으면(어떤 종류든) 바로 멈추고 반납한다. 거부되거나 통화·내비·음성 안내 중이면 재생하지 않고 false.
 */
class DriveSoundPlayer(ctx: Context, private val scope: CoroutineScope, private val onLoss: () -> Unit = {}) {
    private val am = ctx.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null
    private var job: Job? = null

    private val busyUsages = setOf(
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, AudioAttributes.USAGE_ASSISTANT, AudioAttributes.USAGE_VOICE_COMMUNICATION,
    )

    @Synchronized
    fun play(sound: DriveSound, variant: Int = 0, origin: Origin? = null): Boolean {
        stop("replace")
        if (am.mode in setOf(AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE)) return false
        if (runCatching { am.activePlaybackConfigurations.any { it.audioAttributes.usage in busyUsages } }.getOrDefault(false)) return false
        if (am.isStreamMute(AudioManager.STREAM_SYSTEM)) return false
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs).setOnAudioFocusChangeListener { if (it != AudioManager.AUDIOFOCUS_GAIN) { onLoss(); stop("loss") } }.build()
        val granted = am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.i(TAG, "focus request $sound#$variant $origin granted=$granted")
        if (!granted) return false
        return try {
            val pcm = DriveSounds.synth(sound, variant)
            val t = AudioTrack.Builder().setAudioAttributes(attrs)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(DriveSounds.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            t.write(pcm, 0, pcm.size)
            t.play()
            track = t; focus = req
            job = scope.launch { delay(DriveSounds.durationMs(sound) + 60L); stop("end") }
            Log.i(TAG, "play $sound#$variant ${DriveSounds.durationMs(sound)}ms")
            true
        } catch (e: Exception) {
            am.abandonAudioFocusRequest(req)
            Log.w(TAG, "play failed: ${e.javaClass.simpleName}")
            false
        }
    }

    /** 재생 중이면 멈추고 포커스를 반납한다. 기어 P, 화면 꺼짐, 서비스 종료에서도 부른다. */
    @Synchronized
    fun stop(why: String = "stop") {
        job?.cancel(); job = null
        track?.let { runCatching { it.stop() }; it.release() }; track = null
        focus?.let { am.abandonAudioFocusRequest(it); Log.i(TAG, "stop reason=$why abandon") }; focus = null
    }

    private companion object { const val TAG = "ChamaSound" }
}
