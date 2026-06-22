package com.monofocus.app.ui

internal fun buildDiagnosticsText(
    state: MonoFocusUiState,
    appPackageName: String,
    appVersionName: String,
    appVersionCode: Long,
    androidRelease: String,
    sdkInt: Int,
    manufacturer: String,
    brand: String,
    model: String,
    device: String,
): String = buildString {
    appendLine("MonoFocus diagnostics")
    appendLine()
    appendLine("App")
    appendKeyValue("package", appPackageName)
    appendKeyValue("versionName", appVersionName)
    appendKeyValue("versionCode", appVersionCode)
    appendLine()
    appendLine("Device")
    appendKeyValue("androidRelease", androidRelease)
    appendKeyValue("sdkInt", sdkInt)
    appendKeyValue("manufacturer", manufacturer)
    appendKeyValue("brand", brand)
    appendKeyValue("model", model)
    appendKeyValue("device", device)
    appendLine()
    appendLine("Engine")
    appendKeyValue("engineEnabled", state.engineEnabled)
    appendKeyValue("canEnableEngine", state.canEnableEngine)
    appendKeyValue("setupRequired", state.setupRequired)
    appendKeyValue("lastEngineStopReason", state.lastEngineStopReason?.name ?: "none")
    appendKeyValue("selectedLaunchableAppCount", state.selectedPackageCount)
    appendKeyValue("visibleAppCount", state.apps.size)
    appendKeyValue("loadingApps", state.loadingApps)
    appendKeyValue("showSelectedOnly", state.showSelectedOnly)
    appendLine()
    appendLine("Permissions")
    appendKeyValue("supportedApi", state.permissionState.supportedApi)
    appendKeyValue("usageAccessGranted", state.permissionState.usageAccessGranted)
    appendKeyValue("notificationRuntimeGranted", state.permissionState.notificationRuntimeGranted)
    appendKeyValue("notificationPolicyAccessGranted", state.permissionState.notificationPolicyAccessGranted)
    appendKeyValue("ready", state.permissionState.ready)
}

private fun StringBuilder.appendKeyValue(key: String, value: Any) {
    append(key)
    append("=")
    appendLine(value)
}
