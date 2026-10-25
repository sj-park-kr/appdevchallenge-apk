package com.carmagotchi.app.data

import com.carmagotchi.app.domain.PetState
import com.carmagotchi.app.domain.TripSummary
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate

data class Settings(
    val arriveCard: Boolean = true,
    val departGreeting: Boolean = true,
    val demoMode: Boolean = false, // 숨은 시연 모드. release에도 있다
    // 아래 3개는 Gson이 옛 파일에서 null/false로 읽을 수 있어 "없음"이 기본값이 되게 둔다(driveSoundsOff = false -> 소리 켜짐)
    val driveSoundsOff: Boolean = false,
    val quietUntilMs: Long = 0, // "조용히": 이 시각(다음 04:00)까지 앱 소리(주행 중·정차 중) 없음
    val sfxOff: Boolean = false, // 정차 중 효과음(F15). 옛 파일에는 없어서 false(= 켜짐, SFX_DEFAULT_ON)로 읽힌다
    val passengerNames: List<String>? = null,
    val placed: List<String>? = null,
)

/** 저장 단위. checkpoint는 주행 중 60초마다 남기는 열린 주행의 요약(프로세스가 죽었을 때 복원용). */
data class Snapshot(val pet: PetState, val settings: Settings = Settings(), val checkpoint: TripSummary? = null)

private data class Core(val pet: PetState, val settings: Settings, val checkpoint: TripSummary?, val schemaVersion: Int)

/** 자주 바뀌지 않는 큰 목록. 바뀌었을 때만 다시 쓴다. */
private data class History(
    val trips: List<TripSummary>,
    val visitors: Map<String, com.carmagotchi.app.domain.VisitorRecord>,
    val places: Map<String, com.carmagotchi.app.domain.PlaceRecord>,
    val items: Map<String, Instant>,
    val companions: Map<String, com.carmagotchi.app.domain.CompanionRecord>,
    val memory: List<com.carmagotchi.app.domain.MemoryEntry>,
    val ledger: Set<String>,
    val dialogueHistory: List<com.carmagotchi.app.domain.DialogueRecord>,
)

/**
 * 암호화 파일 2개(core.bin, history.bin). 쓰기는 임시 파일 + 이름 바꾸기(원자적).
 * shortcut: Gson 리플렉션이라 새 필드를 추가하면 옛 파일의 그 필드가 null이 된다. 필드를 바꿀 때 SCHEMA와 마이그레이션을 더한다.
 */
class PetStore(private val dir: File, private val crypto: BlobCrypto) {
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, object : TypeAdapter<Instant>() {
            override fun write(out: JsonWriter, v: Instant) { out.value(v.toString()) }
            override fun read(r: JsonReader): Instant = Instant.parse(r.nextString())
        }.nullSafe())
        .registerTypeAdapter(LocalDate::class.java, object : TypeAdapter<LocalDate>() {
            override fun write(out: JsonWriter, v: LocalDate) { out.value(v.toString()) }
            override fun read(r: JsonReader): LocalDate = LocalDate.parse(r.nextString())
        }.nullSafe())
        .create()

    private var lastHistory: History? = null

    fun exists() = File(dir, CORE).exists()

    fun save(s: Snapshot) {
        val p = s.pet
        val h = History(p.trips, p.visitors, p.places, p.items, p.companions, p.memory, p.ledger, p.dialogueHistory)
        val core = Core(
            p.copy(trips = emptyList(), visitors = emptyMap(), places = emptyMap(), items = emptyMap(),
                companions = emptyMap(), memory = emptyList(), ledger = emptySet(), dialogueHistory = emptyList()),
            s.settings, s.checkpoint, SCHEMA,
        )
        write(CORE, gson.toJson(core))
        if (h != lastHistory || !File(dir, HISTORY).exists()) { write(HISTORY, gson.toJson(h)); lastHistory = h }
    }

    /** 없으면 null. 읽기에 실패하면(키 분실, 변조) 파일을 *.corrupt로 옮기고 null: 새 알로 시작한다. */
    fun load(): Snapshot? {
        if (!exists()) return null
        return try {
            val core = gson.fromJson(read(CORE), Core::class.java)
            val h = gson.fromJson(read(HISTORY), History::class.java)
            lastHistory = h
            val pet = core.pet.copy(
                trips = h.trips, visitors = h.visitors, places = h.places, items = h.items,
                companions = h.companions, memory = h.memory, ledger = h.ledger, dialogueHistory = h.dialogueHistory,
            )
            Snapshot(pet, core.settings, core.checkpoint)
        } catch (e: Exception) {
            for (n in listOf(CORE, HISTORY)) File(dir, n).let { if (it.exists()) it.renameTo(File(dir, "$n.corrupt")) }
            null
        }
    }

    /** 동의 철회: 파일, 임시 파일, 손상 파일과 키를 모두 지운다. */
    fun deleteAll() {
        dir.listFiles { f -> f.name.startsWith("core.bin") || f.name.startsWith("history.bin") }?.forEach { it.delete() }
        crypto.destroyKey()
        lastHistory = null
    }

    private fun write(name: String, json: String) {
        dir.mkdirs()
        val tmp = File(dir, "$name.tmp")
        FileOutputStream(tmp).use { it.write(crypto.seal(json.toByteArray(Charsets.UTF_8), name.toByteArray())); it.fd.sync() }
        Files.move(tmp.toPath(), File(dir, name).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun read(name: String) = String(crypto.open(File(dir, name).readBytes(), name.toByteArray()), Charsets.UTF_8)

    private companion object {
        const val CORE = "core.bin"
        const val HISTORY = "history.bin"
        const val SCHEMA = 1
    }
}
