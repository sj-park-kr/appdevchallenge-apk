package com.carmagotchi.app.ui.pet

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** 방 안 이동 중인 펫의 값(방 화면 안에서만, 저장 안 함). 발 x는 dp, 방향 반전은 화면 층이 맡고 여기서는 바퀴·기울기·통통만 얹는다(BUILD-GUIDE 9.11). */
@Stable
class PetMove(val home: Float, homeY: Float = 0f) {
    enum class Mode { IDLE, DRIVE, DRAG }
    class Puff(val xDp: Float, val startMs: Long, val yDp: Float = 0f)

    val foot = Animatable(home)
    /** 월드 안 발 y(dp, 바퀴 닿는 선). 상하 이동 */
    val footY = Animatable(homeY)
    /** 깜빡이: 화면 기준 -1 왼쪽, +1 오른쪽, 0 꺼짐(좌우로 방향을 틀 때만) */
    var blink by mutableIntStateOf(0)
    var heading by mutableStateOf(Heading.FRONT)
    /** 진행 방향으로 기운 정도(그림 좌표 도). 그림이 왼쪽을 보니 앞으로 숙이면 음수 */
    val lean = Animatable(0f)
    /** 멈춤 흔들림·놓은 뒤 튀기의 진행(ms). 300이면 끝 */
    val settle = Animatable(300f)
    var settleBounce = false
    var mode by mutableStateOf(Mode.IDLE)
    /** -1 왼쪽, +1 오른쪽. 기본 그림이 왼쪽을 본다 */
    var facing by mutableIntStateOf(-1)
    var puffs by mutableStateOf(emptyList<Puff>())
    var rwDp = 8f
    /** 에너지 부족으로 느리게 가는 중: 통통을 느리고 작게 */
    var tired = false
    var job: Job? = null

    fun applyTo(l: PetLook, tMs: Long): PetLook {
        var r = l.copy(heading = heading, lamp = blink != 0 && (tMs / 300L) % 2L == 0L, wheelRad = l.wheelRad - facing * foot.value / rwDp, tilt = l.tilt + lean.value)
        if (mode == Mode.DRIVE) r = if (tired) r.copy(dy = r.dy - .004f * abs(sin(PI.toFloat() * tMs / 240f))) else r.copy(dy = r.dy - .008f * abs(sin(PI.toFloat() * tMs / 120f)))
        val s = settle.value
        if (s < 300f) {
            val p = s / 300f
            r = if (settleBounce) r.copy(squashY = r.squashY * (.94f + .06f * p), dy = r.dy - .02f * (1f - p) * (1f - p))
            else r.copy(tilt = r.tilt + if (p < .5f) 3f - 10f * p else -2f + 4f * (p - .5f), squashY = r.squashY * (.96f + .04f * p))
        }
        return r
    }
}
