package com.example.hyundai_contest.detect

import com.example.hyundai_contest.model.CarState
import com.example.hyundai_contest.model.EngineEvent
import com.example.hyundai_contest.model.Gear
import com.example.hyundai_contest.model.RecordType
import com.example.hyundai_contest.model.Seat
import com.example.hyundai_contest.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Feeds states in order to a fresh engine and returns every event it emitted. */
fun drive(vararg states: CarState): List<EngineEvent> {
    val engine = DetectionEngine()
    return states.flatMap { engine.onState(it) }
}

fun records(events: List<EngineEvent>) = events.filterIsInstance<EngineEvent.NewRecord>()

/** States every second from [fromS] to [toS] inclusive, built by [f]. */
fun seconds(fromS: Int, toS: Int, f: (Long) -> CarState): Array<CarState> =
    (fromS..toS).map { f(it * 1000L) }.toTypedArray()

val parked = CarState(atMs = 0, gear = Gear.PARK, speedKph = 0f, steeringDeg = 0f)

class DetectionEngineTest {

    @Test fun tripStartsWhenLeavingPark() {
        val ev = drive(parked, parked.copy(atMs = 1000, gear = Gear.DRIVE))
        assertEquals(1, ev.filterIsInstance<EngineEvent.TripStarted>().size)
    }

    @Test fun firstStateAlreadyDriveStartsTripWithoutRecords() {
        val ev = drive(CarState(atMs = 0, gear = Gear.DRIVE, speedKph = 50f, steeringDeg = 0f, turnSignal = Side.LEFT))
        assertEquals(listOf(EngineEvent.TripStarted::class), ev.map { it::class })
    }

    @Test fun exitIsParkPlusDriverDoorOpen() {
        val d = parked.copy(atMs = 1000, gear = Gear.DRIVE)
        val p = parked.copy(atMs = 2000)
        val ev = drive(parked, d, p, p.copy(atMs = 3000, doorsOpen = setOf(Seat.FL)))
        assertEquals(1, ev.filterIsInstance<EngineEvent.TripEnded>().size)
    }

    @Test fun signalLeftOnRecordedOnceAfter10sStraight() {
        val ev = drive(parked, *seconds(1, 12) {
            CarState(atMs = it, gear = Gear.DRIVE, speedKph = 25f, steeringDeg = 0f, turnSignal = Side.LEFT)
        })
        val r = records(ev).filter { it.record.type == RecordType.SIGNAL_LEFT_ON }
        assertEquals(1, r.size)
        assertEquals("왼쪽 깜빡이가 켜져 있어요", r[0].announce)
        assertEquals("LEFT", r[0].record.detail["side"])
    }

    @Test fun signalOnWhileTurningIsNotRecorded() {
        val ev = drive(parked, *seconds(1, 12) {
            CarState(atMs = it, gear = Gear.DRIVE, speedKph = 25f, steeringDeg = 120f, turnSignal = Side.LEFT)
        })
        assertTrue(records(ev).none { it.record.type == RecordType.SIGNAL_LEFT_ON })
    }

    @Test fun signalOnBelowSpeedIsNotRecorded() {
        val ev = drive(parked, *seconds(1, 12) {
            CarState(atMs = it, gear = Gear.DRIVE, speedKph = 10f, steeringDeg = 0f, turnSignal = Side.LEFT)
        })
        assertTrue(records(ev).isEmpty())
    }

    @Test fun unknownSpeedUsesDriveGear() {
        val ev = drive(parked.copy(speedKph = null), *seconds(1, 12) {
            CarState(atMs = it, gear = Gear.DRIVE, speedKph = null, steeringDeg = 0f, turnSignal = Side.RIGHT)
        })
        val r = records(ev).filter { it.record.type == RecordType.SIGNAL_LEFT_ON }
        assertEquals(1, r.size)
        assertEquals("오른쪽 깜빡이가 켜져 있어요", r[0].announce)
    }

    @Test fun exitWithOpenWindowRecordsAndAnnounces() {
        val win = mapOf(Seat.FR to 40)
        val ev = drive(
            parked, parked.copy(atMs = 1000, gear = Gear.DRIVE, windowsOpenPct = win),
            parked.copy(atMs = 2000, windowsOpenPct = win),
            parked.copy(atMs = 3000, windowsOpenPct = win, doorsOpen = setOf(Seat.FL)),
        )
        val r = records(ev).single { it.record.type == RecordType.LEFT_OPEN_ON_EXIT }
        assertEquals("FR", r.record.detail["windows"])
        assertEquals("조수석 창문이 열려 있어요", r.announce)
    }

    @Test fun exitHeadlampIgnoredWhenAuto() {
        val ev = drive(
            parked, parked.copy(atMs = 1000, gear = Gear.DRIVE),
            parked.copy(atMs = 2000, headlampOn = true, headlampAuto = true),
            parked.copy(atMs = 3000, headlampOn = true, headlampAuto = true, doorsOpen = setOf(Seat.FL)),
        )
        assertTrue(records(ev).isEmpty())
    }

    @Test fun exitHeadlampRecordedWhenManual() {
        val ev = drive(
            parked, parked.copy(atMs = 1000, gear = Gear.DRIVE),
            parked.copy(atMs = 2000, headlampOn = true, headlampAuto = false),
            parked.copy(atMs = 3000, headlampOn = true, headlampAuto = false, doorsOpen = setOf(Seat.FL)),
        )
        val r = records(ev).single()
        assertEquals("전조등이 켜져 있어요", r.announce)
        assertEquals("true", r.record.detail["headlamp"])
    }

    @Test fun announcementJoinsSeats() {
        assertEquals(
            "운전석, 조수석 창문이 열려 있어요. 전조등이 켜져 있어요",
            Announcements.leftOpenOnExit(listOf(Seat.FL, Seat.FR), true),
        )
        assertNull(Announcements.leftOpenOnExit(emptyList(), false))
    }
}
