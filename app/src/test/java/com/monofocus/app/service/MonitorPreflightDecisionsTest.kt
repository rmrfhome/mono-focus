package com.monofocus.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorPreflightDecisionsTest {
    @Test
    fun startsForegroundMonitoringOnlyWhenPersistedEnabledPermissionsReadyAndAppsSelected() {
        assertTrue(
            shouldStartForegroundMonitoring(
                engineEnabled = true,
                permissionsReady = true,
                hasSelectedLaunchableApps = true,
            ),
        )
    }

    @Test
    fun doesNotStartForegroundMonitoringWhenPersistedEngineIsDisabled() {
        assertFalse(
            shouldStartForegroundMonitoring(
                engineEnabled = false,
                permissionsReady = true,
                hasSelectedLaunchableApps = true,
            ),
        )
    }

    @Test
    fun doesNotStartForegroundMonitoringWhenPermissionsAreMissing() {
        assertFalse(
            shouldStartForegroundMonitoring(
                engineEnabled = true,
                permissionsReady = false,
                hasSelectedLaunchableApps = true,
            ),
        )
    }

    @Test
    fun doesNotStartForegroundMonitoringWhenNoSelectedLaunchableAppsRemain() {
        assertFalse(
            shouldStartForegroundMonitoring(
                engineEnabled = true,
                permissionsReady = true,
                hasSelectedLaunchableApps = false,
            ),
        )
    }

    @Test
    fun runsMonitorOnlyWhenPersistedEnabledPermissionsReadyAndLaunchableAppsSelected() {
        assertTrue(
            shouldRunMonitor(
                engineEnabled = true,
                permissionsReady = true,
                hasSelectedLaunchableApps = true,
            ),
        )
    }

    @Test
    fun doesNotRunMonitorWhenPersistedEngineIsDisabled() {
        assertFalse(
            shouldRunMonitor(
                engineEnabled = false,
                permissionsReady = true,
                hasSelectedLaunchableApps = true,
            ),
        )
    }

    @Test
    fun doesNotRunMonitorWhenPermissionsAreMissing() {
        assertFalse(
            shouldRunMonitor(
                engineEnabled = true,
                permissionsReady = false,
                hasSelectedLaunchableApps = true,
            ),
        )
    }

    @Test
    fun doesNotRunMonitorWhenNoSelectedLaunchableAppsRemain() {
        assertFalse(
            shouldRunMonitor(
                engineEnabled = true,
                permissionsReady = true,
                hasSelectedLaunchableApps = false,
            ),
        )
    }
}
