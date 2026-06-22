package com.monofocus.app.ui

import com.monofocus.app.domain.AppEntry
import com.monofocus.app.domain.EngineStopReason
import com.monofocus.app.platform.PermissionState

data class MonoFocusUiState(
    val supportedApi: Boolean = true,
    val permissionState: PermissionState = PermissionState(
        usageAccessGranted = false,
        notificationRuntimeGranted = false,
        notificationPolicyAccessGranted = false,
        supportedApi = true,
    ),
    val apps: List<AppEntry> = emptyList(),
    val searchQuery: String = "",
    val showSelectedOnly: Boolean = false,
    val engineEnabled: Boolean = false,
    val lastEngineStopReason: EngineStopReason? = null,
    val selectedPackageCount: Int = 0,
    val loadingApps: Boolean = true,
    val showPrivacy: Boolean = false,
) {
    val setupRequired: Boolean
        get() = !permissionState.ready

    val canEnableEngine: Boolean
        get() = permissionState.ready && selectedPackageCount > 0
}
