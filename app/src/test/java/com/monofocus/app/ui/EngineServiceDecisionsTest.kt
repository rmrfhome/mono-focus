package com.monofocus.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineServiceDecisionsTest {
    @Test
    fun toggleEnablesAndStartsOnlyWhenRequestedReadyAndAppsSelected() {
        assertEquals(
            EngineToggleAction.EnableAndStart,
            chooseEngineToggleAction(
                requestedEnabled = true,
                permissionsReady = true,
                hasSelectedApps = true,
            ),
        )
    }

    @Test
    fun toggleDisablesAndStopsWhenUserTurnsEngineOff() {
        assertEquals(
            EngineToggleAction.DisableAndStop,
            chooseEngineToggleAction(
                requestedEnabled = false,
                permissionsReady = true,
                hasSelectedApps = true,
            ),
        )
    }

    @Test
    fun toggleStoresEnabledIntentWithoutStartingWhenPermissionsAreMissing() {
        assertEquals(
            EngineToggleAction.EnableWithoutStarting,
            chooseEngineToggleAction(
                requestedEnabled = true,
                permissionsReady = false,
                hasSelectedApps = true,
            ),
        )
    }

    @Test
    fun toggleStoresEnabledIntentWithoutStartingWhenNoAppsAreSelected() {
        assertEquals(
            EngineToggleAction.EnableWithoutStarting,
            chooseEngineToggleAction(
                requestedEnabled = true,
                permissionsReady = true,
                hasSelectedApps = false,
            ),
        )
    }

    @Test
    fun resumeRestoresMonitoringOnlyWhenPersistedEnabledReadyAndAppsSelected() {
        assertEquals(
            EngineResumeAction.EnsureRuleAndStart,
            chooseEngineResumeAction(
                engineEnabled = true,
                permissionsReady = true,
                hasSelectedApps = true,
            ),
        )
    }

    @Test
    fun resumeStopsMonitoringOnlyWhenPersistedEnabledButPermissionsAreMissing() {
        assertEquals(
            EngineResumeAction.StopMonitoringOnly,
            chooseEngineResumeAction(
                engineEnabled = true,
                permissionsReady = false,
                hasSelectedApps = true,
            ),
        )
    }

    @Test
    fun resumeStopsMonitoringOnlyWhenPersistedEnabledButNoAppsAreSelected() {
        assertEquals(
            EngineResumeAction.StopMonitoringOnly,
            chooseEngineResumeAction(
                engineEnabled = true,
                permissionsReady = true,
                hasSelectedApps = false,
            ),
        )
    }

    @Test
    fun resumeOnlyDeactivatesWhenPersistedEngineIsDisabled() {
        assertEquals(
            EngineResumeAction.DeactivateOnly,
            chooseEngineResumeAction(
                engineEnabled = false,
                permissionsReady = true,
                hasSelectedApps = true,
            ),
        )
    }
}
