package com.carmagotchi.app.domain

import java.io.InputStream
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import kotlin.math.floor

/** assets/dialogue.tsv(tools/gen-dialogue.js가 DIALOGUE.md에서 만든다)의 한 줄. */
class Line(val id: String, val stage: String, val cond: String, val text: String) {
    private val parts = id.split("-")
    val moment get() = parts[1] // HATCH, DEPART, ARRIVE, ROOM, GLEO, SYS
    val key get() = parts[2]

    /** 쉼표는 AND, 세로선은 OR. */
    val clauses: List<List<String>> = if (cond.isEmpty()) emptyList() else cond.split(",").map { it.trim().split("|") }

    fun stageOk(s: Stage) = when (stage) {
        "E" -> s == Stage.EGG
        "B" -> s == Stage.BABY
        "B+" -> s >= Stage.BABY
        "C+" -> s >= Stage.CHILD
        "A+" -> s >= Stage.ADULT
        "Y" -> s == Stage.BUDDY
        else -> true // 시스템·전 단계·성장 순간
    }
}

object Dialogue {
    fun parse(input: InputStream): List<Line> =
        input.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map {
            val c = it.split("\t", limit = 4)
            Line(c[0], c[1], c[2], c[3])
        }

    /** 2.2 받침 판정. 한글은 종성, 숫자는 읽는 소리(영·일·삼·육·칠·팔·십), 영문은 l·m·n만 받침. */
    fun hasBatchim(s: String): Boolean {
        val c = s.last()
        return when {
            c in '가'..'힣' -> (c - '가') % 28 != 0
            c.isDigit() -> c in "013678"
            c.isLetter() -> c.lowercaseChar() in "lmn"
            else -> false
        }
    }

    private fun josa(v: String, pair: String): String {
        val (withB, withoutB) = pair.split("/")
        val rieul = v.last() in '가'..'힣' && (v.last() - '가') % 28 == 8
        return if (hasBatchim(v) && !(pair == "으로/로" && rieul)) withB else withoutB
    }

    private val PH = Regex("\\{([^{}|]+)(?:\\|([^{}]+))?\\}")

    /** 자리표시자를 채운다. 값이 없거나 {장소}가 8자를 넘으면 null(그 대사는 후보에서 뺀다). */
    fun fill(text: String, vars: Map<String, String>): String? {
        var bad = false
        val out = PH.replace(text) { m ->
            val v = vars[m.groupValues[1]]
            if (v == null || (m.groupValues[1] == "장소" && v.length > 8)) { bad = true; "" }
            else if (m.groupValues[2].isEmpty()) v else v + josa(v, m.groupValues[2])
        }
        return if (bad) null else out
    }

    private const val NOTE = " / 해설: "

    /** EGG 대사는 의성어 + 해설이라 해설 부분만 잰다. */
    private fun isEggStyle(t: String) = NOTE in t || t.startsWith("(해설) ")
    private fun measured(t: String) = if (NOTE in t) t.substringAfter(NOTE) else t.removePrefix("(해설) ")

    fun maxLen(moment: String, text: String): Int {
        val m = when (moment) {
            "ARRIVE" -> Const.ARRIVE_LINE_MAX
            "DEPART", "ROOM" -> Const.DEPART_LINE_MAX
            else -> 40
        }
        return if (isEggStyle(text)) maxOf(m, 24) else m
    }

    fun fits(moment: String, text: String) = measured(text).length <= maxLen(moment, text)

    /** 3절 금지어. 한 글자 금지어는 단어 단위, 두 글자 이상은 부분 문자열. */
    private val BANNED = listOf(
        "너무 빨라", "빨리", "과속", "위험해", "조심해", "조심 좀", "왜 그랬어", "운전 못", "서툴", "초보", "잘못", "실수",
        "점수", "등급", "랭킹", "순위", "1등", "레벨", "미션", "퀘스트", "게임", "클리어", "달성", "보너스",
        "달려", "밟아", "쌩쌩", "더 빨리", "신기록", "km/h", "시속",
        "또 급", "또 깜짝", "또 휘청", "또 아파",
        "왜 이제 왔어", "기다리다 지쳤어", "버려", "외로워", "매일", "꼭 와", "내일도 꼭", "연속", "출석", "놓치면", "서운해",
        "죽겠", "다쳐", "쓰러져", "아파 죽",
        "다른 차는", "다른 사람은", "남들은",
        "좌표", "다 봤어", "기록했어", "지켜보고 있어",
        "했쪄", "하뗘", "뿌잉", "쮸쮸", "꾸잉", "다마고치", "포켓몬",
    )
    private val BANNED_WORDS = listOf("죽", "피")

    fun bannedIn(text: String): String? =
        BANNED.firstOrNull { it in text } ?: BANNED_WORDS.firstOrNull { w -> text.split(Regex("[\\s.,!?~…]+")).any { it == w } }
            ?: Regex("\\d+시 \\d+분").find(text)?.value
}

class Picked(val line: Line, val text: String)

/** DIALOGUE 4.1 대사 고르기. 같은 입력이면 같은 대사. */
class DialogueSelector(private val lines: List<Line>, private val clock: Clock, private val zone: ZoneId = ZoneId.of("Asia/Seoul")) {

    fun pick(
        state: PetState, moment: String, key: String, tags: Set<String>, vars: Map<String, String>, seedKey: String,
    ): Picked? {
        val now = clock.instant()
        val pool = lines.filter { it.moment == moment && it.key == key && it.stageOk(state.stage) }
        class C(val line: Line, val text: String, val score: Int, val lastShown: java.time.Instant?)
        val cands = pool.mapNotNull { l ->
            if (!l.clauses.all { alt -> alt.any { it in tags } }) return@mapNotNull null
            val text = Dialogue.fill(l.text, vars)?.takeIf { Dialogue.fits(moment, it) } ?: return@mapNotNull null
            // 기억·계절 태그 +2, 그 밖의 태그 +1, 태그 없음 0
            val score = l.clauses.sumOf { alt -> alt.first { it in tags }.let { t -> if (t.startsWith("MEM:") || t.startsWith("SE-")) 2 else 1 }.toInt() }
            C(l, text, score, state.dialogueHistory.filter { it.lineId == l.id }.maxOfOrNull { it.shownAt })
        }
        if (cands.isEmpty()) {
            // 끝까지 없으면 그 키의 태그 없는 첫 대사
            val l = pool.firstOrNull { it.clauses.isEmpty() } ?: return null
            return Dialogue.fill(l.text, vars)?.let { Picked(l, it) }
        }
        val cutoff = now.minus(Duration.ofDays(Const.NO_REPEAT_DAYS.toLong()))
        val fresh = cands.filter { it.lastShown == null || it.lastShown.isBefore(cutoff) }
        if (fresh.isEmpty()) return cands.minByOrNull { it.lastShown!! }!!.let { Picked(it.line, it.text) } // 다 썼으면 가장 오래전 것
        val best = fresh.maxOf { it.score }
        val tied = fresh.filter { it.score == best }.sortedBy { it.line.id }
        val c = tied[floor(seededRandom(state.vinSeedHash, seedKey, "LINE") * tied.size).toInt()]
        return Picked(c.line, c.text)
    }

    /** 고른 대사를 기록한다(최근 30일만 보관). */
    fun shown(state: PetState, p: Picked): PetState {
        val now = clock.instant()
        val cut = now.minus(Duration.ofDays(30))
        return state.copy(dialogueHistory = (state.dialogueHistory + DialogueRecord(p.line.id, now)).filter { it.shownAt.isAfter(cut) })
    }

    class Key(val moment: String, val key: String)

    /** PRD F2 도착 우선순위 1~15. 위의 하나만 고른다. */
    fun arriveKey(events: Set<EventId>, tags: Set<String>): Key = when {
        "SERIOUS" in tags || "CRASH" in tags -> Key("DEPART", "WARN_OTHER")
        "FIRST:VISITOR" in tags -> Key("ARRIVE", "VISITOR")
        "FIRST:SEA" in tags || "FIRST:MOUNTAIN" in tags -> Key("ARRIVE", "NEW_PLACE")
        "FIRST:RAIN" in tags || "FIRST:SNOW" in tags -> Key("ARRIVE", "RAIN")
        "FIRST:PASSENGER" in tags -> Key("ARRIVE", "PASSENGER_ON")
        "FIRST:NIGHT" in tags -> Key("ARRIVE", "NIGHT")
        "FIRST:REST" in tags -> Key("ARRIVE", "REST_STOP")
        EventId.MILESTONE_KM in events -> Key("ARRIVE", "MILESTONE_KM")
        EventId.WARN_CLEARED in events -> Key("ARRIVE", "WARN_CLEARED")
        EventId.REST_STOP in events -> Key("ARRIVE", "REST_STOP")
        EventId.HARD_BRAKE in events -> Key("ARRIVE", "HARD_BRAKE")
        "REVISIT" in tags -> Key("ARRIVE", "VISITOR")
        EventId.NEW_PLACE in events -> Key("ARRIVE", "NEW_PLACE")
        // 장마·첫눈 대사는 RAIN 키에 있다(SE 태그가 점수만 올린다)
        tags.any { it in setOf("SE-CHERRY", "SE-AUTUMN", "SE-SEOLLAL", "SE-CHUSEOK") } -> Key("ARRIVE", "SEASON_EVENT")
        EventId.RAIN in events -> Key("ARRIVE", "RAIN")
        EventId.NIGHT in events -> Key("ARRIVE", "NIGHT")
        EventId.PASSENGER_ON in events -> Key("ARRIVE", "PASSENGER_ON")
        EventId.SHARP_TURN in events -> Key("ARRIVE", "SHARP_TURN")
        EventId.SMOOTH_TRIP in events -> Key("ARRIVE", "SMOOTH_TRIP")
        else -> Key("ARRIVE", "DEFAULT")
    }

    /** PRD F3 출발 우선순위 1~13. 태그 NOTICE:{TPMS|WASHER|OIL}는 안내 빈도를 통과한 아픔, NOTICE_HUNGER는 배고픔 안내. */
    fun departKey(events: Set<EventId>, tags: Set<String>): Key = when {
        "SERIOUS" in tags || "CRASH" in tags -> Key("DEPART", "WARN_OTHER")
        EventId.STAGE_UP in events -> Key("DEPART", "STAGE_UP")
        EventId.BIRTHDAY in events -> Key("DEPART", "BIRTHDAY")
        EventId.LONG_ABSENCE in events -> Key("DEPART", "LONG_ABSENCE")
        EventId.REFUEL in events -> Key("DEPART", "REFUEL")
        EventId.RECHARGE in events -> Key("DEPART", "RECHARGE")
        EventId.WARN_CLEARED in events -> Key("DEPART", "WARN_CLEARED")
        EventId.SEASON_EVENT in events -> Key("DEPART", "SEASON_EVENT")
        tags.any { it.startsWith("NOTICE:") } -> Key("DEPART", "WARN_" + tags.first { it.startsWith("NOTICE:") }.removePrefix("NOTICE:"))
        "NOTICE_HUNGER" in tags -> Key("DEPART", if ("PROFILE:EV" in tags) "LOW_BATTERY" else "LOW_FUEL")
        EventId.PASSENGER_ON in events -> Key("DEPART", "PASSENGER_ON")
        EventId.REST_STOP in events -> Key("DEPART", "REST_STOP")
        EventId.FIRST_OF_DAY in events -> Key("DEPART", "FIRST_OF_DAY")
        else -> Key("DEPART", "AGAIN")
    }

    /** 4.3~4.4 공통 태그: 시간대, 주말, 말투 갈래, 안전 상태. */
    fun baseTags(state: PetState): Set<String> {
        val t = clock.instant().atZone(zone)
        val tr = state.traits
        return buildSet {
            add(when (t.hour) { in 4..9 -> "TIME:MORNING"; in 10..16 -> "TIME:DAY"; in 17..20 -> "TIME:EVENING"; else -> "TIME:NIGHT" })
            if (t.dayOfWeek == DayOfWeek.SATURDAY || t.dayOfWeek == DayOfWeek.SUNDAY) add("DAY:WEEKEND")
            if (tr.temperament != Temperament.UNDECIDED) add(tr.temperament.name)
            if (tr.roamer != Roamer.UNDECIDED) add(tr.roamer.name)
            if (tr.social != Social.UNDECIDED) add(tr.social.name)
            if (state.serious) add("SERIOUS")
            if (state.crashQuiet(clock.instant())) add("CRASH")
            if (state.energyProfile == EnergyProfile.EV) add("PROFILE:EV")
            state.ailments.forEach { add("AIL:${it.type.name}") }
            Seasons.dateEvents(dayKey(clock.instant(), zone), dayKey(state.hatchedAt, zone)).forEach { add(it.first) }
        }
    }

    /** 도착 카드 문장 한 줄. step은 StateEngine.confirmTrip의 결과(미리보기 포함). */
    fun arrive(step: Step, trip: TripSummary): Picked? {
        val s = step.state
        val events = step.events.map { it.id }.toSet()
        val tags = (baseTags(s) + step.tags).toMutableSet()
        val k = arriveKey(events, tags)
        if (trip.rain) tags += "RAIN"
        if (trip.snow) tags += "SNOW"
        if (trip.fog) tags += "FOG"
        if (trip.night) tags += "NIGHT"
        if (trip.passengers > 0) tags += "PAX"
        if (trip.passengers >= 2) tags += "PAX2+"
        if (trip.rearPassenger) tags += "REAR" else if (trip.passengers > 0) tags += "SEAT:FRONT"
        if (trip.hardBrakes >= 2) tags += "COUNT:2+"
        tags += when { trip.driveSec < 600 -> "LEN:SHORT"; trip.driveSec < 2400 -> "LEN:MID"; else -> "LEN:LONG" }
        trip.place?.let { p -> s.places[p.label]?.let { tags += "KIND:${it.kind.name}" } }
        if (k.key in setOf("HARD_BRAKE", "SHARP_TURN") || k.key.startsWith("WARN")) tags.removeAll { it.startsWith("MEM:") }
        val first = when (k.key) {
            "NEW_PLACE" -> "FIRST:SEA" in tags || "FIRST:MOUNTAIN" in tags
            "RAIN" -> "FIRST:RAIN" in tags || "FIRST:SNOW" in tags
            "PASSENGER_ON" -> "FIRST:PASSENGER" in tags
            "NIGHT" -> "FIRST:NIGHT" in tags
            else -> false
        }
        if (first) tags += "FIRST"
        val vars = mutableMapOf(
            "펫이름" to s.name, "분" to ((trip.driveSec + 30) / 60).toString(),
            "동승자" to (trip.companionName ?: "친구"),
        )
        trip.place?.let { vars["장소"] = it.label }
        step.events.firstOrNull { it.id == EventId.MILESTONE_KM }?.detail?.let { vars["km"] = "%,d".format(it.toInt()) }
        step.visitor?.let { id ->
            val n = Content.VISITORS.first { it.id == id }.name.substringAfter(' ')
            vars["길동무"] = if (Dialogue.hasBatchim(n)) n + "이" else n
        }
        // 그 키에 맞는 대사가 없으면(예: 명절인데 긴 주행이 아님) 기본 문장으로 내려간다
        return pick(s, k.moment, k.key, tags, vars, trip.tripId) ?: pick(s, "ARRIVE", "DEFAULT", tags, vars, trip.tripId)
    }

    /** 출발 인사 한 줄. step은 StateEngine.startUp의 결과, extra는 호출자가 모은 사실(NOTICE:*, PASSENGER_ON 등). */
    fun depart(step: Step, extraEvents: Set<EventId> = emptySet(), extraTags: Set<String> = emptySet()): Picked? {
        val s = step.state
        val events = step.events.map { it.id }.toSet() + extraEvents
        val tags = (baseTags(s) + step.tags + extraTags).toMutableSet()
        val k = departKey(events, tags)
        for (e in step.events) when (e.id) {
            EventId.STAGE_UP -> e.detail?.split(",")?.forEach { tags += "TO:$it" }
            EventId.SEASON_EVENT -> e.detail?.let { tags += it }
            EventId.BIRTHDAY -> {
                if (e.detail!!.startsWith("LATE")) tags += "LATE"
                if (e.detail.substringAfter(":").toInt() >= 2) tags += "YEAR2+"
            }
            EventId.LONG_ABSENCE -> if (e.detail!!.toInt() >= Const.VERY_LONG_ABSENCE_D) tags += "ABSENT30"
            else -> {}
        }
        if ("HEARTS_FULL" in tags && "TO:BUDDY" in tags) tags -= "TO:ADULT" // 어른·단짝이 한 번에 되면 H4
        val vars = mutableMapOf("펫이름" to s.name, "동승자" to "친구")
        step.events.firstOrNull { it.id == EventId.LONG_ABSENCE }?.detail?.let { vars["일수"] = it }
        if (k.key == "FIRST_OF_DAY") {
            val days = s.daysTogether + if (s.lastCountedDayKey == dayKey(clock.instant(), zone)) 0 else 1
            if (days % 10 == 0) vars["일수"] = days.toString() // "{일수}일째!"는 10의 배수인 날만
        }
        return pick(s, k.moment, k.key, tags, vars, dayKey(clock.instant(), zone).toString())
    }
}
