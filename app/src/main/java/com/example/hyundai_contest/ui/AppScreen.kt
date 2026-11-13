package com.example.hyundai_contest.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hyundai_contest.CoachApp
import com.example.hyundai_contest.coach.Coach
import com.example.hyundai_contest.detect.Announcements
import com.example.hyundai_contest.model.CarState
import com.example.hyundai_contest.model.Gear
import com.example.hyundai_contest.model.Record
import com.example.hyundai_contest.model.RecordType
import com.example.hyundai_contest.model.Side
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab(val label: String) { TRIPS("주행 코칭"), GROWTH("성장 기록"), PLACES("자주 놓치는 곳") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppScreen(app: CoachApp) {
    val db = app.db
    val recorder = app.recorder
    val version by db.version.collectAsState()
    val car by recorder.car.collectAsState()
    val unsupported by recorder.source.unsupported.collectAsState()
    val liveRecords by recorder.liveRecords.collectAsState()
    val tripId by recorder.currentTripId.collectAsState()
    var tab by remember { mutableStateOf(Tab.TRIPS) }
    var demoPanel by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Limitation: queries run on the main thread; fine for months of records, move to IO if it grows.
    val records = remember(version) { db.records() }
    val trips = remember(version) { db.trips() }
    val nowMs = remember(version) { System.currentTimeMillis() }

    LaunchedEffect(message) { if (message != null) { delay(2500); message = null } }

    // On the way (any gear but P): the live coaching screen. Back in P: the summary, even before getting out.
    val onTheWay = tripId != null && car.gear != Gear.PARK

    Surface(Modifier.fillMaxSize(), color = Ink.surface) {
        Column(Modifier.fillMaxSize()) {
            // One row for title and navigation: vertical space is the scarce one on a landscape head unit.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "새싹운전",
                    Modifier.combinedClickable(onClick = {}, onLongClick = { demoPanel = true }).padding(vertical = 16.dp),
                    fontSize = Type.title, fontWeight = FontWeight.Bold, color = Ink.primary,
                )
                Text(message ?: "", Modifier.weight(1f), color = Ink.secondary, fontSize = Type.caption, maxLines = 1)
                if (onTheWay) {
                    Text("● 코칭 중", color = Ink.ok, fontSize = Type.title, fontWeight = FontWeight.Bold)
                } else {
                    Tab.entries.forEach { t -> BigButton(t.label, selected = tab == t, modifier = Modifier.width(190.dp)) { tab = t } }
                }
            }
            if (onTheWay) {
                DrivingScreen(car, liveRecords)
            } else {
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        Tab.TRIPS -> TripsScreen(trips, records)
                        Tab.GROWTH -> GrowthScreen(records, unsupported, nowMs)
                        Tab.PLACES -> PlacesScreen(trips, records)
                    }
                }
            }
        }
    }

    if (demoPanel) {
        DemoPanel(
            onLoad = { scope.launch { recorder.loadDemoHistory(System.currentTimeMillis()); message = "12주 데모 기록을 불러왔어요" } },
            onClear = { scope.launch { recorder.clear(); message = "기록을 모두 지웠어요" } },
            onDismiss = { demoPanel = false },
        )
    }
}

/** The one button style of the app: tall enough to hit without aiming, label large enough to read at a glance. */
@Composable
fun BigButton(label: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = TOUCH).widthIn(min = TOUCH)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Ink.series else Ink.raised)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = Type.body, maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Ink.primary,
        )
    }
}

/**
 * The screen that stays up while the car moves, so it is glance-only: nothing to tap, a few large words.
 * A new coaching line takes the big card for a few seconds, then joins the short list on the left and the
 * card goes quiet again; a stale "turn your signal off" must not sit there for the rest of the drive.
 */
@Composable
fun DrivingScreen(car: CarState, records: List<Record>) {
    // Only a record that arrives while this screen is up is news; coming back from P must not replay an old one.
    val atEntry = remember { records.lastOrNull() }
    var fresh by remember { mutableStateOf(false) }
    LaunchedEffect(records.size) {
        if (records.isNotEmpty() && records.last() !== atEntry) { fresh = true; delay(FRESH_MS); fresh = false }
    }
    val newest = records.lastOrNull().takeIf { fresh }
    val summary = remember(records) { Coach.summarize(records) }
    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // The car's side-camera view pops up over the top-left whenever a turn signal is on, so the left
        // column keeps its top empty and stacks this trip's tally and the last few items from the bottom.
        Column(Modifier.weight(CAMERA_SIDE).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)) {
            (if (newest != null) records.dropLast(1) else records).takeLast(3).forEach { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(markGlyph(r.type), Modifier.width(32.dp), color = markColor(r.type), fontSize = Type.title, fontWeight = FontWeight.Bold)
                    Text(Labels.name(r.type), color = Ink.secondary, fontSize = Type.body, maxLines = 1)
                }
            }
            val misses = records.count { it.type in Labels.MISSES }
            Row(Modifier.fillMaxWidth().background(Ink.raised, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 16.dp)) {
                Tally("깜빡이", if (summary.turns == 0) "–" else "${summary.signaled}/${summary.turns}", if (summary.turns == 0) Ink.muted else Ink.ok)
                Tally("놓친 조작", "$misses", if (misses == 0) Ink.muted else Ink.record)
                Tally("주차", summary.parkShifts.lastOrNull()?.let { "${it}번" } ?: "–", if (summary.parkShifts.isEmpty()) Ink.muted else Ink.primary)
            }
        }
        Column(Modifier.weight(1f - CAMERA_SIDE).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusStrip(car)
            val accent = newest?.let { markColor(it.type) } ?: Ink.line
            Row(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Ink.raised)) {
                Box(Modifier.width(8.dp).fillMaxHeight().background(accent))
                Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
                    if (newest == null) {
                        Text("운전에 집중하세요", color = Ink.primary, fontSize = Type.display, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("필요한 것만 음성으로 알려드려요", color = Ink.secondary, fontSize = Type.title)
                    } else {
                        Text(Labels.name(newest.type), color = Ink.secondary, fontSize = Type.body)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${markGlyph(newest.type)}  ${Labels.coach(newest.type)}",
                            color = accent, fontSize = Type.display * 1.3f, fontWeight = FontWeight.Bold, lineHeight = Type.display * 1.5f,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(Labels.detail(newest), color = Ink.primary, fontSize = Type.title)
                    }
                }
            }
        }
    }
}

/** Share of the app width the car's side-camera pop-up covers on the Pleos emulator (measured ~45%). */
private const val CAMERA_SIDE = 0.46f

/** How long a new coaching line owns the big card: long enough for two glances. */
private const val FRESH_MS = 8_000L

/** One labelled number of this trip's running tally. */
@Composable
private fun RowScope.Tally(label: String, value: String, color: Color) {
    Column(Modifier.weight(1f)) {
        Text(label, color = Ink.secondary, fontSize = Type.caption, maxLines = 1)
        Text(value, color = color, fontSize = Type.display, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** What the coach is watching, in one line: gear, turn signal, speed, wheel. Anything left open or on shows up as a chip. */
@Composable
private fun StatusStrip(car: CarState) {
    val open = car.windowsOpenPct.filterValues { it > 0 }.keys.sortedBy { it.ordinal }
    Row(
        Modifier.fillMaxWidth().height(TOUCH + 8.dp).background(Ink.raised, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            when (car.gear) { Gear.PARK -> "P"; Gear.DRIVE -> "D"; Gear.REVERSE -> "R"; Gear.NEUTRAL -> "N"; Gear.UNKNOWN -> "-" },
            color = Ink.primary, fontSize = Type.display, fontWeight = FontWeight.Bold,
        )
        // Both arrows are always there, like the cluster lamps: the lit one is the signal.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("◀", color = if (car.turnSignal == Side.LEFT) Ink.signal else Ink.line, fontSize = Type.title)
            Text("▶", color = if (car.turnSignal == Side.RIGHT) Ink.signal else Ink.line, fontSize = Type.title)
        }
        car.speedKph?.let {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%.0f".format(it), color = Ink.primary, fontSize = Type.title, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(" km/h", color = Ink.muted, fontSize = Type.caption, maxLines = 1)
            }
        }
        car.steeringDeg?.let { Text("핸들 %.0f°".format(it), color = Ink.muted, fontSize = Type.caption, maxLines = 1) }
        Spacer(Modifier.weight(1f))
        if (car.beltBuckled == false) WarnChip("벨트")
        if (car.highBeamOn) WarnChip("상향등")
        if (open.isNotEmpty()) WarnChip(open.joinToString(", ") { Announcements.seatName(it) } + " 창문")
        if (car.doorsOpen.isNotEmpty()) WarnChip("문 열림")
    }
}

@Composable
private fun WarnChip(text: String) = Text(
    text, Modifier.background(Ink.record, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
    color = Color.Black, fontSize = Type.caption, fontWeight = FontWeight.Bold, maxLines = 1,
)

@Composable
fun DemoPanel(onLoad: () -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    // A dialog is its own window and falls back to the platform density, so carry the car scale into it.
    val density = LocalDensity.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("시연 패널", fontSize = Type.title) },
        text = {
            CompositionLocalProvider(LocalDensity provides density) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("12주 데모 기록 불러오기", Modifier.fillMaxWidth()) { onLoad(); onDismiss() }
                    BigButton("모든 기록 지우기", Modifier.fillMaxWidth()) { onClear(); onDismiss() }
                    BigButton("닫기", Modifier.fillMaxWidth(), onClick = onDismiss)
                }
            }
        },
        confirmButton = {},
        containerColor = Ink.surface,
    )
}
