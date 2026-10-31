package com.carmagotchi.app.platform

import android.content.Context
import android.util.Log
import com.carmagotchi.app.data.KeystoreCrypto
import com.carmagotchi.app.data.PetStore
import com.carmagotchi.app.data.Settings
import com.carmagotchi.app.data.Snapshot
import com.carmagotchi.app.domain.Dialogue
import com.carmagotchi.app.domain.DriveSound
import com.carmagotchi.app.domain.Gear
import com.carmagotchi.app.domain.DialogueSelector
import com.carmagotchi.app.domain.PetState
import com.carmagotchi.app.domain.StateEngine
import com.carmagotchi.app.domain.VehicleSample
import com.carmagotchi.app.gleo.FN_THANKS
import com.carmagotchi.app.gleo.GleoAnswer
import com.carmagotchi.app.gleo.GleoAnswers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 시연 모드의 시간 건너뛰기용. offsetMs가 0이면 시스템 시계와 같다. 메모리에만 있다(프로세스가 죽으면 0). */
class OffsetClock(private val zone: ZoneId = ZoneId.of("Asia/Seoul")) : Clock() {
    @Volatile var offsetMs = 0L
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = Instant.now().plusMillis(offsetMs)
}

/**
 * 앱 전체가 하나만 쓰는 상태 보관소(Application 클래스 없이). 서비스, Activity, Gleo 수신기가 같은 것을 본다.
 * 상태를 바꾸는 일은 모두 한 스레드(scope)에서 순서대로 처리한다. UI는 아래 StateFlow를 읽고 update로 바꾼다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PetRuntime private constructor(private val app: Context) {
    val clock = OffsetClock()
    val engine = StateEngine(clock)
    val lines = app.assets.open("dialogue.tsv").use(Dialogue::parse)
    val selector = DialogueSelector(lines, clock)
    val gleoAnswers = GleoAnswers(lines)
    private val store = PetStore(dataDir(app), KeystoreCrypto.create())
    private val saver = SessionSaver(store::save)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    val pet = MutableStateFlow<PetState?>(null)
    val arrival = MutableStateFlow<Arrival?>(null)
    val depart = MutableStateFlow<DepartInfo?>(null)
    val settings = MutableStateFlow(Settings())
    val saveFailed = MutableStateFlow(false)
    /** CarUxRestrictions가 주행 규제(DO 필요)를 걸었는지. 서비스가 돌 때만 갱신된다. */
    val uxRestricted = MutableStateFlow(false)
    val lastGleo = MutableStateFlow<GleoAnswer?>(null)
    val gear = MutableStateFlow(Gear.P)
    /** 낼 차례가 된 주행 중 동행 소리(F14). 서비스가 받아 재생한다. */
    val sounds = MutableSharedFlow<DriveSound>(extraBufferCapacity = 2)
    /** 시연 패널의 "소리 미리 듣기". 주행 규칙·횟수와 무관하다(개발 도구). */
    val previews = MutableSharedFlow<DriveSound>(extraBufferCapacity = 2)

    /** 서비스가 도는 동안만 있다. */
    @Volatile var source: VehicleSignalSource? = null
    private var session: PetSession? = null

    init {
        store.load()?.let { session = PetSession(it, engine, clock) }
        publish()
        scope.launch { while (true) { delay(30_000); if (saver.failed) publish() } }
    }

    /** 저장하고(바뀌었으면) 흐름에 반영한다. scope 스레드에서만 부른다. */
    private fun publish(forceSave: Boolean = false) {
        val s = session
        saver.persist(s, clock.instant(), forceSave)
        saveFailed.value = saver.failed
        pet.value = s?.pet
        arrival.value = s?.arrival
        depart.value = s?.depart
        settings.value = s?.settings ?: Settings()
        source?.let { it.locationOn = s?.pet?.locationOn ?: true; if (s?.settings?.demoMode == true || isEmulator) it.fastWarn = true }
    }

    private fun post(block: () -> Unit) {
        scope.launch { try { block() } catch (e: Throwable) { Log.e(TAG, "runtime error: ${e.javaClass.simpleName}") }; publish() }
    }

    fun onSample(s: VehicleSample) { gear.value = s.gear; post { session?.let { it.onSample(s); it.takeSound()?.let(sounds::tryEmit) } } }
    /** 소리를 못 냈다(포커스 거부, 통화 중 등): 횟수에서 뺀다. */
    fun soundFailed() = post { session?.cancelSound() }
    fun setPassengerNames(names: List<String>) = post { session?.setPassengerNames(names) }
    fun clearPlaces() = post { source?.locationOn = false; session?.clearPlaces() }
    fun retrySave() { scope.launch { publish(forceSave = true) } }
    fun setCompanion(name: String?) = post { session?.companion = name }
    fun sessionStart() = post { session?.onSessionStart() }
    fun sessionEnd() = post { session?.onSessionEnd(clock.instant()) }

    /** 펫 상태를 바꾼다. 부화는 null -> 새 펫, 교감·설정 등은 현재 -> 다음. 예: update { engine.bond(it!!, Bond.PET).state } */
    fun update(f: (PetState?) -> PetState?) = post {
        val next = f(session?.pet) ?: return@post
        session?.replace(next) ?: PetSession(Snapshot(next, settings.value), engine, clock).also { it.changed(); session = it }
    }

    fun updateSettings(f: (Settings) -> Settings) = post { session?.let { it.settings = f(it.settings); it.changed() } }

    /** 부화: 차에서 VIN 씨앗·주행거리·연료 종류를 읽어 새 펫을 만들고 서비스를 시작한다(F11). 이미 펫이 있으면 아무 일도 하지 않는다. */
    suspend fun hatch(name: String, locationOn: Boolean, carBirthday: LocalDate) {
        if (session != null) return
        val info = readHatchInfo(app)
        scope.async {
            if (session != null) return@async
            val pet = engine.hatch(name, info.vinSeedHash, info.odoKm, carBirthday, info.energyProfile).copy(locationOn = locationOn)
            session = PetSession(Snapshot(pet, settings.value), engine, clock).also { it.changed() }
            publish()
        }.await()
        PetService.start(app)
    }

    /** 모두 지우기(동의 철회): 파일과 키를 지우고 알 상태로. 서비스는 pet이 null이 되면 스스로 끝난다. */
    fun deleteAll() = post { session = null; store.deleteAll() }

    /** Gleo 함수 하나에 답한다(7초 안, 목표 1초). THANKS는 상태도 바꾼다. */
    suspend fun gleo(fn: String, param: String?): GleoAnswer {
        val remain = if (fn == "CHAMA_DISTANCE") source?.remainKm() else null // 목적지는 NaviHelper에 물어본다. 서비스가 없으면 null
        return scope.async {
            val s = session
            val ctx = s?.gleoContext(remain)
            val a = gleoAnswers.answer(fn, param, ctx)
            if (fn == FN_THANKS && a.status == "success") s?.thanks()
            lastGleo.value = a
            publish()
            a
        }.await()
    }

    companion object {
        private const val TAG = "ChamaRuntime"
        @Volatile private var inst: PetRuntime? = null

        fun get(ctx: Context): PetRuntime = inst ?: synchronized(this) { inst ?: PetRuntime(ctx.applicationContext).also { inst = it } }

        fun dataDir(ctx: Context) = File(ctx.filesDir, "chama")

        /** 부팅 때 서비스를 시작해도 되는지: 펫이 있을 때만(Keystore를 건드리지 않고 파일만 본다). */
        fun hasSavedPet(ctx: Context) = File(dataDir(ctx), "core.bin").exists()

        // 에뮬레이터 확인: Pleos 이미지는 ro.hardware=ranchu, ro.product.name=crp_avd
        val isEmulator = android.os.Build.HARDWARE.let { it == "ranchu" || it == "goldfish" } || android.os.Build.PRODUCT.contains("emulator", true)
    }
}
