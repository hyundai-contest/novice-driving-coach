package com.example.hyundai_contest.model

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }
enum class Side { LEFT, RIGHT }
enum class Seat { FL, FR, RL, RR }

/** One snapshot of everything the app knows about the car. Null means "this car does not tell us". */
data class CarState(
    val atMs: Long = 0,
    val gear: Gear = Gear.UNKNOWN,
    val speedKph: Float? = null,
    val steeringDeg: Float? = null,
    val turnSignal: Side? = null,
    val doorsOpen: Set<Seat> = emptySet(),
    val windowsOpenPct: Map<Seat, Int> = emptyMap(),
    val headlampOn: Boolean = false,
    val headlampAuto: Boolean? = null,
    val highBeamOn: Boolean = false,
    /** Driver belt. Null also when the sensor has never been seen to change (see PleosSignalSource). */
    val beltBuckled: Boolean? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)
