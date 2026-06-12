# Acceptance Checklist

This checklist maps `TODO.md` acceptance criteria to current implementation evidence and the remaining proof needed before release.

## AC-1: First Launch Setup

Implementation evidence:

- `MainActivity` refreshes permission state on resume.
- `PermissionChecker` checks Usage Access, Notifications permission, and Notification Policy Access.
- `MonoFocusApp` shows setup cards and settings buttons when access is missing.
- `AndroidManifest.xml` declares `android.permission.ACCESS_NOTIFICATION_POLICY`, so Android Settings exposes MonoFocus in Do Not Disturb access.
- `tools/verify-release.ps1` checks the required setup and main-screen copy in the release source.
- `tools/verify-release.ps1` checks the expected Android permission surface, including `ACCESS_NOTIFICATION_POLICY`.
- `MonoFocusViewModel.setEngineEnabled` refuses to start the engine without every required permission.
- `chooseEngineToggleAction` only allows service start when the user requested enable, permissions are ready, and at least one app is selected.
- `MonoFocusViewModel.disableAndStopEngine` persists the disabled state and attempts best-effort grayscale deactivation before stopping the monitoring service.

Remaining proof:

- Complete release/physical-device validation for Usage Access, Notifications permission, and Modes permission setup paths returning to an updated setup state.
- Supporting debug-emulator evidence on 2026-06-12 shows Usage Access and Modes revocation paths return to setup-required UI with the engine disabled.
- Current build evidence: `lint`, `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, and `bundleRelease` pass.

## AC-2: App List

Implementation evidence:

- `AndroidLaunchableAppsProvider` queries `ACTION_MAIN` + `CATEGORY_LAUNCHER`.
- `LaunchableAppFilters` removes MonoFocus, blank labels, blank package names, and duplicate packages.
- `AndroidLaunchableAppsProvider` caches labels and icons for packages already seen in the current process while still re-querying launcher packages so removed apps disappear from the visible list.
- Launchable-app loading fails closed: package-query failures show an empty list, and individual label/icon failures skip the broken entry rather than crashing the app.
- `buildPresentedApps` marks selected apps, filters search by label or package name, trims search text, and sorts visible apps by display label.
- `selectedLaunchablePackageCount` ignores stored selected package names that are not currently launchable, so uninstalled selections do not keep the visible selected count or engine eligibility alive.
- `FocusMonitorService` preflights persisted engine state, permission readiness, and selected package names against the current launchable app list before foreground promotion, so stale stored selections cannot keep foreground monitoring alive.
- `AndroidManifest.xml` uses launcher-query package visibility and does not declare `QUERY_ALL_PACKAGES`.
- `tools/verify-release.ps1` checks that release package visibility is limited to the launcher intent query and does not include package/provider/service broad-visibility entries.
- `tools/verify-release.ps1` checks that MonoFocus owns only the expected manifest components: `MainActivity`, `FocusMonitorService`, and `PackageChangeReceiver`.
- `tools/verify-release.ps1` checks release APK output metadata for the expected package, release variant, version, and Android 15+ dexing floor.
- `tools/verify-release.ps1` checks the release AAB has one base module manifest plus the expected base dex, resources, app metadata, icon resources, and data extraction rules.

Remaining proof:

- Run on a device with many installed apps and verify launcher apps appear correctly.
- Current build evidence: package filtering, launchable-app cache, active selected-app counting, and app-list presentation unit tests pass.

## AC-3: Selecting Apps

Implementation evidence:

- App row switches call `AppSelectionRepository.setPackageSelected`.
- `DataStoreAppSelectionRepository` stores selected package names in private DataStore storage.
- `DataStoreAppSelectionRepository` trims package names and ignores blank package identifiers at the persistence boundary.
- `DataStoreAppSelectionRepository` defensively normalizes stored package names and blank zen rule IDs on read, so malformed or legacy preferences do not leak into engine decisions.
- `DataStoreAppSelectionRepository` keeps the optional start-after-reboot setting defaulted off; the only reboot receiver is cleanup-only and does not start monitoring.
- Stored selected package names can remain after uninstall, but engine enable/resume policy only treats currently launchable selected apps as active selections; reinstalling the same package can make it selected again.
- Package-removal handling uses a shared selected-package removal handler from the manifest `PackageChangeReceiver` and from the `MonoFocusApplication` runtime safety receiver, so active monitoring can stop promptly while the app process/service is alive and stale service starts still fail closed later.
- Package-removal handling disables/stops the engine if no selected launchable apps remain, ignores package replacement during app updates, and short-circuits unrelated package removals before launchable-app loading.
- `PackageChangeReceiver` is exported only for system package-removal delivery and requires sender permission `android.permission.BROADCAST_PACKAGE_REMOVED`, so ordinary third-party apps cannot spoof selected-package removal.

Remaining proof:

- Complete release/physical-device validation for selection persistence and selected-app uninstall/reinstall.
- Supporting debug-emulator evidence on 2026-06-12 shows Calendar selection persisted across app force-stop/relaunch and debug reinstall.
- Supporting debug-emulator evidence on 2026-06-12 shows uninstalling the only selected launchable app, Drive, stopped `FocusMonitorService`, returned the MonoFocus rule to `STATE_FALSE`, removed Drive from the fresh app list, and left the engine off; reinstalling the same package made Drive reappear selected while the engine stayed off.
- Current build evidence: DataStore-backed repository persistence, package-name normalization, malformed stored-value normalization, default-off reboot autostart state, package-removal decision, and package-removal candidate unit tests pass.

## AC-4 and AC-5: Grayscale Activation and Deactivation

Implementation evidence:

- `UsageStatsForegroundAppDetector` emits foreground package changes using UsageStats events.
- `UsageStatsForegroundAppDetector` uses named production timing defaults: 500 ms polling, 300 ms debounce, and 1500 ms unreliable-detection timeout.
- Foreground inference captures usage-event timestamps and applies events in timestamp order, so the latest foreground/resume event wins even if snapshots are processed out of order.
- Foreground inference captures usage-event class names and carries active activity state across polling windows, so an in-package activity stop cannot clear a package while another activity in that package remains resumed.
- `ForegroundDetectionStabilizer` debounces normal foreground transitions and emits unknown immediately when unreliable detection reaches the configured timeout.
- `FocusEngine` refuses to run when the persisted engine setting is off and stops if it becomes off while monitoring.
- `FocusEngine` activates grayscale only when the foreground package is selected.
- `chooseEngineResumeAction` restarts monitoring on app resume only when the persisted engine flag is enabled, permissions are ready, and selected apps exist.
- `MonoFocusViewModel` runs the same reconciliation after the initial app-list load, so app launch after reboot/process death restores or disables the persisted engine state based on current permissions and launchable selections.
- The strict minimal v1 does not start monitoring on boot. `BootCleanupReceiver` handles only `BOOT_COMPLETED` to deactivate a stale app-owned grayscale rule, and `tools/verify-release.ps1` enforces this cleanup-only receiver shape.
- `FocusMonitorService` uses the same preflight invariant before foreground promotion after a service start or sticky restart.
- `ZenGrayscaleController` uses `AutomaticZenRule`, `ZenDeviceEffects.setShouldDisplayGrayscale(true)`, and `setAutomaticZenRuleState`.

Remaining proof:

- Run on Android 15+ hardware/emulator and measure visual activation/deactivation within 1.5 seconds.
- Supporting debug-emulator evidence on 2026-06-12 shows the current MonoFocus rule moves to `STATE_TRUE` with `deviceEffects=[grayscale]` while Calendar is top-resumed/currently active, and back to `STATE_FALSE` when Home becomes active.
- Supporting debug-emulator evidence on 2026-06-12 shows five Calendar-to-Clock cycles where Calendar was selected, Clock was unselected, Calendar became top-resumed with the MonoFocus rule `STATE_TRUE`, and Clock became top-resumed with the MonoFocus rule `STATE_FALSE`.
- Supporting debug-emulator timing evidence on 2026-06-12 shows current-rule-state activation from unselected Clock to selected Calendar reached `STATE_TRUE` in 1148 ms, 1153 ms, 1154 ms, 1236 ms, and 1250 ms, and deactivation from selected Calendar to unselected Clock reached `STATE_FALSE` in 951 ms, 1111 ms, 1029 ms, 984 ms, and 1168 ms; visual grayscale/color timing remains pending.
- Supporting debug-emulator display-service timing evidence on 2026-06-12 shows `ColorDisplayService` logged saturation level `0` after selected Calendar activation in 682 ms, 1016 ms, 826 ms, 1180 ms, and 1378 ms, and saturation level `100` after unselected Clock deactivation in 1186 ms, 777 ms, 1026 ms, 1167 ms, and 1126 ms.
- Visual grayscale/timing proof should still use direct observation, external camera/video, or another method that captures final display output; Android emulator `adb screencap` is not sufficient by itself because it produced color screenshots while the MonoFocus rule was `STATE_TRUE`, and `ColorDisplayService` logs prove only the system display-service saturation command.
- Current build evidence: foreground inference, production timing defaults, debounce/fail-safe timing, grayscale decision, engine enabled-state, engine service decision, and engine flow unit tests pass.

## AC-6: Notifications Are Not Blocked

Implementation evidence:

- `ZenGrayscaleController` configures the automatic rule with `NotificationManager.INTERRUPTION_FILTER_ALL`.

Remaining proof:

- Receive a normal notification while grayscale is active and confirm it is not suppressed by MonoFocus.
- Supporting debug-emulator evidence on 2026-06-12 shows a shell-posted normal notification remained listed while the MonoFocus rule was `STATE_TRUE` and `ZEN_MODE_OFF`; release/internal-test validation remains pending.
- Current APK evidence: release rule code sets `NotificationManager.INTERRUPTION_FILTER_ALL`.

## AC-7: Permission Revoked

Implementation evidence:

- `FocusEngine` checks permissions before and during monitoring.
- `FocusEngine` polls required permission state while monitoring, so revocation can be detected even if the foreground app does not change.
- `FocusEngine` disables the engine and deactivates grayscale if foreground detection or grayscale activation fails internally.
- UI-side disable/stop paths also attempt best-effort grayscale deactivation before stopping the monitoring service.
- `UsageStatsForegroundAppDetector` emits `null` and stops when Usage Access is revoked.
- `ZenGrayscaleController.setGrayscaleActive` throws when rule preparation or `setAutomaticZenRuleState` fails, so the engine can stop instead of silently assuming grayscale state changed.
- `ZenGrayscaleController.deactivate` attempts to deactivate the stored rule ID first, falls back to rule discovery only while policy access is available, and treats cleanup as best effort.
- `ZenGrayscaleController.deactivate` no longer rewrites the already-stored rule ID on normal cleanup; it only persists a fallback-discovered rule ID when the stored ID was missing or stale.
- `FocusEngine` treats deactivation during stop/final cleanup as best effort, so cleanup failures cannot prevent persisted engine disable or service stop callbacks.
- `FocusEngine` treats expected monitoring stops as normal completion rather than coroutine cancellation, while preserving external cancellation propagation.
- Foreground service stop, failed-preflight cleanup, service destruction, app shutdown/revocation safety handling, boot cleanup, selected-package removal cleanup, view-model reconciliation, and engine final cleanup all route through the shared bounded best-effort grayscale deactivation helper, so cleanup cannot wait indefinitely on unnecessary deactivation work.
- `FocusMonitorService` notification actions pause grayscale for 15 minutes or until tomorrow without disabling the engine, and pause expiry lets monitoring resume automatically.
- `FocusMonitorService` keeps the foreground notification visible while paused, and offers a Resume action during an active pause.
- `PermissionState.ready` requires notification runtime permission so the foreground notification and pause actions can be shown.
- `FocusMonitorService` checks persisted engine state, current permission readiness, and selected launchable-app availability before foreground promotion, so stale service starts stop instead of entering foreground monitoring when required access or selected apps are missing.
- `MonoFocusApplication` registers a runtime safety receiver for `ACTION_SHUTDOWN` and `ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED`; on observed policy-access revocation it attempts best-effort deactivation, persists the engine off, and stops `FocusMonitorService`.
- `BootCleanupReceiver` deactivates the app-owned grayscale rule after normal boot completion without starting `FocusMonitorService` or changing the persisted engine preference.
- `docs/play-store.md` includes foreground service declaration notes aligned with the manifest special-use subtype, persistent notification, pause actions, local-only behavior, and no-network implementation.
- `tools/verify-release.ps1` checks the foreground-service special-use subtype, monitoring notification text, and pause action strings.

Remaining proof:

- Complete release/physical-device validation for revoking each access while monitoring and verify the service stops, grayscale deactivates when possible, and setup state is shown.
- Complete release/physical-device restart or power-off validation with grayscale active and verify the device is not left grayscale.
- Supporting debug-emulator evidence on 2026-06-12 shows Usage Access revocation stops `FocusMonitorService`, returns the current MonoFocus rule to `STATE_FALSE`, and shows Usage Access setup-required UI.
- Supporting debug-emulator evidence on 2026-06-12 shows `POST_NOTIFICATIONS` revocation stops `FocusMonitorService`, returns the current MonoFocus rule to `STATE_FALSE`, and shows Notifications setup-required UI.
- Supporting debug-emulator evidence on 2026-06-12 shows the real Android Settings Do Not Disturb access revocation path moves the current MonoFocus rule to `STATE_FALSE` before revoke, removes the MonoFocus automatic rule on confirmation, leaves `FocusMonitorService` absent, and shows Modes setup-required UI.
- Supporting debug-emulator evidence on 2026-06-12 shows reboot while the MonoFocus rule is `STATE_TRUE` completes with `FocusMonitorService` absent and the app-owned rule `STATE_FALSE`; launching MonoFocus after reboot restores monitoring only then, with permissions and selection intact.
- Supporting debug-emulator evidence on 2026-06-12 after the shared bounded cleanup helper refactor shows switching the engine off after a selected-app activation leaves the engine unchecked, `FocusMonitorService` absent, no destroying service record, no foreground notification, and the current MonoFocus rule `STATE_FALSE`.
- The adb-only `cmd notification disallow_dnd` path can bypass the normal Settings foreground transition and should not be treated as the user revocation acceptance path.
- Current build evidence: permission-state, engine permission-revocation, expected-stop completion, internal-error cleanup, cleanup-failure, bounded cleanup timeout, pause-window, and paused-engine behavior unit tests pass; app/service/application compile.

## AC-8: Rule Deleted

Implementation evidence:

- `ZenGrayscaleController.ensureReady` recreates a missing stored rule, repairs incorrect rule properties, and removes duplicate MonoFocus-owned rules.
- `MonoFocusViewModel` calls `ensureReady` on resume when the engine is enabled.
- Engine resume decision tests cover restoring monitoring only when persisted engine state is valid, and disabling/stopping it when permissions or selected apps are missing.
- `tools/verify-release.ps1` checks the release manifest for the Automatic Zen Rule configuration intent filter, rule type metadata, and one-rule instance limit metadata.

Remaining proof:

- Delete the rule from system settings and verify one replacement rule is created on app resume or engine restart.
- Current build evidence: zen-rule reconciliation unit tests cover stored-rule reuse, repair, missing stored-rule recovery, duplicate cleanup, create-new-rule decisions, and deactivation persistence planning.

## AC-9: No Network

Implementation evidence:

- `AndroidManifest.xml` does not declare `INTERNET`.
- `AndroidManifest.xml` declares the expected Android permission surface for v1: Usage Access, Notification Policy Access, Notifications, foreground-service permissions, and cleanup-only boot completion.
- Dependencies are limited to Kotlin, AndroidX, Compose, Lifecycle, DataStore, Core, and Material 3.
- No analytics, ads, Retrofit, OkHttp, Firebase, or remote config dependencies are present.
- `tools/verify-release.ps1` enforces the explicit v1 unsupported-approach policy for WebView, Accessibility Service, screen capture / MediaProjection, overlays, root/Shizuku/ADB-style execution, hidden-API reflection markers, VPN/device-admin, camera/microphone, wake locks, keep-screen-awake APIs, network APIs, schedules/jobs/alarms, accounts/billing, and external-storage permissions/APIs.
- `docs/data-safety.md` provides a Play Console Data Safety draft consistent with the current no-network, local-only implementation.
- `targetSdk = 35` matches the current official Google Play target API requirement for new app submissions as verified on 2026-06-12: https://developer.android.com/google/play/requirements/target-sdk

Remaining proof:

- Verified: `assembleRelease` succeeds.
- Verified: `bundleRelease` succeeds.
- Verified: `lint` succeeds with 0 errors.
- Verified: `aapt dump permissions app-release-unsigned.apk` shows no `INTERNET`, `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, or `WRITE_SECURE_SETTINGS`.
- Verified: `tools/verify-release.ps1` inspects the release AAB base manifest for expected MonoFocus package/component/permission markers, including `ACCESS_NOTIFICATION_POLICY` and cleanup-only `RECEIVE_BOOT_COMPLETED`, and rejects forbidden sensitive permissions in the Play-upload artifact.
- Verified: `tools/verify-release.ps1` allows only the expected app-private AndroidX dynamic receiver custom permission outside Android's permission namespace.
- Verified: release dependency metadata does not include analytics, ads, Retrofit, OkHttp, Firebase, Room, or Hilt.
- Verified: `tools/verify-release.ps1` passes and automates the local release gate, Gradle release configuration checks, APK identity and permission checks, unsupported-approach scans, manifest privacy checks, source policy scans, and release dependency checks.

## AC-10: Unsupported OS

Implementation evidence:

- `minSdk` is 35, so normal installs below Android 15 are rejected.
- `UnsupportedScreen` exists if a nonstandard install path ever runs the app below API 35.

Remaining proof:

- Confirm Android 14 install rejection or verify the unsupported screen through a compatibility build/test harness.
- Supporting release artifact evidence on 2026-06-12 shows `aapt dump badging app-release-unsigned.apk` reports `sdkVersion:'35'`, and release APK output metadata reports `minSdkVersionForDexing` 35.
- Current build evidence: release output metadata shows `minSdkVersionForDexing` 35.

## Non-Functional and Play Readiness

Implementation evidence:

- The app uses a single-activity native Kotlin/Compose Material 3 UI with no WebView or backend.
- Engine and app-selection switches expose explicit TalkBack-facing labels and state descriptions.
- Top app bar icon buttons attach their accessible labels to the clickable `IconButton` node for Privacy/About and Back.
- The app uses a fixed light/dark Material color palette rather than wallpaper-derived dynamic colors, so contrast is deterministic across supported devices.
- JVM theme contrast tests cover the fixed light and dark text pairs at 4.5:1 or higher, plus primary icon/background pairs at 3:1 or higher.
- Launcher resources include adaptive icon foreground/background and monochrome icon layers.
- Release builds enable R8/minification and use deterministic `versionCode` / `versionName` values.
- Backup and device-transfer extraction rules exclude app-private state from platform backup/export.
- `PRIVACY.md`, `docs/data-safety.md`, and `docs/play-store.md` provide privacy policy, Data Safety, and listing copy drafts consistent with the local-only implementation.
- `tools/verify-release.ps1` checks the required in-app privacy/about text, unsupported-OS text, display-level grayscale limitation text, and standalone privacy policy content.
- `tools/verify-release.ps1` fails if app UI/source resources introduce v1 out-of-scope navigation surfaces or product copy for dashboards, timers, schedules, sessions, streaks, achievements, accounts, cloud sync, recommendations, social sharing, or gamification.
- `tools/verify-release.ps1` checks the required Play Store listing copy, Data Safety draft answers, foreground-service declaration notes, target API evidence, and screenshot checklist.
- `tools/verify-release.ps1` now validates the four local Play screenshot draft PNGs under `docs/play-screenshots/` for required names, PNG headers, portrait phone dimensions, and manual-test evidence references.
- `docs/manual-test-results.md` records local draft evidence for Play listing Android 15+ wording, local-only/no-ads/no-analytics wording, Data Safety answers, foreground-service declaration notes, and local screenshot drafts while leaving hosted/uploaded Play Console proof pending.
- `docs/manual-test-results.md` records local test-signed release APK cold-start evidence on the Android 15 emulator: five force-stop `am start -W TotalTime` launches measured 854 ms, 752 ms, 782 ms, 830 ms, and 846 ms.
- `docs/manual-test-results.md` provides the release-blocking device matrix, timing measurements, accessibility pass, and Play Console evidence template.
- `tools/verify-release.ps1` checks that `docs/manual-test-results.md` still contains the release-blocking device matrix, timing measurements, accessibility rows, and Play Console evidence rows.
- `tools/verify-release.ps1` provides a repeatable local release-readiness check before Play upload, including Gradle SDK/version/minification settings, release package/version identity, Android and custom permission surface, launcher-only package visibility, app-owned manifest component surface/exported flags, required UI/privacy copy, Play Store/Data Safety drafts, foreground-service strings, backup/export, and cleartext/debuggable manifest checks.
- The verifier allows only `BootCleanupReceiver` to handle `BOOT_COMPLETED`; it rejects locked-boot handling and any other boot-completed source usage so the app cannot silently start monitoring after reboot.
- The verifier also checks the release manifest for `FOREGROUND_SERVICE_SPECIAL_USE` and the required `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` declaration.
- The verifier checks that the release manifest uses adaptive launcher icons and that both standard and round adaptive icon resources include monochrome layers.
- The verifier checks the Automatic Zen Rule settings integration metadata used by Android system settings.

Remaining proof:

- Run a TalkBack/manual accessibility pass for switch labels, touch targets, dynamic font sizes, and contrast.
- Supporting debug-emulator evidence on 2026-06-12 shows UIAutomator found 0 enabled unlabeled clickable nodes, labeled clickable nodes for Privacy/About, Back, Grayscale engine, Calendar selection, and Clock selection, and 0 non-clipped enabled clickable nodes below 48 dp at 420 dpi.
- Supporting debug-emulator evidence on 2026-06-12 shows the main controls, a scrolled app-row switch, Privacy screen, and Back action remained reachable with system `font_scale=2.0`.
- Supporting local release-emulator evidence on 2026-06-12 shows force-stop cold launches under 1 second by `am start -W TotalTime`; precise app-list load timing with a large installed-app inventory still needs physical/release validation because UIAutomator dump overhead was 2006-2032 ms on the ready screen.
- Local debug-emulator screenshot drafts for setup, app list, selected apps, and enabled engine states are captured under `docs/play-screenshots/`; final release screenshots and Play Console upload remain pending.
- Publish or host the privacy policy URL required by Play Console.
- Complete internal testing / closed testing release steps required by the developer account.
