package com.carmagotchi.app.domain

/** PRD F16 모습 꾸미기. 부위별 값과 한글 라벨. PetState.look이 null이면 VIN 처음 모습(carLookFromVin)이다. */
enum class BodyShape(val label: String) { SEDAN("세단형"), SUV("SUV형"), HATCH("해치백형"), MINI("경차형") }
enum class Rim(val label: String) { DOT("점"), STAR("별"), FLOWER("꽃"), RING("고리") }
enum class Mirror(val label: String) { NONE("없음"), ROUND("동글"), SQUARE("네모"), TEAR("물방울") }
enum class Antenna(val label: String) { FIN("샤크핀"), ROD("막대"), DOME("돔") }
enum class EyeShape(val label: String) { PILL("알약"), ROUND("동글"), SQUARE("네모"), SLANT("눈꼬리") }
enum class CheekColor(val label: String) { AMBER("호박"), ORANGE("주황"), CORAL("산호"), ICE("얼음") }

data class CarLook(
    val body: BodyShape = BodyShape.SEDAN,
    val length: Int = 2,            // 0..4
    val color: Int = 0,             // 0..11 (P0~P11)
    val pattern: Int = 0,           // 0..5
    val wheelSize: Int = 1,         // 0..2
    val rim: Rim = Rim.DOT,
    val mirror: Mirror = Mirror.ROUND,
    val antenna: Antenna = Antenna.FIN,
    val eyes: EyeShape = EyeShape.PILL,
    val cheek: CheekColor = CheekColor.AMBER,
    val ride: Int = 0,              // 0 기본 / 1 낮춤 / 2 리프트업
    val wing: Int = 0,              // 0 없음 / 1 립 스포일러 / 2 GT 윙
) {
    companion object {
        val LENGTH_LABELS = listOf("아주 짧게", "짧게", "보통", "길게", "아주 길게")
        val COLOR_NAMES = listOf("크림", "세이지", "스카이", "라벤더", "피치", "민트", "코코아", "슬레이트", "실버", "펄 화이트", "웜 그레이", "아쿠아")
        val WHEEL_LABELS = listOf("작게", "보통", "크게")
        val RIDE_LABELS = listOf("기본", "낮춤", "리프트업")
        val WING_LABELS = listOf("없음", "립 스포일러", "GT 윙")
    }
}

/** 범위 밖 값을 가장 가까운 값으로 맞춘다(저장 전 검증). */
fun CarLook.clamped() = copy(
    length = length.coerceIn(0, 4), color = color.coerceIn(0, 11), pattern = pattern.coerceIn(0, 5),
    wheelSize = wheelSize.coerceIn(0, 2), ride = ride.coerceIn(0, 2), wing = wing.coerceIn(0, 2),
)

private fun pick(h: String, part: String, n: Int) = (seededRandom(h, "look", part) * n).toInt().coerceAtMost(n - 1)

/** VIN 처음 모습. 색·무늬는 기존 appearance를 이어받아 먼저 부화한 펫의 겉모습이 바뀌지 않는다. */
fun carLookFromVin(vinSeedHash: String, a: Appearance) = CarLook(
    body = BodyShape.entries[pick(vinSeedHash, "body", 4)],
    color = a.palette, pattern = a.pattern,
    rim = Rim.entries[pick(vinSeedHash, "rim", 4)],
    mirror = Mirror.entries[1 + pick(vinSeedHash, "mirror", 3)],
    cheek = if (a.palette == 4 || a.palette == 6) CheekColor.ORANGE else CheekColor.AMBER,
)

fun PetState.carLook() = look ?: carLookFromVin(vinSeedHash, appearance)
