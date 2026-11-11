package com.example.hyundai_contest.signal

/** A GPS speed is only trusted for this long after its fix (tunnels, garages, a stopped emulator feed). */
const val SPEED_STALE_MS = 3_000L

/** The last fix's speed, or null ("unknown") once the fix is older than [SPEED_STALE_MS]. */
fun freshSpeed(speedKph: Float?, fixAtMs: Long, nowMs: Long): Float? =
    speedKph?.takeIf { nowMs - fixAtMs <= SPEED_STALE_MS }
