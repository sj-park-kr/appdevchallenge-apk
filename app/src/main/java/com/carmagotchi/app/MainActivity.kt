package com.carmagotchi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import com.carmagotchi.app.platform.speedPermissionGranted
import com.carmagotchi.app.ui.AppRoot
import com.carmagotchi.app.ui.FakePetController
import com.carmagotchi.app.ui.Gallery
import com.carmagotchi.app.ui.LocalRequestSpeed
import com.carmagotchi.app.ui.Samples

class MainActivity : ComponentActivity() {
    // 디버그 빌드의 sample 인자가 있을 때만 예시 상태(FakePetController). 그 밖에는 모두 실제 런타임.
    private val fake by lazy { if (BuildConfig.DEBUG) intent.getStringExtra("sample")?.let { FakePetController(Samples.named(it)) } else null }
    private val real by lazy { RuntimePetController.get(this) }
    private var afterSpeed: (() -> Unit)? = null
    private val speedAsk = registerForActivityResult(ActivityResultContracts.RequestPermission()) { afterSpeed?.invoke(); afterSpeed = null } // 거부해도 진행(속도 없이 동작)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 디버그 빌드만: adb shell am start -n .../.MainActivity --es screen gallery|settings|share:m2|... --ei tab 1 --ei hatch 2
        val screen = if (BuildConfig.DEBUG) intent.getStringExtra("screen") else null
        val ctl: PetController = fake ?: real
        setContent {
            if (screen == "gallery") com.carmagotchi.app.ui.ChamaTheme { Gallery(intent.getIntExtra("page", 0)) }
            else CompositionLocalProvider(LocalRequestSpeed provides { next ->
                if (speedPermissionGranted(this)) next() else { afterSpeed = next; speedAsk.launch("android.car.permission.CAR_SPEED") }
            }) {
                AppRoot(ctl, startTab = if (BuildConfig.DEBUG) intent.getIntExtra("tab", 0) else 0, startOverlay = screen, startHatchStep = if (BuildConfig.DEBUG) intent.getIntExtra("hatch", 0) else 0)
            }
        }
    }

    // 소리는 화면이 resumed일 때만 나므로(F15) 보임 신호도 onResume·onPause에 건다. 화면이 꺼지면 onPause가 먼저 와서 소리가 바로 멈춘다
    override fun onResume() { super.onResume(); if (fake == null) real.setVisible(true) }
    override fun onPause() { if (fake == null) real.setVisible(false); super.onPause() }
}
