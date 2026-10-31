package com.carmagotchi.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.carmagotchi.app.Memory
import com.carmagotchi.app.domain.EventId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1133dp-h808dp-mdpi")
class ShareScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sharingRequiresSeparateConsentForNameAndLocation() {
        val state = Samples.room()
        val memory = Memory("privacy", "서윤이랑 강릉시에 처음 갔어", state.now, EventId.NEW_PLACE, with = "서윤", place = "강릉시")
        rule.setContent { ChamaTheme(false, true) { ShareScreen(state, memory) {} } }
        rule.onNodeWithText("새로운 곳에 처음 갔어").assertExists()
        rule.onNodeWithText(memory.title).assertDoesNotExist()
        rule.onNodeWithText("장소 넣기 (강릉시)").performScrollTo().performClick()
        rule.onNodeWithText("친구랑 강릉시에 처음 갔어").assertExists()
        rule.onNodeWithText("이름 넣기 (서윤)").performScrollTo().performClick()
        rule.onNodeWithText(memory.title).assertExists()
        rule.onNodeWithText("장소 넣기 (강릉시)").performScrollTo().performClick()
        rule.onNodeWithText("새로운 곳에 처음 갔어").assertExists()
    }
}
