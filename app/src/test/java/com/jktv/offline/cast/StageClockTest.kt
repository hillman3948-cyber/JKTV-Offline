package com.jktv.offline.cast

import org.junit.Assert.*
import org.junit.Test

class StageClockTest {
    @Test fun transitCompensationRespectsTempoAndPause() {
        assertEquals(1150L, StageClock.target(1000, 10000, true, 1.5f, 100))
        assertEquals(1000L, StageClock.target(1000, 10000, false, 1.5f, 100))
    }
    @Test fun extremeTransitAndSongEndAreBounded() {
        assertEquals(1250L, StageClock.target(1000, 10000, true, 1f, 9000))
        assertEquals(2000L, StageClock.target(1990, 2000, true, 1f, 100))
        assertEquals(0L, StageClock.target(-1000, 0, true, 1f, -100))
    }
    @Test fun ordinaryJitterDoesNotContinuouslySeekButDiscontinuitiesDo() {
        assertFalse(StageClock.needsSeek(1000, 1100, true, false))
        assertTrue(StageClock.needsSeek(1000, 2000, true, false))
        assertTrue(StageClock.needsSeek(1000, 1000, true, true))
        assertTrue(StageClock.needsSeek(1000, 1020, false, false))
        assertFalse(StageClock.needsSeek(1000, 1000, false, false))
    }
}
