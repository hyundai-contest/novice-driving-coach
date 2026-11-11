package com.example.hyundai_contest.demo

import com.example.hyundai_contest.model.Record
import com.example.hyundai_contest.model.RecordType
import com.example.hyundai_contest.store.Place
import com.example.hyundai_contest.store.Places
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

data class GenTrip(
    val startMs: Long,
    val endMs: Long,
    val path: List<Pair<Double, Double>>,
    val records: List<Record>,
)

/**
 * Deterministic 12-week history for the demo persona: a new driver who just started commuting by car.
 * Everything improves week by week except one stubborn right turn, so the growth and places screens both have a story.
 */
object SyntheticHistory {
    private val home = place("집")
    private val cityHall = place("시청 사거리")
    private val office = place("회사 주차장")
    private val mart = place("동네마트")
    private val gym = place("헬스장")

    private fun place(name: String) = Places.DEMO.single { it.name == name }

    fun generate(seed: Long = 26L, endMs: Long, weeks: Int = 12): List<GenTrip> {
        val rnd = Random(seed)
        val zone = ZoneId.systemDefault()
        val lastDay = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate().minusDays(1)
        val firstDay = lastDay.minusDays(weeks * 7L - 1)
        val trips = mutableListOf<GenTrip>()
        for (i in 0 until weeks * 7) {
            val date = firstDay.plusDays(i.toLong())
            // 0.0 in the first week → 1.0 in the last: how far along the learner is.
            val skill = (i / 7).toDouble() / (weeks - 1)
            for ((route, hours) in routesFor(date, i / 7)) trips += trip(rnd, date, zone, route, hours, skill)
        }
        return trips.sortedBy { it.startMs }
    }

    private fun routesFor(date: LocalDate, w: Int): List<Pair<List<Place>, IntRange>> = buildList {
        val dow = date.dayOfWeek
        if (dow <= DayOfWeek.FRIDAY) {
            add(listOf(home, office) to 8..8)
            add(listOf(office, home) to 18..19)
        }
        if (dow == DayOfWeek.TUESDAY || dow == DayOfWeek.SATURDAY) {
            add(listOf(home, cityHall, mart) to 11..11)
            add(listOf(mart, cityHall, home) to 12..13)
        }
        if (dow == DayOfWeek.SUNDAY && w % 2 == 0) {
            add(listOf(home, gym) to 15..15)
            add(listOf(gym, home) to 16..16)
        }
    }

    private fun lerp(from: Double, to: Double, skill: Double) = from + (to - from) * skill

    private fun trip(rnd: Random, date: LocalDate, zone: ZoneId, route: List<Place>, hours: IntRange, skill: Double): GenTrip {
        val start = date.atTime(rnd.nextInt(hours.first, hours.last + 1), rnd.nextInt(0, 45))
            .atZone(zone).toInstant().toEpochMilli()
        val end = start + rnd.nextLong(10, 26) * 60_000
        val path = interpolate(route, 20)
        fun at(fraction: Double) = start + ((end - start) * fraction).toLong()
        fun point(fraction: Double) = path[((path.size - 1) * fraction).toInt()]
        fun side() = if (rnd.nextBoolean()) "LEFT" else "RIGHT"

        val records = mutableListOf<Record>()

        // Ordinary turns along the way: signalled in time more and more often.
        repeat(rnd.nextInt(3, 6)) {
            val f = rnd.nextDouble(0.1, 0.9)
            val (lat, lng) = point(f)
            val roll = rnd.nextDouble()
            val ok = lerp(0.55, 0.95, skill)
            records += when {
                roll < ok -> Record(RecordType.TURN_OK, at(f), lat, lng, mapOf("side" to side()))
                roll < ok + (1 - ok) * 0.5 ->
                    Record(RecordType.TURN_LATE_SIGNAL, at(f), lat, lng, mapOf("side" to side(), "leadMs" to rnd.nextLong(-800, 900).toString()))
                else -> Record(RecordType.TURN_NO_SIGNAL, at(f), lat, lng, mapOf("side" to side()))
            }
        }
        // The stubborn one: the right turn at the city hall intersection improves much more slowly.
        if (cityHall in route) {
            val type = if (rnd.nextDouble() < lerp(0.8, 0.45, skill)) RecordType.TURN_NO_SIGNAL else RecordType.TURN_OK
            records += Record(type, at(0.5), cityHall.lat + jitter(rnd), cityHall.lng + jitter(rnd), mapOf("side" to "RIGHT"))
        }
        if (rnd.nextDouble() < lerp(0.30, 0.03, skill)) {
            val f = rnd.nextDouble(0.2, 0.9)
            val (lat, lng) = point(f)
            records += Record(
                RecordType.SIGNAL_LEFT_ON, at(f), lat, lng,
                mapOf("side" to side(), "heldMs" to rnd.nextLong(10_000, 40_000).toString()),
            )
        }
        // Reverse parking at the destination: from about seven gear changes down to about two.
        val shifts = (lerp(7.0, 2.0, skill) + rnd.nextDouble(-1.0, 1.0)).toInt().coerceAtLeast(1)
        records += Record(
            RecordType.PARKING, end - 5_000, route.last().lat, route.last().lng,
            mapOf(
                "shifts" to shifts.toString(),
                "steerReversals" to (shifts + rnd.nextInt(0, 3)).toString(),
                "durationMs" to (shifts * rnd.nextLong(12_000, 20_000)).toString(),
            ),
        )
        if (rnd.nextDouble() < lerp(0.12, 0.01, skill)) {
            val seat = listOf("FR", "RL", "FL")[rnd.nextInt(3)]
            records += Record(
                RecordType.LEFT_OPEN_ON_EXIT, end, route.last().lat, route.last().lng,
                mapOf("windows" to seat, "headlamp" to "false"),
            )
        }
        if (skill < 0.3 && rnd.nextDouble() < 0.04) {
            records += Record(RecordType.EXIT_NOT_PARKED, end - 8_000, route.last().lat, route.last().lng, mapOf("gear" to "DRIVE"))
        }
        return GenTrip(start, end, path, records.sortedBy { it.atMs })
    }

    /** ~20 m of noise so pins are not stacked on one pixel. */
    private fun jitter(rnd: Random) = rnd.nextDouble(-0.00015, 0.00015)

    private fun interpolate(route: List<Place>, points: Int): List<Pair<Double, Double>> {
        val legs = route.zipWithNext()
        return (0 until points).map { i ->
            val t = i.toDouble() / (points - 1) * legs.size
            val leg = minOf(t.toInt(), legs.size - 1)
            val f = t - leg
            val (a, b) = legs[leg]
            (a.lat + (b.lat - a.lat) * f) to (a.lng + (b.lng - a.lng) * f)
        }
    }
}
