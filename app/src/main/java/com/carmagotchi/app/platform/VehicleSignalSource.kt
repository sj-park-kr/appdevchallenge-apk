package com.carmagotchi.app.platform

import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import ai.pleos.playground.vehicle.listener.PowerDownWarningIndicatorListener
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.carmagotchi.app.domain.AilmentType
import com.carmagotchi.app.domain.EnergyProfile
import com.carmagotchi.app.domain.Gear
import com.carmagotchi.app.domain.PlaceInfo
import com.carmagotchi.app.domain.Seat
import com.carmagotchi.app.domain.VehicleSample
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import ai.pleos.playground.vehicle.constant.control.VehicleGear as PleosGear

private const val TAG = "ChamaSignal"

/** 앱이 쓰는 권한 중 실행 중 허용이 필요한 것은 CAR_SPEED뿐이다(POST_NOTIFICATIONS 제외). UI가 동의 뒤에 요청한다. */
fun speedPermissionGranted(ctx: Context) = ctx.checkSelfPermission(Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED

/**
 * Pleos SDK + AAOS Car 신호를 모아 도메인 VehicleSample 흐름으로 낸다. 지원하지 않는 신호는 null로 남고 나머지는 계속 동작한다.
 * 모든 SDK 호출은 방어적으로 감싼다(에뮬레이터에 없는 신호, 권한 거부).
 */
class VehicleSignalSource(private val ctx: Context, private val clock: Clock) {
    /** 마지막으로 받은 원시 값. 시연 모드가 이것을 직접 고친다. */
    data class Raw(
        val gear: Gear = Gear.P,
        val odo: Double = 0.0,
        val speedKmh: Double? = null,
        val brake: Boolean? = null,
        val steer: Double? = null,
        val wiper: Boolean? = null,
        val lowBeam: Boolean? = null,
        val fog: Boolean? = null,
        val tempC: Double? = null,
        val windows: Map<String, Int> = emptyMap(),
        val occ: Map<Seat, Boolean> = emptyMap(),
        val belt: Map<Seat, Boolean> = emptyMap(),
        val fuel: Double? = null,
        val soc: Double? = null,
        val refillKm: Double? = null,
        val warns: Map<String, Set<AilmentType>> = emptyMap(),
        val locAddress: String? = null,
        val destName: String? = null,
        val destAddress: String? = null,
        val remainM: Int? = null,
        val placeOverride: PlaceInfo? = null, // 시연 주입. P를 떠나면 지워진다
    )

    @Volatile var locationOn = true
        set(value) { synchronized(this) { field = value; if (!value) raw = raw.withoutPlace() } }
    @Volatile var fastWarn = false // 에뮬레이터·시연: 경고 디바운스 2초(실차 10초)

    val uxRestricted = MutableStateFlow(false)
    val ignition = MutableSharedFlow<Boolean>(extraBufferCapacity = 4) // AAOS IGNITION_STATE를 읽을 수 있을 때만 나온다

    private var raw = Raw()
    private var sink: ((VehicleSample) -> Unit)? = null
    private val warn = WarnFilter()
    private val undo = mutableListOf<() -> Unit>()
    private val destUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val vehicle by lazy { Vehicle(ctx) }
    private val navi by lazy { NaviHelper(ctx) }
    private var naviReady = false
    private var car: Car? = null
    private var pm: CarPropertyManager? = null
    private var driveSubscribed = false

    fun current(): Raw = synchronized(this) { raw }

    private fun Raw.withoutPlace() = copy(locAddress = null, destName = null, destAddress = null, remainM = null, placeOverride = null)

    /** 시연 모드가 기어를 바꾼다(속도·조향 구독 전환과 위치 요청까지 실제 기어 변화와 같은 경로). */
    fun injectGear(g: Gear) = setGear(g)

    fun samples(): Flow<VehicleSample> = callbackFlow {
        sink = { trySend(it) }
        launch { connect() }
        launch { while (isActive) { update { it }; delay(if (current().gear == Gear.P) 5_000 else 1_000) } } // 심장박동: 정차 확정, 10초 중앙값
        launch { while (isActive) { pollTelltales(); delay(if (current().gear == Gear.P) 30_000 else 60_000) } }
        awaitClose { sink = null; stop() }
    }

    fun update(f: (Raw) -> Raw) {
        val s = synchronized(this) { raw = f(raw).let { if (locationOn) it else it.withoutPlace() }; toSample(raw) }
        sink?.invoke(s)
    }

    private fun toSample(r: Raw): VehicleSample {
        val at = clock.instant()
        warn.onSec = if (fastWarn) 2.0 else 10.0
        val place = if (r.gear != Gear.P || !locationOn) null
        else r.placeOverride ?: SampleMapping.placeOf(r.locAddress, r.destName, r.destAddress, r.remainM)
        return VehicleSample(
            at = at, gear = r.gear, odometerKm = r.odo, speedKmh = r.speedKmh, brakePressed = r.brake, steeringDeg = r.steer,
            wiperOn = r.wiper, lowBeamOn = r.lowBeam, fogLightOn = r.fog, outsideTempC = r.tempC,
            windowOpenPct = r.windows.values.maxOrNull()?.toDouble(),
            occupied = r.occ.takeIf { it.isNotEmpty() }?.filterValues { it }?.keys,
            belted = r.belt.takeIf { it.isNotEmpty() }?.filterValues { it }?.keys,
            fuelPct = r.fuel, evSoc = r.soc, tripSinceRefillKm = r.refillKm,
            warnings = warn.filter(r.warns.values.flatten().toSet(), at),
            place = place,
        )
    }

    private fun setGear(g: Gear) {
        val old = current().gear
        update {
            if (g == Gear.P) it.copy(gear = g, speedKmh = null, steer = null)
            else if (old == Gear.P) it.copy(gear = g, locAddress = null, destName = null, destAddress = null, remainM = null, placeOverride = null)
            else it.copy(gear = g)
        }
        if (g == old) return
        if (g == Gear.P) {
            unsubscribeDrive()
            requestNavi()
        } else subscribeDrive()
    }

    // ---- 연결 ----

    private suspend fun connect() {
        connectCar()
        var ok = false
        repeat(3) { // 에뮬레이터·부팅 직후에 서비스가 아직 없을 수 있다. 2초 간격으로 최대 3번
            if (!ok) try { vehicle.initialize(); registerVehicle(); ok = true } catch (e: Throwable) { Log.w(TAG, "vehicle init failed: ${e.javaClass.simpleName}"); delay(2_000) }
        }
        try {
            navi.addListener(naviListener)
            navi.initialize({ naviReady = true; if (current().gear == Gear.P) requestNavi() }, { Log.w(TAG, "navi init failed: ${it.javaClass.simpleName}") })
        } catch (e: Throwable) { Log.w(TAG, "navi unavailable: ${e.javaClass.simpleName}") }
    }

    private fun stop() {
        undo.asReversed().forEach { runCatching(it) }
        undo.clear()
        runCatching { vehicle.release() }
        runCatching { navi.removeListener(naviListener); navi.release() }
        runCatching { car?.disconnect() }
        naviReady = false; driveSubscribed = false; pm = null; car = null
    }

    private fun guard(tag: String, block: () -> Unit) = try { block() } catch (e: Throwable) { Log.w(TAG, "$tag unsupported: ${e.javaClass.simpleName}") }
    private fun fail(tag: String, e: Exception) { Log.w(TAG, "$tag failed: ${e.message}") }

    // ---- AAOS CarPropertyManager / CarUxRestrictionsManager ----

    private val gearCb = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(v: CarPropertyValue<*>) { (v.value as? Int)?.let(SampleMapping::gearOfAaos)?.let(::setGear) }
        override fun onErrorEvent(propId: Int, zone: Int) = Unit
    }
    private val speedCb = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(v: CarPropertyValue<*>) { (v.value as? Float)?.let { m -> update { it.copy(speedKmh = SampleMapping.kmh(m)) } } }
        override fun onErrorEvent(propId: Int, zone: Int) = Unit
    }
    private val ignitionCb = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(v: CarPropertyValue<*>) { (v.value as? Int)?.let(SampleMapping::ignitionOn)?.let { ignition.tryEmit(it) } }
        override fun onErrorEvent(propId: Int, zone: Int) = Unit
    }
    private val tempCb = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(v: CarPropertyValue<*>) { (v.value as? Float)?.let { c -> update { it.copy(tempC = c.toDouble()) } } }
        override fun onErrorEvent(propId: Int, zone: Int) = Unit
    }

    private fun connectCar() = guard("car") {
        car = Car.createCar(ctx, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
            if (!ready) { pm = null; return@createCar } // car service가 재시작하면 ready=true로 다시 불린다
            guard("carManagers") {
                val m = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                pm = m
                m?.let { sub(it, gearCb, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
                m?.let { sub(it, ignitionCb, VehiclePropertyIds.IGNITION_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
                m?.let { sub(it, tempCb, VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
                guard("gearInit") { m?.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, 0)?.let(SampleMapping::gearOfAaos)?.let(::setGear) }
                if (current().gear != Gear.P) subscribeDrive()
                val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                ux?.registerListener { r -> uxRestricted.value = r.isRequiresDistractionOptimization }
                ux?.currentCarUxRestrictions?.let { uxRestricted.value = it.isRequiresDistractionOptimization }
                undo += { ux?.unregisterListener() }
            }
        }
    }

    private fun sub(m: CarPropertyManager, cb: CarPropertyManager.CarPropertyEventCallback, id: Int, rate: Float) {
        if (m.getCarPropertyConfig(id) == null) return // 이 차(에뮬레이터)가 지원하지 않는 속성
        guard("prop $id") { m.registerCallback(cb, id, rate); undo += { m.unregisterCallback(cb, id) } }
    }

    /** 주행 중에만: 속도(10Hz, CAR_SPEED 허용 필요)와 조향각. P에서는 끊는다(PRD F1 8번). */
    private fun subscribeDrive() {
        if (driveSubscribed) return
        driveSubscribed = true
        pm?.takeIf { speedPermissionGranted(ctx) }?.let { m ->
            if (m.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED) != null)
                guard("speed") { m.registerCallback(speedCb, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FAST) }
        }
        guard("steering") { vehicle.getSteeringWheel().registerSteeringWheelAngle(steerL) }
    }

    private fun unsubscribeDrive() {
        if (!driveSubscribed) return
        driveSubscribed = false
        guard("speed") { pm?.unregisterCallback(speedCb, VehiclePropertyIds.PERF_VEHICLE_SPEED) }
        guard("steering") { vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steerL) }
    }

    private val steerL = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(p0: Float?) { p0?.let { a -> update { it.copy(steer = a.toDouble()) } } }
        override fun onFailed(e: Exception) = fail("steering", e)
    }

    // ---- Pleos Vehicle SDK ----

    private fun warnSet(key: String, types: Set<AilmentType>) = update { it.copy(warns = it.warns + (key to types)) }

    private fun registerVehicle() {
        guard("gear") {
            val l = object : GearSelectionListener {
                override fun onSelected(p0: PleosGear?) { p0?.let { SampleMapping.gearOfPleos(it.name) }?.let(::setGear) }
                override fun onFailed(e: Exception) = fail("gear", e)
            }
            vehicle.getGear().registerGearSelection(l); undo += { vehicle.getGear().unregisterGearSelection(l) }
            vehicle.getGear().getCurrentGearSelectionState({ g -> g?.let { SampleMapping.gearOfPleos(it.name) }?.let(::setGear) }, { fail("gearInit", it) })
        }
        guard("brake") {
            val l = object : BrakePedalStatusListener {
                override fun onBrakePedalStatusUpdated(p0: Boolean?) { p0?.let { v -> update { it.copy(brake = v) } } }
                override fun onFailed(e: Exception) = fail("brake", e)
            }
            vehicle.getBrake().registerBrakePedalStatus(l); undo += { vehicle.getBrake().unregisterBrakePedalStatus(l) }
        }
        guard("odometer") {
            val l = object : DistanceDrivenListener {
                override fun onDistanceDrivenUpdated(p0: Float?) { p0?.let { v -> update { it.copy(odo = v.toDouble()) } } }
                override fun onFailed(e: Exception) = fail("odometer", e)
            }
            vehicle.getOdometer().registerDistanceDriven(l); undo += { vehicle.getOdometer().unregisterDistanceDriven(l) }
            vehicle.getOdometer().getCurrentDistanceDriven({ v -> v?.let { d -> update { it.copy(odo = d.toDouble()) } } }, { fail("odometerInit", it) })
        }
        guard("refill") {
            val l = object : DistanceDrivenListener {
                override fun onDistanceDrivenUpdated(p0: Float?) { p0?.let { v -> update { it.copy(refillKm = v.toDouble()) } } }
                override fun onFailed(e: Exception) = fail("refill", e)
            }
            vehicle.getOdometer().registerTripSinceRefillDrivingDistance(l); undo += { vehicle.getOdometer().unregisterTripSinceRefillDrivingDistance(l) }
        }
        guard("fuel") {
            val l = object : FuelPercentListener {
                override fun onFuelPercentUpdated(p0: Int?) { p0?.let { v -> update { it.copy(fuel = v.toDouble()) } } }
                override fun onFailed(e: Exception) = fail("fuel", e)
            }
            vehicle.getFuel().registerFuelPercent(l); undo += { vehicle.getFuel().unregisterFuelPercent(l) }
            vehicle.getFuel().getFuelPercent({ v -> v?.let { d -> update { it.copy(fuel = d.toDouble()) } } }, { fail("fuelInit", it) })
        }
        guard("soc") {
            val l = object : EvBatteryStateOfChargeListener {
                override fun onEvBatteryStateOfChargeUpdated(p0: Int?) { p0?.let { v -> update { it.copy(soc = v.toDouble()) } } }
                override fun onFailed(e: Exception) = fail("soc", e)
            }
            vehicle.getEvBattery().registerEvBatteryStateOfCharge(l); undo += { vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(l) }
            vehicle.getEvBattery().getEvBatteryStateOfCharge({ v -> v?.let { d -> update { it.copy(soc = d.toDouble()) } } }, { fail("socInit", it) })
        }
        guard("wiper") {
            val a = WindowArea(WindowValue.FRONT_WINDSHIELD)
            val l = object : WiperStateListener {
                override fun onWiperStateUpdated(p0: ai.pleos.playground.vehicle.constant.control.WiperState?) { p0?.let { s -> update { it.copy(wiper = s.name == "ON") } } }
                override fun onFailed(e: Exception) = fail("wiper", e)
            }
            vehicle.getWiper().registerWiperState(a, l); undo += { vehicle.getWiper().unregisterWiperState(a, l) }
        }
        guard("lowBeam") {
            val l = lightListener("lowBeam") { v -> update { it.copy(lowBeam = v) } }
            vehicle.getLight().registerLowBeamLightState(l); undo += { vehicle.getLight().unregisterLowBeamLightState(l) }
        }
        guard("fog") {
            val l = lightListener("fog") { v -> update { it.copy(fog = v) } }
            vehicle.getLight().registerFrontFogLightState(l); undo += { vehicle.getLight().unregisterFrontFogLightState(l) }
        }
        for (w in listOf(WindowValue.ROW_1_LEFT, WindowValue.ROW_1_RIGHT, WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT)) guard("window $w") {
            val a = WindowArea(w)
            val l = object : WindowPositionListener {
                override fun onWindowPositionUpdated(p0: Int?) { p0?.let { v -> update { it.copy(windows = it.windows + (w.name to v)) } } }
                override fun onFailed(e: Exception) = fail("window", e)
            }
            vehicle.getWindow().registerWindowPosition(a, l); undo += { vehicle.getWindow().unregisterWindowPosition(a, l) }
        }
        // 동승석·뒷좌석만. 운전석은 읽지 않는다(PRD 16.2)
        for (sv in listOf(SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)) guard("seat $sv") {
            val seat = SampleMapping.seatOfPleos(sv.name) ?: return@guard
            val a = SeatArea(sv)
            val occ = object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(p0: Boolean?) { p0?.let { v -> update { it.copy(occ = it.occ + (seat to SampleMapping.occupied(v))) } } }
                override fun onFailed(e: Exception) = fail("seat", e)
            }
            vehicle.getSeat().registerSeatOccupancy(a, occ); undo += { vehicle.getSeat().unregisterSeatOccupancy(a, occ) }
            vehicle.getSeat().getCurrentSeatOccupancy(a, { v -> v?.let { d -> update { it.copy(occ = it.occ + (seat to SampleMapping.occupied(d))) } } }, { fail("seatInit", it) })
            val belt = object : SeatBeltBuckledStateListener {
                override fun onSeatBeltBuckledStateUpdated(p0: Boolean?) { p0?.let { v -> update { it.copy(belt = it.belt + (seat to v)) } } }
                override fun onFailed(e: Exception) = fail("belt", e)
            }
            guard("belt $sv") {
                vehicle.getSeat().registerSeatBeltBuckledState(a, belt); undo += { vehicle.getSeat().unregisterSeatBeltBuckledState(a, belt) }
                vehicle.getSeat().getCurrentSeatBeltBuckledState(a, { v -> v?.let { d -> update { it.copy(belt = it.belt + (seat to d)) } } }, { fail("beltInit", it) })
            }
        }
        // 경고: 구독 가능한 것은 구독하고, 경고등(TPMS 등)은 poll
        guard("washer") {
            val l = object : LowWasherFluidWaringListener {
                override fun onLowWasherFluidWaringUpdated(p0: Boolean?) { p0?.let { warnSet("washer", if (it) setOf(AilmentType.WASHER) else emptySet()) } }
                override fun onFailed(e: Exception) = fail("washer", e)
            }
            vehicle.getSafety().registerLowWasherFluidWaringState(l); undo += { vehicle.getSafety().unregisterLowWasherFluidWaringState(l) }
        }
        guard("engineOil") {
            val l = oilListener("oil", AilmentType.OIL)
            vehicle.getSafety().registerEngineOilLevelState(l); undo += { vehicle.getSafety().unregisterEngineOilLevelState(l) }
        }
        guard("brakeFluid") {
            val l = oilListener("brakeFluid", AilmentType.BRAKE_FLUID)
            vehicle.getSafety().registerBrakeFluidLevelState(l); undo += { vehicle.getSafety().unregisterBrakeFluidLevelState(l) }
        }
        guard("transOil") {
            val l = oilListener("transOil", AilmentType.MISSION_OIL)
            vehicle.getSafety().registerTransmissionOilLevelState(l); undo += { vehicle.getSafety().unregisterTransmissionOilLevelState(l) }
        }
        guard("powerDown") {
            val l = object : PowerDownWarningIndicatorListener {
                override fun onPowerDownWarningIndicatorUpdated(p0: Boolean?) { p0?.let { warnSet("powerDown", if (it) setOf(AilmentType.POWER_DOWN) else emptySet()) } }
                override fun onFailed(e: Exception) = fail("powerDown", e)
            }
            vehicle.getSafety().registerPowerDownWarningIndicator(l); undo += { vehicle.getSafety().unregisterPowerDownWarningIndicator(l) }
        }
        for (sv in listOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT)) guard("airbag $sv") {
            val a = SeatArea(sv)
            val l = object : SeatAirbagDeployedListener {
                override fun onSeatAirbagDeployUpdated(p0: Boolean?) { p0?.let { warnSet("airbag$sv", if (it) setOf(AilmentType.CRASH) else emptySet()) } }
                override fun onFailed(e: Exception) = fail("airbag", e)
            }
            vehicle.getSafety().registerSeatAirbagDeployed(a, l); undo += { vehicle.getSafety().unregisterSeatAirbagDeployed(a, l) }
        }
    }

    private fun lightListener(tag: String, set: (Boolean) -> Unit) = object : LightStateListener {
        override fun onLightStateUpdated(p0: ai.pleos.playground.vehicle.constant.control.LightState?) { p0?.let { set(it.name == "ON") } }
        override fun onFailed(e: Exception) = fail(tag, e)
    }

    private fun oilListener(key: String, type: AilmentType) = object : OilLevelStateListener {
        override fun onOilLevelUpdated(p0: ai.pleos.playground.vehicle.constant.control.OilLevelState?) {
            p0?.let { warnSet(key, if (SampleMapping.oilLow(it.name)) setOf(type) else emptySet()) }
        }
        override fun onFailed(e: Exception) = fail(key, e)
    }

    /** 경고등은 구독이 막혀 있다(registerTelltales는 PRIVATE_CAR_SAFETY). 30초(정차)/60초(주행)마다 조회. */
    private fun pollTelltales() = guard("telltale") {
        vehicle.getSafety().getTelltalesInfo(
            { t -> t?.telltales?.map { it.type.name to it.state.name }?.let { warnSet("telltale", SampleMapping.telltaleAilments(it)) } },
            { fail("telltale", it) },
        )
    }

    // ---- NaviHelper ----

    private val naviListener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) { update { it.copy(locAddress = info.address) } }
        override fun onDestinationInfo(info: DestinationInfo) {
            // 에뮬레이터 확인: 경로가 없으면 name="", remainDistance=0으로 온다. 경로 없음으로 다룬다. remainDistance 단위(m 가정)는 경로가 있을 때 확인 필요
            if (info.name.isNullOrBlank()) clearRoute() else update { it.copy(destName = info.name, destAddress = info.address, remainM = info.remainDistance) }
            destUpdates.tryEmit(Unit)
        }
        override fun onRouteEnded() { clearRoute() }
        override fun onRouteCancelled() { clearRoute() }
        override fun onDestinationArrived(info: ai.pleos.playground.navi.data.DestinationArrivedInfo) { update { it.copy(remainM = 0) } }
    }

    private fun clearRoute() = update { it.copy(destName = null, destAddress = null, remainM = null) }

    /** 도착 P에서 1번. 위치 사용이 꺼져 있으면 NaviHelper를 부르지 않는다(F13-AC4). 좌표는 읽지 않고 주소만 쓴다. */
    private fun requestNavi() {
        if (!locationOn || !naviReady) return
        guard("navi") { navi.getCurrentLocationInfo(); navi.getDestinationInfo() }
    }

    /** CHAMA_DISTANCE용: 목적지까지 남은 km. 요청하고 최대 2초 기다리며, 안 오면 마지막 값. 경로가 없으면 null. */
    suspend fun remainKm(): Double? {
        if (!locationOn) return null
        if (locationOn && naviReady) withTimeoutOrNull(2_000) {
            destUpdates.onSubscription { guard("navi") { navi.getDestinationInfo() } }.first()
        }
        return if (locationOn) current().remainM?.let { it / 1000.0 } else null
    }
}

/** 부화에 필요한 값. SDK에 임시로 연결해 읽고 끊는다. 못 읽은 값은 기본값(VIN은 무작위 씨앗, 주행거리 0, 연료 종류 FUEL). */
data class HatchInfo(val vinSeedHash: String, val odoKm: Double, val energyProfile: EnergyProfile)

suspend fun readHatchInfo(ctx: Context): HatchInfo {
    val vin = CompletableDeferred<String?>()
    val odo = CompletableDeferred<Double?>()
    val fuel = CompletableDeferred<List<Int>?>()
    val vehicle = Vehicle(ctx)
    var car: Car? = null
    try {
        vehicle.initialize()
        vehicle.getCarInfo().getVINInfo({ vin.complete(it) }, { vin.complete(null) })
        vehicle.getOdometer().getCurrentDistanceDriven({ odo.complete(it?.toDouble()) }, { odo.complete(null) })
    } catch (e: Throwable) { vin.complete(null); odo.complete(null) }
    try {
        car = Car.createCar(ctx, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
            if (ready) fuel.complete(runCatching {
                val v = (c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).getProperty<Any>(VehiclePropertyIds.INFO_FUEL_TYPE, 0).value
                when (v) { is IntArray -> v.toList(); is Array<*> -> v.mapNotNull { it as? Int }; else -> null }
            }.getOrNull())
        }
    } catch (e: Throwable) { fuel.complete(null) }
    val info = HatchInfo(
        SampleMapping.vinSeedHash(withTimeoutOrNull(3_000) { vin.await() }),
        withTimeoutOrNull(3_000) { odo.await() } ?: 0.0,
        SampleMapping.energyProfile(withTimeoutOrNull(3_000) { fuel.await() }),
    )
    runCatching { vehicle.release() }
    runCatching { car?.disconnect() }
    return info
}
