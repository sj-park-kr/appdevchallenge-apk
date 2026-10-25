package com.carmagotchi.app.platform

import com.carmagotchi.app.domain.AilmentType
import com.carmagotchi.app.domain.EnergyProfile
import com.carmagotchi.app.domain.Gear
import com.carmagotchi.app.domain.PlaceKind
import com.carmagotchi.app.domain.Seat
import com.carmagotchi.app.domain.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleMappingTest {
    @Test fun gear() {
        assertEquals(Gear.P, SampleMapping.gearOfAaos(4))
        assertEquals(Gear.D, SampleMapping.gearOfAaos(8))
        assertEquals(Gear.D, SampleMapping.gearOfAaos(16))
        assertNull(SampleMapping.gearOfAaos(0))
        assertEquals(Gear.P, SampleMapping.gearOfPleos("GEAR_PARK"))
        assertEquals(Gear.R, SampleMapping.gearOfPleos("GEAR_REVERSE"))
        assertEquals(Gear.D, SampleMapping.gearOfPleos("GEAR_3"))
        assertNull(SampleMapping.gearOfPleos("GEAR_UNKNOWN"))
    }

    @Test fun ignitionAndSpeed() {
        assertEquals(false, SampleMapping.ignitionOn(3))
        assertEquals(true, SampleMapping.ignitionOn(4))
        assertNull(SampleMapping.ignitionOn(0))
        assertEquals(36.0, SampleMapping.kmh(10f), 1e-6)
    }

    @Test fun seatsSkipDriver() {
        assertEquals(Seat.FRONT, SampleMapping.seatOfPleos("ROW_1_RIGHT"))
        assertEquals(Seat.REAR_LEFT, SampleMapping.seatOfPleos("ROW_2_LEFT"))
        assertNull(SampleMapping.seatOfPleos("ROW_1_LEFT"))
        assertTrue(SampleMapping.occupied(false) && !SampleMapping.occupied(true)) // SDK Boolean이 뒤집혀 온다
    }

    @Test fun telltales() {
        val on = SampleMapping.telltaleAilments(listOf("TPMS" to "ON", "ABS" to "OFF", "LOW_FUEL" to "ON", "POWER_DOWN" to "BLINK", "AIRBAG" to "OFF"))
        assertEquals(setOf(AilmentType.TPMS, AilmentType.POWER_DOWN), on)
        assertTrue(SampleMapping.telltaleAilments(listOf("TPMS" to "OFF")).isEmpty())
        assertTrue(SampleMapping.oilLow("CRITICALLY_LOW") && !SampleMapping.oilLow("NORMAL"))
    }

    @Test fun energyProfile() {
        assertEquals(EnergyProfile.EV, SampleMapping.energyProfile(listOf(10)))
        assertEquals(EnergyProfile.PHEV, SampleMapping.energyProfile(listOf(10, 1)))
        assertEquals(EnergyProfile.FUEL, SampleMapping.energyProfile(listOf(1)))
        assertEquals(EnergyProfile.FUEL, SampleMapping.energyProfile(null))
    }

    @Test fun place() {
        val region = SampleMapping.placeOf("강원특별자치도 강릉시 창해로", null, null, null)!!
        assertEquals("강릉시", region.label)
        assertEquals(PlaceKind.REGION, region.kind)
        val dest = SampleMapping.placeOf("강원 강릉시 안목해변", "경포해수욕장", "강원 강릉시", 100)!!
        assertEquals("경포해수욕장", dest.label)
        assertEquals(PlaceKind.SEA, dest.kind)
        val other = SampleMapping.placeOf("서울 강남구", "우리집", null, 100)!!
        assertEquals(PlaceKind.REGION, other.kind) // 바다·산·휴게소가 아닌 목적지는 일반
        assertNull(SampleMapping.placeOf(null, null, null, null))
    }

    @Test fun vinHashIsStableAndRandomWhenMissing() {
        assertEquals(SampleMapping.vinSeedHash("KMH123"), SampleMapping.vinSeedHash("KMH123"))
        assertTrue(SampleMapping.vinSeedHash(null) != SampleMapping.vinSeedHash(null))
    }

    @Test fun warnFilterDebounce() {
        val f = WarnFilter(10.0)
        val t = at("2026-10-25T09:00:00")
        val tpms = setOf(AilmentType.TPMS)
        assertTrue(f.filter(tpms, t).isEmpty())
        assertTrue(f.filter(tpms, t.plusSeconds(9)).isEmpty())
        assertEquals(tpms, f.filter(tpms, t.plusSeconds(10)))
        assertTrue(f.filter(emptySet(), t.plusSeconds(11)).isEmpty())
        assertTrue(f.filter(tpms, t.plusSeconds(12)).isEmpty()) // 다시 켜지면 처음부터
        assertEquals(setOf(AilmentType.CRASH), f.filter(setOf(AilmentType.CRASH), t.plusSeconds(13)))
    }
}
