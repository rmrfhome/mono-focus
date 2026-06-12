package com.monofocus.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenRuleReconciliationTest {
    @Test
    fun reusesHealthyStoredRuleAndRemovesOtherMonoFocusRules() {
        val plan = planZenRuleReconciliation(
            storedRule = healthyRule(id = "stored", creationTime = 20),
            existingRules = listOf(
                healthyRule(id = "stored", creationTime = 20),
                healthyRule(id = "duplicate", creationTime = 10),
                healthyRule(id = "other", name = "Other Rule", conditionId = "other://rule"),
            ),
            desired = desired,
        )

        val useRule = plan as ZenRuleReconciliationPlan.UseRule
        assertEquals("stored", useRule.ruleId)
        assertFalse(useRule.repairNeeded)
        assertEquals(listOf("duplicate"), useRule.duplicateRuleIds)
    }

    @Test
    fun repairsStoredRuleWhenRequiredPropertiesAreMissing() {
        val plan = planZenRuleReconciliation(
            storedRule = healthyRule(
                id = "stored",
                displaysGrayscale = false,
                interruptionFilter = 2,
            ),
            existingRules = emptyList(),
            desired = desired,
        )

        val useRule = plan as ZenRuleReconciliationPlan.UseRule
        assertEquals("stored", useRule.ruleId)
        assertTrue(useRule.repairNeeded)
        assertEquals(emptyList<String>(), useRule.duplicateRuleIds)
    }

    @Test
    fun reusesOldestMatchingRuleWhenStoredRuleIsMissing() {
        val plan = planZenRuleReconciliation(
            storedRule = null,
            existingRules = listOf(
                healthyRule(id = "newer", creationTime = 20),
                healthyRule(id = "oldest", creationTime = 10),
                healthyRule(id = "unrelated", name = "Other Rule", conditionId = "other://rule"),
            ),
            desired = desired,
        )

        val useRule = plan as ZenRuleReconciliationPlan.UseRule
        assertEquals("oldest", useRule.ruleId)
        assertFalse(useRule.repairNeeded)
        assertEquals(listOf("newer"), useRule.duplicateRuleIds)
    }

    @Test
    fun createsRuleWhenStoredAndMatchingRulesAreMissing() {
        val plan = planZenRuleReconciliation(
            storedRule = null,
            existingRules = listOf(
                healthyRule(id = "unrelated", name = "Other Rule", conditionId = "other://rule"),
            ),
            desired = desired,
        )

        assertTrue(plan is ZenRuleReconciliationPlan.CreateRule)
        assertEquals(emptyList<String>(), plan.duplicateRuleIds)
    }

    @Test
    fun treatsNameOnlyMatchAsMonoFocusRuleForRepair() {
        val plan = planZenRuleReconciliation(
            storedRule = null,
            existingRules = listOf(
                healthyRule(id = "renamed-condition", conditionId = "monofocus://old-condition"),
            ),
            desired = desired,
        )

        val useRule = plan as ZenRuleReconciliationPlan.UseRule
        assertEquals("renamed-condition", useRule.ruleId)
        assertTrue(useRule.repairNeeded)
    }

    @Test
    fun deactivationPlanUsesStoredRuleWithoutPersistingSameRuleId() {
        val candidates = planZenRuleDeactivation(
            storedRuleId = "stored",
            fallbackRuleId = "stored",
        )

        assertEquals(
            listOf(
                ZenRuleDeactivationCandidate(
                    ruleId = "stored",
                    persistOnSuccess = false,
                ),
            ),
            candidates,
        )
    }

    @Test
    fun deactivationPlanPersistsFallbackRuleOnlyWhenStoredRuleCannotBeUsed() {
        val candidates = planZenRuleDeactivation(
            storedRuleId = "stale",
            fallbackRuleId = "fallback",
        )

        assertEquals(
            listOf(
                ZenRuleDeactivationCandidate(
                    ruleId = "stale",
                    persistOnSuccess = false,
                ),
                ZenRuleDeactivationCandidate(
                    ruleId = "fallback",
                    persistOnSuccess = true,
                ),
            ),
            candidates,
        )
    }

    @Test
    fun deactivationPlanPersistsDiscoveredRuleWhenStoredRuleIsMissing() {
        val candidates = planZenRuleDeactivation(
            storedRuleId = null,
            fallbackRuleId = "fallback",
        )

        assertEquals(
            listOf(
                ZenRuleDeactivationCandidate(
                    ruleId = "fallback",
                    persistOnSuccess = true,
                ),
            ),
            candidates,
        )
    }

    private fun healthyRule(
        id: String,
        name: String? = desired.name,
        conditionId: String? = desired.conditionId,
        configurationActivity: String? = desired.configurationActivity,
        enabled: Boolean = true,
        interruptionFilter: Int = desired.interruptionFilter,
        triggerDescription: String? = desired.triggerDescription,
        type: Int = desired.type,
        displaysGrayscale: Boolean = true,
        creationTime: Long = 0,
    ): ZenRuleSnapshot = ZenRuleSnapshot(
        id = id,
        name = name,
        conditionId = conditionId,
        configurationActivity = configurationActivity,
        enabled = enabled,
        interruptionFilter = interruptionFilter,
        triggerDescription = triggerDescription,
        type = type,
        displaysGrayscale = displaysGrayscale,
        creationTime = creationTime,
    )

    private companion object {
        val desired = DesiredZenRuleSpec(
            name = "MonoFocus Grayscale",
            conditionId = "monofocus://selected-app-active",
            configurationActivity = "com.monofocus.app/.MainActivity",
            interruptionFilter = 1,
            triggerDescription = "When selected apps are open",
            type = 0,
        )
    }
}
