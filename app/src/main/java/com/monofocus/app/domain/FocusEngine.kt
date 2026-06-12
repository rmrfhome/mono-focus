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
) {
    suspend fun run(onStopRequested: suspend (EngineStopReason) -> Unit) {
        var stopReason: EngineStopReason? = null

        suspend fun deactivateSafely() {
            grayscaleController.deactivateBestEffort()
        }

        suspend fun stop(reason: EngineStopReason) {
            stopReason = reason
            deactivateSafely()
            repository.setEngineEnabled(false)
            onStopRequested(reason)
        }

        try {
            if (!repository.engineEnabled.first()) {
                stop(EngineStopReason.EngineDisabled)
                return
            }

            val permissions = permissionChecker.currentState()
            if (!permissions.ready) {
                stop(EngineStopReason.PermissionsMissing)
                return
            }

            if (repository.selectedPackages.first().isEmpty()) {
                stop(EngineStopReason.NoSelectedApps)
                return
            }

            if (!grayscaleController.ensureReady()) {
                stop(EngineStopReason.RuleUnavailable)
                return
            }

            var lastAppliedActive: Boolean? = null
            combine(
                repository.engineEnabled,
                repository.selectedPackages,
                foregroundAppDetector.observeForegroundPackage(),
                observePermissionState(),
            ) { engineEnabled, selectedPackages, foregroundPackage, permissions ->
                EngineInputs(
                    engineEnabled = engineEnabled,
                    selectedPackages = selectedPackages,
                    foregroundPackage = foregroundPackage,
                    permissions = permissions,
                )
            }.collect { inputs ->
                if (!currentCoroutineContext().isActive) return@collect

                when {
                    !inputs.engineEnabled -> {
                        stop(EngineStopReason.EngineDisabled)
                        throw EngineStoppedException("Engine disabled")
                    }
                    !inputs.permissions.ready -> {
                        stop(EngineStopReason.PermissionsMissing)
                        throw EngineStoppedException("Permissions missing")
                    }
                    inputs.selectedPackages.isEmpty() -> {
                        stop(EngineStopReason.NoSelectedApps)
                        throw EngineStoppedException("No selected apps")
                    }
                    else -> {
                        val shouldBeActive = shouldActivateGrayscale(
                            foregroundPackage = inputs.foregroundPackage,
                            selectedPackages = inputs.selectedPackages,
                        )
                        if (shouldBeActive != lastAppliedActive) {
                            grayscaleController.setGrayscaleActive(shouldBeActive)
                            lastAppliedActive = shouldBeActive
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

    private data class EngineInputs(
        val engineEnabled: Boolean,
        val selectedPackages: Set<String>,
        val foregroundPackage: String?,
        val permissions: com.monofocus.app.platform.PermissionState,
    )

    private companion object {
        const val PERMISSION_CHECK_INTERVAL_MILLIS = 1_000L
    }

    private class EngineStoppedException(message: String) : RuntimeException(message)
}
