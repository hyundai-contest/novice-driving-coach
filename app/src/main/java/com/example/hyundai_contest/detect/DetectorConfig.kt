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
    val turnPreWindowMs: Long = 5_000,
    val turnLateMs: Long = 1_000,
    /** Steering past this angle to one side, then past it to the other, is one reversal while parking. */
    val parkSteerDeg: Float = 90f,
    /** Reaching this speed (or, when speed is unknown, staying in D this long) means the car drove off: it was not parking. */
    val parkMaxKph: Float = 20f,
    val parkAbandonMs: Long = 20_000,
    /** At or above this speed a door opening is not treated as the driver getting out. */
    val stoppedMaxKph: Float = 3f,
    /** Peak speed during a turn at or above this is a fast turn. */
    val turnFastKph: Float = 30f,
    val reverseFastKph: Float = 7f,
    /** Shifting between D and R at or above this speed counts as shifting before the car has stopped. */
    val rollingShiftKph: Float = 3f,
    /** The belt and doors are checked the first time the car reaches this speed. */
    val departMinKph: Float = 5f,
    /** Limitation: high beam on a dark empty road is correct use; this only says it once per switch-on. */
    val highBeamMinKph: Float = 20f,
    val highBeamHoldMs: Long = 30_000,
)
