package com.monofocus.app.ui

internal enum class EngineToggleAction {
    EnableAndStart,
    DisableAndStop,
}

internal enum class EngineResumeAction {
    EnsureRuleAndStart,
    DisableAndStop,
    DeactivateOnly,
}

internal fun chooseEngineToggleAction(
    requestedEnabled: Boolean,
    permissionsReady: Boolean,
    hasSelectedApps: Boolean,
): EngineToggleAction =
    if (requestedEnabled && permissionsReady && hasSelectedApps) {
        EngineToggleAction.EnableAndStart
    } else {
        EngineToggleAction.DisableAndStop
    }

internal fun chooseEngineResumeAction(
    engineEnabled: Boolean,
    permissionsReady: Boolean,
    hasSelectedApps: Boolean,
): EngineResumeAction =
    when {
        engineEnabled && permissionsReady && hasSelectedApps -> EngineResumeAction.EnsureRuleAndStart
        engineEnabled -> EngineResumeAction.DisableAndStop
        else -> EngineResumeAction.DeactivateOnly
    }
