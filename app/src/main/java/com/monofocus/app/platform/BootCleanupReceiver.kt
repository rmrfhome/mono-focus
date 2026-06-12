package com.monofocus.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.monofocus.app.MonoFocusApplication
import com.monofocus.app.domain.deactivateBestEffort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldRunBootCleanup(intent.action)) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                handleBootCompleted(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleBootCompleted(context: Context) {
        val application = context as? MonoFocusApplication ?: return
        application.container.grayscaleController.deactivateBestEffort()
    }
}

internal fun shouldRunBootCleanup(action: String?): Boolean =
    action == Intent.ACTION_BOOT_COMPLETED
