package com.monofocus.app

import android.Manifest
import android.app.NotificationManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.monofocus.app.domain.deactivateBestEffort
import com.monofocus.app.domain.EngineStopReason
import com.monofocus.app.platform.handleSelectedPackageRemoved
import com.monofocus.app.service.FocusMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MonoFocusApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val safetyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SHUTDOWN -> deactivateBeforeAsyncFinish()
                Intent.ACTION_PACKAGE_REMOVED -> disableIfSelectedPackageRemoved(context, intent)
                NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED -> {
                    if (container.permissionChecker.hasNotificationPolicyAccess()) return
                    disableAndStopAfterPolicyAccessRevoked(context)
                }
            }
        }

        private fun deactivateBeforeAsyncFinish() {
            val pendingResult = goAsync()
            applicationScope.launch {
                try {
                    container.grayscaleController.deactivateBestEffort()
                } finally {
                    pendingResult.finish()
                }
            }
        }

        private fun disableAndStopAfterPolicyAccessRevoked(context: Context) {
            val pendingResult = goAsync()
            applicationScope.launch {
                try {
                    container.grayscaleController.deactivateBestEffort()
                    container.repository.setLastEngineStopReason(EngineStopReason.PermissionsMissing)
                    FocusMonitorService.stopMonitoringOnly(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }

        private fun disableIfSelectedPackageRemoved(context: Context, intent: Intent) {
            val pendingResult = goAsync()
            applicationScope.launch {
                try {
                    handleSelectedPackageRemoved(context.applicationContext, intent)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
        registerSafetyReceiver()
        applicationScope.launch {
            container.recordCurrentApi()
        }
    }

    private fun registerSafetyReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SHUTDOWN)
            addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
        }
        ContextCompat.registerReceiver(
            applicationContext,
            safetyReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        applicationContext.registerReceiver(
            safetyReceiver,
            IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply {
                addDataScheme("package")
            },
            Manifest.permission.BROADCAST_PACKAGE_REMOVED,
            null,
            Context.RECEIVER_EXPORTED,
        )
    }
}
