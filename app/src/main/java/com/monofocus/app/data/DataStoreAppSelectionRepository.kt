package com.monofocus.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.monofocus.app.domain.AppSelectionRepository
import com.monofocus.app.domain.AppSettings
import com.monofocus.app.domain.EngineStopReason
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.monoFocusDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "monofocus_settings",
)

class DataStoreAppSelectionRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : AppSelectionRepository {
    constructor(context: Context) : this(context.monoFocusDataStore)

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            AppSettings(
                selectedPackageNames = preferences[Keys.SelectedPackages]
                    .orEmpty()
                    .normalizedPackageNames(),
                engineEnabled = preferences[Keys.EngineEnabled] ?: false,
                lastEngineStopReason = preferences[Keys.LastEngineStopReason].toEngineStopReason(),
                zenRuleId = preferences[Keys.ZenRuleId].normalizedRuleId(),
                pausedUntilEpochMillis = (preferences[Keys.PausedUntilEpochMillis] ?: 0L)
                    .coerceAtLeast(0L),
                onboardingCompleted = preferences[Keys.OnboardingCompleted] ?: false,
                lastKnownSupportedApi = preferences[Keys.LastKnownSupportedApi] ?: 0,
                startAfterRebootEnabled = preferences[Keys.StartAfterRebootEnabled] ?: false,
            )
        }

    override val selectedPackages: Flow<Set<String>> =
        settings.map { it.selectedPackageNames }

    override val engineEnabled: Flow<Boolean> =
        settings.map { it.engineEnabled }

    override suspend fun setPackageSelected(packageName: String, selected: Boolean) {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isBlank()) return

        dataStore.edit { preferences ->
            val current = preferences[Keys.SelectedPackages].orEmpty()
            preferences[Keys.SelectedPackages] = if (selected) {
                current + normalizedPackageName
            } else {
                current - normalizedPackageName
            }
        }
    }

    override suspend fun setEngineEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.EngineEnabled] = enabled
        }
    }

    override suspend fun setLastEngineStopReason(reason: EngineStopReason?) {
        dataStore.edit { preferences ->
            if (reason == null) {
                preferences.remove(Keys.LastEngineStopReason)
            } else {
                preferences[Keys.LastEngineStopReason] = reason.name
            }
        }
    }

    override suspend fun getZenRuleId(): String? =
        dataStore.data.first()[Keys.ZenRuleId].normalizedRuleId()

    override suspend fun setZenRuleId(ruleId: String?) {
        dataStore.edit { preferences ->
            val normalizedRuleId = ruleId?.trim()
            if (normalizedRuleId.isNullOrBlank()) {
                preferences.remove(Keys.ZenRuleId)
            } else {
                preferences[Keys.ZenRuleId] = normalizedRuleId
            }
        }
    }

    override suspend fun setPausedUntilEpochMillis(epochMillis: Long) {
        dataStore.edit { preferences ->
            val normalizedEpochMillis = epochMillis.coerceAtLeast(0L)
            if (normalizedEpochMillis == 0L) {
                preferences.remove(Keys.PausedUntilEpochMillis)
            } else {
                preferences[Keys.PausedUntilEpochMillis] = normalizedEpochMillis
            }
        }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.OnboardingCompleted] = completed
        }
    }

    override suspend fun setLastKnownSupportedApi(api: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.LastKnownSupportedApi] = api
        }
    }

    private object Keys {
        val SelectedPackages = stringSetPreferencesKey("selected_package_names")
        val EngineEnabled = booleanPreferencesKey("engine_enabled")
        val LastEngineStopReason = stringPreferencesKey("last_engine_stop_reason")
        val ZenRuleId = stringPreferencesKey("zen_rule_id")
        val PausedUntilEpochMillis = longPreferencesKey("paused_until_epoch_millis")
        val OnboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val LastKnownSupportedApi = intPreferencesKey("last_known_supported_api")
        val StartAfterRebootEnabled = booleanPreferencesKey("start_after_reboot_enabled")
    }
}

private fun Set<String>.normalizedPackageNames(): Set<String> =
    map { packageName -> packageName.trim() }
        .filter { packageName -> packageName.isNotBlank() }
        .toSet()

private fun String?.normalizedRuleId(): String? =
    this?.trim()?.takeIf { ruleId -> ruleId.isNotBlank() }

private fun String?.toEngineStopReason(): EngineStopReason? =
    this?.trim()?.takeIf { value -> value.isNotBlank() }?.let { value ->
        runCatching { EngineStopReason.valueOf(value) }.getOrNull()
    }
