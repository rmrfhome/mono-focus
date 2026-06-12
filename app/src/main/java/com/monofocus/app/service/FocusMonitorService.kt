package com.monofocus.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.monofocus.app.MonoFocusApplication
import com.monofocus.app.R
import com.monofocus.app.domain.activePauseUntil
import com.monofocus.app.domain.deactivateBestEffort
import com.monofocus.app.domain.hasSelectedAvailablePackage
import com.monofocus.app.domain.pauseForFifteenMinutesFrom
import com.monofocus.app.domain.pauseUntilTomorrowFrom
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class FocusMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var notificationHealthJob: Job? = null
    @Volatile
    private var currentNotificationPauseUntilEpochMillis = 0L

    private val container
        get() = (application as MonoFocusApplication).container

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                monitorJob?.cancel()
                notificationHealthJob?.cancel()
                serviceScope.launch {
                    try {
                        container.repository.setEngineEnabled(false)
                        container.grayscaleController.deactivateBestEffort()
                    } finally {
                        stopSelf()
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_PAUSE_15_MINUTES -> {
                serviceScope.launch {
                    pauseMonitoringUntil(
                        pauseForFifteenMinutesFrom(System.currentTimeMillis()),
                    )
                }
                return START_STICKY
            }
            ACTION_PAUSE_UNTIL_TOMORROW -> {
                serviceScope.launch {
                    pauseMonitoringUntil(
                        pauseUntilTomorrowFrom(ZonedDateTime.now()),
                    )
                }
                return START_STICKY
            }
            ACTION_RESUME -> {
                serviceScope.launch {
                    resumeMonitoring()
                }
                return START_STICKY
            }
            else -> return startMonitoring()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitorJob?.cancel()
        notificationHealthJob?.cancel()
        runBlocking(Dispatchers.IO) {
            container.grayscaleController.deactivateBestEffort()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMonitoring(): Int {
        val canStartMonitoring = runBlocking(Dispatchers.Default) {
            runCatching {
                canStartForegroundMonitoring()
            }.getOrDefault(false)
        }

        if (!canStartMonitoring) {
            stopWithoutMonitoring()
            return START_NOT_STICKY
        }

        val pauseUntilEpochMillis = currentActivePauseUntilEpochMillis()
        currentNotificationPauseUntilEpochMillis = pauseUntilEpochMillis
        startForeground(
            NOTIFICATION_ID,
            buildNotification(pauseUntilEpochMillis),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        startNotificationHealthCheck()

        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            container.createFocusEngine().run(onStopRequested = {
                notificationHealthJob?.cancel()
                stopSelf()
            })
        }

        return START_STICKY
    }

    private fun stopWithoutMonitoring() {
        monitorJob?.cancel()
        notificationHealthJob?.cancel()
        serviceScope.launch {
            try {
                container.repository.setEngineEnabled(false)
                container.grayscaleController.deactivateBestEffort()
            } finally {
                stopSelf()
            }
        }
    }

    private suspend fun canStartForegroundMonitoring(): Boolean =
        shouldStartForegroundMonitoring(
            engineEnabled = container.repository.engineEnabled.first(),
            permissionsReady = container.permissionChecker.currentState().ready,
            hasSelectedLaunchableApps = hasSelectedLaunchableApps(),
        )

    private suspend fun hasSelectedLaunchableApps(): Boolean {
        val selectedPackages = container.repository.selectedPackages.first()
        if (selectedPackages.isEmpty()) return false

        val availablePackageNames = container.launchableAppsProvider
            .getLaunchableApps()
            .map { app -> app.packageName }
            .toSet()

        return hasSelectedAvailablePackage(
            selectedPackages = selectedPackages,
            availablePackageNames = availablePackageNames,
        )
    }

    private fun ensureNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitor_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            description = getString(R.string.monitor_notification_text)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private suspend fun pauseMonitoringUntil(pausedUntilEpochMillis: Long) {
        container.repository.setPausedUntilEpochMillis(pausedUntilEpochMillis)
        container.grayscaleController.deactivateBestEffort()
        refreshForegroundNotification(
            activePauseUntil(
                pausedUntilEpochMillis = pausedUntilEpochMillis,
                nowEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun resumeMonitoring() {
        container.repository.setPausedUntilEpochMillis(0L)
        refreshForegroundNotification(0L)
    }

    private fun buildNotification(pauseUntilEpochMillis: Long): Notification {
        val activePauseUntilEpochMillis = activePauseUntil(
            pausedUntilEpochMillis = pauseUntilEpochMillis,
            nowEpochMillis = System.currentTimeMillis(),
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, com.monofocus.app.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val contentText = if (activePauseUntilEpochMillis > 0L) {
            getString(
                R.string.monitor_notification_paused_until,
                formatPauseUntil(activePauseUntilEpochMillis),
            )
        } else {
            getString(R.string.monitor_notification_text)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monofocus)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setLocalOnly(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (activePauseUntilEpochMillis > 0L) {
            builder.addAction(
                0,
                getString(R.string.monitor_notification_resume),
                serviceAction(ACTION_RESUME, requestCode = 2),
            )
        }

        return builder
            .addAction(
                0,
                getString(R.string.monitor_notification_pause_15_minutes),
                serviceAction(ACTION_PAUSE_15_MINUTES, requestCode = 3),
            )
            .addAction(
                0,
                getString(R.string.monitor_notification_pause_until_tomorrow),
                serviceAction(ACTION_PAUSE_UNTIL_TOMORROW, requestCode = 4),
            )
            .build()
            .apply {
                flags = flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
            }
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, FocusMonitorService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private suspend fun refreshForegroundNotification(
        pauseUntilEpochMillis: Long = currentActivePauseUntilEpochMillis(),
    ) {
        withContext(Dispatchers.Main.immediate) {
            runCatching {
                currentNotificationPauseUntilEpochMillis = pauseUntilEpochMillis
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(pauseUntilEpochMillis),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            }
        }
    }

    private fun currentActivePauseUntilEpochMillis(): Long {
        val pausedUntilEpochMillis = runBlocking(Dispatchers.Default) {
            container.repository.settings.first().pausedUntilEpochMillis
        }
        return activePauseUntil(
            pausedUntilEpochMillis = pausedUntilEpochMillis,
            nowEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun formatPauseUntil(pauseUntilEpochMillis: Long): String =
        Instant.ofEpochMilli(pauseUntilEpochMillis)
            .atZone(ZoneId.systemDefault())
            .format(PAUSE_UNTIL_FORMATTER)

    private fun startNotificationHealthCheck() {
        notificationHealthJob?.cancel()
        notificationHealthJob = serviceScope.launch {
            while (isActive) {
                delay(NOTIFICATION_HEALTH_CHECK_INTERVAL_MILLIS)
                if (!container.repository.engineEnabled.first()) {
                    continue
                }
                val pauseUntilEpochMillis = currentActivePauseUntilEpochMillis()
                if (
                    !isMonitorNotificationVisible() ||
                    pauseUntilEpochMillis != currentNotificationPauseUntilEpochMillis
                ) {
                    refreshForegroundNotification(pauseUntilEpochMillis)
                }
            }
        }
    }

    private fun isMonitorNotificationVisible(): Boolean {
        val notificationManager = getSystemService(NotificationManager::class.java)
        return notificationManager.activeNotifications.any { notification ->
            notification.id == NOTIFICATION_ID &&
                notification.tag == null &&
                notification.packageName == packageName
        }
    }

    companion object {
        private const val ACTION_START = "com.monofocus.app.action.START_MONITORING"
        private const val ACTION_STOP = "com.monofocus.app.action.STOP_MONITORING"
        private const val ACTION_PAUSE_15_MINUTES =
            "com.monofocus.app.action.PAUSE_15_MINUTES"
        private const val ACTION_PAUSE_UNTIL_TOMORROW =
            "com.monofocus.app.action.PAUSE_UNTIL_TOMORROW"
        private const val ACTION_RESUME = "com.monofocus.app.action.RESUME_MONITORING"
        private const val CHANNEL_ID = "focus_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_HEALTH_CHECK_INTERVAL_MILLIS = 5_000L
        private val PAUSE_UNTIL_FORMATTER = DateTimeFormatter.ofPattern("EEE HH:mm")

        fun start(context: Context) {
            val intent = Intent(context, FocusMonitorService::class.java)
                .setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusMonitorService::class.java)
                .setAction(ACTION_STOP)
            runCatching {
                context.startService(intent)
            }
        }
    }
}
