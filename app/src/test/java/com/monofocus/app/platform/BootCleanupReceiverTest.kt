package com.monofocus.app.platform

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootCleanupReceiverTest {
    @Test
    fun runsOnlyForBootCompleted() {
        assertTrue(shouldRunBootCleanup(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(shouldRunBootCleanup(Intent.ACTION_SHUTDOWN))
        assertFalse(shouldRunBootCleanup(Intent.ACTION_PACKAGE_REMOVED))
        assertFalse(shouldRunBootCleanup(null))
    }
}
