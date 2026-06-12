package com.monofocus.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.monofocus.app.AppContainer
import com.monofocus.app.domain.AppEntry
import com.monofocus.app.domain.deactivateBestEffort
import com.monofocus.app.service.FocusMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
class MonoFocusViewModel(
    context: Context,
    private val container: AppContainer,
) : ViewModel() {
    private val context = context.applicationContext
    private val permissionState = MutableStateFlow(container.permissionChecker.currentState())
    private val baseApps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val searchQuery = MutableStateFlow("")
    private val showSelectedOnly = MutableStateFlow(false)
    private val loadingApps = MutableStateFlow(true)
    private val showPrivacy = MutableStateFlow(false)

    private val listInputs = combine(
        permissionState,
        baseApps,
        searchQuery,
        showSelectedOnly,
        loadingApps,
    ) { permissions, apps, query, selectedOnly, loading ->
        ListInputs(
            permissions = permissions,
            apps = apps,
            query = query,
            selectedOnly = selectedOnly,
            loading = loading,
        )
    }

    val uiState = combine(
        listInputs,
        showPrivacy,
        container.repository.selectedPackages,
        container.repository.engineEnabled,
    ) { inputs, privacyVisible, selectedPackages, engineEnabled ->
        val selectedLaunchablePackageCount = selectedLaunchablePackageCount(
            apps = inputs.apps,
            selectedPackages = selectedPackages,
        )
        MonoFocusUiState(
            supportedApi = inputs.permissions.supportedApi,
            permissionState = inputs.permissions,
            apps = buildPresentedApps(
                apps = inputs.apps,
                selectedPackages = selectedPackages,
                searchQuery = inputs.query,
                showSelectedOnly = inputs.selectedOnly,
            ),
            searchQuery = inputs.query,
            showSelectedOnly = inputs.selectedOnly,
            engineEnabled = engineEnabled &&
                inputs.permissions.ready &&
                selectedLaunchablePackageCount > 0,
            selectedPackageCount = selectedLaunchablePackageCount,
            loadingApps = inputs.loading,
            showPrivacy = privacyVisible,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MonoFocusUiState(
            permissionState = container.permissionChecker.currentState(),
        ),
    )

    init {
        refreshPermissions()
        reloadApps(reconcileAfterLoad = true)
    }

    fun onResume() {
        refreshPermissions()
        reloadApps(reconcileAfterLoad = true)
    }

    fun refreshPermissions() {
        permissionState.value = container.permissionChecker.currentState()
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setShowSelectedOnly(showSelectedOnly: Boolean) {
        this.showSelectedOnly.value = showSelectedOnly
    }

    fun setPrivacyVisible(visible: Boolean) {
        showPrivacy.value = visible
    }

    fun setPackageSelected(packageName: String, selected: Boolean) {
        viewModelScope.launch {
            container.repository.setPackageSelected(packageName, selected)
            val selectedPackages = container.repository.selectedPackages.first()
            if (
                !hasSelectedLaunchableApps(
                    apps = baseApps.value,
                    selectedPackages = selectedPackages,
                ) &&
                container.repository.engineEnabled.first()
            ) {
                setEngineEnabled(false)
            }
        }
    }

    fun setEngineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            refreshPermissions()
            val permissions = permissionState.value
            val selectedPackages = container.repository.selectedPackages.first()

            when (
                chooseEngineToggleAction(
                    requestedEnabled = enabled,
                    permissionsReady = permissions.ready,
                    hasSelectedApps = hasSelectedLaunchableApps(
                        apps = baseApps.value,
                        selectedPackages = selectedPackages,
                    ),
                )
            ) {
                EngineToggleAction.EnableAndStart -> {
                    container.repository.setPausedUntilEpochMillis(0L)
                    container.repository.setEngineEnabled(true)
                    FocusMonitorService.start(context)
                }
                EngineToggleAction.DisableAndStop -> disableAndStopEngine()
            }
        }
    }

    fun openUsageSettings() {
        context.startActivity(
            container.settingsIntentFactory.usageAccessSettings()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openNotificationPolicySettings() {
        context.startActivity(
            container.settingsIntentFactory.notificationPolicyAccessSettings()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun reloadApps(reconcileAfterLoad: Boolean = false) {
        viewModelScope.launch(Dispatchers.Default) {
            loadingApps.value = true
            baseApps.value = runCatching {
                container.launchableAppsProvider.getLaunchableApps()
            }.getOrDefault(emptyList())
            loadingApps.value = false
            if (reconcileAfterLoad) {
                reconcileEngineService()
            }
        }
    }

    private fun reconcileEngineService() {
        viewModelScope.launch {
            val engineEnabled = container.repository.engineEnabled.first()
            val selectedPackages = container.repository.selectedPackages.first()
            val permissions = permissionState.value

            when (
                chooseEngineResumeAction(
                    engineEnabled = engineEnabled,
                    permissionsReady = permissions.ready,
                    hasSelectedApps = hasSelectedLaunchableApps(
                        apps = baseApps.value,
                        selectedPackages = selectedPackages,
                    ),
                )
            ) {
                EngineResumeAction.EnsureRuleAndStart -> {
                    if (container.grayscaleController.ensureReady()) {
                        FocusMonitorService.start(context)
                    } else {
                        disableAndStopEngine()
                    }
                }
                EngineResumeAction.DisableAndStop -> disableAndStopEngine()
                EngineResumeAction.DeactivateOnly -> {
                    container.grayscaleController.deactivateBestEffort()
                }
            }
        }
    }

    private suspend fun disableAndStopEngine() {
        container.repository.setEngineEnabled(false)
        container.repository.setPausedUntilEpochMillis(0L)
        container.grayscaleController.deactivateBestEffort()
        FocusMonitorService.stop(context)
    }

    class Factory(
        private val context: Context,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MonoFocusViewModel(context.applicationContext, container) as T
    }

    private data class ListInputs(
        val permissions: com.monofocus.app.platform.PermissionState,
        val apps: List<AppEntry>,
        val query: String,
        val selectedOnly: Boolean,
        val loading: Boolean,
    )
}
