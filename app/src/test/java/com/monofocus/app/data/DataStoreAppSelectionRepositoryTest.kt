package com.monofocus.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.monofocus.app.domain.EngineStopReason
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreAppSelectionRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultsToDisabledLocalOnlySettings() = runTest {
        val repository = DataStoreAppSelectionRepository(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(temporaryFolder.root, "defaults.preferences_pb") },
            ),
        )

        assertTrue(repository.selectedPackages.first().isEmpty())
        assertFalse(repository.engineEnabled.first())
        assertNull(repository.getZenRuleId())

        val settings = repository.settings.first()
        assertTrue(settings.selectedPackageNames.isEmpty())
        assertFalse(settings.engineEnabled)
        assertNull(settings.zenRuleId)
        assertNull(settings.lastEngineStopReason)
        assertEquals(0L, settings.pausedUntilEpochMillis)
        assertFalse(settings.onboardingCompleted)
        assertEquals(0, settings.lastKnownSupportedApi)
        assertFalse(settings.startAfterRebootEnabled)
    }

    @Test
    fun persistsSelectionAndSettings() = runTest {
        val repository = DataStoreAppSelectionRepository(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
            ),
        )

        assertTrue(repository.selectedPackages.first().isEmpty())
        assertFalse(repository.engineEnabled.first())

        repository.setPackageSelected("com.example.social", selected = true)
        repository.setPackageSelected("com.example.reader", selected = true)
        repository.setPackageSelected("com.example.reader", selected = false)
        repository.setEngineEnabled(true)
        repository.setLastEngineStopReason(EngineStopReason.RuleUnavailable)
        repository.setZenRuleId("rule-123")
        repository.setPausedUntilEpochMillis(123_456L)
        repository.setOnboardingCompleted(true)
        repository.setLastKnownSupportedApi(35)

        assertEquals(setOf("com.example.social"), repository.selectedPackages.first())
        assertTrue(repository.engineEnabled.first())
        assertEquals("rule-123", repository.getZenRuleId())

        val settings = repository.settings.first()
        assertEquals(setOf("com.example.social"), settings.selectedPackageNames)
        assertTrue(settings.engineEnabled)
        assertEquals(EngineStopReason.RuleUnavailable, settings.lastEngineStopReason)
        assertEquals(123_456L, settings.pausedUntilEpochMillis)
        assertTrue(settings.onboardingCompleted)
        assertEquals(35, settings.lastKnownSupportedApi)
        assertFalse(settings.startAfterRebootEnabled)

        repository.setZenRuleId(null)
        repository.setLastEngineStopReason(null)
        repository.setPausedUntilEpochMillis(0L)

        assertNull(repository.getZenRuleId())
        assertNull(repository.settings.first().lastEngineStopReason)
        assertEquals(0L, repository.settings.first().pausedUntilEpochMillis)
    }

    @Test
    fun normalizesPackageNamesAtPersistenceBoundary() = runTest {
        val repository = DataStoreAppSelectionRepository(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(temporaryFolder.root, "normalized.preferences_pb") },
            ),
        )

        repository.setPackageSelected("  com.example.social  ", selected = true)
        repository.setPackageSelected("   ", selected = true)

        assertEquals(setOf("com.example.social"), repository.selectedPackages.first())

        repository.setPackageSelected(" com.example.social ", selected = false)

        assertTrue(repository.selectedPackages.first().isEmpty())
    }

    @Test
    fun normalizesMalformedStoredValuesOnRead() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "malformed.preferences_pb") },
        )
        val repository = DataStoreAppSelectionRepository(dataStore)
        val selectedPackagesKey = stringSetPreferencesKey("selected_package_names")
        val zenRuleIdKey = stringPreferencesKey("zen_rule_id")
        val lastEngineStopReasonKey = stringPreferencesKey("last_engine_stop_reason")
        val pausedUntilEpochMillisKey = longPreferencesKey("paused_until_epoch_millis")

        dataStore.edit { preferences ->
            preferences[selectedPackagesKey] = setOf(
                "  com.example.social  ",
                " ",
                "",
                "com.example.reader",
            )
            preferences[zenRuleIdKey] = "   "
            preferences[lastEngineStopReasonKey] = "not-a-stop-reason"
            preferences[pausedUntilEpochMillisKey] = -1L
        }

        assertEquals(
            setOf("com.example.social", "com.example.reader"),
            repository.selectedPackages.first(),
        )
        assertNull(repository.getZenRuleId())

        val settings = repository.settings.first()
        assertEquals(
            setOf("com.example.social", "com.example.reader"),
            settings.selectedPackageNames,
        )
        assertNull(settings.zenRuleId)
        assertNull(settings.lastEngineStopReason)
        assertEquals(0L, settings.pausedUntilEpochMillis)
    }
}
