package com.monofocus.app.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStateTest {
    @Test
    fun readyWhenAllRequiredStateIsPresent() {
        assertTrue(
            PermissionState(
                usageAccessGranted = true,
                notificationRuntimeGranted = true,
                notificationPolicyAccessGranted = true,
                supportedApi = true,
            ).ready,
        )
    }

    @Test
    fun notReadyWhenUsageAccessIsMissing() {
        assertFalse(
            PermissionState(
                usageAccessGranted = false,
                notificationRuntimeGranted = true,
                notificationPolicyAccessGranted = true,
                supportedApi = true,
            ).ready,
        )
    }

    @Test
    fun notReadyWhenNotificationRuntimePermissionIsMissing() {
        assertFalse(
            PermissionState(
                usageAccessGranted = true,
                notificationRuntimeGranted = false,
                notificationPolicyAccessGranted = true,
                supportedApi = true,
            ).ready,
        )
    }

    @Test
    fun notReadyWhenNotificationPolicyAccessIsMissing() {
        assertFalse(
            PermissionState(
                usageAccessGranted = true,
                notificationRuntimeGranted = true,
                notificationPolicyAccessGranted = false,
                supportedApi = true,
            ).ready,
        )
    }

    @Test
    fun notReadyOnUnsupportedApi() {
        assertFalse(
            PermissionState(
                usageAccessGranted = true,
                notificationRuntimeGranted = true,
                notificationPolicyAccessGranted = true,
                supportedApi = false,
            ).ready,
        )
    }
}
