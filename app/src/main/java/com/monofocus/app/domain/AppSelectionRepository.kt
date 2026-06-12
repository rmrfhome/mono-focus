package com.monofocus.app.domain

import kotlinx.coroutines.flow.Flow

interface AppSelectionRepository {
    val selectedPackages: Flow<Set<String>>
    val engineEnabled: Flow<Boolean>
    val settings: Flow<AppSettings>

    suspend fun setPackageSelected(packageName: String, selected: Boolean)
    suspend fun setEngineEnabled(enabled: Boolean)
    suspend fun getZenRuleId(): String?
    suspend fun setZenRuleId(ruleId: String?)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setLastKnownSupportedApi(api: Int)
}

data class AppSettings(
    val selectedPackageNames: Set<String> = emptySet(),
    val engineEnabled: Boolean = false,
    val zenRuleId: String? = null,
    val onboardingCompleted: Boolean = false,
    val lastKnownSupportedApi: Int = 0,
    val startAfterRebootEnabled: Boolean = false,
)
