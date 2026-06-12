package com.monofocus.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monofocus.app.ui.MonoFocusApp
import com.monofocus.app.ui.MonoFocusViewModel
import com.monofocus.app.ui.theme.MonoFocusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MonoFocusApplication).container
        setContent {
            MonoFocusTheme {
                val viewModel: MonoFocusViewModel = viewModel(
                    factory = MonoFocusViewModel.Factory(
                        context = applicationContext,
                        container = container,
                    ),
                )
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) {
                    viewModel.refreshPermissions()
                }

                DisposableEffect(lifecycleOwner, viewModel) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.onResume()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                MonoFocusApp(
                    state = uiState,
                    onEngineEnabledChanged = viewModel::setEngineEnabled,
                    onPackageSelectedChanged = viewModel::setPackageSelected,
                    onSearchQueryChanged = viewModel::setSearchQuery,
                    onShowSelectedOnlyChanged = viewModel::setShowSelectedOnly,
                    onOpenUsageSettings = viewModel::openUsageSettings,
                    onRequestNotificationPermission = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onOpenNotificationPolicySettings = viewModel::openNotificationPolicySettings,
                    onShowPrivacy = { viewModel.setPrivacyVisible(true) },
                    onHidePrivacy = { viewModel.setPrivacyVisible(false) },
                )
            }
        }
    }
}
