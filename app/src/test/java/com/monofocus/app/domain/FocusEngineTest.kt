package com.monofocus.app.domain

import com.monofocus.app.platform.PermissionState
import com.monofocus.app.platform.PermissionStatusProvider
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FocusEngineTest {
    @Test
    fun activatesAndDeactivatesBasedOnSelectedForegroundPackage() = runTest {
        val repository = FakeRepository(selectedPackages = setOf("com.example.social"))
        val detector = FakeForegroundAppDetector(initialPackage = null)
        val controller = FakeGrayscaleController()
        val engine = FocusEngine(repository, detector, controller, FakePermissions())

        val job = launch {
            engine.run { }
        }
        runCurrent()

        detector.foregroundPackage.value = "com.example.social"
        runCurrent()
        detector.foregroundPackage.value = "com.example.reader"
        runCurrent()

        assertEquals(listOf(false, true, false), controller.activeRequests)

        job.cancelAndJoin()
    }

    @Test
    fun stopsWhenNoAppsAreSelectedBeforeStart() = runTest {
        val repository = FakeRepository(selectedPackages = emptySet())
        val controller = FakeGrayscaleController()
        val stopReasons = mutableListOf<EngineStopReason>()
        val engine = FocusEngine(
            repository = repository,
            foregroundAppDetector = FakeForegroundAppDetector(initialPackage = null),
            grayscaleController = controller,
            permissionChecker = FakePermissions(),
        )

        engine.run { reason -> stopReasons += reason }

        assertEquals(listOf(EngineStopReason.NoSelectedApps), stopReasons)
        assertFalse(repository.engineEnabled.firstValue())
        assertTrue(controller.deactivationCount >= 1)
    }

    @Test
    fun stopsWhenEngineDisabledBeforeStart() = runTest {
        val repository = FakeRepository(
            selectedPackages = setOf("com.example.social"),
            engineEnabled = false,
        )
        val controller = FakeGrayscaleController()
        val stopReasons = mutableListOf<EngineStopReason>()
        val engine = FocusEngine(
            repository = repository,
            foregroundAppDetector = FakeForegroundAppDetector(initialPackage = null),
            grayscaleController = controller,
            permissionChecker = FakePermissions(),
        )

        engine.run { reason -> stopReasons += reason }

        assertEquals(listOf(EngineStopReason.EngineDisabled), stopReasons)
        assertFalse(repository.engineEnabled.firstValue())
        assertEquals(0, controller.ensureReadyCount)
        assertTrue(controller.deactivationCount >= 1)
    }

    @Test
    fun stopsWhenZenRuleCannotBePrepared() = runTest {
        val repository = FakeRepository(selectedPackages = setOf("com.example.social"))
        val controller = FakeGrayscaleController(ensureReadyResult = false)
        val stopReasons = mutableListOf<EngineStopReason>()
        val engine = FocusEngine(
            repository = repository,
            foregroundAppDetector = FakeForegroundAppDetector(initialPackage = null),
            grayscaleController = controller,
            permissionChecker = FakePermissions(),
        )

        engine.run { reason -> stopReasons += reason }

        assertEquals(listOf(EngineStopReason.RuleUnavailable), stopReasons)
        assertFalse(repository.engineEnabled.firstValue())
        assertTrue(controller.deactivationCount >= 1)
    }

    @Test
    fun stopsWhenPermissionsAreRevokedDuringMonitoring() = runTest {
        val repository = FakeRepository(selectedPackages = setOf("com.example.social"))
        val detector = FakeForegroundAppDetector(initialPackage = "com.example.social")
        val controller = FakeGrayscaleController()
        val permissions = FakePermissions()
        val stopReasons = mutableListOf<EngineStopReason>()
        val engine = FocusEngine(repository, detector, controller, permissions)

        val job = launch {
            engine.run { reason -> stopReasons += reason }
        }
        runCurrent()

        permissions.state = PermissionState(
            usageAccessGranted = false,
            notificationRuntimeGranted = true,
            notificationPolicyAccessGranted = true,
            supportedApi = true,
        )
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(listOf(EngineStopReason.PermissionsMissing), stopReasons)
        assertFalse(repository.engineEnabled.firstValue())
        assertTrue(controller.deactivationCount >= 1)

        job.cancelAndJoin()
    }

    @Test
    fun stopsWhenEngineDisabledDuringMonitoring() = runTest {
        val repository = FakeRepository(selectedPackages = setOf("com.example.social"))
        val detector = FakeForegroundAppDetector(initialPackage = "com.example.social")
        val controller = FakeGrayscaleController()
        val stopReasons = mutableListOf<EngineStopReason>()
        val engine = FocusEngine(repository, detector, controller, FakePermissions())

        val job = launch {
            engine.run { reason -> stopReasons += reason }
        }
        runCurrent()

        repository.setEngineEnabled(false)
        runCurrent()

        assertEquals(listOf(EngineStopReason.EngineDisabled), stopReasons)
        assertFalse(repository.engineEnabled.firstValue())
        assertTrue(controller.deactivationCount >= 1)

        job.cancelAndJoin()
    }

    @Test
    fun stopsWhenNotificationPermissionIsRevokedDuringMonitoring() = runTest {
        val repository = FakeRepository(selectedPackages = setOf("com.example.social"))
        val detector = FakeForegroundAppDetector(initialPackage = "com.example.social")
        val controller = FakeGrayscaleController()
        val permissions = FakePermissions()
        val stopReasons = mutableListOf<EngineStopReason>()
        val engine = FocusEngine(repository, detector, controller, permissions)

        val job = launch {
            engine.run { reason -> stopReasons += reason }
        }
        runCurrent()

        permissions.state = PermissionState(
            usageAccessGranted = true,
            notificationRuntimeGranted = false,
            notificationPolicyAccessGranted = true,
            supportedApi = true,
        )
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(listOf(EngineStopReason.PermissionsMissing), stopReasons)
        assertFalse(repository.engineEnabled.firstValue())
        assertTrue(controller.deactivationCount >= 1)

        job.cancelAndJoin()
    }

    @Test
    fun stopsWhenNotificationPolicyAccessIsRevokedDuringMonitoring() = runTest {
        val repository = FakeRepository(selectedPackages = setOf("com.example.social"))
        val detector = FakeForegroundAppDetector(initialPackage = "com.example.social")
        val controller = FakeGrayscaleController()
        val permissions = FakePermissions()
        val stopReasons = mutableListOf<EngineStopReason>()
        val engine = FocusEngine(repository, detector, controller, permissions)

        val job = launch {
            engine.run { reason -> stopReasons += reason }
        }
        runCurrent()

        permissions.state = PermissionState(
            usageAccessGranted = true,
            notificationRuntimeGranted = true,
            notificationPolicyAccessGranted = false,
            supportedApi = true,
        )
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(listOf(EngineStopReason.PermissionsMissing), stopReasons)
        assertFalse(repository.engineEnabled.firstValue())
        assertTrue(controller.deactivationCount >= 1)

        job.cancelAndJoin()
    }

    @Test
    fun stopsWhenSelectionBecomesEmptyDuringMonitoring() = runTest {
        val repository = FakeRepository(selectedPackages = setOf("com.example.social"))
        val detector = FakeForegroundAppDetector(initialPackage = "com.example.social")
        val controller = FakeGrayscaleController()
        val stopReasons = mutableListOf<EngineStopReason>()
        val engine = FocusEngine(repository, detector, controller, FakePermissions())

        val job = launch {
            engine.run { reason -> stopReasons += reason }
        }
        runCurrent()

        repository.replaceSelectedPackages(emptySet())
        runCurrent()

        assertEquals(listOf(EngineStopReason.NoSelectedApps), stopReasons)
        assertFalse(repository.engineEnabled.firstValue())
        assertTrue(controller.deactivationCount >= 1)

        job.join()
        assertFalse(job.isCancelled)
    }

    @Test
    fun stopsOnGrayscaleControllerFailure() = runTest {
        val repository = FakeRepository(selectedPackages = setOf("com.example.social"))
        val controller = FakeGrayscaleController(throwOnSetActive = true)
        val stopReasons = mutableListOf<EngineStopReason>()
        val engine = FocusEngine(
            repository = repository,
            foregroundAppDetector = FakeForegroundAppDetector(initialPackage = "com.example.social"),
            grayscaleController = controller,
            permissionChecker = FakePermissions(),
        )

        engine.run { reason -> stopReasons += reason }

        assertEquals(listOf(EngineStopReason.InternalError), stopReasons)
        assertFalse(repository.engineEnabled.firstValue())
        assertTrue(controller.deactivationCount >= 1)
    }

    @Test
    fun disablesAndReportsStopWhenCleanupDeactivationFails() = runTest {
        val repository = FakeRepository(selectedPackages = emptySet())
        val controller = FakeGrayscaleController(throwOnDeactivate = true)
        val stopReasons = mutableListOf<EngineStopReason>()
        val engine = FocusEngine(
            repository = repository,
            foregroundAppDetector = FakeForegroundAppDetector(initialPackage = null),
            grayscaleController = controller,
            permissionChecker = FakePermissions(),
        )

        engine.run { reason -> stopReasons += reason }

        assertEquals(listOf(EngineStopReason.NoSelectedApps), stopReasons)
        assertFalse(repository.engineEnabled.firstValue())
        assertTrue(controller.deactivationCount >= 1)
    }

    @Test
    fun disablesAndReportsInternalErrorWhenActivationAndCleanupFail() = runTest {
        val repository = FakeRepository(selectedPackages = setOf("com.example.social"))
        val controller = FakeGrayscaleController(
            throwOnSetActive = true,
            throwOnDeactivate = true,
        )
        val stopReasons = mutableListOf<EngineStopReason>()
        val engine = FocusEngine(
            repository = repository,
            foregroundAppDetector = FakeForegroundAppDetector(initialPackage = "com.example.social"),
            grayscaleController = controller,
            permissionChecker = FakePermissions(),
        )

        engine.run { reason -> stopReasons += reason }

        assertEquals(listOf(EngineStopReason.InternalError), stopReasons)
        assertFalse(repository.engineEnabled.firstValue())
        assertTrue(controller.deactivationCount >= 1)
    }

    @Test
    fun stopsOnForegroundDetectorFailure() = runTest {
        val repository = FakeRepository(selectedPackages = setOf("com.example.social"))
        val controller = FakeGrayscaleController()
        val stopReasons = mutableListOf<EngineStopReason>()
        val engine = FocusEngine(
            repository = repository,
            foregroundAppDetector = ThrowingForegroundAppDetector(),
            grayscaleController = controller,
            permissionChecker = FakePermissions(),
        )

        engine.run { reason -> stopReasons += reason }

        assertEquals(listOf(EngineStopReason.InternalError), stopReasons)
        assertFalse(repository.engineEnabled.firstValue())
        assertTrue(controller.deactivationCount >= 1)
    }

    private class FakeRepository(
        selectedPackages: Set<String>,
        engineEnabled: Boolean = true,
    ) : AppSelectionRepository {
        private val settingsFlow = MutableStateFlow(
            AppSettings(
                selectedPackageNames = selectedPackages,
                engineEnabled = engineEnabled,
            ),
        )

        override val selectedPackages: Flow<Set<String>> =
            settingsFlow.map { it.selectedPackageNames }

        override val engineEnabled: Flow<Boolean> =
            settingsFlow.map { it.engineEnabled }

        override val settings: Flow<AppSettings> = settingsFlow

        override suspend fun setPackageSelected(packageName: String, selected: Boolean) {
            settingsFlow.value = settingsFlow.value.copy(
                selectedPackageNames = if (selected) {
                    settingsFlow.value.selectedPackageNames + packageName
                } else {
                    settingsFlow.value.selectedPackageNames - packageName
                },
            )
        }

        override suspend fun setEngineEnabled(enabled: Boolean) {
            settingsFlow.value = settingsFlow.value.copy(engineEnabled = enabled)
        }

        override suspend fun getZenRuleId(): String? =
            settingsFlow.value.zenRuleId

        override suspend fun setZenRuleId(ruleId: String?) {
            settingsFlow.value = settingsFlow.value.copy(zenRuleId = ruleId)
        }

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            settingsFlow.value = settingsFlow.value.copy(onboardingCompleted = completed)
        }

        override suspend fun setLastKnownSupportedApi(api: Int) {
            settingsFlow.value = settingsFlow.value.copy(lastKnownSupportedApi = api)
        }

        fun replaceSelectedPackages(packages: Set<String>) {
            settingsFlow.value = settingsFlow.value.copy(selectedPackageNames = packages)
        }
    }

    private class FakeForegroundAppDetector(
        initialPackage: String?,
    ) : ForegroundAppDetector {
        val foregroundPackage = MutableStateFlow(initialPackage)

        override fun observeForegroundPackage(): Flow<String?> = foregroundPackage
    }

    private class ThrowingForegroundAppDetector : ForegroundAppDetector {
        override fun observeForegroundPackage(): Flow<String?> = flow {
            throw IllegalStateException("usage events failed")
        }
    }

    private class FakeGrayscaleController(
        private val ensureReadyResult: Boolean = true,
        private val throwOnSetActive: Boolean = false,
        private val throwOnDeactivate: Boolean = false,
    ) : GrayscaleController {
        val activeRequests = mutableListOf<Boolean>()
        var deactivationCount = 0
            private set
        var ensureReadyCount = 0
            private set

        override suspend fun ensureReady(): Boolean {
            ensureReadyCount += 1
            return ensureReadyResult
        }

        override suspend fun setGrayscaleActive(active: Boolean) {
            if (throwOnSetActive) throw IllegalStateException("grayscale failed")
            activeRequests += active
        }

        override suspend fun deactivate() {
            deactivationCount += 1
            if (throwOnDeactivate) throw IllegalStateException("deactivation failed")
        }
    }

    private class FakePermissions(
        var state: PermissionState = PermissionState(
            usageAccessGranted = true,
            notificationRuntimeGranted = true,
            notificationPolicyAccessGranted = true,
            supportedApi = true,
        ),
    ) : PermissionStatusProvider {
        override fun currentState(): PermissionState = state

        override fun hasUsageAccess(): Boolean = state.usageAccessGranted

        override fun hasNotificationRuntimePermission(): Boolean =
            state.notificationRuntimeGranted

        override fun hasNotificationPolicyAccess(): Boolean =
            state.notificationPolicyAccessGranted
    }

    private suspend fun Flow<Boolean>.firstValue(): Boolean = first()
}
