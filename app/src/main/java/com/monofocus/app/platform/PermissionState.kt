package com.monofocus.app.platform

data class PermissionState(
    val usageAccessGranted: Boolean,
    val notificationRuntimeGranted: Boolean,
    val notificationPolicyAccessGranted: Boolean,
    val supportedApi: Boolean,
) {
    val ready: Boolean
        get() = usageAccessGranted &&
            notificationRuntimeGranted &&
            notificationPolicyAccessGranted &&
            supportedApi
}
