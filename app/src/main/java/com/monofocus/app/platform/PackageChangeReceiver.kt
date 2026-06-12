package com.monofocus.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.monofocus.app.MonoFocusApplication
import com.monofocus.app.domain.deactivateBestEffort
import com.monofocus.app.domain.isSelectedPackageRemovalCandidate
import com.monofocus.app.domain.shouldDisableEngineAfterPackageRemoved
import com.monofocus.app.service.FocusMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_REMOVED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                handleSelectedPackageRemoved(context.applicationContext, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal suspend fun handleSelectedPackageRemoved(context: Context, intent: Intent) {
    val application = context.applicationContext as? MonoFocusApplication ?: return
    val container = application.container
    val selectedPackages = container.repository.selectedPackages.first()
    val engineEnabled = container.repository.engineEnabled.first()
    val removedPackageName = intent.data?.schemeSpecificPart
    val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

    if (
        !isSelectedPackageRemovalCandidate(
            engineEnabled = engineEnabled,
            removedPackageName = removedPackageName,
            isReplacing = isReplacing,
            selectedPackages = selectedPackages,
        )
    ) {
        return
    }

    val availablePackageNames = container.launchableAppsProvider
        .getLaunchableApps()
        .map { app -> app.packageName }
        .toSet()

    if (
        shouldDisableEngineAfterPackageRemoved(
            engineEnabled = engineEnabled,
            removedPackageName = removedPackageName,
            isReplacing = isReplacing,
            selectedPackages = selectedPackages,
            availablePackageNames = availablePackageNames,
        )
    ) {
        container.repository.setEngineEnabled(false)
        container.grayscaleController.deactivateBestEffort()
        FocusMonitorService.stop(context)
    }
}
