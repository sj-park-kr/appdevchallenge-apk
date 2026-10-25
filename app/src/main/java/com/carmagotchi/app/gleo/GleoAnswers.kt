package com.carmagotchi.app.gleo

import com.carmagotchi.app.domain.AilmentType
import com.carmagotchi.app.domain.Dialogue
import com.carmagotchi.app.domain.EnergyProfile
import com.carmagotchi.app.domain.Hunger
import com.carmagotchi.app.domain.Line
import com.carmagotchi.app.domain.MoodBand
import com.carmagotchi.app.domain.PetState
import com.carmagotchi.app.domain.SignalProfile
import com.carmagotchi.app.domain.Stage
import com.carmagotchi.app.domain.hungerOf
import com.carmagotchi.app.domain.moodBand
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.roundToInt

const val FN_MOOD = "CHAMA_MOOD"
const val FN_DISTANCE = "CHAMA_DISTANCE"
const val FN_THANKS = "CHAMA_THANKS"
val GLEO_FUNCTIONS = setOf(FN_MOOD, FN_DISTANCE, FN_THANKS)
val GLEO_PARAM_KEY = mapOf(FN_MOOD to "topic", FN_DISTANCE to "scope", FN_THANKS to "kind")

/** 메모리 누적기에서 온 값(PetSession.gleoContext). */
data class GleoContext(
    val pet: PetState, val now: Instant, val guest: Boolean, val crash: Boolean,
    val tripKm: Double?, val todayKm: Double, val togetherKm: Double, val remainKm: Double?,
    val rough: Boolean, val rear: Boolean, val passengers: Boolean, val rain: Boolean, val night: Boolean, val smooth: Boolean,
)

/** message는 영어 완전한 문장(Gleo 규칙), say는 펫이 할 한국어 문장. */
data class GleoAnswer(
    val functionName: String, val status: String, val message: String,
    val say: String, val petName: String? = null, val mood: String? = null,
) {
    /** APP_ACTION_RESPONSE의 response extra. mode는 항상 voice: 앱은 소리를 내지 않는다. */
    fun toJson(): String = JSONObject()
        .put("functionName", functionName)
        .put("status", status)
        .put("message", message)
        .put("data", JSONArray().put(JSONObject().apply {
            petName?.let { put("petName", it) }
            put("say", say)
            mood?.let { put("mood", it) }
        }).toString())
        .put("mode", "voice")
        .toString()
}

/** PRD 11.3 우선순위에 따라 DIALOGUE.md 5.5절의 대사(DLG-GLEO-*)를 ID로 고른다. 되묻지 않는다(asking 없음). */
class GleoAnswers(private val lines: List<Line>) {
    private fun line(id: String) = lines.first { it.id == "DLG-GLEO-$id" }

    /** ctx가 null이면 부화 전. */
    fun answer(fn: String, param: String?, ctx: GleoContext?): GleoAnswer {
        if (ctx == null) return fail(fn, hatched = false)
        val p = ctx.pet
        val egg = p.stage == Stage.EGG
        val km = { v: Double -> (if (v >= 100) (v / 10).roundToInt() * 10 else v.roundToInt()).toString() }
        val vars = mapOf(
            "펫이름" to p.name, "km.이번" to km(ctx.tripKm ?: 0.0), "km.남음" to km(ctx.remainKm ?: 0.0),
            "km.오늘" to km(ctx.todayKm), "km.함께" to km(ctx.togetherKm),
        )
        fun ok(id: String, en: String): GleoAnswer {
            val l = line(id)
            val say = (Dialogue.fill(l.text, vars) ?: l.text).removePrefix("(해설) ") // 알 단계 해설 표식은 음성에 읽지 않는다
            return GleoAnswer(fn, "success", en, say, p.name, moodBand(p.moodAt(ctx.now)).name)
        }
        if (egg) return when (fn) {
            FN_MOOD -> ok("MOOD-E1", "The pet is still an egg and seems to be in a good mood.")
            FN_DISTANCE -> ok("DISTANCE-E1", "The pet is still an egg and has traveled ${vars["km.오늘"]} kilometers with you today.")
            else -> ok("THANKS-E1", "The egg wiggles happily.")
        }
        if (ctx.crash) return ok("MOOD-01", "The pet asks if the user is all right and says to take care of yourself first.")
        if (ctx.guest) return ok("MOOD-21", "The pet is resting right now.")
        return when (fn) {
            FN_MOOD -> mood(param, ctx, ::ok)
            FN_DISTANCE -> distance(param, ctx, vars, ::ok)
            else -> thanks(param, p.stage, ::ok)
        }
    }

    private fun mood(topic: String?, c: GleoContext, ok: (String, String) -> GleoAnswer): GleoAnswer {
        val p = c.pet
        val ev = p.energyProfile == EnergyProfile.EV ||
            (p.signalProfile == SignalProfile.EMULATOR && p.lastFuelPct == null && p.lastSoc != null)
        val hungry = hungerOf(if (ev) p.lastSoc else p.lastFuelPct, ev) == Hunger.HUNGRY
        val ail = p.ailments.map { it.type }
        val band = moodBand(p.moodAt(c.now))
        return when {
            p.serious -> ok("MOOD-02", "The pet is worried and suggests getting the car checked after getting out.")
            c.rear && c.rough -> ok("MOOD-03", "The pet is excited about the friend in the back seat and asks to take the curves gently.")
            c.rough -> ok("MOOD-04", "The pet was startled a moment ago but is fine now and says taking it slowly is fine.")
            topic == "hunger" || hungry ->
                if (!hungry) ok("MOOD-07", "The pet is full and says there is nothing to worry about.")
                else if (ev) ok("MOOD-06", "The pet is a little hungry and hopes for charging next time.")
                else ok("MOOD-05", "The pet is a little hungry and hopes for refueling next time.")
            AilmentType.TPMS in ail -> ok("MOOD-08", "The pet says its feet feel chilly and asks to check the tire pressure after getting out.")
            AilmentType.WASHER in ail -> ok("MOOD-09", "The pet says its eyes feel dry and asks to refill the washer fluid next time.")
            AilmentType.OIL in ail -> ok("MOOD-10", "The pet feels low on energy and asks to check the oil after getting out.")
            topic == "health" -> ok("MOOD-11", "The pet says it has no health problems and feels strong.")
            c.passengers -> ok("MOOD-12", "The pet is excited about the friends riding along.")
            c.rain -> ok("MOOD-13", "The pet is cozy listening to the rain.")
            c.night -> ok("MOOD-14", "The pet is watching the stars on the night road.")
            c.smooth -> ok("MOOD-15", "The pet is sleepy because the road has been smooth.")
            p.stage == Stage.BUDDY && band >= MoodBand.GOOD -> ok("MOOD-20", "The pet says anywhere is good as long as it is with the user.")
            band == MoodBand.GREAT -> ok("MOOD-16", "The pet is very excited and says today's road is the best.")
            band == MoodBand.GOOD -> ok("MOOD-17", "The pet feels good about riding together.")
            band == MoodBand.OK -> ok("MOOD-18", "The pet feels fine and is looking out the window.")
            else -> ok("MOOD-19", "The pet feels a little drowsy but is glad to be with the user.")
        }
    }

    private fun distance(scope: String?, c: GleoContext, v: Map<String, String>, ok: (String, String) -> GleoAnswer): GleoAnswer {
        val remain = c.remainKm
        val routed = remain != null
        fun withRoute() = if (remain!! < 5) ok("DISTANCE-02", "The pet says the destination is almost here, with ${v["km.남음"]} kilometers remaining.")
        else ok("DISTANCE-01", "The pet has traveled ${v["km.이번"]} kilometers on this trip, and ${v["km.남음"]} kilometers remain to the destination.")
        return when (scope) {
            "today" -> ok("DISTANCE-04", "The pet has traveled ${v["km.오늘"]} kilometers with you today.")
            "together" -> ok("DISTANCE-05", "The pet has traveled ${v["km.함께"]} kilometers with you in total.")
            "remaining" -> if (routed) withRoute() else ok("DISTANCE-07", "The pet does not know the destination yet and has traveled ${v["km.오늘"]} kilometers with you today.")
            "trip" -> if ((c.tripKm ?: 0.0) < 1) ok("DISTANCE-06", "The pet says the trip has only just started.")
            else ok("DISTANCE-03", "The pet has traveled ${v["km.이번"]} kilometers on this trip.")
            else -> when { // 기본: 안내 중이면 이번 주행 + 남은 거리, 아니면 오늘
                (c.tripKm ?: 0.0) < 1 && c.tripKm != null -> ok("DISTANCE-06", "The pet says the trip has only just started.")
                routed -> withRoute()
                else -> ok("DISTANCE-04", "The pet has traveled ${v["km.오늘"]} kilometers with you today.")
            }
        }
    }

    private fun thanks(kind: String?, stage: Stage, ok: (String, String) -> GleoAnswer): GleoAnswer {
        val k = kind?.takeIf { it in setOf("thanks", "goodnight", "praise", "love") } ?: "thanks"
        // 같은 종류 중 번호가 가장 큰(= 가장 구체적인 단계) 대사
        val id = lines.filter { it.id.startsWith("DLG-GLEO-THANKS-0") && it.cond == "kind=$k" && it.stageOk(stage) }
            .maxOf { it.id }.removePrefix("DLG-GLEO-")
        val en = when (k) {
            "goodnight" -> "The pet wishes the user good night and hopes to meet in dreams."
            "praise" -> "The pet tells the user they worked hard too."
            "love" -> "The pet says it loves the user back very much."
            else -> "The pet is happy to hear thanks and says thanks back."
        }
        return ok(id, en)
    }

    /** 부화 전이거나 내부 오류·시간 초과. */
    fun fail(fn: String, hatched: Boolean = true, timeout: Boolean = false): GleoAnswer =
        if (!hatched) GleoAnswer(fn, "fail", "The pet has not hatched yet. Please open the app while parked.", line("FAIL-01").text)
        else GleoAnswer(
            fn, "fail",
            if (timeout) "A timeout occurred while processing the request." else "An unexpected internal error occurred.",
            line("FAIL-02").text,
        )
}

/**
 * 파라미터를 읽는다. 문서에는 두 방식이 섞여 있다: key마다 putExtra, 또는 ACTION_PARAM 하나에 JSON 문자열
 * (따옴표로 한 번 더 감싼 형태 포함). 없으면 null = 기본값.
 */
fun readGleoParam(extra: (String) -> String?, key: String): String? {
    extra(key)?.let { return it }
    val raw = extra("ACTION_PARAM") ?: return null
    val s = raw.trim().removeSurrounding("\"").replace("\\\"", "\"")
    return runCatching { JSONObject(s).optString(key).ifBlank { null } }.getOrNull()
}
