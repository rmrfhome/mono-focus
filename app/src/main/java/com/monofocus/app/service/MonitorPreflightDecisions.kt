package com.monofocus.app.service

import com.monofocus.app.domain.EngineStopReason

internal fun shouldRunMonitor(
    engineEnabled: Boolean,
    permissionsReady: Boolean,
    hasSelectedLaunchableApps: Boolean,
): Boolean = engineEnabled && permissionsReady && hasSelectedLaunchableApps

internal fun shouldStartForegroundMonitoring(
    engineEnabled: Boolean,
    permissionsReady: Boolean,
    hasSelectedLaunchableApps: Boolean,
): Boolean = shouldRunMonitor(
    engineEnabled = engineEnabled,
    permissionsReady = permissionsReady,
    hasSelectedLaunchableApps = hasSelectedLaunchableApps,
)

internal fun monitoringStartBlockReason(
    engineEnabled: Boolean,
    permissionsReady: Boolean,
    hasSelectedLaunchableApps: Boolean,
): EngineStopReason? =
    when {
        !engineEnabled -> EngineStopReason.EngineDisabled
        !permissionsReady -> EngineStopReason.PermissionsMissing
        !hasSelectedLaunchableApps -> EngineStopReason.NoSelectedApps
        else -> null
    }
