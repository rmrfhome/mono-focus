package com.monofocus.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock
import com.monofocus.app.domain.ForegroundAppDetector
import com.monofocus.app.platform.PermissionStatusProvider
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class UsageStatsForegroundAppDetector(
    private val context: Context,
    private val permissionChecker: PermissionStatusProvider,
    private val pollIntervalMillis: Long = ForegroundDetectionDefaults.POLL_INTERVAL_MILLIS,
    private val debounceMillis: Long = ForegroundDetectionDefaults.DEBOUNCE_MILLIS,
    private val initialLookbackMillis: Long = ForegroundDetectionDefaults.INITIAL_LOOKBACK_MILLIS,
    private val queryOverlapMillis: Long = ForegroundDetectionDefaults.QUERY_OVERLAP_MILLIS,
    private val unreliableTimeoutMillis: Long = ForegroundDetectionDefaults.UNRELIABLE_TIMEOUT_MILLIS,
) : ForegroundAppDetector {
    private val usageStatsManager: UsageStatsManager =
        context.getSystemService(UsageStatsManager::class.java)

    override fun observeForegroundPackage(): Flow<String?> = flow {
        val usageEventState = ForegroundUsageEventState()
        val stabilizer = ForegroundDetectionStabilizer(
            debounceMillis = debounceMillis,
            unreliableTimeoutMillis = unreliableTimeoutMillis,
            initialElapsedMillis = SystemClock.elapsedRealtime(),
        )
        var lastQueryEndTime = System.currentTimeMillis() - initialLookbackMillis

        while (coroutineContext.isActive) {
            if (!permissionChecker.hasUsageAccess()) {
                emit(null)
                return@flow
            }

            val nowWallTime = System.currentTimeMillis()
            val queryStartTime = (lastQueryEndTime - queryOverlapMillis).coerceAtLeast(0L)
            val detection = detectForegroundPackage(
                startTime = queryStartTime,
                endTime = nowWallTime,
                usageEventState = usageEventState,
            )
            lastQueryEndTime = nowWallTime

            val now = SystemClock.elapsedRealtime()
            when (val output = stabilizer.update(detection, now)) {
                is ForegroundStabilizerOutput.Emit -> emit(output.packageName)
                ForegroundStabilizerOutput.NoEmission -> Unit
            }
            if (!detection.reliable && stabilizer.activePackage == null) {
                usageEventState.clear()
            }

            delay(pollIntervalMillis)
        }
    }.distinctUntilChanged()

    private fun detectForegroundPackage(
        startTime: Long,
        endTime: Long,
        usageEventState: ForegroundUsageEventState,
    ): ForegroundDetection {
        val events = runCatching {
            usageStatsManager.queryEvents(startTime, endTime)
        }.getOrNull() ?: return ForegroundDetection(
            packageName = usageEventState.activePackage,
            reliable = false,
        )

        val snapshots = mutableListOf<UsageEventSnapshot>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            snapshots += UsageEventSnapshot(
                packageName = event.packageName,
                className = event.className,
                eventType = event.eventType,
                eventTimeMillis = event.timeStamp,
            )
        }

        return ForegroundDetection(
            packageName = usageEventState.updateFromEvents(
                ownPackageName = context.packageName,
                events = snapshots,
            ),
            reliable = true,
        )
    }

}

internal object ForegroundDetectionDefaults {
    const val POLL_INTERVAL_MILLIS = 500L
    const val DEBOUNCE_MILLIS = 300L
    const val INITIAL_LOOKBACK_MILLIS = 30_000L
    const val QUERY_OVERLAP_MILLIS = 1_000L
    const val UNRELIABLE_TIMEOUT_MILLIS = 1_500L
}

internal data class ForegroundDetection(
    val packageName: String?,
    val reliable: Boolean,
)

internal sealed interface ForegroundStabilizerOutput {
    data object NoEmission : ForegroundStabilizerOutput
    data class Emit(val packageName: String?) : ForegroundStabilizerOutput
}

internal class ForegroundDetectionStabilizer(
    private val debounceMillis: Long,
    private val unreliableTimeoutMillis: Long,
    initialElapsedMillis: Long,
) {
    var activePackage: String? = null
        private set

    private var lastEmitted: String? = null
    private var hasEmitted = false
    private var pendingPackage: String? = null
    private var pendingSince = initialElapsedMillis
    private var unreliableSince: Long? = null

    fun update(
        detection: ForegroundDetection,
        nowElapsedMillis: Long,
    ): ForegroundStabilizerOutput {
        val forceImmediateUnknown = if (detection.reliable) {
            unreliableSince = null
            activePackage = detection.packageName
            false
        } else {
            val firstUnreliable = unreliableSince ?: nowElapsedMillis
            unreliableSince = firstUnreliable
            if (nowElapsedMillis - firstUnreliable >= unreliableTimeoutMillis) {
                activePackage = null
                true
            } else {
                false
            }
        }

        if (activePackage != pendingPackage) {
            pendingPackage = activePackage
            pendingSince = if (forceImmediateUnknown) {
                nowElapsedMillis - debounceMillis
            } else {
                nowElapsedMillis
            }
        }

        val pendingStable = nowElapsedMillis - pendingSince >= debounceMillis
        val shouldEmit = pendingStable && (!hasEmitted || pendingPackage != lastEmitted)

        return if (shouldEmit) {
            hasEmitted = true
            lastEmitted = pendingPackage
            ForegroundStabilizerOutput.Emit(pendingPackage)
        } else {
            ForegroundStabilizerOutput.NoEmission
        }
    }
}

data class UsageEventSnapshot(
    val packageName: String?,
    val className: String? = null,
    val eventType: Int,
    val eventTimeMillis: Long = 0L,
)

private data class ActiveActivityToken(
    val packageName: String,
    val className: String?,
)

internal class ForegroundUsageEventState(
    initialPackage: String? = null,
) {
    var activePackage: String? = initialPackage
        private set

    private val activeActivityTokens = mutableSetOf<ActiveActivityToken>()

    fun updateFromEvents(
        ownPackageName: String,
        events: List<UsageEventSnapshot>,
    ): String? {
        events
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<UsageEventSnapshot>> { it.value.eventTimeMillis }
                    .thenBy { it.index },
            )
            .forEach { indexedEvent ->
                val event = indexedEvent.value
                val packageName = event.packageName ?: return@forEach
                @Suppress("DEPRECATION")
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    -> if (packageName == ownPackageName) {
                        clear()
                    } else {
                        activeActivityTokens += ActiveActivityToken(
                            packageName = packageName,
                            className = event.className,
                        )
                        activePackage = packageName
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    -> {
                        if (event.className == null) {
                            activeActivityTokens.removeAll { it.packageName == packageName }
                        } else {
                            activeActivityTokens -= ActiveActivityToken(
                                packageName = packageName,
                                className = event.className,
                            )
                        }
                        if (
                            activePackage == packageName &&
                            activeActivityTokens.none { it.packageName == packageName }
                        ) {
                            activePackage = null
                        }
                    }
                }
            }

        return activePackage
    }

    fun clear() {
        activePackage = null
        activeActivityTokens.clear()
    }
}

fun inferForegroundPackageFromEvents(
    previousPackage: String?,
    ownPackageName: String,
    events: List<UsageEventSnapshot>,
): String? {
    return ForegroundUsageEventState(initialPackage = previousPackage).updateFromEvents(
        ownPackageName = ownPackageName,
        events = events,
    )
}
