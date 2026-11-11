package com.example.hyundai_contest.store

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

data class Place(val name: String, val lat: Double, val lng: Double)

/** Groups records by place without reverse geocoding: known places by radius, everything else by a ~50 m grid. */
object Places {
    private const val RADIUS_M = 80.0
    private const val GRID_DEG = 0.0005

    val DEMO = listOf(
        Place("집", 37.5712, 126.9690),
        Place("시청 사거리", 37.5658, 126.9772),
        Place("회사 주차장", 37.5745, 126.9840),
        Place("동네마트", 37.5585, 126.9795),
        Place("헬스장", 37.5700, 126.9850),
    )

    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /** What people read: a known place's name, otherwise "이름 없는 장소" (the grid key stays internal). */
    fun label(lat: Double, lng: Double): String = keyFor(lat, lng).let { if (it.startsWith("격자")) "이름 없는 장소" else it }

    fun keyFor(lat: Double, lng: Double, known: List<Place> = DEMO): String {
        known.minByOrNull { distanceM(lat, lng, it.lat, it.lng) }
            ?.takeIf { distanceM(lat, lng, it.lat, it.lng) <= RADIUS_M }
            ?.let { return it.name }
        fun snap(v: Double) = (v / GRID_DEG).roundToLong() * GRID_DEG
        return String.format(Locale.US, "격자 %.4f,%.4f", snap(lat), snap(lng))
    }
}
