package com.monofocus.app.platform

import android.content.Intent
import android.provider.Settings

class SettingsIntentFactory {
    fun usageAccessSettings(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun notificationPolicyAccessSettings(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
}
