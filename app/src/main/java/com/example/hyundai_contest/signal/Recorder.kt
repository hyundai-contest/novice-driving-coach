package com.example.hyundai_contest.signal

import android.util.Log
import com.example.hyundai_contest.coach.Coach
import com.example.hyundai_contest.demo.SyntheticHistory
import com.example.hyundai_contest.detect.DetectionEngine
import com.example.hyundai_contest.model.CarState
import com.example.hyundai_contest.model.EngineEvent
import com.example.hyundai_contest.model.Record
import com.example.hyundai_contest.model.RecordType
import com.example.hyundai_contest.store.CoachDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Samples the car state, runs the detection engine, persists what it finds and speaks the coaching.
 * Runs in the Application scope; [com.example.hyundai_contest.CoachService] keeps the process alive.
 */
class Recorder(
    val source: PleosSignalSource,
    private val db: CoachDb,
    private val announcer: Announcer,
    private val scope: CoroutineScope,
) {
    private val engine = DetectionEngine()
    private val lock = Mutex()

    private val _currentTripId = MutableStateFlow<Long?>(null)
    val currentTripId: StateFlow<Long?> = _currentTripId
    private val _liveRecords = MutableStateFlow<List<Record>>(emptyList())
    val liveRecords: StateFlow<List<Record>> = _liveRecords
    private val _livePath = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())

    private val _car = MutableStateFlow(CarState())

    /** The state the engine just judged (stale speed already dropped), for the screen. Changes only when the car does. */
    val car: StateFlow<CarState> = _car

    private var started = false

    fun start() {
        if (started) return
        started = true
        db.closeDanglingTrips()
        source.start()
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                // Sampling on a clock (not only on change) lets time-based rules such as "on for 10 s" fire.
                val s = source.stateAt(System.currentTimeMillis())
                // One failing sample (DB, TTS) must not end coaching for the rest of the drive.
                lock.withLock { runCatching { handle(s) }.onFailure { Log.e(TAG, "sample failed", it) } }
                delay(SAMPLE_MS)
            }
        }
    }

    private var lastLogged: CarState? = null

    private fun handle(s: CarState) {
        _car.value = s.copy(atMs = 0)
        if (lastLogged?.copy(atMs = 0, lat = null, lng = null) != s.copy(atMs = 0, lat = null, lng = null)) {
            Log.d(TAG, "state ${s.gear} kph=${s.speedKph} steer=${s.steeringDeg} signal=${s.turnSignal} doors=${s.doorsOpen} windows=${s.windowsOpenPct}")
            lastLogged = s
        }

        val tripId = _currentTripId.value
        if (tripId != null && s.lat != null && s.lng != null && _livePath.value.lastOrNull() != (s.lat to s.lng)) {
            _livePath.value = _livePath.value + (s.lat to s.lng)
            db.appendPath(tripId, s.lat, s.lng)
        }

        for (ev in engine.onState(s)) when (ev) {
            is EngineEvent.TripStarted -> {
                db.closeDanglingTrips()
                val id = db.startTrip(ev.atMs, ev.lat, ev.lng)
                _currentTripId.value = id
                _liveRecords.value = emptyList()
                _livePath.value = listOfNotNull(if (ev.lat != null && ev.lng != null) ev.lat to ev.lng else null)
                Log.i(TAG, "trip $id started")
            }
            is EngineEvent.NewRecord -> {
                val say = ev.announce ?: parkingFeedback(ev.record)
                db.append(_currentTripId.value, ev.record)
                _liveRecords.value = _liveRecords.value + ev.record
                Log.i(TAG, "record ${ev.record.type} ${ev.record.detail}")
                say?.let(announcer::say)
            }
            is EngineEvent.TripEnded -> {
                _currentTripId.value?.let { db.endTrip(it, ev.atMs) }
                Log.i(TAG, "trip ${_currentTripId.value} ended")
                _currentTripId.value = null
            }
        }
    }

    /** Must run before the record is stored, so "previous" really is the earlier parkings. */
    private fun parkingFeedback(r: Record): String? {
        if (r.type != RecordType.PARKING) return null
        val shifts = r.detail["shifts"]?.toIntOrNull() ?: return null
        val previous = db.records().filter { it.record.type == RecordType.PARKING }
            .mapNotNull { it.record.detail["shifts"]?.toIntOrNull() }
        return Coach.parkingFeedback(shifts, previous)
    }

    /** Replaces everything with the deterministic 12-week demo history. */
    suspend fun loadDemoHistory(nowMs: Long) = withContext(Dispatchers.IO) {
        lock.withLock {
            // Clearing inside the batch: a failed load must not leave the driver with no records at all.
            db.batch {
                db.clearAll()
                for (t in SyntheticHistory.generate(endMs = nowMs)) {
                    val id = db.startTrip(t.startMs, t.path.first().first, t.path.first().second)
                    db.setPath(id, t.path)
                    t.records.forEach { db.append(id, it) }
                    db.endTrip(id, t.endMs)
                }
            }
            _liveRecords.value = emptyList()
            reattachLiveTrip(nowMs)
            Log.i(TAG, "demo history loaded")
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock {
            db.clearAll()
            _liveRecords.value = emptyList()
            reattachLiveTrip(System.currentTimeMillis())
        }
    }

    /** After the tables are wiped mid-drive, give the drive in progress a fresh row so its records stay visible. */
    private fun reattachLiveTrip(nowMs: Long) {
        if (_currentTripId.value == null) return
        val here = _livePath.value.lastOrNull()
        _currentTripId.value = db.startTrip(nowMs, here?.first, here?.second)
        _livePath.value = listOfNotNull(here)
    }

    private companion object {
        const val SAMPLE_MS = 250L
    }
}
