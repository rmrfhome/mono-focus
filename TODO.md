# Technical Specification

## Native Android Application for Reducing Phone Addiction via Selected-App Grayscale Mode

**Working title:** MonoFocus
**Platform:** Native Android
**Language:** Kotlin
**UI:** Jetpack Compose / Material 3
**Target distribution:** Google Play
**Document version:** 1.0
**Date:** 2026-06-11

---

## 1. Product Summary

MonoFocus is a minimal native Android application that helps users reduce compulsive use of selected mobile apps by automatically making the screen grayscale while those apps are in the foreground.

The application has one primary function:

> The user selects installed, launchable apps. When one of those apps becomes the active foreground app, MonoFocus enables grayscale display mode. When the user leaves the selected app, grayscale mode is disabled.

The product must remain intentionally narrow. It must not include social features, accounts, streaks, gamification, analytics dashboards, blocking timers, cloud sync, ads, recommendations, AI, or web-based functionality.

The desired result is a small, reliable, elegant utility that the developer can personally use and publish on Google Play without policy shortcuts or technical hacks.

---

## 2. Core Feasibility Decision

### 2.1 Chosen implementation model

The app shall use the Android 15+ public API path:

1. Detect the current foreground app using `UsageStatsManager`.
2. Maintain a user-selected list of package names.
3. Create an app-owned `AutomaticZenRule`.
4. Attach `ZenDeviceEffects` with grayscale enabled.
5. Set the rule state to active when the foreground package is selected.
6. Set the rule state to inactive when the foreground package is not selected.

Android’s `ZenDeviceEffects.Builder.setShouldDisplayGrayscale(true)` sets display saturation to minimum, effectively making the display grayscale while the rule is active. ([Android Developers][1])

`AutomaticZenRule.setDeviceEffects(...)` is available from API 35, and an automatic rule can use `INTERRUPTION_FILTER_ALL`, meaning the rule can apply device effects without blocking notifications. ([Android Developers][2])

The app must request Notification Policy Access before creating or managing automatic zen rules. Android exposes `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` for directing users to grant this access. ([Android Developers][3])

### 2.2 Important product limitation

The grayscale effect is **display-level while active**, not a per-window compositor effect. In normal one-app-at-a-time phone usage, this matches the product goal: selected apps appear grayscale while they are used.

In split-screen, picture-in-picture, system overlays, or multi-window scenarios, the whole display may become grayscale while a selected app is considered active. This is acceptable for v1 and shall be documented in the app’s help/privacy text.

### 2.3 Unsupported technical approaches

The app must not use:

* Accessibility Service to monitor or manipulate other apps.
* Root, Shizuku, ADB-only permissions, or privileged permissions.
* Hidden / non-SDK Android APIs.
* Screen capture / MediaProjection.
* Overlay filters using `SYSTEM_ALERT_WINDOW`.
* WebView-based implementation.
* Any backend service.

This is intentional. Accessibility API use is heavily policy-sensitive on Google Play, and hidden or non-SDK APIs are explicitly risky because Android restricts their use and they may break across versions. ([Google Help][4])

The app must not depend on `WRITE_SECURE_SETTINGS`; Android documents it as not available for normal third-party apps. ([Android Developers][5])

---

## 3. Supported Android Versions

### 3.1 Recommended release configuration

**Minimum supported OS for full product:** Android 15 / API 35
**Recommended `minSdk`:** 35
**Recommended `targetSdk`:** latest required by Google Play at submission time, not lower than API 35.

Google Play’s target API requirements change over time; at the time of writing, the official Play Console guidance states that from August 31, 2025, new apps and updates must target Android 15 / API 35 or higher. ([Android Developers][6])

### 3.2 Compatibility policy

The cleanest v1 product should be published as **Android 15+ only**.

Reason: the core promise is automatic grayscale for selected apps. On Android versions below API 35, the public API required for `ZenDeviceEffects` is not available. Publishing to unsupported versions would either require a degraded product or policy-risky technical workarounds.

### 3.3 Optional non-core compatibility build

A separate compatibility build may support Android 10–14 only as a passive app-selection interface with an unsupported-state message. However, this is not recommended for the first Google Play release because it weakens the product promise.

---

## 4. Product Scope

### 4.1 In scope

The app shall provide:

1. A list of installed, user-launchable applications.
2. A simple way to select or deselect apps.
3. Automatic grayscale activation when a selected app is in the foreground.
4. Automatic grayscale deactivation when leaving selected apps.
5. Clear permission setup for:

   * Usage Access.
   * Notification Policy Access.
6. Local-only storage of selected packages and engine state.
7. A minimal privacy/about screen.
8. A clean, native, responsive Android UI.

### 4.2 Out of scope

The app shall not provide:

1. Usage statistics dashboard.
2. App blocking.
3. Timers.
4. Schedules.
5. Focus sessions.
6. Streaks.
7. Achievements.
8. Social sharing.
9. Accounts.
10. Cloud sync.
11. Web dashboard.
12. Ads.
13. Recommendations.
14. AI-generated advice.
15. Notifications about usage.
16. Remote configuration.
17. VPN, DNS, or network filtering.
18. Browser extension.
19. iOS version.
20. Desktop version.

---

## 5. User Experience Requirements

## 5.1 Design principle

The application shall feel like a system utility, not a habit-tracking product.

The main screen should answer only three questions:

1. Is the engine enabled?
2. Are required permissions granted?
3. Which apps are selected?

The UI must be calm, fast, and visually restrained.

---

## 5.2 First launch flow

On first launch, the app shall show a setup screen if any required permission is missing.

### Required setup steps

#### Step 1 — Usage Access

Purpose shown to user:

> MonoFocus needs Usage Access to know which app is currently open. It does not read app content, messages, text, images, or browsing activity.

Button:

> Open Usage Access Settings

Behavior:

* Opens Android Usage Access settings.
* User manually grants access.
* Returning to the app refreshes permission state.

The app uses `UsageStatsManager` to query app usage events. Android requires the `PACKAGE_USAGE_STATS` app-op permission for this type of access. ([Android Developers][7])

#### Step 2 — Notification Policy Access

Purpose shown to user:

> Android uses Modes / Do Not Disturb access to allow apps to apply grayscale display effects. MonoFocus will not silence notifications.

Button:

> Open Modes Permission Settings

Behavior:

* Opens Android Notification Policy Access settings.
* User manually grants access.
* Returning to the app refreshes permission state.

The app shall use an automatic zen rule with notification interruption filter set to allow all notifications while still applying grayscale device effects. ([Android Developers][2])

---

## 5.3 Main screen

The main screen shall contain:

1. App title.
2. Engine on/off switch.
3. Permission status indicator.
4. Search field.
5. Scrollable app list.
6. App row with:

   * App icon.
   * App name.
   * Optional package name in smaller text.
   * Toggle switch.

### Main screen copy

Title:

> MonoFocus

Subtitle:

> Make distracting apps grayscale while you use them.

Engine switch label:

> Grayscale engine

Search placeholder:

> Search apps

Empty state:

> No launchable apps found.

No selected apps state:

> Select apps that should appear in grayscale.

Permission missing state:

> Setup required before grayscale can work.

---

## 5.4 App list behavior

The list shall show installed, launchable apps, not all packages on the device.

This means:

* Apps visible in the launcher should be listed.
* Background services and internal packages should not be listed.
* The MonoFocus app itself should not be selectable.
* Duplicate launcher activities from the same package should be collapsed into one row.
* Apps should be sorted alphabetically by localized display name.
* Search should match app label and package name.
* Toggling an app should update storage immediately.

Android 11+ restricts broad package visibility because the installed-app inventory is treated as sensitive. Therefore, the app should use launchable-app discovery rather than requesting broad package visibility. Google Play also restricts `QUERY_ALL_PACKAGES` and requires developers to use narrower package visibility whenever possible. ([Android Developers][8])

---

## 5.5 Unsupported OS screen

If the app is ever installed on an unsupported OS version, it shall show a simple unsupported screen:

Title:

> Android 15 or newer required

Body:

> MonoFocus uses Android’s public grayscale device effect API. This feature is available only on Android 15 or newer.

No workaround buttons shall be provided in v1.

---

## 6. Functional Requirements

## FR-1: Permission State Detection

The app shall detect whether Usage Access is granted.

The app shall detect whether Notification Policy Access is granted.

The app shall refresh permission state:

* On launch.
* On resume.
* After returning from settings.
* Before starting the grayscale engine.

If any required permission is missing, the engine shall not start.

---

## FR-2: Installed App Discovery

The app shall load installed launchable applications.

Each discovered app shall include:

```text
packageName: String
label: String
icon: Drawable/ImageBitmap
isSelected: Boolean
```

The app shall exclude:

* Its own package.
* Non-launchable packages.
* Duplicate package entries.
* Packages that cannot be resolved to a user-facing label.

The app shall not request `QUERY_ALL_PACKAGES`.

The app shall not upload, transmit, or share the app list.

---

## FR-3: App Selection

The user shall be able to select and deselect apps from the main list.

Selected apps shall be stored locally.

The app shall persist selected package names across:

* App restart.
* Device reboot.
* App process death.
* App update.

If a selected app is uninstalled, the package may remain in storage but shall not be shown in the active app list. If the app is reinstalled later with the same package name, it may appear as selected again.

---

## FR-4: Grayscale Engine Toggle

The user shall be able to enable or disable the grayscale engine.

When disabled:

* The app shall not monitor foreground apps.
* The app shall deactivate its grayscale rule if active.
* No foreground service shall continue running.

When enabled:

* The app shall verify permissions.
* The app shall ensure the automatic zen rule exists.
* The app shall begin foreground-app detection.
* The app shall activate grayscale only for selected apps.

---

## FR-5: Foreground App Detection

The app shall determine the current foreground package using `UsageStatsManager`.

The implementation shall use usage events such as foreground/resumed and background/paused events where available.

The detector shall:

1. Query recent usage events.
2. Determine the most likely active package.
3. Ignore MonoFocus’s own package.
4. Debounce rapid app transitions.
5. Emit foreground package changes only when the package actually changes.

Recommended behavior:

```text
Polling interval while engine is enabled: 500–1000 ms
Transition debounce: 300–700 ms
Maximum grayscale activation delay: 1500 ms
Maximum grayscale deactivation delay: 1500 ms
```

The app shall handle cases where foreground detection is temporarily unavailable by failing safe:

* If the app cannot determine the foreground package, grayscale shall be disabled.
* If permission is revoked, the engine shall stop and show setup-required state.

---

## FR-6: Automatic Zen Rule Creation

The app shall create one app-owned automatic zen rule.

The rule shall be created only after Notification Policy Access is granted.

Rule properties:

```text
Name: MonoFocus Grayscale
Trigger description: When selected apps are open
Interruption filter: ALL
Device effect: grayscale enabled
Condition URI: monofocus://selected-app-active
```

The rule shall be stored by its returned rule ID.

The app shall reuse the existing rule if already created.

The app shall recreate the rule if:

* The stored rule ID no longer exists.
* The user deleted the rule from system settings.
* Android reports the rule as unavailable.
* Device effects are missing or incorrect.

The app must not create multiple duplicate rules.

---

## FR-7: Grayscale Activation

When the current foreground package is in the selected package set:

1. The app shall activate its automatic zen rule.
2. The screen shall become grayscale.
3. Notifications shall not be blocked or silenced by MonoFocus.

When the current foreground package is not selected:

1. The app shall deactivate its automatic zen rule.
2. The screen shall return to normal color.

The app shall only change the state of its own rule. Android’s API allows an app to set the state of a rule it owns. ([Android Developers][3])

---

## FR-8: Cleanup and Safety

The app shall deactivate grayscale when:

* The engine is turned off.
* Required permissions are revoked.
* The service is stopped.
* The app detects an internal fatal engine error.
* The device is shutting down, if observable.
* The selected package list becomes empty.
* The foreground app becomes unknown for longer than the configured timeout.

The app shall not leave the device permanently grayscale after the user disables the engine.

On boot completion, the app may run a cleanup-only receiver to deactivate its own grayscale rule. This receiver must not start foreground monitoring.

On next launch, the app shall check whether its rule is active and reconcile state.

---

## FR-9: Background Operation

To react while the user opens other apps, the app requires a small background monitoring component.

Preferred v1 implementation:

* Use a foreground service only while the engine is enabled.
* Show a persistent, low-priority notification while monitoring is active.
* Stop the service immediately when the engine is disabled.
* Do not run background work when no apps are selected.

Notification text:

> MonoFocus is watching selected apps

Notification action:

> Stop

If the user taps Stop:

* The engine shall be disabled.
* Grayscale shall be deactivated.
* The service shall stop.

The service shall not perform network operations.

---

## FR-10: Reboot Behavior

Recommended v1 behavior:

* Persist the user’s engine-enabled preference.
* After reboot, do not silently start foreground monitoring.
* A cleanup-only boot receiver may deactivate a stale app-owned grayscale rule after reboot.
* On app launch after reboot, restore the engine state if permissions are still granted.

Optional v1.1 behavior:

* Add explicit “Start after reboot” setting.
* Extend the boot-completed path beyond cleanup only if this user setting is enabled.
* Start monitoring only if:

  * The user enabled the setting.
  * At least one app is selected.
  * Required permissions are still granted.

For the strict minimal v1, automatic start after reboot may be omitted.

---

## FR-11: Permission Revocation Handling

If Usage Access is revoked while the engine is enabled:

* Stop foreground detection.
* Deactivate grayscale.
* Show setup-required state.

If Notification Policy Access is revoked while the engine is enabled:

* Stop the engine.
* Attempt no further zen rule state changes.
* Show setup-required state.

If the user changes or deletes MonoFocus’s automatic rule in system settings:

* Detect on next app resume or engine cycle.
* Recreate or repair the rule if permission is still granted.
* Never create duplicates.

---

## FR-12: Privacy Screen

The app shall include a minimal privacy/about screen.

Required text:

> MonoFocus works locally on your device.
> It stores only the package names of apps you select.
> It uses Usage Access only to detect which app is currently open.
> It does not read app content, messages, notifications, text, images, or browsing activity.
> It does not send data to any server.

The app shall not declare `INTERNET` permission in v1.

---

## 7. Non-Functional Requirements

## 7.1 Performance

The app shall be lightweight.

Targets:

```text
Cold start to main screen: under 1 second on a modern mid-range device
App list load for 300 launchable apps: under 2 seconds
Foreground transition reaction: under 1.5 seconds
Average active monitoring CPU usage: low enough to be unnoticeable in normal use
Network usage: zero
```

The app shall cache app labels and icons during a session.

The app shall avoid repeated expensive package-manager calls during active monitoring.

---

## 7.2 Battery

The app shall minimize polling cost.

Requirements:

* Monitoring shall run only when the engine is enabled.
* Monitoring shall stop when no apps are selected.
* Polling interval shall not be below 500 ms in production builds.
* The foreground service shall not perform unnecessary wake locks.
* The app shall not keep the screen awake.

---

## 7.3 Reliability

The app shall not crash when:

* Usage Access is missing.
* Notification Policy Access is missing.
* A selected app is uninstalled.
* The stored zen rule ID is invalid.
* The user changes system mode settings.
* The device is in multi-window mode.
* App icons fail to load.
* Package labels are missing.
* Usage events return no data.

Failure mode shall be conservative: disable grayscale rather than leave the device in grayscale unexpectedly.

---

## 7.4 Security

The app shall:

* Store selected package names locally.
* Avoid network permissions.
* Avoid third-party analytics SDKs.
* Avoid crash reporters in v1 unless explicitly reviewed.
* Avoid logging selected package names in release builds.
* Avoid writing sensitive state to external storage.

---

## 7.5 Accessibility

The app’s own UI shall support:

* Dynamic font sizes.
* TalkBack labels.
* Sufficient contrast.
* Large touch targets.
* Keyboard navigation where practical.

The app shall not declare or use `AccessibilityService`.

---

## 8. Data Model

Use Jetpack DataStore.

Recommended format: Proto DataStore.

Stored fields:

```text
selected_package_names: repeated string
engine_enabled: bool
zen_rule_id: string?
onboarding_completed: bool
last_known_supported_api: int
start_after_reboot_enabled: bool // optional, default false
```

No database is required.

Room is out of scope for v1.

---

## 9. Technical Architecture

## 9.1 Suggested package structure

```text
com.monofocus.app
  MainActivity
  MonoFocusApplication

com.monofocus.ui
  MainScreen
  SetupScreen
  UnsupportedScreen
  PrivacyScreen
  AppListItem
  PermissionStatusCard

com.monofocus.domain
  AppSelectionRepository
  FocusEngine
  ForegroundAppState
  EngineState

com.monofocus.data
  DataStoreAppSelectionRepository
  LaunchableAppsProvider
  UsageStatsForegroundAppDetector
  ZenGrayscaleController

com.monofocus.service
  FocusMonitorService

com.monofocus.platform
  PermissionChecker
  SettingsIntentFactory
  PackageChangeReceiver // optional
  BootReceiver // optional
```

## 9.2 Dependency policy

Use minimal dependencies.

Allowed:

* Kotlin coroutines.
* Jetpack Compose.
* AndroidX Lifecycle.
* AndroidX DataStore.
* AndroidX Core.
* Material 3.

Avoid in v1:

* Hilt, unless the developer strongly prefers it.
* Room.
* Retrofit.
* OkHttp.
* Firebase.
* Analytics SDKs.
* Remote config.
* Ads SDKs.

Manual constructor injection is sufficient.

---

## 10. Core Components

## 10.1 `LaunchableAppsProvider`

Responsibility:

* Load user-facing launchable apps.
* Return stable app entries.
* Exclude own package.
* Sort by label.
* Deduplicate by package name.

Interface:

```kotlin
interface LaunchableAppsProvider {
    suspend fun getLaunchableApps(): List<AppEntry>
}
```

Model:

```kotlin
data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isSelected: Boolean
)
```

---

## 10.2 `AppSelectionRepository`

Responsibility:

* Store selected package names.
* Expose selected package set as flow.
* Persist engine-enabled state.
* Persist zen rule ID.

Interface:

```kotlin
interface AppSelectionRepository {
    val selectedPackages: Flow<Set<String>>
    val engineEnabled: Flow<Boolean>

    suspend fun setPackageSelected(packageName: String, selected: Boolean)
    suspend fun setEngineEnabled(enabled: Boolean)

    suspend fun getZenRuleId(): String?
    suspend fun setZenRuleId(ruleId: String?)
}
```

---

## 10.3 `UsageStatsForegroundAppDetector`

Responsibility:

* Query usage events.
* Infer current foreground package.
* Emit changes.
* Fail safe when permission is missing.

Interface:

```kotlin
interface ForegroundAppDetector {
    fun observeForegroundPackage(): Flow<String?>
}
```

Behavior:

```text
If Usage Access is missing -> emit null and stop.
If no reliable foreground package is found -> emit null.
If package changes -> emit new package.
If package is unchanged -> emit nothing.
```

---

## 10.4 `ZenGrayscaleController`

Responsibility:

* Check Notification Policy Access.
* Create, repair, and manage the automatic zen rule.
* Activate or deactivate grayscale.
* Avoid duplicate rules.
* Reset grayscale on shutdown/stop.

Interface:

```kotlin
interface GrayscaleController {
    suspend fun ensureReady(): Boolean
    suspend fun setGrayscaleActive(active: Boolean)
    suspend fun deactivate()
}
```

Behavior:

```text
ensureReady():
  - Check API level >= 35
  - Check Notification Policy Access
  - Find or create MonoFocus rule
  - Ensure grayscale device effect is set
  - Ensure interruption filter allows all notifications
  - Store rule ID
```

---

## 10.5 `FocusEngine`

Responsibility:

* Combine selected package state with foreground package state.
* Decide when grayscale should be active.
* Avoid redundant API calls.
* Stop safely.

Pseudo-logic:

```text
When engine is enabled:
  observe selectedPackages
  observe foregroundPackage

  shouldBeGrayscale =
      foregroundPackage != null &&
      selectedPackages contains foregroundPackage

  if shouldBeGrayscale changed:
      grayscaleController.setGrayscaleActive(shouldBeGrayscale)

When engine is disabled:
  grayscaleController.deactivate()
```

---

## 11. Permissions and Manifest Requirements

## 11.1 Required permissions / accesses

### Usage Access

Manifest:

```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
```

Notes:

* This is a special access permission.
* User must manually grant it in system settings.
* The app must clearly explain why it is needed.

### Notification Policy Access

No normal runtime permission dialog exists.

Manifest:

```xml
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
```

The app shall direct the user to:

```text
Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
```

This access is needed to create and manage the app-owned automatic zen rule. ([Android Developers][3])

### Foreground service

If the monitoring engine uses a foreground service, declare the required foreground-service permission and service entry according to the target SDK requirements at implementation time.

The foreground service shall run only while the engine is enabled.

---

## 11.2 Permissions that must not be declared

The app shall not declare:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
```

The app shall not include an Accessibility Service declaration.

---

## 12. UI Specification

## 12.1 Main screen layout

Structure:

```text
Top app bar:
  MonoFocus
  Optional overflow menu with Privacy/About

Status section:
  Engine switch
  Permission state

Search:
  Search apps

App list:
  Icon | App name | Package name | Switch
```

No bottom navigation.

No tabs.

No charts.

No onboarding carousel.

---

## 12.2 Visual style

Use Material 3.

Recommended style:

* Neutral background.
* Single accent color.
* No illustrations required.
* No gradients.
* No animations except standard switch/list interactions.
* No gamified language.
* No “dopamine detox” marketing tone.

---

## 12.3 App row states

Normal row:

```text
[icon] Instagram
       com.instagram.android                         [switch]
```

Selected row:

* Switch on.
* Optional subtle selected background.
* No warning badges.

Disabled row:

* Only used if app cannot be selected.
* Prefer not showing disabled apps in v1.

---

## 12.4 Permission card states

All permissions granted:

```text
Ready
Selected apps will become grayscale while open.
```

Usage Access missing:

```text
Usage Access required
MonoFocus needs this to detect which app is currently open.
[Open Settings]
```

Notification Policy Access missing:

```text
Modes permission required
Android requires this to apply grayscale display effects.
[Open Settings]
```

Unsupported OS:

```text
Android 15 or newer required
This Android version does not provide the public grayscale effect API MonoFocus needs.
```

---

## 13. Edge Cases

## 13.1 Multi-window / split-screen

If a selected app is active in split-screen, the whole display may become grayscale.

Expected behavior:

* Accept this limitation.
* Do not attempt per-window filtering.
* Do not use overlays.

## 13.2 Picture-in-picture

If a selected app enters PiP, behavior may depend on Android’s foreground-app reporting.

Expected behavior:

* Best effort only.
* Grayscale should deactivate if the selected app is no longer the active foreground package.

## 13.3 Launcher and recents screen

When the user goes home or opens recents:

* Grayscale shall deactivate unless the launcher itself is selected.
* The app shall ignore transient package changes shorter than debounce threshold.

## 13.4 Calls, alarms, system UI

System UI may temporarily become foreground.

Expected behavior:

* Grayscale deactivates unless a selected app is still reported active.
* MonoFocus shall not interfere with calls, alarms, or notifications.

## 13.5 User manually enables grayscale elsewhere

If the user enables system grayscale independently, MonoFocus must not try to override it.

MonoFocus only manages its own automatic zen rule.

## 13.6 User manually changes Do Not Disturb / Modes

MonoFocus must not assume it owns all mode state.

It shall only create and control its own rule.

---

## 14. Acceptance Criteria

## AC-1: First launch setup

Given a fresh install on Android 15+
When the user opens the app
Then the app shows setup steps for missing required accesses
And the engine cannot be enabled until both accesses are granted.

---

## AC-2: App list

Given required permissions may or may not be granted
When the user opens the main screen
Then launchable installed apps are shown
And the app’s own package is not shown
And package visibility does not require `QUERY_ALL_PACKAGES`.

---

## AC-3: Selecting apps

Given the app list is visible
When the user toggles an app on
Then its package name is stored locally
And the selection survives app restart.

---

## AC-4: Grayscale activation

Given Android 15+
And Usage Access is granted
And Notification Policy Access is granted
And the engine is enabled
And package `com.example.social` is selected
When the user opens that app
Then the display becomes grayscale within 1.5 seconds.

---

## AC-5: Grayscale deactivation

Given grayscale is active because a selected app is foreground
When the user returns to home or opens an unselected app
Then grayscale is disabled within 1.5 seconds.

---

## AC-6: Notifications are not blocked

Given grayscale mode is active
When the device receives a normal notification
Then MonoFocus must not suppress it through its automatic rule.

---

## AC-7: Permission revoked

Given the engine is enabled
When the user revokes Usage Access
Then the engine stops
And grayscale is deactivated
And the app shows setup-required state.

---

## AC-8: Rule deleted

Given the user deletes MonoFocus’s automatic rule in system settings
When the app resumes or the engine starts
Then the app recreates one rule if permission is still granted
And does not create duplicates.

---

## AC-9: No network

Given the release APK/AAB is inspected
Then it does not declare `INTERNET` permission
And it contains no analytics, ads, or network SDKs.

---

## AC-10: Unsupported OS

Given the app runs on Android below API 35
When opened
Then it does not claim full functionality
And it shows a clear unsupported message.

---

## 15. Testing Plan

## 15.1 Unit tests

Cover:

* Selection repository.
* Package filtering and deduplication.
* Foreground package decision logic.
* Grayscale activation decision:

  * selected foreground app.
  * unselected foreground app.
  * null foreground app.
  * empty selection.
* Permission-state mapping.
* Zen rule state reconciliation logic.

---

## 15.2 Integration tests

Cover:

* DataStore persistence.
* App list loading with fake package data.
* Engine state flow.
* Rule creation/update behavior with mocked `NotificationManager`.
* Permission revocation behavior.

---

## 15.3 Manual device test matrix

Minimum manual testing:

```text
Pixel device or emulator, Android 15 / API 35
Pixel device or emulator, Android 16 if available
Samsung device, Android 15+
Android 14 device/emulator for unsupported-state verification
```

Manual scenarios:

1. Fresh install.
2. Grant both permissions.
3. Select one social/media app.
4. Enable engine.
5. Open selected app.
6. Confirm grayscale.
7. Open unselected app.
8. Confirm color restored.
9. Lock/unlock device.
10. Revoke Usage Access.
11. Revoke Notification Policy Access.
12. Delete MonoFocus rule from system settings.
13. Uninstall a selected app.
14. Reinstall selected app.
15. Disable engine from foreground notification.
16. Force-stop MonoFocus.
17. Reboot device, if reboot behavior is implemented.

---

## 16. Google Play Readiness Requirements

The app must be submitted as a narrow utility with a clear core feature.

Store listing should state:

> MonoFocus makes selected apps grayscale while you use them, helping reduce compulsive scrolling without blocking apps or collecting data.

The listing must also state:

> Requires Android 15 or newer.

The Play submission must avoid restricted or unnecessary permissions.

Do not request `QUERY_ALL_PACKAGES`; Google Play treats broad installed-app visibility as sensitive and allows it only when broad visibility is essential to the app’s core purpose and no narrower alternative is suitable. ([Google Help][9])

The app must be functional, stable, and not misleading. Google Play’s minimum functionality policy rejects apps that are broken, crash, freeze, or fail to provide a stable user experience. ([Google Help][10])

---

## 17. Privacy Requirements

The app shall have a privacy policy, even if it collects no server-side data.

Privacy policy content shall state:

1. The app stores selected package names locally.
2. The app uses Usage Access to detect the current foreground app.
3. The app does not read the contents of other apps.
4. The app does not read notifications.
5. The app does not collect messages, text, images, browser history, or screen content.
6. The app does not transmit data to any server.
7. The app does not use analytics or advertising SDKs.
8. The app does not sell or share user data.

Google Play Data Safety form should be completed consistently with the actual implementation. If no data leaves the device, this must be reflected accurately.

---

## 18. Build and Release Requirements

Recommended build setup:

```text
Language: Kotlin
UI: Jetpack Compose
Architecture: single-activity app
Minimum SDK: 35
Target SDK: latest Play-required SDK, not lower than 35
Release format: Android App Bundle
Backend: none
Internet permission: none
```

Release build requirements:

* Enable R8/minification.
* Remove debug logs.
* Do not log foreground package names in release.
* Use a deterministic versioning scheme.
* Provide adaptive icon.
* Provide monochrome icon.
* Provide privacy policy URL.
* Provide concise Play screenshots showing:

  * Setup screen.
  * App list.
  * Selected apps.
  * Engine enabled state.

---

## 19. Implementation Milestones

## Milestone 1 — Technical spike

Goal: prove the grayscale mechanism.

Deliverables:

* Minimal Android 15 test app.
* Request Notification Policy Access.
* Create one automatic zen rule.
* Attach grayscale device effect.
* Toggle rule active/inactive manually.
* Confirm display becomes grayscale and returns to color.
* Confirm notifications are not blocked.

Exit criteria:

* Grayscale works on at least one Pixel Android 15+ device/emulator.
* Rule does not silence notifications.
* Rule can be cleaned up safely.

---

## Milestone 2 — Foreground detection spike

Goal: prove selected-app triggering.

Deliverables:

* Request Usage Access.
* Detect foreground package.
* Log foreground package in debug builds only.
* Match package against hardcoded selected package list.
* Toggle grayscale based on foreground package.

Exit criteria:

* Opening a selected app activates grayscale.
* Leaving selected app deactivates grayscale.
* Delay is under 1.5 seconds in normal use.

---

## Milestone 3 — Production UI

Goal: build the real app shell.

Deliverables:

* Setup screen.
* Main app list.
* Search.
* Toggles.
* Engine switch.
* Privacy/about screen.
* Unsupported OS screen.

Exit criteria:

* User can complete setup without developer assistance.
* App list is fast and stable.
* Selection persists across restart.

---

## Milestone 4 — Hardening

Goal: make it publishable.

Deliverables:

* Rule repair logic.
* Permission revocation handling.
* Service stop handling.
* No duplicate rules.
* No stuck grayscale state.
* Manual test matrix completed.

Exit criteria:

* All acceptance criteria pass.
* App does not declare unnecessary sensitive permissions.
* Release build has no debug package-name logging.

---

## Milestone 5 — Google Play preparation

Deliverables:

* App icon.
* Screenshots.
* Short description.
* Full description.
* Privacy policy.
* Data Safety form.
* Internal testing release.
* Closed testing release if required by the developer account status.

Exit criteria:

* Internal testers can install and use the app.
* No known crash on supported devices.
* Store listing accurately describes Android 15+ requirement.

---

## 20. Suggested Play Store Copy

### Short description

> Make distracting apps grayscale while you use them.

### Full description

> MonoFocus is a minimal Android utility that helps reduce compulsive app use by making selected apps appear in grayscale while they are open.
>
> Select the apps you want to make less stimulating. When one of them is active, MonoFocus applies Android’s grayscale display effect. When you leave the app, color returns automatically.
>
> MonoFocus does not block apps, show streaks, collect analytics, use ads, or send your data anywhere.
>
> Requirements: Android 15 or newer. Usage Access and Modes / Do Not Disturb access are required for the core feature.

---

## 21. Final Product Definition

MonoFocus v1 is successful if it does exactly this and nothing more:

1. Shows launchable installed apps.
2. Lets the user select distracting apps.
3. Makes the display grayscale while a selected app is active.
4. Restores color when the user leaves.
5. Stores everything locally.
6. Uses public Android APIs.
7. Avoids unnecessary permissions.
8. Feels like a clean native system utility.

Anything beyond that should be rejected for v1 unless it directly improves reliability of the core grayscale behavior.

[1]: https://developer.android.com/reference/android/service/notification/ZenDeviceEffects.Builder "ZenDeviceEffects.Builder  |  API reference  |  Android Developers"
[2]: https://developer.android.com/reference/android/app/AutomaticZenRule "AutomaticZenRule  |  API reference  |  Android Developers"
[3]: https://developer.android.com/reference/android/app/NotificationManager "NotificationManager  |  API reference  |  Android Developers"
[4]: https://support.google.com/googleplay/android-developer/answer/10964491?hl=en "Use of the AccessibilityService API - Play Console Help"
[5]: https://developer.android.com/reference/android/Manifest.permission "Manifest.permission  |  API reference  |  Android Developers"
[6]: https://developer.android.com/google/play/requirements/target-sdk?utm_source=chatgpt.com "Meet Google Play's target API level requirement"
[7]: https://developer.android.com/reference/android/app/usage/UsageStatsManager "UsageStatsManager  |  API reference  |  Android Developers"
[8]: https://developer.android.com/training/package-visibility "Package visibility filtering on Android  |  App architecture  |  Android Developers"
[9]: https://support.google.com/googleplay/android-developer/answer/10158779?hl=en&utm_source=chatgpt.com "Use of the broad package (App) visibility ... - Google Help"
[10]: https://support.google.com/googleplay/android-developer/answer/9898783?hl=en "Functionality, Content, and User Experience - Play Console Help"
