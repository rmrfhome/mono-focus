package com.monofocus.app

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.monofocus.app.data.AndroidLaunchableAppsProvider
import com.monofocus.app.data.DataStoreAppSelectionRepository
import com.monofocus.app.data.UsageStatsForegroundAppDetector
import com.monofocus.app.data.ZenGrayscaleController
import com.monofocus.app.domain.FocusEngine
import com.monofocus.app.platform.PermissionChecker
import com.monofocus.app.platform.SettingsIntentFactory

class AppContainer(
    private val context: Context,
) {
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    val repository = DataStoreAppSelectionRepository(context)
    val permissionChecker = PermissionChecker(context, notificationManager)
    val settingsIntentFactory = SettingsIntentFactory()
    val launchableAppsProvider = AndroidLaunchableAppsProvider(context)
    val foregroundAppDetector = UsageStatsForegroundAppDetector(
        context = context,
        permissionChecker = permissionChecker,
    )
    val grayscaleController = ZenGrayscaleController(
        context = context,
        notificationManager = notificationManager,
        repository = repository,
        permissionChecker = permissionChecker,
    )

    fun createFocusEngine(): FocusEngine = FocusEngine(
        repository = repository,
        foregroundAppDetector = foregroundAppDetector,
        grayscaleController = grayscaleController,
        permissionChecker = permissionChecker,
    )

    suspend fun recordCurrentApi() {
        repository.setLastKnownSupportedApi(Build.VERSION.SDK_INT)
    }
}
