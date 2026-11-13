package com.example.hyundai_contest.detect

import com.example.hyundai_contest.model.CarState
import com.example.hyundai_contest.model.Gear
import com.example.hyundai_contest.model.RecordType
import com.example.hyundai_contest.model.Seat
import com.example.hyundai_contest.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedAndDepartureTest {
    private fun types(vararg states: CarState) = records(drive(*states)).map { it.record.type }

    @Test fun fastTurnIsRecordedNextToTheSignalVerdict() {
        val r = records(drive(parked, *turn(-120f, mapOf(1000L to Side.LEFT), kph = 34f)))
        assertEquals(listOf(RecordType.TURN_OK, RecordType.TURN_FAST), r.map { it.record.type })
        assertEquals("34", r[0].record.detail["kph"])
        assertEquals("34", r[1].record.detail["kph"])
        assertNull(r[0].announce)
    }

    @Test fun speedingUpAfterStraighteningIsNotAFastTurn() {
        val states = arrayOf(d(1000), d(4000, steer = -120f), d(5000, steer = -120f), d(6000, steer = -120f), d(7000, steer = 0f, kph = 45f))
        assertEquals(listOf(RecordType.TURN_NO_SIGNAL), types(parked, *states))
    }

    @Test fun turnBelowTheLimitIsNotFast() {
        assertEquals(listOf(RecordType.TURN_NO_SIGNAL), types(parked, *turn(-120f, kph = 29f)))
    }

    @Test fun turnWithUnknownSpeedIsNeverFast() {
        assertEquals(listOf(RecordType.TURN_NO_SIGNAL), types(parked, *turn(-120f, kph = null)))
    }

    private fun g(atS: Int, gear: Gear, kph: Float?) = CarState(atMs = atS * 1000L, gear = gear, speedKph = kph, steeringDeg = 0f)

    @Test fun fastReverseIsSpokenOncePerTimeInReverse() {
        val r = records(drive(parked, g(1, Gear.REVERSE, 2f), g(2, Gear.REVERSE, 9f), g(3, Gear.REVERSE, 10f), g(4, Gear.REVERSE, 2f)))
        assertEquals(listOf(RecordType.REVERSE_FAST), r.map { it.record.type })
        assertEquals("9", r[0].record.detail["kph"])
        assertEquals(Announcements.REVERSE_FAST, r[0].announce)
    }

    @Test fun reverseWithUnknownSpeedIsNotJudged() {
        assertTrue(types(parked, g(1, Gear.REVERSE, null), g(2, Gear.REVERSE, null)).isEmpty())
    }

    @Test fun shiftingBeforeTheCarStopsIsCountedInTheParking() {
        val r = records(
            drive(parked, g(1, Gear.DRIVE, 10f), g(5, Gear.REVERSE, 4f), g(9, Gear.DRIVE, 0f), g(13, Gear.REVERSE, 0f), g(17, Gear.PARK, 0f)),
        ).single()
        assertEquals("3", r.record.detail["shifts"])
        assertEquals("1", r.record.detail["rollingShifts"])
    }

    @Test fun movingOffUnbuckledIsRecordedSilently() {
        val r = records(drive(parked, g(1, Gear.DRIVE, 0f).copy(beltBuckled = false), g(2, Gear.DRIVE, 8f).copy(beltBuckled = false))).single()
        assertEquals(RecordType.DEPART_UNREADY, r.record.type)
        assertEquals("false", r.record.detail["belt"])
        assertNull(r.announce)
    }

    @Test fun bucklingUpBeforeMovingIsFine() {
        assertTrue(types(parked, g(1, Gear.DRIVE, 0f).copy(beltBuckled = false), g(2, Gear.DRIVE, 8f).copy(beltBuckled = true)).isEmpty())
    }

    @Test fun unknownBeltIsNotAMistake() {
        assertTrue(types(parked, g(1, Gear.DRIVE, 0f), g(2, Gear.DRIVE, 8f)).isEmpty())
    }

    @Test fun movingOffWithADoorOpenIsSpoken() {
        val r = records(drive(parked, g(1, Gear.DRIVE, 8f).copy(doorsOpen = setOf(Seat.RL)))).single()
        assertEquals(RecordType.DEPART_UNREADY, r.record.type)
        assertEquals("왼쪽 뒷좌석 문이 열려 있어요", r.announce)
    }

    @Test fun departureIsCheckedOnlyOncePerTrip() {
        val open = setOf(Seat.RL)
        assertEquals(1, types(parked, g(1, Gear.DRIVE, 8f).copy(doorsOpen = open), g(2, Gear.DRIVE, 0f), g(3, Gear.DRIVE, 8f).copy(doorsOpen = open)).size)
    }

    @Test fun shiftingIntoDriveWithNoSpeedSignalIsNotMovingOff() {
        assertTrue(types(parked, g(1, Gear.DRIVE, null).copy(beltBuckled = false), g(2, Gear.DRIVE, null).copy(beltBuckled = false)).isEmpty())
    }

    @Test fun departureIsCheckedAgainOnTheNextTrip() {
        val open = setOf(Seat.RL)
        val exit = g(3, Gear.PARK, 0f).copy(doorsOpen = setOf(Seat.FL))
        assertEquals(
            listOf(RecordType.DEPART_UNREADY, RecordType.DEPART_UNREADY),
            types(parked, g(1, Gear.DRIVE, 8f).copy(doorsOpen = open), g(2, Gear.PARK, 0f), exit, g(4, Gear.PARK, 0f), g(5, Gear.DRIVE, 8f).copy(doorsOpen = open)),
        )
    }

    @Test fun fastReverseIsJudgedAgainAfterLeavingReverse() {
        assertEquals(2, types(parked, g(1, Gear.REVERSE, 9f), g(2, Gear.DRIVE, 0f), g(3, Gear.REVERSE, 9f)).count { it == RecordType.REVERSE_FAST })
    }

    @Test fun highBeamTimerRestartsWhenTheCarSlowsDown() {
        val states = (1..20).map { beam(it, true) } + beam(21, true, kph = 5f) + (22..45).map { beam(it, true) }
        assertTrue(types(parked, *states.toTypedArray()).isEmpty())
    }

    private fun beam(atS: Int, on: Boolean, kph: Float = 50f) = g(atS, Gear.DRIVE, kph).copy(highBeamOn = on)

    @Test fun highBeamLeftOnIsSpokenOnceAfterHalfAMinute() {
        val r = records(drive(parked, *(1..40).map { beam(it, true) }.toTypedArray()))
        assertEquals(listOf(RecordType.HIGH_BEAM_LEFT_ON), r.map { it.record.type })
        assertEquals(Announcements.HIGH_BEAM_LEFT_ON, r[0].announce)
    }

    @Test fun aFlashOfHighBeamIsNotRecorded() {
        assertTrue(types(parked, beam(1, false), beam(2, true), beam(4, true), beam(5, false), beam(40, false)).isEmpty())
    }
}
