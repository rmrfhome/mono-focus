package com.monofocus.app.data

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenDeviceEffects
import com.monofocus.app.MainActivity
import com.monofocus.app.R
import com.monofocus.app.domain.AppSelectionRepository
import com.monofocus.app.domain.GrayscaleController
import com.monofocus.app.platform.PermissionStatusProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ZenGrayscaleController(
    private val context: Context,
    private val notificationManager: NotificationManager,
    private val repository: AppSelectionRepository,
    private val permissionChecker: PermissionStatusProvider,
) : GrayscaleController {
    private val mutex = Mutex()
    private var lastRequestedActive: Boolean? = null

    override suspend fun ensureReady(): Boolean = mutex.withLock {
        if (Build.VERSION.SDK_INT < 35) return@withLock false
        if (!permissionChecker.hasNotificationPolicyAccess()) return@withLock false

        val storedRuleId = repository.getZenRuleId()
        val storedRule = storedRuleId?.let { id ->
            runCatching { notificationManager.getAutomaticZenRule(id) }.getOrNull()
        }
        val automaticRules = runCatching { notificationManager.automaticZenRules }
            .getOrNull()
            .orEmpty()
        val desired = desiredRuleSpec
        val plan = planZenRuleReconciliation(
            storedRule = storedRule?.toSnapshot(storedRuleId.orEmpty()),
            existingRules = automaticRules.map { (id, rule) -> rule.toSnapshot(id) },
            desired = desired,
        )

        plan.duplicateRuleIds.forEach { duplicateId ->
            runCatching { notificationManager.removeAutomaticZenRule(duplicateId) }
        }

        when (plan) {
            is ZenRuleReconciliationPlan.UseRule -> {
                val retainedRule = if (plan.ruleId == storedRuleId && storedRule != null) {
                    storedRule
                } else {
                    automaticRules[plan.ruleId]
                        ?: runCatching { notificationManager.getAutomaticZenRule(plan.ruleId) }.getOrNull()
                } ?: return@withLock false

                if (storedRule == null || plan.ruleId != storedRuleId || plan.repairNeeded) {
                    lastRequestedActive = null
                }

                val repaired = if (plan.repairNeeded) {
                    repairRule(plan.ruleId, retainedRule)
                } else {
                    true
                }

                if (repaired) repository.setZenRuleId(plan.ruleId)
                return@withLock repaired
            }

            is ZenRuleReconciliationPlan.CreateRule -> {
                lastRequestedActive = null
            }
        }

        val ruleId = runCatching {
            notificationManager.addAutomaticZenRule(buildDesiredRule())
        }.getOrNull()

        if (ruleId.isNullOrBlank()) {
            repository.setZenRuleId(null)
            false
        } else {
            repository.setZenRuleId(ruleId)
            true
        }
    }

    override suspend fun setGrayscaleActive(active: Boolean) {
        if (!ensureReady()) {
            throw IllegalStateException("MonoFocus grayscale rule is unavailable")
        }
        if (lastRequestedActive == active) return

        val ruleId = repository.getZenRuleId()
            ?: throw IllegalStateException("MonoFocus grayscale rule ID is unavailable")
        if (!permissionChecker.hasNotificationPolicyAccess()) {
            throw IllegalStateException("Notification Policy Access is unavailable")
        }

        val state = if (active) Condition.STATE_TRUE else Condition.STATE_FALSE
        val summary = if (active) "Selected app active" else "Selected app inactive"
        val condition = Condition(CONDITION_URI, summary, state, Condition.SOURCE_CONTEXT)

        val updated = runCatching {
            notificationManager.setAutomaticZenRuleState(ruleId, condition)
        }.onSuccess {
            lastRequestedActive = active
        }.isSuccess

        if (!updated) {
            throw IllegalStateException("Failed to update MonoFocus grayscale rule state")
        }
    }

    override suspend fun deactivate() {
        if (Build.VERSION.SDK_INT < 35) return

        val storedRuleId = repository.getZenRuleId()
        val fallbackRuleId = if (permissionChecker.hasNotificationPolicyAccess()) {
            findExistingRuleId()
        } else {
            null
        }
        val candidates = planZenRuleDeactivation(
            storedRuleId = storedRuleId,
            fallbackRuleId = fallbackRuleId,
        )
        if (candidates.isEmpty()) return

        val condition = Condition(
            CONDITION_URI,
            "Selected app inactive",
            Condition.STATE_FALSE,
            Condition.SOURCE_CONTEXT,
        )

        for (candidate in candidates) {
            val updated = runCatching {
                notificationManager.setAutomaticZenRuleState(candidate.ruleId, condition)
            }.isSuccess
            if (updated) {
                if (candidate.persistOnSuccess) {
                    repository.setZenRuleId(candidate.ruleId)
                }
                lastRequestedActive = false
                return
            }
        }
    }

    private fun repairRule(ruleId: String, rule: AutomaticZenRule): Boolean {
        val updated = buildDesiredRule(existingRule = rule)
        val repaired = runCatching {
            notificationManager.updateAutomaticZenRule(ruleId, updated)
        }.getOrDefault(false)
        if (repaired) lastRequestedActive = null
        return repaired
    }

    private fun findExistingRuleId(): String? =
        runCatching { notificationManager.automaticZenRules }
            .getOrNull()
            .orEmpty()
            .entries
            .firstOrNull { (id, rule) -> rule.toSnapshot(id).isMonoFocusRule(desiredRuleSpec) }
            ?.key

    private fun buildDesiredRule(existingRule: AutomaticZenRule? = null): AutomaticZenRule {
        val builder = if (existingRule == null) {
            AutomaticZenRule.Builder(RULE_NAME, CONDITION_URI)
        } else {
            AutomaticZenRule.Builder(existingRule)
                .setName(RULE_NAME)
                .setConditionId(CONDITION_URI)
        }

        return builder
            .setConfigurationActivity(configurationActivity)
            .setDeviceEffects(grayscaleEffects)
            .setEnabled(true)
            .setIconResId(R.drawable.ic_stat_monofocus)
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            .setManualInvocationAllowed(false)
            .setTriggerDescription(TRIGGER_DESCRIPTION)
            .setType(AutomaticZenRule.TYPE_OTHER)
            .build()
    }

    private fun AutomaticZenRule.toSnapshot(ruleId: String): ZenRuleSnapshot =
        ZenRuleSnapshot(
            id = ruleId,
            name = name,
            conditionId = conditionId?.toString(),
            configurationActivity = configurationActivity?.flattenToString(),
            enabled = isEnabled,
            interruptionFilter = interruptionFilter,
            triggerDescription = triggerDescription,
            type = type,
            displaysGrayscale = deviceEffects?.shouldDisplayGrayscale() == true,
            creationTime = creationTime,
        )

    private val configurationActivity: ComponentName
        get() = ComponentName(context, MainActivity::class.java)

    private val grayscaleEffects: ZenDeviceEffects
        get() = ZenDeviceEffects.Builder()
            .setShouldDisplayGrayscale(true)
            .build()

    private val desiredRuleSpec: DesiredZenRuleSpec
        get() = DesiredZenRuleSpec(
            name = RULE_NAME,
            conditionId = CONDITION_URI.toString(),
            configurationActivity = configurationActivity.flattenToString(),
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
            triggerDescription = TRIGGER_DESCRIPTION,
            type = AutomaticZenRule.TYPE_OTHER,
        )

    companion object {
        const val RULE_NAME = "MonoFocus Grayscale"
        const val TRIGGER_DESCRIPTION = "When selected apps are open"
        val CONDITION_URI: Uri = Uri.parse("monofocus://selected-app-active")
    }
}
