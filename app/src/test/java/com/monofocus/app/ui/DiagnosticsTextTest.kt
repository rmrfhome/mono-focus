package com.monofocus.app.ui

import com.monofocus.app.domain.AppEntry
import com.monofocus.app.domain.EngineStopReason
import com.monofocus.app.platform.PermissionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTextTest {
    @Test
    fun diagnosticsTextIncludesEnginePermissionsAndDeviceSummary() {
        val diagnostics = buildDiagnosticsText(
            state = MonoFocusUiState(
                permissionState = PermissionState(
                    usageAccessGranted = true,
                    notificationRuntimeGranted = false,
                    notificationPolicyAccessGranted = true,
                    supportedApi = true,
                ),
                apps = listOf(
                    AppEntry(
                        packageName = "com.example.social",
                        label = "Social",
                        icon = null,
                    ),
                ),
                engineEnabled = true,
                lastEngineStopReason = EngineStopReason.RuleUnavailable,
                selectedPackageCount = 1,
            ),
            appPackageName = "com.rmrfhome.monofocus.debug",
            appVersionName = "0.1.1-debug",
            appVersionCode = 2L,
            androidRelease = "15",
            sdkInt = 35,
            manufacturer = "Samsung",
            brand = "samsung",
            model = "Galaxy A14",
            device = "a14",
        )

        assertTrue(diagnostics.contains("MonoFocus diagnostics"))
        assertTrue(diagnostics.contains("package=com.rmrfhome.monofocus.debug"))
        assertTrue(diagnostics.contains("model=Galaxy A14"))
        assertTrue(diagnostics.contains("engineEnabled=true"))
        assertTrue(diagnostics.contains("lastEngineStopReason=RuleUnavailable"))
        assertTrue(diagnostics.contains("notificationRuntimeGranted=false"))
        assertTrue(diagnostics.contains("selectedLaunchableAppCount=1"))
        assertTrue(diagnostics.contains("visibleAppCount=1"))
        assertFalse(diagnostics.contains("com.example.social"))
        assertFalse(diagnostics.contains("Social"))
    }
}
