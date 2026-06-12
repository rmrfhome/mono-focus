package com.monofocus.app.platform

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat

interface PermissionStatusProvider {
    fun currentState(): PermissionState
    fun hasUsageAccess(): Boolean
    fun hasNotificationRuntimePermission(): Boolean
    fun hasNotificationPolicyAccess(): Boolean
}

class PermissionChecker(
    private val context: Context,
    private val notificationManager: NotificationManager,
) : PermissionStatusProvider {
    override fun currentState(): PermissionState = PermissionState(
        usageAccessGranted = hasUsageAccess(),
        notificationRuntimeGranted = hasNotificationRuntimePermission(),
        notificationPolicyAccessGranted = hasNotificationPolicyAccess(),
        supportedApi = Build.VERSION.SDK_INT >= 35,
    )

    override fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun hasNotificationRuntimePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    override fun hasNotificationPolicyAccess(): Boolean =
        notificationManager.isNotificationPolicyAccessGranted
}
