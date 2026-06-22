package com.monofocus.app.service

import com.monofocus.app.domain.EngineStopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun reportsNoBlockReasonWhenMonitoringCanStart() {
        assertNull(
            monitoringStartBlockReason(
                engineEnabled = true,
                permissionsReady = true,
                hasSelectedLaunchableApps = true,
            ),
        )
    }

    @Test
    fun reportsPreflightBlockReasonInPriorityOrder() {
        assertEquals(
            EngineStopReason.EngineDisabled,
            monitoringStartBlockReason(
                engineEnabled = false,
                permissionsReady = false,
                hasSelectedLaunchableApps = false,
            ),
        )
        assertEquals(
            EngineStopReason.PermissionsMissing,
            monitoringStartBlockReason(
                engineEnabled = true,
                permissionsReady = false,
                hasSelectedLaunchableApps = false,
            ),
        )
        assertEquals(
            EngineStopReason.NoSelectedApps,
            monitoringStartBlockReason(
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
