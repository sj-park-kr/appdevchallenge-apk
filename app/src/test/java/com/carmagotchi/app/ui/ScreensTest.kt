package com.carmagotchi.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 화면별 PNG를 build/shots/ 에 만든다: ./gradlew testDebugUnitTest --tests '*ScreensTest*' -Proborazzi.test.record=true */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1680dp-h1210dp-mdpi")
class ScreensTest {
    @get:Rule val rule = createComposeRule()

    // (이름, sample, tab, overlay, hatchStep)
    private data class S(val n: String, val sample: String, val tab: Int = 0, val ov: String? = null, val hatch: Int = 0)
    private val screens = listOf(
        S("hatch0", "hatch"), S("hatch2", "hatch", hatch = 2), S("hatch3", "hatch", hatch = 3), S("hatch4", "hatch", hatch = 4), S("hatch5", "room", hatch = 5),
        S("depart", "depart"), S("depart-greet", "greet"), S("depart-grow", "grow"), S("arrive", "arrive"), S("arrive-brake", "arrive2"), S("arrive-egg", "arrive-egg"),
        S("room", "room"), S("room-baby", "baby"), S("room-guest", "guest"), S("room-empty", "empty"),
        S("dex", "room", 1), S("dex-empty", "empty", 1), S("album", "room", 2), S("album-empty", "empty", 2),
        S("share", "room", ov = "share:m2"), S("settings", "room", ov = "settings"),
    )
    private val sizes = listOf("portrait" to (560 to 808), "land" to (1133 to 808), "real" to (1680 to 1210))

    private class Cur(val n: Int, val w: Int, val h: Int, val dark: Boolean, val content: @androidx.compose.runtime.Composable () -> Unit)
    private var cur by androidx.compose.runtime.mutableStateOf<Cur?>(null)
    private var started = false
    private var seq = 0

    private fun shoot(name: String, w: Int, h: Int, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        cur = Cur(++seq, w, h, dark, content); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        if (!started) { started = true; rule.setContent { cur?.let { k -> androidx.compose.runtime.key(k.n) { ChamaTheme(k.dark, true) { androidx.compose.foundation.layout.Box(Modifier.size(k.w.dp, k.h.dp)) { k.content() } } } } } }
        repeat(10) { rule.mainClock.advanceTimeBy(50) }; rule.mainClock.advanceTimeBy(4000)
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test fun screens() {
        for ((sn, wh) in sizes) for (dark in listOf(false, true)) for (s in screens) {
            if (sn == "real" && dark && s.n !in setOf("room", "arrive", "settings")) continue
            val ctl = FakePetController(Samples.named(s.sample).copy(reduceMotion = true))
            shoot("${s.n}-$sn-${if (dark) "dark" else "light"}", wh.first, wh.second, dark) { AppContent(ctl, s.tab, s.ov, s.hatch) }
        }
    }

    @Test fun gallery() {
        for (p in 0..9) shoot("gallery$p", 1680, 1210, false) { Gallery(p) }
        shoot("gallery3-dark", 1680, 1210, true) { Gallery(3) }
    }
}
