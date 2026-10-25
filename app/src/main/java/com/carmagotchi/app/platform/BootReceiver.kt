package com.carmagotchi.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 차 부팅·앱 업데이트 뒤 서비스를 시작한다. 저장된 펫이 있을 때만(첫 실행 전에는 수집하지 않는다). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (PetRuntime.hasSavedPet(context)) PetService.start(context)
    }
}
