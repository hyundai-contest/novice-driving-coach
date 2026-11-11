package com.example.hyundai_contest.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.cos
import kotlin.math.max

/**
 * Fits lat/lng points into a canvas with equal metres per pixel on both axes.
 * Limitation: no base map (no tiles on the head unit); add an offline tile layer if the demo needs streets.
 */
class Projector(points: List<Pair<Double, Double>>, private val size: Size, private val padPx: Float = 32f) {
    private val minLat = points.minOfOrNull { it.first } ?: 0.0
    private val maxLat = points.maxOfOrNull { it.first } ?: 0.0
    private val minLng = points.minOfOrNull { it.second } ?: 0.0
    private val maxLng = points.maxOfOrNull { it.second } ?: 0.0
    private val lngScale = cos(Math.toRadians((minLat + maxLat) / 2))
    private val spanX = max((maxLng - minLng) * lngScale, 1e-6)
    private val spanY = max(maxLat - minLat, 1e-6)
    private val scale = minOf((size.width - 2 * padPx) / spanX, (size.height - 2 * padPx) / spanY)
    private val offX = (size.width - spanX * scale) / 2
    private val offY = (size.height - spanY * scale) / 2

    fun at(lat: Double, lng: Double) = Offset(
        (offX + (lng - minLng) * lngScale * scale).toFloat(),
        (offY + (maxLat - lat) * scale).toFloat(),
    )
}
