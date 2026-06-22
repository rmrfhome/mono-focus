package com.monofocus.app.ui

internal enum class EngineToggleAction {
    EnableAndStart,
    EnableWithoutStarting,
    DisableAndStop,
}

internal enum class EngineResumeAction {
    EnsureRuleAndStart,
    StopMonitoringOnly,
    DeactivateOnly,
}

internal fun chooseEngineToggleAction(
    requestedEnabled: Boolean,
    permissionsReady: Boolean,
    hasSelectedApps: Boolean,
): EngineToggleAction =
    when {
        requestedEnabled && permissionsReady && hasSelectedApps -> EngineToggleAction.EnableAndStart
        requestedEnabled -> EngineToggleAction.EnableWithoutStarting
        else -> EngineToggleAction.DisableAndStop
    }

internal fun chooseEngineResumeAction(
    engineEnabled: Boolean,
    permissionsReady: Boolean,
    hasSelectedApps: Boolean,
): EngineResumeAction =
    when {
        engineEnabled && permissionsReady && hasSelectedApps -> EngineResumeAction.EnsureRuleAndStart
        engineEnabled -> EngineResumeAction.StopMonitoringOnly
        else -> EngineResumeAction.DeactivateOnly
    }
