package com.example.hyundai_contest.detect

/** Every tunable threshold in one place; calibrate on the real car / emulator. */
data class DetectorConfig(
    val signalMinKph: Float = 20f,
    val straightMaxDeg: Float = 15f,
    val signalHoldMs: Long = 10_000,
    val turnStartDeg: Float = 90f,
    val turnEndDeg: Float = 30f,
    val turnConfirmMs: Long = 1_000,
    val turnMinKph: Float = 5f,
    val turnMaxKph: Float = 40f,
    val turnPreWindowMs: Long = 5_000,
    val turnLateMs: Long = 1_000,
    /** Steering past this angle to one side, then past it to the other, is one reversal while parking. */
    val parkSteerDeg: Float = 90f,
    /** Reaching this speed (or, when speed is unknown, staying in D this long) means the car drove off: it was not parking. */
    val parkMaxKph: Float = 20f,
    val parkAbandonMs: Long = 20_000,
    /** At or above this speed a door opening is not treated as the driver getting out. */
    val stoppedMaxKph: Float = 3f,
)
