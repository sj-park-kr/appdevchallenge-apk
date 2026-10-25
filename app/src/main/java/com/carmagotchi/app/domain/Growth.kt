package com.carmagotchi.app.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.time.temporal.ChronoUnit

/** SC-ROOM 자람 카드가 그릴 값(PRD F4 9·10번). 숫자는 날 수 둘뿐이고 레벨·경험치·% 는 없다. lineId는 DLG-SYS-* 의 뒷부분. */
data class GrowthView(val stage: Stage, val nextLabel: String, val done: Int, val total: Int, val lineId: String, val hearts: Float)

object Growth {
    fun of(p: PetState, now: Instant, zone: ZoneId = ZoneId.of("Asia/Seoul")): GrowthView {
        val day = dayKey(now, zone)
        val b = p.carBirthday
        val hearts = hearts(p)
        fun days(label: String, total: Int, next: String) =
            GrowthView(p.stage, label, p.daysTogether.coerceIn(0, total), total, if (p.daysTogether == total - 1) "GROW-NEXT-$next" else "GROW-LINE", hearts)
        return when (p.stage) {
            Stage.EGG -> days("아기까지", Const.BABY_DAYS, "BABY")
            Stage.BABY -> days("어린이까지", Const.CHILD_DAYS, "CHILD")
            Stage.CHILD -> days("어른까지", Const.ADULT_DAYS, "ADULT") // 차 생일이 먼저 지나도 어른까지만 보인다
            Stage.ADULT -> anniv(p, "단짝까지", b, b.plusYears(1), day, "GROW-BUDDY", hearts)
            Stage.BUDDY -> {
                var from = b.plusYears(1)
                while (!from.plusYears(1).isAfter(day)) from = from.plusYears(1)
                anniv(p, "다음 차 생일까지", from, from.plusYears(1), day, "GROW-ANNIV", hearts)
            }
        }
    }

    private fun anniv(p: PetState, label: String, from: LocalDate, to: LocalDate, day: LocalDate, line: String, hearts: Float): GrowthView {
        val total = ChronoUnit.DAYS.between(from, to).toInt()
        return GrowthView(p.stage, label, ChronoUnit.DAYS.between(from, day).toInt().coerceIn(0, total), total, line, hearts)
    }

    /** 0.0~5.0. v는 이번 단계에 쌓은 애정이고 애정은 줄지 않으니 같은 단계 안에서 하트도 줄지 않는다. */
    fun hearts(p: PetState): Float {
        val v = (p.affection + p.affectionOverflow - p.heartBase).coerceAtLeast(0)
        return minOf(5f, 5f * v / Const.heartGoal(p.stage))
    }
}
