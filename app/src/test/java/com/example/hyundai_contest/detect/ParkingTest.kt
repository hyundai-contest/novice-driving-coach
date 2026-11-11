package com.example.hyundai_contest.detect

import com.example.hyundai_contest.model.CarState
import com.example.hyundai_contest.model.EngineEvent
import com.example.hyundai_contest.model.Gear
import com.example.hyundai_contest.model.RecordType
import com.example.hyundai_contest.model.Seat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingTest {
    private fun g(atS: Int, gear: Gear, steer: Float = 0f, kph: Float? = 4f) =
        CarState(atMs = atS * 1000L, gear = gear, speedKph = kph, steeringDeg = steer)

    @Test fun cleanReverseParkIsOneShift() {
        val r = records(
            drive(parked, g(1, Gear.DRIVE), g(5, Gear.REVERSE, steer = -200f), g(9, Gear.REVERSE), g(12, Gear.PARK, kph = 0f)),
        ).single()
        assertEquals(RecordType.PARKING, r.record.type)
        assertEquals("1", r.record.detail["shifts"])
        assertEquals("0", r.record.detail["steerReversals"])
        assertEquals("7000", r.record.detail["durationMs"])
        assertNull(r.announce)
    }

    @Test fun backAndForthCountsEveryShiftAndSteeringReversal() {
        val r = records(
            drive(
                parked, g(1, Gear.DRIVE),
                g(5, Gear.REVERSE, steer = -200f),
                g(9, Gear.DRIVE, steer = 200f), // pulled forward, wheel wound the other way
                g(13, Gear.REVERSE, steer = -200f),
                g(17, Gear.REVERSE, steer = 30f), // straightening is not a reversal
                g(20, Gear.PARK, kph = 0f),
            ),
        ).single()
        assertEquals("3", r.record.detail["shifts"])
        assertEquals("2", r.record.detail["steerReversals"])
    }

    @Test fun fullLockSteeringWhileParkingIsNotJudgedAsATurn() {
        val ev = drive(
            parked, g(1, Gear.DRIVE, kph = 10f),
            g(5, Gear.REVERSE, steer = -200f, kph = 10f), g(7, Gear.REVERSE, steer = -200f, kph = 10f),
            g(9, Gear.REVERSE, kph = 10f), g(10, Gear.PARK, kph = 0f),
        )
        assertEquals(listOf(RecordType.PARKING), records(ev).map { it.record.type })
    }

    @Test fun reversingStraightOutOfParkIsNotParking() {
        // Backing out of the garage, then P to get out and close the garage door.
        val ev = drive(parked, g(1, Gear.REVERSE), g(8, Gear.PARK, kph = 0f))
        assertTrue(records(ev).isEmpty())
    }

    @Test fun threePointTurnThenDrivingOffIsNotParking() {
        val ev = drive(
            parked, g(1, Gear.DRIVE), g(3, Gear.REVERSE), g(6, Gear.DRIVE), g(8, Gear.DRIVE, kph = 30f),
            g(600, Gear.DRIVE, kph = 5f), g(605, Gear.PARK, kph = 0f),
        )
        assertTrue(records(ev).isEmpty())
    }

    @Test fun stayingInDriveWithUnknownSpeedAlsoAbandons() {
        val ev = drive(
            parked, g(1, Gear.DRIVE, kph = null), g(3, Gear.REVERSE, kph = null), g(5, Gear.DRIVE, kph = null),
            g(30, Gear.DRIVE, kph = null), g(40, Gear.PARK, kph = null),
        )
        assertTrue(records(ev).isEmpty())
    }

    @Test fun waitingInDriveAtKnownLowSpeedKeepsTheManoeuvre() {
        // Pulled forward and waited 25 s for a car to pass: still the same parking attempt.
        val r = records(
            drive(
                parked, g(1, Gear.DRIVE), g(3, Gear.REVERSE), g(6, Gear.DRIVE, kph = 0f), g(31, Gear.DRIVE, kph = 0f),
                g(32, Gear.REVERSE), g(36, Gear.PARK, kph = 0f),
            ),
        ).single()
        assertEquals("3", r.record.detail["shifts"])
    }

    @Test fun wheelLeftTurnedAfterParkingIsNotATurn() {
        // Underground car park: no GPS speed, wheel still at full lock when shifting to P, straightened later.
        val ev = drive(
            parked, g(1, Gear.DRIVE, kph = null), g(3, Gear.REVERSE, steer = -200f, kph = null),
            g(8, Gear.PARK, steer = -200f, kph = null), g(10, Gear.PARK, steer = -200f, kph = null), g(11, Gear.PARK, kph = null),
        )
        assertEquals(listOf(RecordType.PARKING), records(ev).map { it.record.type })
    }

    @Test fun shiftingToParkWithTheDoorAlreadyOpenEndsTheTrip() {
        val open = setOf(Seat.FL)
        val win = mapOf(Seat.FR to 40)
        val ev = drive(
            parked, g(1, Gear.DRIVE).copy(windowsOpenPct = win), g(5, Gear.DRIVE, kph = 0f).copy(windowsOpenPct = win),
            g(6, Gear.DRIVE, kph = 0f).copy(doorsOpen = open, windowsOpenPct = win),
            g(8, Gear.PARK, kph = 0f).copy(doorsOpen = open, windowsOpenPct = win),
        )
        assertEquals(listOf(RecordType.EXIT_NOT_PARKED, RecordType.LEFT_OPEN_ON_EXIT), records(ev).map { it.record.type })
        assertEquals(1, ev.count { it is EngineEvent.TripEnded })
    }

    @Test fun unknownGearIsNotReportedAsNotParked() {
        val ev = drive(parked, g(1, Gear.DRIVE), g(5, Gear.UNKNOWN, kph = 0f), g(6, Gear.UNKNOWN, kph = 0f).copy(doorsOpen = setOf(Seat.FL)))
        assertTrue(records(ev).isEmpty())
    }

    @Test fun driverDoorOpenedOutOfParkIsAnnouncedAndTripGoesOn() {
        val ev = drive(
            parked, g(1, Gear.DRIVE), g(5, Gear.DRIVE, kph = 0f),
            g(6, Gear.DRIVE, kph = 0f).copy(doorsOpen = setOf(Seat.FL)),
        )
        val r = records(ev).single()
        assertEquals(RecordType.EXIT_NOT_PARKED, r.record.type)
        assertEquals("DRIVE", r.record.detail["gear"])
        assertEquals(Announcements.EXIT_NOT_PARKED, r.announce)
        assertTrue(ev.none { it is EngineEvent.TripEnded })
    }

    @Test fun doorOpeningWhileMovingIsNotAnExit() {
        val ev = drive(parked, g(1, Gear.DRIVE, kph = 30f), g(2, Gear.DRIVE, kph = 30f).copy(doorsOpen = setOf(Seat.FL)))
        assertTrue(records(ev).isEmpty())
    }
}
