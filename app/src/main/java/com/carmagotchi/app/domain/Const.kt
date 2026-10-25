package com.carmagotchi.app.domain

/** PRD 7.1 상수 표. 모두 (가정)이며 실차·관찰로 고친다. */
object Const {
    const val DAY_BOUNDARY_HOUR = 4
    const val TRIP_MERGE_SEC = 120
    const val MICRO_TRIP_KM = 0.3
    const val MICRO_TRIP_SEC = 180
    const val COUNTED_TRIP_KM = 1.0
    const val COUNTED_TRIP_SEC = 300
    const val CHECKPOINT_SEC = 60
    const val MOOD_BASE = 60
    const val MOOD_MIN = 25
    const val MOOD_MAX = 100
    const val MOOD_DRIFT_PER_HOUR = 0.9
    const val LOW_SPEED_IGNORE_KMH = 5.0
    const val HB_DROP_KMH = 12.0
    const val HB_WINDOW_SEC = 1.0
    const val HB_MIN_SPEED_KMH = 20.0
    const val HB_DEBOUNCE_SEC = 10
    const val ST_LAT_ACC_MS2 = 4.0
    const val ST_HOLD_SEC = 0.5
    const val ST_MIN_SPEED_KMH = 20.0
    const val ST_DEBOUNCE_SEC = 10
    const val WHEELBASE_M = 2.8
    const val STEERING_RATIO = 14.0
    const val EMU_STEER_DELTA_DEG = 90.0
    const val EMU_STEER_WINDOW_SEC = 1.0
    const val EMU_BRAKE_PCT = 80.0
    const val SMOOTH_MIN_SEC = 600
    const val RAIN_MIN_SEC = 60
    const val RAIN_SEGMENT_MIN_SEC = 10
    const val NIGHT_RATIO = 0.5
    const val NIGHT_MIN_SEC = 300
    const val NIGHT_WINDOW_FROM_HOUR = 17 // 17:00~07:00
    const val NIGHT_WINDOW_TO_HOUR = 7
    const val SNOW_TEMP_C = 1.0
    const val FOG_MIN_SEC = 180
    const val BREEZE_WINDOW_PCT = 30.0
    const val BREEZE_MIN_SEC = 300
    const val PASSENGER_STABLE_SEC = 10
    const val REFUEL_PP = 10.0
    const val REFILL_RESET_FROM_KM = 5.0
    const val REFILL_RESET_TO_KM = 1.0
    const val RECHARGE_PP = 5.0
    const val ENERGY_DEBOUNCE_MIN = 30
    const val HUNGER_EXIT_MARGIN_PP = 5.0
    const val ENERGY_MEDIAN_SEC = 10
    const val WARN_CLEAR_SEC = 30
    const val AILMENT_DAILY_DAYS = 7
    const val AILMENT_SPARSE_EVERY_D = 3
    const val REST_DRIVE_MIN = 60
    const val REST_STOP_MIN = 5
    const val REST_DEST_REMAIN_KM = 3.0
    const val REST_RETRO_MAX_MIN = 60
    const val REST_DAILY_MAX = 3
    const val LONG_ABSENCE_H = 72
    const val VERY_LONG_ABSENCE_D = 30
    const val MILESTONE_KM = 1000
    const val TRAIT_WINDOW = 30
    const val TRAIT_MIN_TRIPS = 10
    const val LONG_TRIP_MIN = 40
    const val LONG_TRIP_KM = 30.0
    const val AFFECTION_DAILY_CAP = 3
    const val SNACK_MAX = 5
    const val PET_SPAM_COUNT = 5 // 쓰다듬기를 PET_SPAM_WINDOW_MS 안에 이만큼 누르면 펫이 귀찮아한다
    const val PET_SPAM_WINDOW_MS = 3000L
    const val PET_SPAM_MOOD = -5
    const val PET_SPAM_COOLDOWN_SEC = 30
    const val SNACK_DAILY = 2 // 하루 첫 세션에 받는 간식(주머니 SNACK_MAX까지)
    const val VISITOR_DAILY_MAX = 2
    const val VISITOR_REVISIT_P = 0.5
    const val ALBUM_DAILY_MAX = 3
    const val ARRIVE_LINE_MAX = 20
    const val DEPART_LINE_MAX = 24
    const val NO_REPEAT_DAYS = 7
    const val CRASH_QUIET_H = 24
    const val GUEST_MODE_H = 24

    // 7.7 단계 기준 (운전한 날 수)
    const val BABY_DAYS = 7
    const val CHILD_DAYS = 30
    const val ADULT_DAYS = 100
    const val TRIPS_KEPT = 200

    // 경험치(XP): 값은 여기서만 고친다. 쓰다듬기 50번 = 첫 부화(BABY), 뒤로 갈수록 단계가 길다
    const val XP_PET = 50
    const val XP_SNACK = 30
    const val XP_THANKS = 20
    const val XP_ROAM_PER_SCREEN = 40 // 월드에서 화면 폭 하나 이동
    const val XP_ROAM_MAX = 100 // 이동 한 번당 상한
    const val ROAM_MIN_SCREEN = .05f // 이보다 짧은 이동은 XP 없음(제자리 연타 방지)
    const val XP_CARE = 100 // 주유·충전·경고 회복
    const val XP_TICK_SEC = 10 // 앱을 켜 둔 동안 이 간격마다
    const val XP_TICK_OPEN = 1
    const val XP_TICK_DRIVE = 2 // 주행 중
    /** 단계 EGG, BABY, CHILD, ADULT, BUDDY에 닿는 누적 XP */
    val XP_AT = intArrayOf(0, 2_500, 8_500, 23_500, 53_500)
    fun stageOfXp(xp: Int) = Stage.values().last { xp >= XP_AT[it.ordinal] }
    /** 다음 단계에 필요한 누적 XP. 마지막 단계면 null */
    fun xpNext(s: Stage) = XP_AT.getOrNull(s.ordinal + 1)

    /** 7.1 HEART_GOAL: 단계마다 애정 하트 5개가 가득 차는 애정 양 */
    fun heartGoal(s: Stage) = when (s) { Stage.EGG -> 10; Stage.BABY -> 35; Stage.CHILD -> 100; Stage.ADULT -> 60; Stage.BUDDY -> 100 }
}

/** PRD F14 주행 중 동행 소리 상수. */
object SoundConst {
    const val START_DELAY_SEC = 30
    const val START_WINDOW_SEC = 120
    const val MAX_PER_TRIP = 3
    const val MIN_GAP_SEC = 300
    const val AFTER_EVENT_QUIET_SEC = 10
    const val CALM_EVERY_MIN = 30
    const val PENDING_MAX_SEC = 600
    const val NIGHT_FROM_HOUR = 22
    const val NIGHT_TO_HOUR = 6
    const val MAX_MS = 1000
    const val SFX_DEFAULT_ON = true
    const val SFX_PET_MIN_GAP_MS = 300L
    const val VROOM_MIN_GAP_MS = 1500L
}
