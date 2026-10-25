package com.carmagotchi.app.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import com.carmagotchi.app.R
import com.carmagotchi.app.domain.Dialogue
import com.carmagotchi.app.domain.Gear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 포그라운드 서비스: 주행 중에도 신호를 받아야 하므로(캐시 프로세스 동결 방지) 필요하다.
 * 알림은 가장 낮은 중요도(소리·헤드업 없음)의 상시 한 줄뿐이다. 펫 알림은 만들지 않는다.
 */
class PetService : Service() {
    private lateinit var rt: PetRuntime
    private val svc = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var source: VehicleSignalSource? = null
    private lateinit var player: DriveSoundPlayer

    private val screen = object : BroadcastReceiver() {
        // 시동 OFF를 못 받으므로 화면 꺼짐으로 대신한다(AAOS IGNITION_STATE가 읽히면 그쪽이 먼저). 에뮬레이터 확인: IGNITION_STATE 속성이 없고 SCREEN_OFF/ON은 온다
        override fun onReceive(c: Context, i: Intent) { Log.i("ChamaService", "${i.action}"); if (i.action == Intent.ACTION_SCREEN_OFF) { player.stop(); rt.sessionEnd() } else rt.sessionStart() }
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        rt = PetRuntime.get(this)
        player = DriveSoundPlayer(this, svc)
        Log.i("ChamaService", "create pet=${rt.pet.value != null}")
        // startForegroundService 뒤 5초 안에 불러야 한다. 펫이 없어도 먼저 부르고 끝낸다.
        startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (rt.pet.value == null) { stopSelf(); return }
        attach()
    }

    // 프로세스가 죽으면 시스템이 다시 만든다(onCreate가 저장된 상태를 읽는다). 에뮬레이터 확인: kill -9 뒤 재시작되고 startForeground도 된다
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    private fun attach() {
        val src = VehicleSignalSource(applicationContext, rt.clock).also { source = it; rt.source = it }
        src.locationOn = rt.pet.value?.locationOn ?: true
        // P로 바뀌는 순간에만 멈춘다. P 샘플마다 멈추면 정차 중 미리듣기가 바로 끊긴다
        var lastGear: Gear? = null
        svc.launch { src.samples().collect { rt.onSample(it); if (it.gear == Gear.P && lastGear != Gear.P) player.stop(); lastGear = it.gear } }
        svc.launch { rt.sounds.collect { if (!player.play(it)) rt.soundFailed() } }
        svc.launch { rt.previews.collect { player.play(it) } }
        svc.launch { src.uxRestricted.collect { rt.uxRestricted.value = it } }
        svc.launch { src.ignition.collect { if (it) rt.sessionStart() else rt.sessionEnd() } }
        svc.launch { rt.pet.collect { if (it == null) stopSelf() } } // 모두 지우기 -> 서비스 종료
        registerReceiver(screen, IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }, RECEIVER_NOT_EXPORTED)
        rt.sessionStart() // 서비스 시작 = 세션 시작(M1)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screen) }
        if (::player.isInitialized) player.stop()
        rt.source = null
        source = null
        svc.cancel() // 흐름이 닫히면서 SDK 리스너도 해제된다
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.chama_channel), NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val name = rt.pet.value?.name
        val text = if (name == null) getString(R.string.chama_riding_generic)
        else getString(R.string.chama_riding, name + if (Dialogue.hasBatchim(name)) "이" else "가")
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pet).setContentTitle(text)
            .setOngoing(true).setShowWhen(false).setVisibility(Notification.VISIBILITY_SECRET).setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1
        private const val CHANNEL = "chama_service"

        /** 포그라운드에서(부화 직후, 앱 화면이 열릴 때) 또는 부팅 receiver에서 부른다. */
        fun start(ctx: Context) {
            try { ctx.startForegroundService(Intent(ctx, PetService::class.java)) }
            catch (e: Exception) { Log.w("ChamaService", "start not allowed: ${e.javaClass.simpleName}") }
        }
    }
}
