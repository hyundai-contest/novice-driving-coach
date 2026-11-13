package com.example.hyundai_contest.signal

import android.annotation.SuppressLint
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import com.example.hyundai_contest.model.CarState
import com.example.hyundai_contest.model.Gear
import com.example.hyundai_contest.model.Seat
import com.example.hyundai_contest.model.Side
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

const val TAG = "Coach"

/** Vehicle domains the app reads; one lands in [PleosSignalSource.unsupported] when the car does not provide it. */
enum class Domain { TurnSignal, Gear, Steering, Door, Window, Light, Belt, Location }

/**
 * Pleos Vehicle SDK + the platform car speed and location → one [CarState] stream.
 * Uses only third-party read permissions; nothing here controls the car.
 */
class PleosSignalSource(private val context: Context) {
    private val _state = MutableStateFlow(CarState(atMs = System.currentTimeMillis()))
    val state: StateFlow<CarState> = _state

    private val _unsupported = MutableStateFlow<Set<Domain>>(emptySet())
    val unsupported: StateFlow<Set<Domain>> = _unsupported

    private var vehicle: Vehicle? = null
    private var locationListener: LocationListener? = null
    /** When [CarState.speedKph] was last set, by the car or by GPS. */
    @Volatile private var speedAtMs = 0L

    private var car: Car? = null
    private var speedRegistered = false

    /** When the car last reported its speed. While that is recent GPS speed is ignored; with no grant or no property it stays 0 and GPS stands in. */
    @Volatile private var carSpeedAtMs = 0L

    private val prefs = context.getSharedPreferences("signals", Context.MODE_PRIVATE)

    // ponytail: a belt sensor never seen "buckled" is treated as absent (the emulator is stuck at false).
    // Ceiling: a driver who has never once buckled up in this car is not flagged; switch to the SDK
    // capability check if it turns out to tell a live sensor from a stuck one.
    @Volatile private var beltTrusted = prefs.getBoolean(BELT_SEEN, false)

    /** The current state as of [nowMs]. A speed (car or GPS) not refreshed for [SPEED_STALE_MS] is unknown, not assumed unchanged. */
    fun stateAt(nowMs: Long): CarState = state.value.let {
        it.copy(atMs = nowMs, speedKph = freshSpeed(it.speedKph, speedAtMs, nowMs))
    }

    private fun update(f: (CarState) -> CarState) = _state.update { f(it).copy(atMs = System.currentTimeMillis()) }

    private fun fail(domain: Domain, e: Throwable?) {
        Log.w(TAG, "domain $domain unavailable: ${e?.message}")
        _unsupported.update { it + domain }
    }

    /** SDK calls can throw SecurityException synchronously; a missing domain must never crash the app. */
    private inline fun safely(domain: Domain, block: () -> Unit) = runCatching(block).onFailure { fail(domain, it) }

    private val seatDoors = mapOf(
        Seat.FL to DoorValue.ROW_1_LEFT, Seat.FR to DoorValue.ROW_1_RIGHT,
        Seat.RL to DoorValue.ROW_2_LEFT, Seat.RR to DoorValue.ROW_2_RIGHT,
    )
    private val seatWindows = mapOf(
        Seat.FL to WindowValue.ROW_1_LEFT, Seat.FR to WindowValue.ROW_1_RIGHT,
        Seat.RL to WindowValue.ROW_2_LEFT, Seat.RR to WindowValue.ROW_2_RIGHT,
    )

    // Listeners are kept as fields: the SDK unregisters by identity.
    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = update {
            it.copy(turnSignal = when (state) { TurnSignalValue.LEFT -> Side.LEFT; TurnSignalValue.RIGHT -> Side.RIGHT; else -> null })
        }
        override fun onFailed(e: Exception) = fail(Domain.TurnSignal, e)
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = update { it.copy(gear = gear.toGear()) }
        override fun onFailed(e: Exception) = fail(Domain.Gear, e)
    }
    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) = update { it.copy(steeringDeg = angle?.times(STEERING_SIGN)) }
        override fun onFailed(e: Exception) = fail(Domain.Steering, e)
    }
    private val doorListeners = seatDoors.mapValues { (seat, _) ->
        object : DoorPositionListener {
            override fun onPositionUpdated(pos: Int?) = update {
                it.copy(doorsOpen = if ((pos ?: 0) > 0) it.doorsOpen + seat else it.doorsOpen - seat)
            }
            override fun onFailed(e: Exception) = fail(Domain.Door, e)
        }
    }
    private val windowListeners = seatWindows.mapValues { (seat, _) ->
        object : WindowPositionListener {
            override fun onWindowPositionUpdated(position: Int?) = update {
                it.copy(windowsOpenPct = it.windowsOpenPct + (seat to (position ?: 0)))
            }
            override fun onFailed(e: Exception) = fail(Domain.Window, e)
        }
    }
    private val lowBeamListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            update { it.copy(headlampOn = state == LightState.ON) }
            refreshHeadlampMode()
        }
        override fun onFailed(e: Exception) = fail(Domain.Light, e)
    }

    private val highBeamListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) = update { it.copy(highBeamOn = state == LightState.ON) }
        override fun onFailed(e: Exception) = fail(Domain.Light, e)
    }
    private val beltListener = object : SeatBeltBuckledStateListener {
        override fun onSeatBeltBuckledStateUpdated(status: Boolean?) {
            if (status == true && !beltTrusted) {
                beltTrusted = true
                prefs.edit().putBoolean(BELT_SEEN, true).apply()
            }
            update { it.copy(beltBuckled = status.takeIf { beltTrusted }) }
        }
        override fun onFailed(e: Exception) = fail(Domain.Belt, e)
    }
    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val mps = (value.value as? Float)?.takeIf { value.status == CarPropertyValue.STATUS_AVAILABLE }
                ?: return onErrorEvent(value.propertyId, value.areaId)
            System.currentTimeMillis().let { carSpeedAtMs = it; speedAtMs = it }
            update { it.copy(speedKph = abs(mps) * 3.6f) } // the property is negative in reverse
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) { carSpeedAtMs = 0 }
    }

    private fun VehicleGear?.toGear(): Gear = when (this) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.DRIVE
    }

    private fun refreshHeadlampMode() = safely(Domain.Light) {
        vehicle?.getLight()?.getLowBeamLightSwitch(
            { sw -> update { it.copy(headlampAuto = sw?.let { s -> s == LightSwitch.AUTOMATIC }) } },
            { Log.w(TAG, "headlamp mode unreadable: ${it.message}") },
        )
    }

    fun start() {
        // The SDK queues calls made while the service is still binding, so registering right away is safe.
        val v = Vehicle(context).also { vehicle = it }
        v.initialize()

        safely(Domain.TurnSignal) { v.getTurnSignal().registerTurnSignalState(turnListener) }
        safely(Domain.Gear) {
            v.getGear().getCurrentGearSelectionState({ g -> update { it.copy(gear = g.toGear()) } }, { fail(Domain.Gear, it) })
            v.getGear().registerGearSelection(gearListener)
        }
        safely(Domain.Steering) { v.getSteeringWheel().registerSteeringWheelAngle(steeringListener) }
        safely(Domain.Door) {
            seatDoors.forEach { (seat, value) -> v.getDoor().registerDoorPosition(DoorArea(value), doorListeners.getValue(seat)) }
        }
        safely(Domain.Window) {
            seatWindows.forEach { (seat, value) -> v.getWindow().registerWindowPosition(WindowArea(value), windowListeners.getValue(seat)) }
        }
        safely(Domain.Light) {
            v.getLight().registerLowBeamLightState(lowBeamListener)
            v.getLight().registerHighBeamLightState(highBeamListener)
        }
        safely(Domain.Belt) { v.getSeat().registerSeatBeltBuckledState(DRIVER_SEAT, beltListener) }
        refreshHeadlampMode()
        connectCarSpeed()
        startLocation()
    }

    /**
     * Vehicle speed from the car (AAOS PERF_VEHICLE_SPEED): it keeps working in tunnels and garages where GPS
     * does not. Safe to call again, e.g. once the driver has granted CAR_SPEED.
     */
    @Synchronized fun connectCarSpeed() {
        if (speedRegistered) return
        val c = car
        if (c != null) {
            if (c.isConnected) registerSpeed(c)
            return
        }
        runCatching {
            car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (ready) registerSpeed(connected) else synchronized(this) { speedRegistered = false; carSpeedAtMs = 0 }
            }
        }.onFailure { Log.w(TAG, "car service unavailable, using GPS speed: ${it.message}") }
    }

    @Synchronized private fun registerSpeed(c: Car) {
        if (speedRegistered) return
        runCatching {
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            speedRegistered = pm.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_UI)
        }.onFailure { Log.w(TAG, "car speed unavailable, using GPS speed: ${it.message}") }
    }

    @SuppressLint("MissingPermission") // a missing grant is reported as unsupported
    private fun startLocation() {
        val lm = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { loc ->
            val now = System.currentTimeMillis()
            // GPS speed only stands in while the car is not reporting one; a fix without speed must not refresh an old value.
            val gpsKph = if (now - carSpeedAtMs > SPEED_STALE_MS && loc.hasSpeed()) loc.speed * 3.6f else null
            if (gpsKph != null) speedAtMs = now
            update {
                it.copy(lat = loc.latitude, lng = loc.longitude, speedKph = gpsKph ?: it.speedKph)
            }
        }
        safely(Domain.Location) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            locationListener = listener
        }
    }

    fun stop() {
        vehicle?.apply {
            runCatching {
                getTurnSignal().unregisterTurnSignalState(turnListener)
                getGear().unregisterGearSelection(gearListener)
                getSteeringWheel().unregisterSteeringWheelAngle(steeringListener)
                seatDoors.forEach { (seat, value) -> getDoor().unregisterDoorPosition(DoorArea(value), doorListeners.getValue(seat)) }
                seatWindows.forEach { (seat, value) -> getWindow().unregisterWindowPosition(WindowArea(value), windowListeners.getValue(seat)) }
                getLight().unregisterLowBeamLightState(lowBeamListener)
                getLight().unregisterHighBeamLightState(highBeamListener)
                getSeat().unregisterSeatBeltBuckledState(DRIVER_SEAT, beltListener)
            }
            release()
        }
        vehicle = null
        synchronized(this) {
            runCatching { car?.disconnect() }
            car = null
            speedRegistered = false
            carSpeedAtMs = 0
        }
        locationListener?.let { context.getSystemService(LocationManager::class.java).removeUpdates(it) }
        locationListener = null
    }

    private companion object {
        // Limitation: the emulator reports the console angle with the opposite sign; CarState uses left = negative.
        // Calibrate this knob on a real car.
        const val STEERING_SIGN = -1f
        const val BELT_SEEN = "beltSeenBuckled"
        val DRIVER_SEAT = SeatArea(SeatValue.ROW_1_LEFT)
    }
}
