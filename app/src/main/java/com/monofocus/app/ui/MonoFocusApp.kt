package com.monofocus.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monofocus.app.domain.AppEntry
import com.monofocus.app.domain.EngineStopReason

@Composable
fun MonoFocusApp(
    state: MonoFocusUiState,
    onEngineEnabledChanged: (Boolean) -> Unit,
    onPackageSelectedChanged: (String, Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onShowSelectedOnlyChanged: (Boolean) -> Unit,
    onOpenUsageSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onShowPrivacy: () -> Unit,
    onHidePrivacy: () -> Unit,
) {
    when {
        !state.supportedApi -> UnsupportedScreen()
        state.showPrivacy -> PrivacyScreen(onBack = onHidePrivacy)
        else -> MainScreen(
            state = state,
            onEngineEnabledChanged = onEngineEnabledChanged,
            onPackageSelectedChanged = onPackageSelectedChanged,
            onSearchQueryChanged = onSearchQueryChanged,
            onShowSelectedOnlyChanged = onShowSelectedOnlyChanged,
            onOpenUsageSettings = onOpenUsageSettings,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenNotificationPolicySettings = onOpenNotificationPolicySettings,
            onShowPrivacy = onShowPrivacy,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MonoFocusUiState,
    onEngineEnabledChanged: (Boolean) -> Unit,
    onPackageSelectedChanged: (String, Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onShowSelectedOnlyChanged: (Boolean) -> Unit,
    onOpenUsageSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onShowPrivacy: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MonoFocus") },
                actions = {
                    IconButton(
                        onClick = onShowPrivacy,
                        modifier = Modifier.semantics {
                            contentDescription = "Privacy and about"
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            item {
                HeaderSection()
            }
            item {
                EngineSection(
                    enabled = state.engineEnabled,
                    canEnable = state.canEnableEngine,
                    setupRequired = state.setupRequired,
                    lastEngineStopReason = state.lastEngineStopReason,
                    selectedPackageCount = state.selectedPackageCount,
                    onEnabledChanged = onEngineEnabledChanged,
                )
            }
            item {
                PermissionStatusCard(
                    state = state,
                    onOpenUsageSettings = onOpenUsageSettings,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenNotificationPolicySettings = onOpenNotificationPolicySettings,
                    onCopyDiagnostics = {
                        copyDiagnosticsToClipboard(
                            context = context,
                            state = state,
                        )
                    },
                )
            }
            item {
                SearchField(
                    query = state.searchQuery,
                    onQueryChanged = onSearchQueryChanged,
                )
            }
            item {
                AppListFilter(
                    showSelectedOnly = state.showSelectedOnly,
                    onShowSelectedOnlyChanged = onShowSelectedOnlyChanged,
                )
            }

            when {
                state.loadingApps -> item {
                    StatusText("Loading apps...")
                }
                state.apps.isEmpty() -> item {
                    StatusText(emptyAppListMessage(state))
                }
                else -> items(
                    items = state.apps,
                    key = { app -> app.packageName },
                ) { app ->
                    AppRow(
                        app = app,
                        onSelectedChanged = { selected ->
                            onPackageSelectedChanged(app.packageName, selected)
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 88.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Make distracting apps grayscale while you use them.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EngineSection(
    enabled: Boolean,
    canEnable: Boolean,
    setupRequired: Boolean,
    lastEngineStopReason: EngineStopReason?,
    selectedPackageCount: Int,
    onEnabledChanged: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Grayscale engine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = engineStatusText(
                        enabled = enabled,
                        setupRequired = setupRequired,
                        selectedPackageCount = selectedPackageCount,
                        lastEngineStopReason = lastEngineStopReason,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = enabled,
                enabled = enabled || canEnable,
                onCheckedChange = onEnabledChanged,
                modifier = Modifier.semantics {
                    contentDescription = "Grayscale engine"
                    stateDescription = if (enabled) "On" else "Off"
                },
            )
        }
    }
}

private fun engineStatusText(
    enabled: Boolean,
    setupRequired: Boolean,
    selectedPackageCount: Int,
    lastEngineStopReason: EngineStopReason?,
): String =
    when {
        lastEngineStopReason == EngineStopReason.RuleUnavailable && enabled ->
            "Engine is on, but this device rejected Android's grayscale rule."
        lastEngineStopReason == EngineStopReason.RuleUnavailable ->
            "This device rejected Android's grayscale rule."
        lastEngineStopReason == EngineStopReason.InternalError ->
            "Monitoring stopped after an internal error."
        setupRequired && enabled -> "Engine is on. Setup is required before grayscale can run."
        setupRequired -> "Setup required before grayscale can work."
        selectedPackageCount == 0 && enabled -> "Engine is on. Select apps to grayscale."
        selectedPackageCount == 0 -> "Select apps that should appear in grayscale."
        enabled -> "Selected apps will become grayscale while open."
        else -> "Ready"
    }

@Composable
private fun PermissionStatusCard(
    state: MonoFocusUiState,
    onOpenUsageSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onCopyDiagnostics: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.permissionState.ready) {
                PermissionLine(
                    icon = Icons.Outlined.CheckCircle,
                    title = "Ready",
                    body = "Selected apps will become grayscale while open.",
                )
            } else {
                Text(
                    text = "Setup required before grayscale can work.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!state.permissionState.usageAccessGranted) {
                    PermissionLine(
                        icon = Icons.Outlined.WarningAmber,
                        title = "Usage Access required",
                        body = "MonoFocus needs Usage Access to know which app is currently open. It does not read app content, messages, text, images, or browsing activity.",
                    )
                    Button(
                        onClick = onOpenUsageSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Usage Access Settings", maxLines = 2)
                    }
                }
                if (!state.permissionState.notificationRuntimeGranted) {
                    PermissionLine(
                        icon = Icons.Outlined.WarningAmber,
                        title = "Notifications permission required",
                        body = "MonoFocus needs this to show the persistent monitoring notification and pause actions while the engine is active.",
                    )
                    Button(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Allow Notifications", maxLines = 2)
                    }
                }
                if (!state.permissionState.notificationPolicyAccessGranted) {
                    PermissionLine(
                        icon = Icons.Outlined.WarningAmber,
                        title = "Modes permission required",
                        body = "Android uses Modes / Do Not Disturb access to allow apps to apply grayscale display effects. MonoFocus will not silence notifications.",
                    )
                    Button(
                        onClick = onOpenNotificationPolicySettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Modes Permission Settings", maxLines = 2)
                    }
                }
            }
            OutlinedButton(
                onClick = onCopyDiagnostics,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Copy diagnostics"
                    },
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Copy diagnostics", maxLines = 2)
            }
        }
    }
}

private fun copyDiagnosticsToClipboard(
    context: Context,
    state: MonoFocusUiState,
) {
    val diagnostics = buildDiagnosticsText(
        state = state,
        appPackageName = context.packageName,
        appVersionName = context.appVersionName(),
        appVersionCode = context.appVersionCode(),
        androidRelease = Build.VERSION.RELEASE.orUnknown(),
        sdkInt = Build.VERSION.SDK_INT,
        manufacturer = Build.MANUFACTURER.orUnknown(),
        brand = Build.BRAND.orUnknown(),
        model = Build.MODEL.orUnknown(),
        device = Build.DEVICE.orUnknown(),
    )
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(
        ClipData.newPlainText("MonoFocus diagnostics", diagnostics),
    )
    Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
}

private fun Context.appVersionName(): String =
    runCatching { packageInfo().versionName.orUnknown() }
        .getOrDefault("unknown")

private fun Context.appVersionCode(): Long =
    runCatching { packageInfo().longVersionCode }
        .getOrDefault(0L)

private fun Context.packageInfo() =
    packageManager.getPackageInfo(
        packageName,
        PackageManager.PackageInfoFlags.of(0),
    )

private fun String?.orUnknown(): String =
    this?.takeIf { value -> value.isNotBlank() } ?: "unknown"

@Composable
private fun PermissionLine(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        singleLine = true,
        placeholder = { Text("Search apps") },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListFilter(
    showSelectedOnly: Boolean,
    onShowSelectedOnlyChanged: (Boolean) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        SegmentedButton(
            selected = !showSelectedOnly,
            onClick = { onShowSelectedOnlyChanged(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.weight(1f),
        ) {
            Text("All")
        }
        SegmentedButton(
            selected = showSelectedOnly,
            onClick = { onShowSelectedOnlyChanged(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.weight(1f),
        ) {
            Text("Grayscale")
        }
    }
}

private fun emptyAppListMessage(state: MonoFocusUiState): String =
    when {
        state.showSelectedOnly && state.selectedPackageCount == 0 -> "No grayscale apps selected."
        state.showSelectedOnly -> "No grayscale apps match your search."
        state.searchQuery.isNotBlank() -> "No apps match your search."
        else -> "No launchable apps found."
    }

@Composable
private fun AppRow(
    app: AppEntry,
    onSelectedChanged: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier
            .clickable(
                role = Role.Switch,
                onClick = { onSelectedChanged(!app.isSelected) },
            )
            .semantics {
                contentDescription = "${app.label}, ${app.packageName}"
                stateDescription = if (app.isSelected) "Selected" else "Not selected"
            },
        leadingContent = {
            AppIcon(
                icon = app.icon,
                label = app.label,
            )
        },
        headlineContent = {
            Text(
                text = app.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = app.packageName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Switch(
                checked = app.isSelected,
                onCheckedChange = onSelectedChanged,
                modifier = Modifier.semantics {
                    contentDescription = "${app.label} grayscale selection"
                    stateDescription = if (app.isSelected) "Selected" else "Not selected"
                },
            )
        },
    )
}

@Composable
private fun AppIcon(
    icon: ImageBitmap?,
    label: String,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Apps,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Back"
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "MonoFocus works locally on your device.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text("It stores only the package names of apps you select.")
            Text("It uses Usage Access only to detect which app is currently open.")
            Text("It does not read app content, messages, notifications, text, images, or browsing activity.")
            Text("It does not send data to any server.")
            Text("When a selected app is active, Android may apply grayscale to the whole display, including split-screen, picture-in-picture, and overlays.")
        }
    }
}

@Composable
fun UnsupportedScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Android 15 or newer required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "MonoFocus uses Android's public grayscale device effect API. This feature is available only on Android 15 or newer.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
