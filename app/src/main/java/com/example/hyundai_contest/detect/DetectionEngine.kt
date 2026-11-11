package com.example.hyundai_contest.detect

import com.example.hyundai_contest.model.CarState
import com.example.hyundai_contest.model.EngineEvent
import com.example.hyundai_contest.model.Gear
import com.example.hyundai_contest.model.Record
import com.example.hyundai_contest.model.RecordType
import com.example.hyundai_contest.model.Seat
import com.example.hyundai_contest.model.Side
import kotlin.math.abs

/**
 * Turns a time-ordered stream of [CarState] into trip boundaries and records.
 * Pure: reads time only from [CarState.atMs].
 */
class DetectionEngine(private val config: DetectorConfig = DetectorConfig()) {
    private var prev: CarState? = null
    private var inTrip = false

    // signal left on
    private var signalSide: Side? = null
    private var signalOnSinceMs = 0L
    private var straightSinceMs: Long? = null
    private var signalRecorded = false

    // turns
    private val signalHistory = ArrayDeque<Pair<Long, Side?>>()
    private var turnStartMs: Long? = null
    private var turnSide = Side.LEFT
    private var turnLat: Double? = null
    private var turnLng: Double? = null
    private var turnConfirmed = false
    private var turnIgnored = false

    // reverse parking
    private var parkStartMs: Long? = null
    private var parkShifts = 0
    private var parkReversals = 0
    private var parkSteerSign = 0
    private var parkDriveSinceMs: Long? = null

    fun onState(s: CarState): List<EngineEvent> {
        val out = mutableListOf<EngineEvent>()
        val p = prev
        prev = s

        val wasParked = p == null || p.gear == Gear.PARK || p.gear == Gear.UNKNOWN
        if (!inTrip && s.gear != Gear.PARK && s.gear != Gear.UNKNOWN && wasParked) {
            inTrip = true
            out += EngineEvent.TripStarted(s.atMs, s.lat, s.lng)
        }
        if (p == null) return out // a single state carries no history to judge

        if (inTrip) {
            detectSignalLeftOn(s, out)
            detectParking(p, s, out)
            detectTurn(s, out)
        }

        val driverDoorOpen = Seat.FL in s.doorsOpen
        val driverDoorJustOpened = driverDoorOpen && Seat.FL !in p.doorsOpen
        // Exit = driver door open in P, whichever came last: opening the door, or shifting to P
        // with the door already open (what the "not in P" reminder asks the driver to do).
        if (inTrip && driverDoorOpen && s.gear == Gear.PARK && (driverDoorJustOpened || p.gear != Gear.PARK)) {
            detectLeftOpenOnExit(s, out)
            out += EngineEvent.TripEnded(s.atMs, s.lat, s.lng)
            inTrip = false
            resetSignal(null, s.atMs)
            turnStartMs = null
            parkStartMs = null
            signalHistory.clear()
        } else if (inTrip && driverDoorJustOpened && s.gear in ENGAGED && (s.speedKph?.let { it < config.stoppedMaxKph } ?: true)) {
            out += EngineEvent.NewRecord(
                Record(RecordType.EXIT_NOT_PARKED, s.atMs, s.lat, s.lng, mapOf("gear" to s.gear.name)),
                Announcements.EXIT_NOT_PARKED,
            )
        }
        return out
    }

    private companion object {
        /** Gears the car can roll away in. UNKNOWN is excluded: no reading is not a mistake by the driver. */
        val ENGAGED = setOf(Gear.DRIVE, Gear.REVERSE, Gear.NEUTRAL)
    }

    private fun moving(s: CarState, minKph: Float) =
        s.speedKph?.let { it >= minKph } ?: (s.gear == Gear.DRIVE)

    private fun resetSignal(side: Side?, atMs: Long) {
        signalSide = side
        signalOnSinceMs = atMs
        straightSinceMs = null
        signalRecorded = false
    }

    private fun detectSignalLeftOn(s: CarState, out: MutableList<EngineEvent>) {
        if (s.turnSignal != signalSide) resetSignal(s.turnSignal, s.atMs)
        val side = signalSide ?: return
        val straight = s.steeringDeg?.let { abs(it) < config.straightMaxDeg } ?: true
        if (!moving(s, config.signalMinKph) || !straight) {
            straightSinceMs = null
            return
        }
        val since = straightSinceMs ?: s.atMs.also { straightSinceMs = it }
        if (!signalRecorded && s.atMs - since >= config.signalHoldMs) {
            signalRecorded = true
            out += EngineEvent.NewRecord(
                Record(
                    RecordType.SIGNAL_LEFT_ON, s.atMs, s.lat, s.lng,
                    mapOf("side" to side.name, "heldMs" to (s.atMs - signalOnSinceMs).toString()),
                ),
                Announcements.signalLeftOn(side),
            )
        }
    }

    /**
     * A manoeuvre opens on the shift from D (or N) into R and closes on P. It counts shifts into D or R
     * (a clean reverse park is 1) and steering reversals. Driving off instead of parking discards it.
     * R straight out of P is backing out of a space, not parking, so it never opens one.
     * Limitation: nose-in parking (no R) and a re-adjust after P are not seen; add rules if that matters.
     */
    private fun detectParking(p: CarState, s: CarState, out: MutableList<EngineEvent>) {
        val start = parkStartMs
        if (start == null) {
            if (s.gear == Gear.REVERSE && (p.gear == Gear.DRIVE || p.gear == Gear.NEUTRAL)) {
                parkStartMs = s.atMs
                parkShifts = 1
                parkReversals = 0
                parkSteerSign = steerSign(s)
                parkDriveSinceMs = null
            }
            return
        }
        if (s.gear != p.gear && (s.gear == Gear.DRIVE || s.gear == Gear.REVERSE)) parkShifts++
        val sign = steerSign(s)
        if (sign != 0) {
            if (parkSteerSign != 0 && sign != parkSteerSign) parkReversals++
            parkSteerSign = sign
        }
        when (s.gear) {
            Gear.PARK -> {
                parkStartMs = null
                out += EngineEvent.NewRecord(
                    Record(
                        RecordType.PARKING, s.atMs, s.lat, s.lng,
                        mapOf(
                            "shifts" to parkShifts.toString(),
                            "steerReversals" to parkReversals.toString(),
                            "durationMs" to (s.atMs - start).toString(),
                        ),
                    ),
                    null, // the recorder words it against the driver's own history
                )
            }
            Gear.DRIVE -> {
                val since = parkDriveSinceMs ?: s.atMs.also { parkDriveSinceMs = it }
                // With a known speed only speed decides: waiting in D for a car to pass is still parking.
                val droveOff = s.speedKph?.let { it >= config.parkMaxKph } ?: (s.atMs - since >= config.parkAbandonMs)
                if (droveOff) parkStartMs = null
            }
            else -> parkDriveSinceMs = null
        }
    }

    private fun steerSign(s: CarState): Int {
        val deg = s.steeringDeg ?: return 0
        return when {
            deg >= config.parkSteerDeg -> 1
            deg <= -config.parkSteerDeg -> -1
            else -> 0
        }
    }

    private fun signalAt(t: Long): Side? = signalHistory.lastOrNull { it.first <= t }?.second

    private fun detectTurn(s: CarState, out: MutableList<EngineEvent>) {
        signalHistory.addLast(s.atMs to s.turnSignal)
        val keepFrom = s.atMs - 60_000
        while (signalHistory.size > 1 && signalHistory[1].first <= keepFrom) signalHistory.removeFirst()

        // Full-lock steering while parking, or with the wheel left turned in P/R/N, is not a road turn.
        if (parkStartMs != null || s.gear != Gear.DRIVE) { turnStartMs = null; return }
        val steer = s.steeringDeg ?: run { turnStartMs = null; return }
        val start = turnStartMs
        if (start == null) {
            if (abs(steer) >= config.turnStartDeg) {
                turnStartMs = s.atMs
                turnSide = if (steer < 0) Side.LEFT else Side.RIGHT
                turnLat = s.lat
                turnLng = s.lng
                turnConfirmed = false
                turnIgnored = false
            }
            return
        }
        if (abs(steer) < config.turnEndDeg) {
            if (turnConfirmed) judgeTurn(start, s, out)
            turnStartMs = null
            return
        }
        if (!turnConfirmed && !turnIgnored && s.atMs - start >= config.turnConfirmMs) {
            val speedOk = s.speedKph?.let { it >= config.turnMinKph && it <= config.turnMaxKph } ?: true
            if (speedOk) turnConfirmed = true else turnIgnored = true
        }
    }

    private fun judgeTurn(start: Long, s: CarState, out: MutableList<EngineEvent>) {
        val windowStart = start - config.turnPreWindowMs
        val firstOn = if (signalAt(windowStart) == turnSide) windowStart
        else signalHistory.firstOrNull { it.first in windowStart..s.atMs && it.second == turnSide }?.first
        val detail = mutableMapOf("side" to turnSide.name)
        val type = when {
            firstOn == null -> RecordType.TURN_NO_SIGNAL
            firstOn > start - config.turnLateMs -> {
                detail["leadMs"] = (start - firstOn).toString()
                RecordType.TURN_LATE_SIGNAL
            }
            else -> RecordType.TURN_OK
        }
        out += EngineEvent.NewRecord(Record(type, start, turnLat, turnLng, detail), null)
    }

    private fun detectLeftOpenOnExit(s: CarState, out: MutableList<EngineEvent>) {
        val windows = s.windowsOpenPct.filterValues { it > 0 }.keys.sortedBy { it.ordinal }
        val headlamp = s.headlampOn && s.headlampAuto == false
        val announce = Announcements.leftOpenOnExit(windows, headlamp) ?: return
        out += EngineEvent.NewRecord(
            Record(
                RecordType.LEFT_OPEN_ON_EXIT, s.atMs, s.lat, s.lng,
                mapOf("windows" to windows.joinToString(",") { it.name }, "headlamp" to headlamp.toString()),
            ),
            announce,
        )
    }
}
