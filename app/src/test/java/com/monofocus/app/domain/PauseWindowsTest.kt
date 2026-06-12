package com.monofocus.app.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseWindowsTest {
    @Test
    fun fifteenMinutePauseEndsFifteenMinutesAfterNow() {
        assertEquals(
            1_900_000L,
            pauseForFifteenMinutesFrom(1_000_000L),
        )
    }

    @Test
    fun untilTomorrowEndsAtStartOfNextLocalDay() {
        val zone = ZoneId.of("Europe/Stockholm")
        val now = ZonedDateTime.of(2026, 6, 12, 23, 45, 0, 0, zone)
        val expected = ZonedDateTime.of(2026, 6, 13, 0, 0, 0, 0, zone)

        assertEquals(
            expected.toInstant().toEpochMilli(),
            pauseUntilTomorrowFrom(now),
        )
    }

    @Test
    fun activePauseRequiresFutureDeadline() {
        assertTrue(isPauseActive(pausedUntilEpochMillis = 2_000L, nowEpochMillis = 1_999L))
        assertFalse(isPauseActive(pausedUntilEpochMillis = 2_000L, nowEpochMillis = 2_000L))
        assertFalse(isPauseActive(pausedUntilEpochMillis = 0L, nowEpochMillis = 1_000L))
    }
}
