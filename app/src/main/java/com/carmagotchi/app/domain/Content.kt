package com.carmagotchi.app.domain

import java.time.LocalDate

/** PRD 9.1 길동무 조건 입력. 속도·거리·주행 시간은 하나도 없다. */
data class VisitCtx(
    val day: LocalDate,
    val startHour: Int,
    val rain: Boolean = false,
    val snow: Boolean = false,
    val fog: Boolean = false,
    val night: Boolean = false,
    val breeze: Boolean = false,
    val lowBeam: Boolean = false,
    val passengers: Int = 0,
    val newPlace: Boolean = false,
    val rest: Boolean = false,
    val placeKind: PlaceKind? = null,
    val locationOn: Boolean = true,
    val windowSupported: Boolean = true,
    val fogSupported: Boolean = true,
    val tempSupported: Boolean = true,
) {
    val month get() = day.monthValue
    val daytime get() = startHour in 7..16
}

enum class Rarity { COMMON, UNCOMMON, RARE }

class Visitor(val id: String, val name: String, val rarity: Rarity, val hint: String, val cond: (VisitCtx) -> Boolean)

object Content {
    // 신호가 없을 때 대체 조건(9.1 표). HVAC 신호는 정의돼 있지 않아 VIS-05 대체 조건은 계절·낮만 쓴다. PRD 확인 필요.
    val VISITORS = listOf(
        Visitor("VIS-01", "달팽이 느릿", Rarity.COMMON, "비 오는 길을 좋아해요") { it.rain },
        Visitor("VIS-02", "참새 짹짹이", Rarity.COMMON, "북적북적한 차를 좋아해요") { it.passengers >= 2 },
        Visitor("VIS-03", "반딧불 깜빡이", Rarity.UNCOMMON, "여름밤에 나타나요") { it.night && it.month in 6..8 },
        Visitor("VIS-04", "고슴도치 밤송이", Rarity.UNCOMMON, "가을밤 길가에 살아요") { it.night && it.month in 9..11 },
        Visitor("VIS-05", "바람 강아지 살랑", Rarity.UNCOMMON, "창문 너머 바람을 좋아해요") {
            if (it.windowSupported) it.breeze else it.month in 4..10 && it.daytime
        },
        Visitor("VIS-06", "꿀벌 붕붕", Rarity.UNCOMMON, "꽃 피는 봄날에 와요") { Seasons.cherry(it.day) && it.daytime },
        Visitor("VIS-07", "갈매기 끼룩", Rarity.UNCOMMON, "바다 냄새를 따라와요") { it.placeKind == PlaceKind.SEA },
        Visitor("VIS-08", "다람쥐 도토리", Rarity.UNCOMMON, "숲과 산을 좋아해요") {
            if (it.locationOn) it.placeKind == PlaceKind.MOUNTAIN else it.month in 9..11 && it.daytime
        },
        Visitor("VIS-09", "부엉이 부엉박사", Rarity.UNCOMMON, "쉬어 가는 차를 좋아해요") { it.rest },
        Visitor("VIS-10", "안개 고양이 뽀얌", Rarity.RARE, "뿌연 아침에 살짝 나타나요") {
            if (it.fogSupported) it.fog else it.month in listOf(11, 12, 1, 2) && it.startHour in 7..8 && it.lowBeam
        },
        Visitor("VIS-11", "눈꽃 요정 소복이", Rarity.RARE, "하얀 눈길에서 만나요") {
            if (it.tempSupported) it.snow else it.month in listOf(12, 1, 2) && it.rain
        },
        Visitor("VIS-12", "별똥별 반짝", Rarity.RARE, "처음 가는 밤길에 떨어져요") {
            if (it.locationOn) it.night && it.newPlace else it.night && Seasons.holiday(it.day)
        },
    )

    enum class ItemKind { DECOR, SEASON_DECOR, CLOTHES }
    class Item(val id: String, val name: String, val kind: ItemKind)

    val ITEMS = listOf(
        Item("ITEM-01", "작은 우산", ItemKind.DECOR), Item("ITEM-02", "별 무드등", ItemKind.DECOR),
        Item("ITEM-03", "든든 밥그릇", ItemKind.DECOR), Item("ITEM-04", "회복 리본", ItemKind.DECOR),
        Item("ITEM-05", "친구 방석", ItemKind.DECOR), Item("ITEM-06", "조개껍데기", ItemKind.DECOR),
        Item("ITEM-07", "솔방울", ItemKind.DECOR), Item("ITEM-08", "호두과자 쿠션", ItemKind.DECOR),
        Item("ITEM-09", "1,000km 깃발", ItemKind.DECOR), Item("ITEM-10", "생일 케이크", ItemKind.DECOR),
        Item("ITEM-11", "벚꽃 화분", ItemKind.SEASON_DECOR), Item("ITEM-12", "장마 장화", ItemKind.SEASON_DECOR),
        Item("ITEM-13", "단풍잎 액자", ItemKind.SEASON_DECOR), Item("ITEM-14", "눈송이 모빌", ItemKind.SEASON_DECOR),
        Item("ITEM-15", "색동 한복", ItemKind.CLOTHES), Item("ITEM-16", "복주머니", ItemKind.SEASON_DECOR),
        Item("ITEM-17", "송편 쿠션", ItemKind.SEASON_DECOR), Item("ITEM-18", "네잎클로버", ItemKind.DECOR),
        Item("ITEM-19", "반짝 병뚜껑", ItemKind.DECOR), Item("ITEM-20", "깃털 펜", ItemKind.DECOR),
    )
    val FIND_ITEMS = listOf("ITEM-18", "ITEM-19", "ITEM-20")

    /** 9.2 지역 간식 (시·도 첫 방문). 키는 시·도 이름. */
    val REGION_SNACKS = mapOf(
        "서울" to "떡볶이", "부산" to "어묵", "대구" to "납작만두", "인천" to "쫄면", "광주" to "떡갈비", "대전" to "칼국수",
        "세종" to "복숭아", "경기" to "쌀강정", "강원" to "감자", "충북" to "옥수수", "충남" to "호두", "울산" to "배",
        "전북" to "비빔밥", "전남" to "녹차", "경북" to "사과", "경남" to "단감", "제주" to "귤",
    )

    // 오판 예: 도시 이름 "부산·울산·안산·군산·아산·서산"은 산으로 보지 않는다. 그 밖의 오판은 실데이터로 보강한다.
    private val SEA = Regex("해수욕장|해변|바다|포구|해안|항$")
    private val MOUNTAIN = Regex("휴양림|수목원|계곡|국립공원|(?<!부|울|안|군|아|서)산(\\s|$)")
    private val REST_AREA = Regex("휴게소|졸음쉼터")

    fun classifyPlace(name: String): PlaceKind? = when {
        REST_AREA.containsMatchIn(name) -> PlaceKind.REST_AREA
        SEA.containsMatchIn(name) -> PlaceKind.SEA
        MOUNTAIN.containsMatchIn(name) -> PlaceKind.MOUNTAIN
        else -> null
    }
}

/** PRD 9.4 계절·기념 달력. 음력 명절은 PRD가 적은 날짜만 넣었다(2026~2030 표는 월력요항으로 채워야 함: PRD 확인 필요). */
object Seasons {
    private fun md(d: LocalDate) = d.monthValue * 100 + d.dayOfMonth
    private fun between(d: LocalDate, a: LocalDate, b: LocalDate) = !d.isBefore(a) && !d.isAfter(b)
    private fun period(d: LocalDate, from: Int, to: Int) = md(d) in from..to

    fun cherry(d: LocalDate) = period(d, 325, 415)
    fun monsoon(d: LocalDate) = period(d, 620, 731)
    fun autumn(d: LocalDate) = period(d, 1015, 1115)
    fun winter(d: LocalDate) = d.monthValue >= 11 || d.monthValue <= 3

    private fun chuseok(d: LocalDate) =
        between(d, LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 26)) ||
            between(d, LocalDate.of(2027, 9, 14), LocalDate.of(2027, 9, 16))
    private fun seollal(d: LocalDate) = between(d, LocalDate.of(2027, 2, 5), LocalDate.of(2027, 2, 7))

    fun holiday(d: LocalDate) = chuseok(d) || seollal(d) || period(d, 1224, 1225) || md(d) == 1231 || md(d) == 101

    /** 날짜형 이벤트 ID와 따라오는 물건. 그날 첫 시동(M1)에서 연다. */
    fun dateEvents(d: LocalDate, hatchDay: LocalDate): List<Pair<String, List<String>>> = buildList {
        if (md(d) == 505) add("SE-CHILDREN" to emptyList())
        if (period(d, 725, 815)) add("SE-SUMMER" to emptyList())
        if (chuseok(d)) add("SE-CHUSEOK" to listOf("ITEM-15", "ITEM-17"))
        if (seollal(d)) add("SE-SEOLLAL" to listOf("ITEM-15", "ITEM-16"))
        if (period(d, 1224, 1225)) add("SE-XMAS" to emptyList())
        if (md(d) == 101) add("SE-NEWYEAR" to emptyList())
        if (d == hatchDay.plusDays(99)) add("SE-ANNIV-100" to emptyList())
    }
}
