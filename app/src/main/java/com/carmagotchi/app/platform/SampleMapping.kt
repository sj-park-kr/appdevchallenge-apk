package com.carmagotchi.app.platform

import com.carmagotchi.app.domain.AilmentType
import com.carmagotchi.app.domain.Content
import com.carmagotchi.app.domain.EnergyProfile
import com.carmagotchi.app.domain.Gear
import com.carmagotchi.app.domain.PlaceInfo
import com.carmagotchi.app.domain.PlaceKind
import com.carmagotchi.app.domain.Seat
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

/** SDK 원시 값 -> 도메인 값. SDK 타입을 받지 않고(이름 문자열·숫자만) JVM 테스트로 확인한다. */
object SampleMapping {
    /** AAOS android.car.VehicleGear 값. 0(UNKNOWN)은 null: 이전 값을 유지한다. */
    fun gearOfAaos(v: Int): Gear? = when (v) {
        4 -> Gear.P
        2 -> Gear.R
        1 -> Gear.N
        8 -> Gear.D
        0 -> null
        else -> if (v >= 16) Gear.D else null // GEAR_FIRST(16)... 수동 단수는 D로
    }

    /** Pleos VehicleGear enum 이름. */
    fun gearOfPleos(name: String): Gear? = when (name) {
        "GEAR_PARK" -> Gear.P
        "GEAR_REVERSE" -> Gear.R
        "GEAR_NEUTRAL" -> Gear.N
        "GEAR_DRIVE" -> Gear.D
        "GEAR_UNKNOWN" -> null
        else -> if (name.startsWith("GEAR_")) Gear.D else null // GEAR_1..9
    }

    /** android.car.VehicleIgnitionState: LOCK(1), OFF(2), ACC(3)은 꺼짐, ON(4), START(5)는 켜짐. UNDEFINED(0)은 null. */
    fun ignitionOn(v: Int): Boolean? = when (v) { 1, 2, 3 -> false; 4, 5 -> true; else -> null }

    /**
     * 에뮬레이터 확인: Pleos SDK의 SeatOccupancy Boolean은 값==1일 때 true인데, AAOS VehicleSeatOccupancyState는 VACANT=1, OCCUPIED=2다.
     * Virtual Console의 "Occupied"(값 2)가 SDK에서 false로 온다. 그래서 뒤집어 쓴다. 실차 동작은 확인하지 못했다.
     */
    fun occupied(sdk: Boolean) = !sdk

    fun kmh(mps: Float): Double = mps * 3.6

    fun seatOfPleos(name: String): Seat? = when (name) {
        "ROW_1_RIGHT" -> Seat.FRONT
        "ROW_2_LEFT" -> Seat.REAR_LEFT
        "ROW_2_CENTER" -> Seat.REAR_MIDDLE
        "ROW_2_RIGHT" -> Seat.REAR_RIGHT
        else -> null // 운전석(ROW_1_LEFT)은 구독하지 않는다
    }

    /** getTelltalesInfo의 (TelltaleType 이름, TelltaleState 이름) 목록 -> 아픔. OFF가 아니면 켜진 것. */
    fun telltaleAilments(items: List<Pair<String, String>>): Set<AilmentType> = items.mapNotNull { (type, state) ->
        if (state == "OFF") null else when (type) {
            "TPMS" -> AilmentType.TPMS
            "AIRBAG" -> AilmentType.AIRBAG
            "POWER_DOWN" -> AilmentType.POWER_DOWN
            else -> null // LOW_FUEL, ENGINE_OIL_PRESSURE 등은 쓰지 않는다(PRD 7.6)
        }
    }.toSet()

    /** OilLevelState 이름이 LOW, CRITICALLY_LOW이면 부족. */
    fun oilLow(state: String) = state == "LOW" || state == "CRITICALLY_LOW"

    /** AAOS INFO_FUEL_TYPE(android.car.FuelType): ELECTRIC=10. 전기만이면 EV, 전기와 다른 연료가 함께면 PHEV, 못 읽으면 FUEL. */
    fun energyProfile(fuelTypes: List<Int>?): EnergyProfile = when {
        fuelTypes.isNullOrEmpty() -> EnergyProfile.FUEL
        fuelTypes.all { it == 10 } -> EnergyProfile.EV
        10 in fuelTypes -> EnergyProfile.PHEV
        else -> EnergyProfile.FUEL
    }

    /**
     * 장소 키(PRD 9.2). 안내 중이고 남은 거리가 0.3km 이하면 목적지 이름, 아니면 시·군·구 이름.
     * 목적지가 바다·산·휴게소가 아니면 종류는 REGION(일반). address는 "시·도 시·군·구 ..." 형태를 가정한다. 에뮬레이터는 "수정구 시흥동"처럼 시·도 없이 준다(그 구 이름이 시·도 칸에 들어간다).
     */
    fun placeOf(locAddress: String?, destName: String?, destAddress: String?, remainM: Int?): PlaceInfo? {
        val tokens = (locAddress ?: destAddress)?.trim()?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
        val province = tokens.firstOrNull()
        val city = tokens.getOrNull(1)?.takeIf { it.last() in "시군구" }
        val region = city?.let { "$province $it" } ?: province
        val remainKm = remainM?.let { it / 1000.0 }
        val arrivedAtDest = !destName.isNullOrBlank() && remainM != null && remainM <= 300
        return when {
            arrivedAtDest -> PlaceInfo(destName!!, region ?: destName, province ?: "기타", Content.classifyPlace(destName) ?: PlaceKind.REGION, remainKm)
            province != null -> PlaceInfo(city ?: province, region!!, province, PlaceKind.REGION, remainKm)
            // 주소를 못 받으면 목적지 이름만 쓰고 시·도는 "기타"로 둔다(지역 스탬프 꺼짐)
            else -> destName?.takeIf { it.isNotBlank() }?.let { PlaceInfo(it, it, "기타", Content.classifyPlace(it) ?: PlaceKind.REGION, remainKm) }
        }
    }

    /** VIN 해시(PRD F11 3번). VIN 원문은 저장하지 않는다. 못 읽으면 무작위 16바이트. */
    fun vinSeedHash(vin: String?, salt: String = "chama-v1"): String {
        val src = if (vin.isNullOrBlank()) SecureRandom().let { r -> ByteArray(16).also(r::nextBytes) } else (vin + salt).toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(src).joinToString("") { "%02x".format(it) }
    }
}

/** 경고가 onSec초 이어서 켜져 있어야 통과(점등 자가 점검 오탐 방지). 사고(CRASH)는 바로. */
class WarnFilter(var onSec: Double = 10.0) {
    private val since = mutableMapOf<AilmentType, Instant>()

    fun filter(active: Set<AilmentType>, at: Instant): Set<AilmentType> {
        since.keys.retainAll(active)
        active.forEach { since.getOrPut(it) { at } }
        return active.filterTo(mutableSetOf()) {
            it == AilmentType.CRASH || Duration.between(since.getValue(it), at).toMillis() >= onSec * 1000
        }
    }
}
