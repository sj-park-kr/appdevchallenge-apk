package com.carmagotchi.app.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.carmagotchi.app.PetController
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1133dp-h808dp-mdpi")
class ScreensTestReliability {
    @get:Rule val rule = createComposeRule()

    @Test fun speedPermissionDenialIsReportedWithoutCrashing() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val shadow = org.robolectric.Shadows.shadowOf(app)
        shadow.denyPermissions("android.car.permission.CAR_SPEED")
        org.junit.Assert.assertFalse(com.carmagotchi.app.platform.speedPermissionGranted(app))
        shadow.grantPermissions("android.car.permission.CAR_SPEED")
        org.junit.Assert.assertTrue(com.carmagotchi.app.platform.speedPermissionGranted(app))
    }

    @Test fun disablingLocationClearsCacheAndIgnoresLateUpdates() {
        val source = com.carmagotchi.app.platform.VehicleSignalSource(org.robolectric.RuntimeEnvironment.getApplication(), java.time.Clock.systemUTC())
        source.update { it.copy(locAddress = "강원 강릉시", destName = "경포해수욕장", destAddress = "강원 강릉시", remainM = 100) }
        org.junit.Assert.assertNotNull(source.current().destName)
        source.locationOn = false
        source.update { it.copy(locAddress = "강원 강릉시", destName = "경포해수욕장", remainM = 100) }
        org.junit.Assert.assertNull(source.current().locAddress)
        org.junit.Assert.assertNull(source.current().destName)
        org.junit.Assert.assertNull(source.current().destAddress)
        org.junit.Assert.assertNull(source.current().remainM)
    }

    @Test fun failedSaveOffersRetryAndDisappearsAfterSuccess() {
        val ctl = FakePetController(Samples.room().copy(saveFailed = true, reduceMotion = true, pendingEarlierScene = null))
        rule.setContent { AppRoot(ctl) }
        rule.onNodeWithText("아직 저장하지 못했어요").assertExists()
        rule.onNodeWithText("저장 다시 시도").performClick()
        rule.onNodeWithText("아직 저장하지 못했어요").assertDoesNotExist()
    }

    @Test fun drivingHidesShareAndRetryUntilParkedAgain() {
        val state = MutableStateFlow(Samples.room().copy(saveFailed = true, reduceMotion = true))
        val ctl = object : PetController by FakePetController(state.value) { override val state = state }
        rule.setContent { AppRoot(ctl, startOverlay = "share:m2") }
        rule.onNodeWithText("공유 카드").assertExists()
        rule.runOnIdle { state.value = state.value.copy(isParked = false) }
        rule.onNodeWithText("공유 카드").assertDoesNotExist()
        rule.onNodeWithText("저장 다시 시도").assertDoesNotExist()
        rule.runOnIdle { state.value = state.value.copy(isParked = true) }
        rule.onNodeWithText("공유 카드").assertExists()
        rule.onNodeWithText("저장 다시 시도").assertExists()
    }

    @Test fun deletingPlacesRequiresConfirmationAndUpdatesSettings() {
        val ctl = FakePetController(Samples.room().copy(reduceMotion = true))
        rule.setContent { ChamaTheme(false, true) { SettingsScreen(ctl.state.collectAsState().value, ctl, onBack = {}) } }
        rule.onNodeWithText("데이터·개인정보").performClick()
        rule.onNodeWithText("장소 정보 모두 지우기").performScrollTo().performClick()
        rule.onNodeWithText("장소 정보를 모두 지울까요?").assertExists()
        rule.onNodeWithText("지우기").performClick()
        rule.runOnIdle {
            org.junit.Assert.assertFalse(ctl.state.value.settings.locationOn)
            org.junit.Assert.assertTrue(ctl.state.value.pet!!.places.isEmpty())
            org.junit.Assert.assertTrue(ctl.state.value.memories.none { it.place != null })
        }
    }
}
