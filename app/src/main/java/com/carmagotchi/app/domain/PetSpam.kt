package com.carmagotchi.app.domain

/** 쓰다듬기 연타 판정(순수, 메모리만). 버튼·문지르기 한 번이 한 번이다. 걸리면 쿨다운 동안은 귀찮아하기만 하고 기분은 더 깎지 않는다. */
class PetSpam {
    enum class Result { NONE, TRIGGER, COOLDOWN }

    private val hits = ArrayDeque<Long>()
    private var coolUntilMs = Long.MIN_VALUE

    fun hit(nowMs: Long): Result {
        if (nowMs < coolUntilMs) return Result.COOLDOWN
        hits.addLast(nowMs)
        while (nowMs - hits.first() > Const.PET_SPAM_WINDOW_MS) hits.removeFirst()
        if (hits.size < Const.PET_SPAM_COUNT) return Result.NONE
        hits.clear()
        coolUntilMs = nowMs + Const.PET_SPAM_COOLDOWN_SEC * 1000L
        return Result.TRIGGER
    }
}
