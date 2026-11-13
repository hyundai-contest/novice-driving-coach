package com.example.hyundai_contest.model

enum class RecordType {
    /** A turn signalled in time: the success the coach counts progress with. */
    TURN_OK,
    TURN_NO_SIGNAL, TURN_LATE_SIGNAL, SIGNAL_LEFT_ON,

    /** Speed-related: a turn taken too fast (recorded next to the signal verdict), reversing too fast. */
    TURN_FAST, REVERSE_FAST,
    HIGH_BEAM_LEFT_ON,

    /** Moved off with the belt unbuckled or a door open. */
    DEPART_UNREADY,

    /** One reverse-parking manoeuvre, with how many gear changes and steering reversals it took. */
    PARKING,
    LEFT_OPEN_ON_EXIT, EXIT_NOT_PARKED,
}

data class Record(
    val type: RecordType,
    val atMs: Long,
    val lat: Double?,
    val lng: Double?,
    val detail: Map<String, String> = emptyMap(),
)

sealed interface EngineEvent {
    data class TripStarted(val atMs: Long, val lat: Double?, val lng: Double?) : EngineEvent
    data class TripEnded(val atMs: Long, val lat: Double?, val lng: Double?) : EngineEvent
    data class NewRecord(val record: Record, val announce: String?) : EngineEvent
}
