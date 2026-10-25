package com.carmagotchi.app.gleo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.carmagotchi.app.platform.PetRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Gleo가 sendBroadcast(component=service)로 부르는 함수 3개를 받아 7초 안에(5초 넘으면 fail) 응답 broadcast를 보낸다. 화면은 띄우지 않는다. */
class GleoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val fn = intent.action?.takeIf { it in GLEO_FUNCTIONS } ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            var answer = GleoAnswer(fn, "fail", "An unexpected internal error occurred.", "지금은 대답하기 어려워요. 차를 세우면 만나요.")
            try {
                val rt = PetRuntime.get(context)
                val param = readGleoParam({ intent.getStringExtra(it) }, GLEO_PARAM_KEY.getValue(fn))
                answer = withTimeoutOrNull(5_000) { rt.gleo(fn, param) } ?: rt.gleoAnswers.fail(fn, timeout = true)
            } catch (e: Exception) {
                Log.w(TAG, "$fn error ${e.javaClass.simpleName}")
            }
            respond(context, answer)
            Log.i(TAG, "$fn ${answer.status}") // 이름·장소 같은 내용은 남기지 않는다
            pending.finish()
        }
    }

    private fun respond(context: Context, a: GleoAnswer) {
        // 응답 action은 문서 샘플 그대로. 에뮬레이터 확인: Gleo(GAA)가 APP_ACTION_RESPONSE를 받아 파싱한다
        context.sendBroadcast(
            Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")
                .putExtra("packageName", context.packageName)
                .putExtra("response", a.toJson()),
        )
    }

    private companion object { const val TAG = "ChamaGleo" }
}
