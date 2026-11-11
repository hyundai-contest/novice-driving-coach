package com.example.hyundai_contest.signal

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
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

const val TAG = "Coach"

/** Vehicle domains the app reads; one lands in [PleosSignalSource.unsupported] when the car does not provide it. */
enum class Domain { TurnSignal, Gear, Steering, Door, Window, Light, Location }

/**
 * Pleos Vehicle SDK + platform location → one [CarState] stream.
 * Uses only third-party read permissions; nothing here controls the car.
 */
class PleosSignalSource(private val context: Context) {
    private val _state = MutableStateFlow(CarState(atMs = System.currentTimeMillis()))
    val state: StateFlow<CarState> = _state

    private val _unsupported = MutableStateFlow<Set<Domain>>(emptySet())
    val unsupported: StateFlow<Set<Domain>> = _unsupported

    private var vehicle: Vehicle? = null
    private var locationListener: LocationListener? = null
    @Volatile private var fixAtMs = 0L

    /** The current state as of [nowMs], with a GPS speed older than [SPEED_STALE_MS] treated as unknown. */
    fun stateAt(nowMs: Long): CarState = state.value.let { it.copy(atMs = nowMs, speedKph = freshSpeed(it.speedKph, fixAtMs, nowMs)) }

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
        safely(Domain.Light) { v.getLight().registerLowBeamLightState(lowBeamListener) }
        refreshHeadlampMode()
        startLocation()
    }

    @SuppressLint("MissingPermission") // a missing grant is reported as unsupported
    private fun startLocation() {
        val lm = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { loc ->
            fixAtMs = System.currentTimeMillis()
            update {
                it.copy(lat = loc.latitude, lng = loc.longitude, speedKph = if (loc.hasSpeed()) loc.speed * 3.6f else it.speedKph)
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
            }
            release()
        }
        vehicle = null
        locationListener?.let { context.getSystemService(LocationManager::class.java).removeUpdates(it) }
        locationListener = null
    }

    private companion object {
        // Limitation: the emulator reports the console angle with the opposite sign; CarState uses left = negative.
        // Calibrate this knob on a real car.
        const val STEERING_SIGN = -1f
    }
}
