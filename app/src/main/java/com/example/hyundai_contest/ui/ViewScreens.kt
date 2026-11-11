package com.example.hyundai_contest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.hyundai_contest.coach.Coach
import com.example.hyundai_contest.model.RecordType
import com.example.hyundai_contest.signal.Domain
import com.example.hyundai_contest.store.Places
import com.example.hyundai_contest.store.StoredRecord
import com.example.hyundai_contest.store.TripRow
import java.util.Locale
import kotlin.math.sqrt

private const val WEEK_MS = 7L * 24 * 3600 * 1000
private val CARD = RoundedCornerShape(12.dp)

/** Done right = green, a parking attempt = neutral, something to work on = orange. */
fun markColor(type: RecordType): Color = when (type) {
    RecordType.TURN_OK -> Ink.ok
    RecordType.PARKING -> Ink.series
    else -> Ink.record
}

/** The same distinction as [markColor] without relying on colour. */
fun markGlyph(type: RecordType): String = when (type) {
    RecordType.TURN_OK -> "✓"
    RecordType.PARKING -> "P"
    else -> "!"
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, fontSize = Type.body, color = Ink.primary, fontWeight = FontWeight.SemiBold)

@Composable
fun RecordRow(r: StoredRecord, showDate: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(markGlyph(r.record.type), Modifier.width(32.dp), color = markColor(r.record.type), fontSize = Type.title, fontWeight = FontWeight.Bold)
        Column {
            Text(Labels.name(r.record.type), color = Ink.primary, fontSize = Type.body)
            Text(
                listOfNotNull(
                    if (showDate) Labels.dateTime(r.record.atMs) else Labels.time(r.record.atMs),
                    r.record.lat?.let { Places.label(it, r.record.lng!!) },
                    Labels.detail(r.record),
                ).joinToString(" · "),
                color = Ink.secondary, fontSize = Type.caption,
            )
        }
    }
}

// ───────────────────────────── 주행 코칭 ─────────────────────────────

/**
 * One trip at a glance: the single thing to try next, four large numbers, then the details.
 * Trips are stepped through with two buttons; scrolling a long list is not a parked-car gesture.
 */
@Composable
fun TripsScreen(trips: List<TripRow>, records: List<StoredRecord>, onSpeak: (String) -> Unit) {
    val byTrip = remember(records) { records.groupBy { it.tripId } }
    var index by remember(trips.size) { mutableStateOf(trips.lastIndex) }
    val trip = trips.getOrNull(index)
    if (trip == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("아직 주행 기록이 없어요. 운전을 시작하면 차가 조작을 봐 드려요", color = Ink.secondary, fontSize = Type.title)
        }
        return
    }
    val recs = byTrip[trip.id].orEmpty()
    val summary = remember(recs) { Coach.summarize(recs.map { it.record }) }
    val tip = Coach.tip(summary)
    val details = recs.filter { it.record.type != RecordType.TURN_OK }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("◀ 이전") { if (index > 0) index-- }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Labels.dateTime(trip.startMs), color = Ink.primary, fontSize = Type.title, fontWeight = FontWeight.SemiBold)
                val mins = trip.endMs?.let { "${(it - trip.startMs) / 60_000}분 주행" } ?: "주행 중"
                Text(if (index == trips.lastIndex) "가장 최근 · $mins" else mins, color = Ink.secondary, fontSize = Type.caption)
            }
            BigButton("다음 ▶") { if (index < trips.lastIndex) index++ }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.25f).fillMaxHeight()) {
                Column(Modifier.fillMaxWidth().background(Ink.raised, CARD).padding(20.dp)) {
                    Text("다음 주행에서 해볼 것", color = Ink.muted, fontSize = Type.caption)
                    Spacer(Modifier.height(6.dp))
                    Text(tip, color = Ink.primary, fontSize = Type.title, fontWeight = FontWeight.SemiBold, lineHeight = Type.title * 1.35f)
                    Spacer(Modifier.height(14.dp))
                    BigButton("▶ 음성으로 듣기") { onSpeak(tip) }
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().background(Ink.raised, CARD).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    OperationTimeline(trip.startMs, trip.endMs ?: (recs.maxOfOrNull { it.record.atMs } ?: trip.startMs), recs)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Legend(RecordType.TURN_OK, "제때 켠 깜빡이")
                        Legend(RecordType.PARKING, "주차")
                        Legend(RecordType.TURN_NO_SIGNAL, "놓친 조작")
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    items(details, key = { it.seq }) { RecordRow(it, showDate = false); HorizontalDivider(color = Ink.line) }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatTile(
                        "깜빡이", if (summary.turns == 0) "-" else "${summary.signaled}/${summary.turns}",
                        if (summary.turns == 0) "회전 없음" else "회전 ${summary.turns}번 중 제때",
                        summary.missed + summary.late > 0, Modifier.weight(1f),
                    )
                    StatTile(
                        "후진 주차", summary.parkShifts.lastOrNull()?.let { "${it}번" } ?: "-",
                        if (summary.parkShifts.isEmpty()) "주차 없음" else "기어를 바꾼 횟수", false, Modifier.weight(1f),
                    )
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatTile("깜빡이 켜둔 채 주행", "${summary.leftOn}번", if (summary.leftOn == 0) "없었어요" else "차선 변경 뒤 확인", summary.leftOn > 0, Modifier.weight(1f))
                    val exit = summary.leftOpen + summary.notParked
                    StatTile("하차 체크", if (exit == 0) "✓" else "${exit}건", if (exit == 0) "이상 없음" else "기어·창문 확인", exit > 0, Modifier.weight(1f))
                }
            }
        }
    }
}

/** One number, readable from the driver's seat. An orange number (plus the wording below it) marks something to work on. */
@Composable
fun StatTile(label: String, value: String, note: String, attention: Boolean, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().background(Ink.raised, CARD).padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = Ink.secondary, fontSize = Type.body)
        Text(value, color = if (attention) Ink.record else Ink.primary, fontSize = Type.display * 1.4f, fontWeight = FontWeight.Bold)
        Text(note, color = if (attention) Ink.primary else Ink.muted, fontSize = Type.caption)
    }
}

@Composable
private fun Legend(type: RecordType, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(markGlyph(type), color = markColor(type), fontSize = Type.body, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(text, color = Ink.secondary, fontSize = Type.caption)
    }
}

/** Start→end line with one marker per record; the operation story of one trip. */
@Composable
fun OperationTimeline(startMs: Long, endMs: Long, recs: List<StoredRecord>) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        val y = 24.dp.toPx()
        val r = 11.dp.toPx()
        val span = (endMs - startMs).coerceAtLeast(1)
        val axis = TextStyle(color = Ink.muted, fontSize = Type.caption)
        drawLine(Ink.line, Offset(r, y), Offset(size.width - r, y), strokeWidth = 3.dp.toPx())
        drawText(measurer, Labels.time(startMs), Offset(0f, y + r + 4.dp.toPx()), style = axis)
        val endLabel = measurer.measure(Labels.time(endMs), axis)
        drawText(endLabel, topLeft = Offset(size.width - endLabel.size.width, y + r + 4.dp.toPx()))
        recs.forEach { rec ->
            val x = r + (size.width - 2 * r) * ((rec.record.atMs - startMs).toFloat() / span).coerceIn(0f, 1f)
            drawCircle(Ink.raised, radius = r + 2.dp.toPx(), center = Offset(x, y))
            drawCircle(markColor(rec.record.type), radius = r, center = Offset(x, y))
            val g = measurer.measure(markGlyph(rec.record.type), TextStyle(color = Color.White, fontSize = Type.caption * 0.8f, fontWeight = FontWeight.Bold))
            drawText(g, topLeft = Offset(x - g.size.width / 2, y - g.size.height / 2))
        }
    }
}

// ───────────────────────────── 성장 기록 ─────────────────────────────

private class Metric(
    val title: String, val unit: String, val decimals: Int, val types: Set<RecordType>,
    val value: (List<StoredRecord>) -> Float?,
)

private val METRICS = listOf(
    Metric("깜빡이 제때 켠 비율", "%", 0, setOf(RecordType.TURN_OK, RecordType.TURN_NO_SIGNAL, RecordType.TURN_LATE_SIGNAL)) { week ->
        Coach.summarize(week.map { it.record }).signalRatePct?.toFloat()
    },
    Metric("주차 한 번에 기어 변경", "번", 1, setOf(RecordType.PARKING)) { week ->
        Coach.summarize(week.map { it.record }).parkShifts.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    },
    Metric("깜빡이 켜둔 채 주행", "건", 0, setOf(RecordType.SIGNAL_LEFT_ON)) { week ->
        week.count { it.record.type == RecordType.SIGNAL_LEFT_ON }.toFloat()
    },
    Metric("하차 때 놓친 것", "건", 0, setOf(RecordType.LEFT_OPEN_ON_EXIT, RecordType.EXIT_NOT_PARKED)) { week ->
        week.count { it.record.type == RecordType.LEFT_OPEN_ON_EXIT || it.record.type == RecordType.EXIT_NOT_PARKED }.toFloat()
    },
)

/** Four small multiples, 12 weekly bars each: the driver against their own past, never against other drivers. */
@Composable
fun GrowthScreen(records: List<StoredRecord>, unsupported: Set<Domain>, nowMs: Long) {
    val weeks = 12
    val byWeek = remember(records, nowMs) {
        List(weeks) { i ->
            val ago = weeks - 1 - i
            records.filter { ((nowMs - it.record.atMs) / WEEK_MS).toInt() == ago && it.record.atMs <= nowMs }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        METRICS.chunked(2).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { m ->
                    val missing = m.types.any { t -> Labels.needs(t).any { it in unsupported } }
                    val values = remember(byWeek) { byWeek.map { if (it.isEmpty()) null else m.value(it) } }
                    WeeklyBars(m.title, values, m.unit, m.decimals, missing, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

private fun fmt(v: Float, decimals: Int) = String.format(Locale.US, "%.${decimals}f", v)

/**
 * The headline (first week → latest week) carries the message; the bars are the evidence.
 * Null means "no driving / nothing to measure that week" and is drawn as a gap, not as zero.
 */
@Composable
fun WeeklyBars(title: String, values: List<Float?>, unit: String, decimals: Int, notMeasurable: Boolean, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    var picked by remember { mutableStateOf<Int?>(null) }
    Column(modifier.background(Ink.raised, CARD).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                SectionTitle(title)
                val i = picked
                Text(
                    if (i == null) "막대를 누르면 그 주를 볼 수 있어요"
                    else (values.size - 1 - i).let { ago -> if (ago == 0) "이번 주" else "${ago}주 전" } +
                        " · " + (values[i]?.let { fmt(it, decimals) + unit } ?: "기록 없음"),
                    color = Ink.muted, fontSize = Type.caption,
                )
            }
            val known = values.filterNotNull()
            if (known.size >= 2) {
                Text(
                    "${fmt(known.first(), decimals)} → ${fmt(known.last(), decimals)}$unit",
                    color = Ink.primary, fontSize = Type.title * 1.2f, fontWeight = FontWeight.Bold,
                )
            }
        }
        if (notMeasurable) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("이 차량에서 측정할 수 없음", color = Ink.muted, fontSize = Type.body)
            }
            return@Column
        }
        Canvas(
            Modifier.fillMaxSize().padding(top = 8.dp).pointerInput(values) {
                detectTapGestures { pos -> picked = (pos.x / (size.width / values.size)).toInt().coerceIn(0, values.size - 1) }
            },
        ) {
            val label = TextStyle(color = Ink.secondary, fontSize = Type.caption)
            val axis = TextStyle(color = Ink.muted, fontSize = Type.caption)
            val axisH = 26.dp.toPx()
            val topPad = 26.dp.toPx()
            val plotH = size.height - axisH - topPad
            val slot = size.width / values.size
            val gap = slot * 0.22f
            val max = (values.filterNotNull().maxOrNull() ?: 0f).coerceAtLeast(1f)
            val base = topPad + plotH
            drawLine(Ink.line, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val h = plotH * v / max
                val x = i * slot + gap / 2
                if (v > 0) {
                    drawRoundRect(
                        if (picked == i) Ink.primary else Ink.series,
                        topLeft = Offset(x, base - h), size = Size(slot - gap, h),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
                val t = measurer.measure(fmt(v, decimals), label)
                drawText(t, topLeft = Offset(i * slot + (slot - t.size.width) / 2, base - h - t.size.height - 2.dp.toPx()))
            }
            val first = measurer.measure("${values.size - 1}주 전", axis)
            drawText(first, topLeft = Offset(0f, base + 2.dp.toPx()))
            val last = measurer.measure("이번 주", axis)
            drawText(last, topLeft = Offset(size.width - last.size.width, base + 2.dp.toPx()))
        }
    }
}

// ───────────────────────────── 자주 놓치는 곳 ─────────────────────────────

private data class PlaceGroup(val key: String, val lat: Double, val lng: Double, val recs: List<StoredRecord>, val turnsOk: Int) {
    val named get() = !key.startsWith("격자")
}

/** A single miss somewhere is noise; a pin has to earn its place on a map read at a glance. */
private const val MIN_MISSES_FOR_PIN = 3

/** Only the worst few: large pins must not overlap, and five places is as much as anyone will work on. */
private const val TOP_PLACES = 5

@Composable
fun PlacesScreen(trips: List<TripRow>, records: List<StoredRecord>) {
    val groups = remember(records) {
        val located = records.filter { it.record.lat != null }.groupBy { Places.keyFor(it.record.lat!!, it.record.lng!!) }
        located.mapNotNull { (key, all) ->
            val recs = all.filter { it.record.type in Labels.MISSES }
            if (recs.size < MIN_MISSES_FOR_PIN) return@mapNotNull null
            val known = Places.DEMO.firstOrNull { it.name == key }
            PlaceGroup(
                key, known?.lat ?: recs.map { it.record.lat!! }.average(), known?.lng ?: recs.map { it.record.lng!! }.average(),
                recs, all.count { it.record.type == RecordType.TURN_OK },
            )
        }.sortedByDescending { it.recs.size }.take(TOP_PLACES)
    }
    var selected by remember(groups) { mutableStateOf(groups.firstOrNull()?.key) }
    val measurer = rememberTextMeasurer()
    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Box(Modifier.weight(1.2f).fillMaxHeight()) {
            Canvas(
                Modifier.fillMaxSize().background(Ink.raised, CARD).clipToBounds().pointerInput(groups, trips) {
                    detectTapGestures { tap ->
                        val pts = trips.flatMap { it.path } + groups.map { it.lat to it.lng }
                        if (pts.isEmpty()) return@detectTapGestures
                        val p = Projector(pts, Size(size.width.toFloat(), size.height.toFloat()), 64f)
                        selected = groups.minByOrNull { (p.at(it.lat, it.lng) - tap).getDistance() }?.key
                    }
                },
            ) {
                val pts = trips.flatMap { it.path } + groups.map { it.lat to it.lng }
                if (pts.isEmpty()) return@Canvas
                val p = Projector(pts, size, 64f)
                trips.filter { it.path.size > 1 }.forEach { t ->
                    val line = Path().apply {
                        t.path.forEachIndexed { i, (lat, lng) -> p.at(lat, lng).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                    }
                    drawPath(line, Ink.series.copy(alpha = 0.18f), style = Stroke(width = 3.dp.toPx()))
                }
                val maxN = groups.maxOfOrNull { it.recs.size } ?: 1
                groups.forEach { g ->
                    val c = p.at(g.lat, g.lng)
                    // Never smaller than a fingertip: the pin is the tap target.
                    val r = 16.dp.toPx() + 26.dp.toPx() * sqrt(g.recs.size.toFloat() / maxN)
                    if (g.key == selected) drawCircle(Ink.primary, radius = r + 4.dp.toPx(), center = c)
                    drawCircle(Ink.record, radius = r, center = c)
                    val n = measurer.measure(g.recs.size.toString(), TextStyle(color = Color.White, fontSize = Type.caption, fontWeight = FontWeight.Bold))
                    drawText(n, topLeft = Offset(c.x - n.size.width / 2, c.y - n.size.height / 2))
                    if (g.named) {
                        val t = measurer.measure(g.key, TextStyle(color = Ink.primary, fontSize = Type.body, fontWeight = FontWeight.SemiBold))
                        val right = c.x + r + 8.dp.toPx()
                        // Flip the label to the left of the pin when it would run off the map.
                        val x = if (right + t.size.width > size.width) c.x - r - 8.dp.toPx() - t.size.width else right
                        drawText(t, topLeft = Offset(x, (c.y - t.size.height / 2).coerceIn(0f, size.height - t.size.height)))
                    }
                }
            }
        }
        Spacer(Modifier.width(20.dp))
        val g = groups.firstOrNull { it.key == selected }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (g == null) {
                Text("아직 자주 놓치는 곳이 없어요", color = Ink.secondary, fontSize = Type.title)
                return@Column
            }
            Text(if (g.named) g.key else "이름 없는 장소", color = Ink.primary, fontSize = Type.title * 1.2f, fontWeight = FontWeight.Bold)
            val turnMisses = g.recs.count { it.record.type == RecordType.TURN_NO_SIGNAL || it.record.type == RecordType.TURN_LATE_SIGNAL }
            if (turnMisses > 0) {
                Text(
                    "회전 ${turnMisses + g.turnsOk}번 중 ${g.turnsOk}번만 깜빡이를 제때 켰어요",
                    color = Ink.secondary, fontSize = Type.body,
                )
            }
            Spacer(Modifier.height(12.dp))
            g.recs.groupingBy { it.record.type }.eachCount().toList().sortedByDescending { it.second }.forEach { (type, n) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(Labels.name(type), Modifier.weight(1f), color = Ink.primary, fontSize = Type.body)
                    Text("${n}번", color = Ink.primary, fontSize = Type.body, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Ink.line)
            LazyColumn {
                items(g.recs.sortedByDescending { it.record.atMs }.take(20), key = { it.seq }) { RecordRow(it); HorizontalDivider(color = Ink.line) }
            }
        }
    }
}
