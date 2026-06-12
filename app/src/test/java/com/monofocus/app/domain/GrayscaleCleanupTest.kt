package com.monofocus.app.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrayscaleCleanupTest {
    @Test
    fun deactivateBestEffortReturnsTrueWhenDeactivationCompletes() = runTest {
        val controller = FakeGrayscaleController()

        assertTrue(controller.deactivateBestEffort())
        assertEquals(1, controller.deactivationCount)
    }

    @Test
    fun deactivateBestEffortReturnsFalseWhenDeactivationThrows() = runTest {
        val controller = FakeGrayscaleController(throwOnDeactivate = true)

        assertFalse(controller.deactivateBestEffort())
        assertEquals(1, controller.deactivationCount)
    }

    @Test
    fun deactivateBestEffortReturnsFalseWhenDeactivationTimesOut() = runTest {
        val controller = FakeGrayscaleController(delayMillis = 1_000L)

        assertFalse(controller.deactivateBestEffort(timeoutMillis = 100L))
        assertEquals(1, controller.deactivationCount)
    }

    private class FakeGrayscaleController(
        private val delayMillis: Long = 0L,
        private val throwOnDeactivate: Boolean = false,
    ) : GrayscaleController {
        var deactivationCount = 0
            private set

        override suspend fun ensureReady(): Boolean = true

        override suspend fun setGrayscaleActive(active: Boolean) = Unit

        override suspend fun deactivate() {
            deactivationCount += 1
            if (delayMillis > 0L) delay(delayMillis)
            if (throwOnDeactivate) throw IllegalStateException("deactivation failed")
        }
    }
}
