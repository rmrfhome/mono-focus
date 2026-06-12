package com.monofocus.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundDetectionStabilizerTest {
    @Test
    fun defaultTimingValuesMatchProductionSpec() {
        assertTrue(ForegroundDetectionDefaults.POLL_INTERVAL_MILLIS in 500L..1_000L)
        assertTrue(ForegroundDetectionDefaults.DEBOUNCE_MILLIS in 300L..700L)
        assertEquals(1_500L, ForegroundDetectionDefaults.UNRELIABLE_TIMEOUT_MILLIS)
        assertTrue(ForegroundDetectionDefaults.INITIAL_LOOKBACK_MILLIS >= 1_500L)
        assertTrue(ForegroundDetectionDefaults.QUERY_OVERLAP_MILLIS > 0L)
    }

    @Test
    fun emitsForegroundPackageOnlyAfterDebounceWindow() {
        val stabilizer = stabilizer()

        assertSame(
            ForegroundStabilizerOutput.NoEmission,
            stabilizer.update(ForegroundDetection("com.example.social", reliable = true), 0L),
        )

        assertEquals(
            ForegroundStabilizerOutput.Emit("com.example.social"),
            stabilizer.update(
                ForegroundDetection("com.example.social", reliable = true),
                ForegroundDetectionDefaults.DEBOUNCE_MILLIS,
            ),
        )
    }

    @Test
    fun suppressesTransientForegroundPackageChanges() {
        val stabilizer = stabilizer()

        stabilizer.update(ForegroundDetection("com.example.social", reliable = true), 0L)
        stabilizer.update(
            ForegroundDetection("com.example.reader", reliable = true),
            ForegroundDetectionDefaults.DEBOUNCE_MILLIS / 2,
        )
        stabilizer.update(
            ForegroundDetection("com.example.social", reliable = true),
            ForegroundDetectionDefaults.DEBOUNCE_MILLIS - 100L,
        )

        assertSame(
            ForegroundStabilizerOutput.NoEmission,
            stabilizer.update(
                ForegroundDetection("com.example.social", reliable = true),
                ForegroundDetectionDefaults.DEBOUNCE_MILLIS - 1L,
            ),
        )
        assertEquals(
            ForegroundStabilizerOutput.Emit("com.example.social"),
            stabilizer.update(
                ForegroundDetection("com.example.social", reliable = true),
                ForegroundDetectionDefaults.DEBOUNCE_MILLIS * 2,
            ),
        )
    }

    @Test
    fun doesNotEmitDuplicateStablePackages() {
        val stabilizer = stabilizer()

        stabilizer.update(ForegroundDetection("com.example.social", reliable = true), 0L)
        assertEquals(
            ForegroundStabilizerOutput.Emit("com.example.social"),
            stabilizer.update(
                ForegroundDetection("com.example.social", reliable = true),
                ForegroundDetectionDefaults.DEBOUNCE_MILLIS,
            ),
        )

        assertSame(
            ForegroundStabilizerOutput.NoEmission,
            stabilizer.update(
                ForegroundDetection("com.example.social", reliable = true),
                ForegroundDetectionDefaults.DEBOUNCE_MILLIS * 2,
            ),
        )
    }

    @Test
    fun unreliableDetectionKeepsPreviousPackageUntilTimeout() {
        val stabilizer = stabilizer()

        stabilizer.update(ForegroundDetection("com.example.social", reliable = true), 0L)
        stabilizer.update(
            ForegroundDetection("com.example.social", reliable = true),
            ForegroundDetectionDefaults.DEBOUNCE_MILLIS,
        )

        assertSame(
            ForegroundStabilizerOutput.NoEmission,
            stabilizer.update(ForegroundDetection("com.example.social", reliable = false), 1_000L),
        )
        assertEquals("com.example.social", stabilizer.activePackage)

        assertSame(
            ForegroundStabilizerOutput.NoEmission,
            stabilizer.update(ForegroundDetection("com.example.social", reliable = false), 2_499L),
        )
        assertEquals("com.example.social", stabilizer.activePackage)
    }

    @Test
    fun unreliableDetectionEmitsUnknownImmediatelyAtTimeout() {
        val stabilizer = stabilizer()

        stabilizer.update(ForegroundDetection("com.example.social", reliable = true), 0L)
        stabilizer.update(
            ForegroundDetection("com.example.social", reliable = true),
            ForegroundDetectionDefaults.DEBOUNCE_MILLIS,
        )
        stabilizer.update(ForegroundDetection("com.example.social", reliable = false), 1_000L)

        assertEquals(
            ForegroundStabilizerOutput.Emit(null),
            stabilizer.update(ForegroundDetection("com.example.social", reliable = false), 2_500L),
        )
        assertEquals(null, stabilizer.activePackage)
    }

    private fun stabilizer() = ForegroundDetectionStabilizer(
        debounceMillis = ForegroundDetectionDefaults.DEBOUNCE_MILLIS,
        unreliableTimeoutMillis = ForegroundDetectionDefaults.UNRELIABLE_TIMEOUT_MILLIS,
        initialElapsedMillis = 0L,
    )
}
