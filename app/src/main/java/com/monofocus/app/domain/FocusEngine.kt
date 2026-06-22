package com.monofocus.app.domain

import com.monofocus.app.platform.PermissionStatusProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class FocusEngine(
    private val repository: AppSelectionRepository,
    private val foregroundAppDetector: ForegroundAppDetector,
    private val grayscaleController: GrayscaleController,
    private val permissionChecker: PermissionStatusProvider,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
    private val wallClockIntervalMillis: Long = WALL_CLOCK_INTERVAL_MILLIS,
    private val activeReassertIntervalMillis: Long = ACTIVE_REASSERT_INTERVAL_MILLIS,
) {
    suspend fun run(onStopRequested: suspend (EngineStopReason) -> Unit) {
        var stopReason: EngineStopReason? = null

        suspend fun deactivateSafely() {
            grayscaleController.deactivateBestEffort()
        }

        suspend fun stop(reason: EngineStopReason) {
            stopReason = reason
            deactivateSafely()
            repository.setLastEngineStopReason(reason)
            if (reason == EngineStopReason.EngineDisabled || reason == EngineStopReason.InternalError) {
                repository.setEngineEnabled(false)
            }
            onStopRequested(reason)
        }

        try {
            val initialSettings = repository.settings.first()
            if (!initialSettings.engineEnabled) {
                stop(EngineStopReason.EngineDisabled)
                return
            }

            val permissions = permissionChecker.currentState()
            if (!permissions.ready) {
                stop(EngineStopReason.PermissionsMissing)
                return
            }

            if (initialSettings.selectedPackageNames.isEmpty()) {
                stop(EngineStopReason.NoSelectedApps)
                return
            }

            if (!grayscaleController.ensureReady()) {
                stop(EngineStopReason.RuleUnavailable)
                return
            }

            var lastAppliedActive: Boolean? = null
            var lastActiveRequestEpochMillis = 0L
            combine(
                repository.settings,
                foregroundAppDetector.observeForegroundPackage(),
                observePermissionState(),
                observeWallClock(),
            ) { settings, foregroundPackage, permissions, nowEpochMillis ->
                EngineInputs(
                    settings = settings,
                    foregroundPackage = foregroundPackage,
                    permissions = permissions,
                    nowEpochMillis = nowEpochMillis,
                )
            }.collect { inputs ->
                if (!currentCoroutineContext().isActive) return@collect

                when {
                    !inputs.settings.engineEnabled -> {
                        stop(EngineStopReason.EngineDisabled)
                        throw EngineStoppedException("Engine disabled")
                    }
                    !inputs.permissions.ready -> {
                        stop(EngineStopReason.PermissionsMissing)
                        throw EngineStoppedException("Permissions missing")
                    }
                    inputs.settings.selectedPackageNames.isEmpty() -> {
                        stop(EngineStopReason.NoSelectedApps)
                        throw EngineStoppedException("No selected apps")
                    }
                    else -> {
                        if (
                            inputs.settings.pausedUntilEpochMillis > 0L &&
                            !isPauseActive(
                                pausedUntilEpochMillis = inputs.settings.pausedUntilEpochMillis,
                                nowEpochMillis = inputs.nowEpochMillis,
                            )
                        ) {
                            repository.setPausedUntilEpochMillis(0L)
                        }

                        val shouldBeActive = shouldActivateGrayscale(
                            foregroundPackage = inputs.foregroundPackage,
                            selectedPackages = inputs.settings.selectedPackageNames,
                        ) && !isPauseActive(
                            pausedUntilEpochMillis = inputs.settings.pausedUntilEpochMillis,
                            nowEpochMillis = inputs.nowEpochMillis,
                        )
                        if (
                            shouldBeActive != lastAppliedActive ||
                            shouldReassertActiveGrayscale(
                                shouldBeActive = shouldBeActive,
                                lastAppliedActive = lastAppliedActive,
                                lastActiveRequestEpochMillis = lastActiveRequestEpochMillis,
                                nowEpochMillis = inputs.nowEpochMillis,
                            )
                        ) {
                            runCatching {
                                grayscaleController.setGrayscaleActive(shouldBeActive)
                            }.getOrElse {
                                stop(EngineStopReason.RuleUnavailable)
                                throw EngineStoppedException("Grayscale rule unavailable")
                            }
                            lastAppliedActive = shouldBeActive
                            lastActiveRequestEpochMillis = if (shouldBeActive) {
                                inputs.nowEpochMillis
                            } else {
                                0L
                            }
                        }
                    }
                }
            }

            if (stopReason == null) {
                stop(EngineStopReason.PermissionsMissing)
            }
        } catch (_: EngineStoppedException) {
            Unit
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            stop(EngineStopReason.InternalError)
        } finally {
            deactivateSafely()
        }
    }

    private fun observePermissionState() = flow {
        while (currentCoroutineContext().isActive) {
            emit(permissionChecker.currentState())
            delay(PERMISSION_CHECK_INTERVAL_MILLIS)
        }
    }.distinctUntilChanged()

    private fun shouldReassertActiveGrayscale(
        shouldBeActive: Boolean,
        lastAppliedActive: Boolean?,
        lastActiveRequestEpochMillis: Long,
        nowEpochMillis: Long,
    ): Boolean =
        shouldBeActive &&
            lastAppliedActive == true &&
            nowEpochMillis - lastActiveRequestEpochMillis >= activeReassertIntervalMillis

    private fun observeWallClock() = flow {
        while (currentCoroutineContext().isActive) {
            emit(wallClockMillis())
            delay(wallClockIntervalMillis)
        }
    }

    private data class EngineInputs(
        val settings: AppSettings,
        val foregroundPackage: String?,
        val permissions: com.monofocus.app.platform.PermissionState,
        val nowEpochMillis: Long,
    )

    private companion object {
        const val PERMISSION_CHECK_INTERVAL_MILLIS = 1_000L
        const val WALL_CLOCK_INTERVAL_MILLIS = 1_000L
        const val ACTIVE_REASSERT_INTERVAL_MILLIS = 2_000L
    }

    private class EngineStoppedException(message: String) : RuntimeException(message)
}
