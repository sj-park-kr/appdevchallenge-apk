package com.carmagotchi.app.gleo

import com.carmagotchi.app.domain.Stage
import com.carmagotchi.app.domain.catalog
import com.carmagotchi.app.domain.clockAt
import com.carmagotchi.app.domain.newPet
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GleoTest {
    private val clock = clockAt("2026-10-25T09:00:00")
    private val answers = GleoAnswers(catalog())
    private fun ctx(stage: Stage = Stage.BABY, guest: Boolean = false, remain: Double? = null) = GleoContext(
        newPet(clock, stage), clock.now, guest, crash = false, tripKm = 12.3, todayKm = 40.0, togetherKm = 1234.0, remainKm = remain,
        rough = false, rear = false, passengers = false, rain = false, night = false, smooth = false,
    )

    @Test fun responseJsonFollowsCallbackSchema() {
        val j = JSONObject(answers.answer(FN_MOOD, null, ctx()).toJson())
        assertEquals("CHAMA_MOOD", j.getString("functionName"))
        assertEquals("success", j.getString("status"))
        assertEquals("voice", j.getString("mode"))
        assertTrue(j.getString("message").endsWith("."))
        val data = JSONArray(j.getString("data")).getJSONObject(0)
        assertEquals("콩이", data.getString("petName"))
        assertTrue(data.getString("say").isNotBlank())
    }

    @Test fun noPetFallsBackToFail() {
        val a = answers.answer(FN_DISTANCE, null, null)
        assertEquals("fail", a.status)
        assertTrue(a.say.isNotBlank())
        assertEquals("fail", answers.fail(FN_MOOD, timeout = true).status)
    }

    @Test fun guestAndEggAnswer() {
        assertEquals("success", answers.answer(FN_MOOD, null, ctx(guest = true)).status)
        assertEquals("success", answers.answer(FN_THANKS, "love", ctx(Stage.EGG)).status)
    }

    @Test fun everyFunctionAndParamAnswers() {
        val schema = JSONObject(File("src/main/assets/schema.json").readText())
        val fns = schema.getJSONArray("actionCalls")
        assertEquals(GLEO_FUNCTIONS, (0 until fns.length()).map { fns.getJSONObject(it).getJSONObject("function").getString("name") }.toSet())
        for (i in 0 until fns.length()) {
            val f = fns.getJSONObject(i).getJSONObject("function")
            val name = f.getString("name")
            val prop = f.getJSONObject("parameters").getJSONObject("properties").getJSONObject(GLEO_PARAM_KEY.getValue(name))
            val values = prop.getJSONArray("enum").let { e -> (0 until e.length()).map { e.getString(it) } }
            for (stage in Stage.values()) for (v in values + null) for (remain in listOf(null, 2.0, 80.0))
                assertEquals("$name $v $stage", "success", answers.answer(name, v, ctx(stage, remain = remain)).status)
        }
    }

    @Test fun paramReadsBothStyles() {
        assertEquals("trip", readGleoParam({ if (it == "scope") "trip" else null }, "scope"))
        assertEquals("today", readGleoParam({ if (it == "ACTION_PARAM") "\"{\\\"scope\\\":\\\"today\\\"}\"" else null }, "scope"))
        assertEquals("love", readGleoParam({ if (it == "ACTION_PARAM") "{\"kind\":\"love\"}" else null }, "kind"))
        assertNull(readGleoParam({ null }, "kind"))
    }
}
