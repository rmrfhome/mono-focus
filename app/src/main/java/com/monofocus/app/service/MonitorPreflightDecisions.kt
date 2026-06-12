package com.monofocus.app.service

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
