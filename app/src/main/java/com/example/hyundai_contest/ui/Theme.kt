package com.example.hyundai_contest.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Dark-surface roles from the dataviz reference palette (in-car screens are dark). */
object Ink {
    val surface = Color(0xFF0E0E0E)
    val raised = Color(0xFF1C1C1C)
    val line = Color(0xFF2E2E2E)
    val primary = Color(0xFFF0EFEC)
    val secondary = Color(0xFFC3C2B7)
    val muted = Color(0xFF8E8D85)
    val series = Color(0xFF3987E5)   // data marks (bars, paths)
    val record = Color(0xFFFF8A3D)   // a recorded omission on a timeline / map
    val ok = Color(0xFF3DDC84)
    val signal = Color(0xFFFFC233)   // turn signal lit, matching the cluster lamp
    val bad = Color(0xFFE5484D)
}

/**
 * In-car type scale. With [CAR_SCALE] one unit is about 1.5 px on the Pleos emulator panel, so
 * [caption] (the smallest text anywhere) is ~24 px. Calibrate on the real display.
 */
object Type {
    val caption = 16.sp
    val body = 20.sp
    val title = 26.sp
    val display = 40.sp
}

/** Smallest height of anything meant to be tapped at arm's length (~96 px on the emulator panel). */
val TOUCH = 64.dp

/** The head unit's density makes dp-sized UI read small at arm's length; scale the whole app up. */
private const val CAR_SCALE = 1.4f

@Composable
fun CoachTheme(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * CAR_SCALE, d.fontScale)) {
        Themed(content)
    }
}

@Composable
private fun Themed(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink.surface, surface = Ink.surface, surfaceVariant = Ink.raised,
            onBackground = Ink.primary, onSurface = Ink.primary, onSurfaceVariant = Ink.secondary,
            primary = Ink.series, onPrimary = Ink.primary, outline = Ink.line,
        ),
        content = content,
    )
}
