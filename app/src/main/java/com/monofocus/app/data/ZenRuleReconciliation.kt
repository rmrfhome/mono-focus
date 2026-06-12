package com.monofocus.app.data

internal data class DesiredZenRuleSpec(
    val name: String,
    val conditionId: String,
    val configurationActivity: String,
    val interruptionFilter: Int,
    val triggerDescription: String,
    val type: Int,
)

internal data class ZenRuleSnapshot(
    val id: String,
    val name: String?,
    val conditionId: String?,
    val configurationActivity: String?,
    val enabled: Boolean,
    val interruptionFilter: Int,
    val triggerDescription: String?,
    val type: Int,
    val displaysGrayscale: Boolean,
    val creationTime: Long,
)

internal sealed interface ZenRuleReconciliationPlan {
    val duplicateRuleIds: List<String>

    data class UseRule(
        val ruleId: String,
        val repairNeeded: Boolean,
        override val duplicateRuleIds: List<String>,
    ) : ZenRuleReconciliationPlan

    data class CreateRule(
        override val duplicateRuleIds: List<String> = emptyList(),
    ) : ZenRuleReconciliationPlan
}

internal data class ZenRuleDeactivationCandidate(
    val ruleId: String,
    val persistOnSuccess: Boolean,
)

internal fun planZenRuleReconciliation(
    storedRule: ZenRuleSnapshot?,
    existingRules: List<ZenRuleSnapshot>,
    desired: DesiredZenRuleSpec,
): ZenRuleReconciliationPlan {
    if (storedRule != null) {
        val duplicateIds = existingRules
            .filter { rule ->
                rule.id != storedRule.id && rule.isMonoFocusRule(desired)
            }
            .map { it.id }

        return ZenRuleReconciliationPlan.UseRule(
            ruleId = storedRule.id,
            repairNeeded = storedRule.needsRepair(desired),
            duplicateRuleIds = duplicateIds,
        )
    }

    val matchingRules = existingRules
        .filter { it.isMonoFocusRule(desired) }
        .sortedBy { it.creationTime }

    val retainedRule = matchingRules.firstOrNull()
        ?: return ZenRuleReconciliationPlan.CreateRule()

    return ZenRuleReconciliationPlan.UseRule(
        ruleId = retainedRule.id,
        repairNeeded = retainedRule.needsRepair(desired),
        duplicateRuleIds = matchingRules.drop(1).map { it.id },
    )
}

internal fun ZenRuleSnapshot.needsRepair(desired: DesiredZenRuleSpec): Boolean =
    name != desired.name ||
        conditionId != desired.conditionId ||
        configurationActivity != desired.configurationActivity ||
        !enabled ||
        interruptionFilter != desired.interruptionFilter ||
        triggerDescription != desired.triggerDescription ||
        type != desired.type ||
        !displaysGrayscale

internal fun ZenRuleSnapshot.isMonoFocusRule(desired: DesiredZenRuleSpec): Boolean =
    conditionId == desired.conditionId || name == desired.name

internal fun planZenRuleDeactivation(
    storedRuleId: String?,
    fallbackRuleId: String?,
): List<ZenRuleDeactivationCandidate> =
    listOfNotNull(storedRuleId, fallbackRuleId)
        .distinct()
        .map { ruleId ->
            ZenRuleDeactivationCandidate(
                ruleId = ruleId,
                persistOnSuccess = ruleId != storedRuleId,
            )
        }
