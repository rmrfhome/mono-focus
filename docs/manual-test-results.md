# Manual Test Results

Use this file to record release-blocking device and Play Console validation. Do not mark an item passed without the device, OS version, app version, build artifact, date, and tester recorded.

## Build Under Test

- App version: `0.1.1`
- Version code: `2`
- Artifact: `app/build/outputs/bundle/release/app-release.aab`
- Verifier command: `.\tools\verify-release.ps1`
- Verifier result/date: PASS on 2026-06-22 with Gradle release configuration, APK/AAB artifact inspection, Android/custom permission surface, cleanup-only boot receiver checks, launcher-only package visibility, app-owned manifest component/exported flag, required UI/privacy copy, Play Store/Data Safety draft, foreground-service string, and unsupported API/policy checks
- Tester: Codex local verifier

## Device Matrix

| Device | OS / API | Build fingerprint | Install source | Result |
| --- | --- | --- | --- | --- |
| Pixel device or emulator | Android 15 / API 35 | `google/sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043/12960925:userdebug/dev-keys` | `adb install -r app/build/outputs/apk/debug/app-debug.apk` on emulator `sdk_gphone64_x86_64` | Partial PASS on 2026-06-12 by Codex: debug APK setup/list/persistence/current Zen rule activation-deactivation, notification delivery, Usage/Notifications/Modes revocation, rule deletion/recreation, reboot cleanup, app-launch-after-reboot restore, previous foreground notification stop behavior, and selected-app uninstall/reinstall validated; current pause-action notification behavior, release artifact, visual timing, accessibility, physical/OEM, Android 16, and Android 14 validation pending |
| Pixel device or emulator | Android 16, if available |  |  | Pending |
| Samsung device | Android 15+ |  |  | Pending |
| Android 14 device or emulator | API 34 |  |  | Pending |

## Debug Emulator Validation

Supporting validation only; this is not a substitute for release AAB install, Play internal testing, physical-device, accessibility, or visual timing proof.

- Date/tester: 2026-06-12, Codex
- Device: Android Studio emulator `sdk_gphone64_x86_64`
- OS/API: Android 15 / API 35
- Build fingerprint: `google/sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043/12960925:userdebug/dev-keys`
- Artifact: `app/build/outputs/apk/debug/app-debug.apk`
- App ID: `com.rmrfhome.monofocus.debug` for current builds; earlier debug-emulator evidence in this file was gathered before the Play package alignment, when the debug app ID was `com.monofocus.app.debug`.
- Permissions prepared through adb for validation: Usage Access app-op, `POST_NOTIFICATIONS`, and Notification Policy Access.
- Debug manifest requested `android.permission.ACCESS_NOTIFICATION_POLICY`; after reinstall, package manager showed it requested/granted and Android Settings > Do Not Disturb access showed MonoFocus with access allowed.
- Fresh no-access launch showed setup-required state, disabled engine switch, Usage Access, Notifications, and Modes permission setup cards.
- Ready state showed launchable apps including Calendar, Camera, Chrome, and Clock; MonoFocus was absent from the app list.
- Calendar selection and enabled engine state survived app restart/debug reinstall.
- For selected-app uninstall/reinstall validation, Drive (`com.google.android.apps.docs`) was the only selected launchable app, Calendar was deselected, the engine switch was checked, and `FocusMonitorService` was running.
- Removing Drive with `pm uninstall --user 0 com.google.android.apps.docs` made `pm path com.google.android.apps.docs` fail, stopped `FocusMonitorService`, and returned the current MonoFocus rule to `STATE_FALSE`; after force-stop/relaunch, Drive was absent from the app list, the engine switch was unchecked, and the UI showed the no-selected-launchable-app copy.
- Restoring Drive with `cmd package install-existing --user 0 com.google.android.apps.docs` and force-stop/relaunch made the Drive row reappear selected, left the engine switch unchecked, and left `FocusMonitorService` absent.
- Accessibility source/runtime check: after the top-app-bar semantics fix, UIAutomator on the Android 15 debug emulator showed 9 enabled clickable nodes, 0 enabled unlabeled clickable nodes, and labeled clickable nodes for Privacy/About, Back, Grayscale engine, Calendar selection, and Clock selection.
- Touch target check: at density 420 dpi, 48 dp is 126 px; UIAutomator showed 0 non-clipped enabled clickable nodes smaller than 126 px in either dimension.
- Dynamic font check: with system `font_scale` set to `2.0`, the main title, engine label/status, search field, Privacy/About action, engine switch, a scrolled app-row switch, the Privacy screen title, and Back action remained reachable; font scale was restored to `1.0` after validation.
- Contrast check: the app now uses a fixed light/dark palette instead of wallpaper-derived dynamic colors, and JVM theme contrast tests verify text color pairs at 4.5:1 or higher and primary icon pairs at 3:1 or higher.
- Local Play screenshot draft artifacts captured from the Android 15 debug emulator: `docs/play-screenshots/01-setup-required.png`, `docs/play-screenshots/02-app-list-ready.png`, `docs/play-screenshots/03-selected-apps.png`, and `docs/play-screenshots/04-engine-enabled.png`.
- Local release startup check: a locally test-signed copy of `app/build/outputs/apk/release/app-release-unsigned.apk` was installed as `com.monofocus.app` before Play package alignment; after adb-granting Usage Access, `POST_NOTIFICATIONS`, and Notification Policy Access, five force-stop cold launches returned `am start -W TotalTime` values of 854 ms, 752 ms, 782 ms, 830 ms, and 846 ms. Repeat this check for current package `com.rmrfhome.monofocus` before final production release.
- Local release app-list observation: on the same five launches, UIAutomator observed `Search apps` plus a launchable Calendar row on the first dump after `am start -W`, at 2481 ms, 2539 ms, 2576 ms, 2634 ms, and 2789 ms after the start command returned; a ready-screen UIAutomator dump baseline measured 2006-2032 ms, so this is coarse readiness evidence rather than a precise app-list load duration.
- Unsupported-OS artifact check: `aapt dump badging app/build/outputs/apk/release/app-release-unsigned.apk` reported `sdkVersion:'35'` and `targetSdkVersion:'35'` before Play signing was wired; current release APK output metadata reports `outputFile=app-release.apk` when signing is configured, `applicationId=com.rmrfhome.monofocus`, `variantName=release`, and `minSdkVersionForDexing=35`.
- Visual grayscale note: on the Android 15 emulator, `adb screencap` still produced color screenshots while the MonoFocus rule was `STATE_TRUE`; do not use emulator screencap artifacts as visual grayscale proof. Android system logs can show `ColorDisplayService` saturation changes, but final visual grayscale proof should still be gathered by direct observation, camera/video capture, or another method that captures post-display-effect output.
- Foreground service ran with notification ID `1001`, channel `focus_monitor`, `types=0x40000000`, and one Stop action while the engine was enabled in the previous notification-action build; the current build replaces that notification action with temporary pause actions.
- Launching Calendar left `com.google.android.calendar/.launch.oobe.WhatsNewFullScreen` top-resumed and UsageStats reported `Currently Active: com.google.android.calendar`.
- Earlier debug-emulator MonoFocus automatic rule evidence became `STATE_TRUE` with `condition=Condition[state=STATE_TRUE`, `summary=Selected app active`, `deviceEffects=[grayscale]`, `zenMode=ZEN_MODE_OFF`, and pre-alignment package `com.monofocus.app.debug`.
- While the rule was `STATE_TRUE` / `ZEN_MODE_OFF`, `cmd notification post` created notification key `0|com.android.shell|2020|monofocus-validation|2000`, and `cmd notification list` showed it present.
- Sending Home left `com.google.android.apps.nexuslauncher/.NexusLauncherActivity` top-resumed and UsageStats reported `Currently Active: com.google.android.apps.nexuslauncher`.
- Earlier debug-emulator MonoFocus automatic rule evidence returned to `STATE_FALSE` with `summary=Selected app inactive`, `deviceEffects=[grayscale]`, and `zenMode=ZEN_MODE_OFF`.
- With Calendar selected, Clock unselected, the engine checked, and `FocusMonitorService` running, five Calendar-to-Clock cycles made Calendar top-resumed with the MonoFocus rule `STATE_TRUE` and Clock top-resumed with the MonoFocus rule `STATE_FALSE`.
- Current-rule-state timing check: using the first `MonoFocus Grayscale` rule line in the current `dumpsys notification` zen config, activation from unselected Clock to selected Calendar reached `STATE_TRUE` in 1148 ms, 1153 ms, 1154 ms, 1236 ms, and 1250 ms; deactivation from selected Calendar to unselected Clock reached `STATE_FALSE` in 951 ms, 1111 ms, 1029 ms, 984 ms, and 1168 ms. The parser/dumpsys baseline while stable was 55-80 ms.
- Display-service saturation timing check: using `logcat -v epoch ColorDisplayService:D *:S`, activation from unselected Clock to selected Calendar logged `Setting saturation level: 0` in 682 ms, 1016 ms, 826 ms, 1180 ms, and 1378 ms; deactivation from selected Calendar to unselected Clock logged `Setting saturation level: 100` in 1186 ms, 777 ms, 1026 ms, 1167 ms, and 1126 ms. These timings prove the emulator display service received the saturation commands within 1.5 seconds, but direct visual proof remains pending.
- Revoking Usage Access while monitoring made `FocusMonitorService` absent, returned the current MonoFocus rule to `STATE_FALSE` with `summary=Selected app inactive`, and the app showed setup-required Usage Access UI on launch.
- Revoking `POST_NOTIFICATIONS` while monitoring made `FocusMonitorService` absent, returned the current MonoFocus rule to `STATE_FALSE`, and the app showed setup-required Notifications UI with the engine off.
- Revoking Modes / Do Not Disturb access through Android Settings while Calendar had previously been active first moved the current MonoFocus rule to `STATE_FALSE` when Settings became foreground; confirming the system revoke dialog then removed the MonoFocus automatic rule, left `FocusMonitorService` absent, showed the Settings switch unchecked, and the app showed setup-required Modes UI with the engine off.
- The adb-only `cmd notification disallow_dnd com.monofocus.app.debug` path can bypass the normal Settings foreground transition and leave the shell-forced active rule stale; release evidence should use the real Settings revocation path above.
- Previous foreground notification Stop action evidence is superseded by the current pause-action notification design; direct validation of Pause 15 min, Until tomorrow, automatic resume, and Resume remains pending.
- Service cleanup regression check after the shared bounded cleanup helper refactor: with the updated debug APK installed on `emulator-5554`, Calendar selected, and the engine active, launching Calendar made the current MonoFocus rule `STATE_TRUE`; returning to MonoFocus and switching the engine off left the engine switch unchecked, `FocusMonitorService` absent, no destroying service record, no `focus_monitor` notification, and the current MonoFocus rule `STATE_FALSE`.
- Android Settings > Schedules showed one `MonoFocus Grayscale` rule; using Settings overflow > Delete schedules to delete only MonoFocus removed the row, and resuming MonoFocus with the engine enabled recreated exactly one `MonoFocus Grayscale` row.
- Reboot test before the boot-cleanup fix showed `FocusMonitorService` absent but the app-owned rule still `STATE_TRUE`, so the build was fixed before recording a pass.
- Final reboot test: with Calendar foreground, `FocusMonitorService` running, and the MonoFocus rule `STATE_TRUE`, reboot completed with `FocusMonitorService` absent and the MonoFocus rule `STATE_FALSE`.
- After that reboot and before app launch, no foreground monitoring service was running; launching MonoFocus restored monitoring from persisted enabled state because permissions and the selected Calendar app were still valid.

## Acceptance Scenarios

| ID | Scenario | Expected result | Device evidence | Result |
| --- | --- | --- | --- | --- |
| AC-1 | Fresh install with missing accesses | Setup steps are shown; engine cannot be enabled | Android 15 debug emulator on 2026-06-12: no-access UI dump showed setup-required state, disabled engine switch, Usage Access, Notifications, and Modes setup cards | Partial PASS - debug APK only |
| AC-1 | Return from Usage Access settings | Usage Access state refreshes correctly | Android 15 debug emulator on 2026-06-12: after `GET_USAGE_STATS` was denied while monitoring and the app returned to foreground, UI showed Usage Access setup-required state and disabled engine | Partial PASS - debug APK only |
| AC-1 | Return from Modes / Do Not Disturb settings | Notification Policy Access state refreshes correctly | Android 15 debug emulator on 2026-06-12: after revoking Do Not Disturb access through Settings, UI showed Modes permission required and disabled engine | Partial PASS - debug APK only |
| AC-2 | Launchable app list | Launcher apps appear; MonoFocus is absent | Android 15 debug emulator on 2026-06-12: Calendar, Camera, Chrome, and Clock appeared; MonoFocus was absent | Partial PASS - debug APK only |
| AC-3 | Selection persistence | Selected app remains selected after app restart/process death | Android 15 debug emulator on 2026-06-12: Calendar remained selected after app force-stop/relaunch and debug reinstall | Partial PASS - debug APK only |
| AC-4 | Selected app activation | Display becomes grayscale within 1.5 seconds | Android 15 debug emulator on 2026-06-12: Calendar top-resumed/currently active and current MonoFocus rule was `STATE_TRUE` with `deviceEffects=[grayscale]`; current-rule-state activation observations were 1148 ms, 1153 ms, 1154 ms, 1236 ms, and 1250 ms; `ColorDisplayService` logged saturation level `0` in 682 ms, 1016 ms, 826 ms, 1180 ms, and 1378 ms; direct visual proof still pending | Partial PASS - timed rule/display-service signal |
| AC-5 | Unselected app deactivation | Display returns to color within 1.5 seconds | Android 15 debug emulator on 2026-06-12: in five cycles with Calendar selected and Clock unselected, Clock became top-resumed and current MonoFocus rule reached `STATE_FALSE` in 951 ms, 1111 ms, 1029 ms, 984 ms, and 1168 ms; `ColorDisplayService` logged saturation level `100` in 1186 ms, 777 ms, 1026 ms, 1167 ms, and 1126 ms; direct visual proof still pending | Partial PASS - timed rule/display-service signal |
| AC-5 | Home/recents deactivation | Display returns to color unless launcher is selected | Android 15 debug emulator on 2026-06-12: Home made Nexus Launcher top-resumed/currently active and current MonoFocus rule returned to `STATE_FALSE`; visual color restoration and five-run timing still pending | Partial PASS - rule state only |
| AC-6 | Notification delivery | Normal notification is not suppressed while grayscale is active | Android 15 debug emulator on 2026-06-12: while MonoFocus rule was `STATE_TRUE` / `ZEN_MODE_OFF`, posted shell notification key `0|com.android.shell|2020|monofocus-validation|2000` appeared in `cmd notification list` | Partial PASS - debug APK only |
| AC-7 | Usage Access revoked | Engine stops, grayscale deactivates, setup-required state appears | Android 15 debug emulator on 2026-06-12: after `GET_USAGE_STATS` was denied while monitoring, `FocusMonitorService` was absent, current MonoFocus rule was `STATE_FALSE`, and UI showed Usage Access setup-required state | Partial PASS - debug APK only |
| AC-7 | Modes access revoked | Engine stops and setup-required state appears | Android 15 debug emulator on 2026-06-12: real Settings revoke path made MonoFocus rule `STATE_FALSE` before revoke, system confirmation removed the rule, `FocusMonitorService` was absent, Settings switch was unchecked, and app UI showed Modes setup-required state with engine off | Partial PASS - debug APK only |
| AC-7 | Notifications permission revoked | Monitoring stops before foreground notification requirements are violated | Android 15 debug emulator on 2026-06-12: after `POST_NOTIFICATIONS` was revoked while monitoring, `FocusMonitorService` was absent, current MonoFocus rule was `STATE_FALSE`, and UI showed Notifications setup-required state with engine off | Partial PASS - debug APK only |
| AC-8 | MonoFocus rule deleted | One replacement rule is created on app resume or engine restart | Android 15 debug emulator on 2026-06-12: deleting only `MonoFocus Grayscale` through Settings > Schedules removed the row; resuming MonoFocus with engine enabled recreated exactly one row | Partial PASS - debug APK only |
| AC-8 | Duplicate rule cleanup | Only one MonoFocus Grayscale rule remains | Android 15 debug emulator on 2026-06-12: after rule deletion and resume-based recreation, Settings > Schedules showed exactly one `MonoFocus Grayscale` row; seeded duplicate cleanup remains unit-test-only evidence | Partial PASS - debug APK only |
| FR-8 | Shutdown or power off while active | Device is not left grayscale after restart | Android 15 debug emulator on 2026-06-12: with Calendar foreground, service running, and MonoFocus rule `STATE_TRUE`, reboot completed with `FocusMonitorService` absent and MonoFocus rule `STATE_FALSE` after boot cleanup | Partial PASS - debug APK only |
| FR-9 | Foreground notification pause actions | Pause 15 min and Until tomorrow deactivate grayscale temporarily without disabling the engine; grayscale resumes automatically after the pause deadline, and Resume ends an active pause early | Current source implements pause actions; direct device validation pending | Pending |
| FR-10 | Reboot before app launch | MonoFocus does not silently start monitoring | Android 15 debug emulator on 2026-06-12: after reboot, before app launch, `FocusMonitorService` was absent and MonoFocus rule was `STATE_FALSE`; app process existed only from cleanup-only boot receiver | Partial PASS - debug APK only |
| FR-10 | App launch after reboot | Persisted engine state restores only with permissions and selected launchable app | Android 15 debug emulator on 2026-06-12: launching MonoFocus after reboot with permissions and Calendar selection intact started `FocusMonitorService`, UI showed Ready, and engine switch was checked | Partial PASS - debug APK only |
| FR-11 | Selected app uninstall | Removed app disappears; engine stops if no selected launchable apps remain | Android 15 debug emulator on 2026-06-12: after Drive was the only selected launchable app and monitoring was running, `pm uninstall --user 0 com.google.android.apps.docs` removed it, `FocusMonitorService` became absent, the MonoFocus rule returned to `STATE_FALSE`, and fresh app launch showed Drive absent with the engine off | Partial PASS - debug APK only |
| FR-11 | Selected app reinstall | Same package can reappear selected | Android 15 debug emulator on 2026-06-12: `cmd package install-existing --user 0 com.google.android.apps.docs` restored Drive, and fresh app launch showed the Drive row present and selected while the engine remained off | Partial PASS - debug APK only |
| AC-10 | Android 14 release install | Platform rejects normal release install because `minSdk` is 35 | Release artifact evidence on 2026-06-12: `aapt dump badging app-release-unsigned.apk` reported `sdkVersion:'35'`, and `output-metadata.json` reported `minSdkVersionForDexing=35`; actual API 34 device/emulator install rejection still pending | Partial PASS - release artifact only |
| NFR | TalkBack labels | Switches and actions have useful labels and states | Android 15 debug emulator on 2026-06-12: UIAutomator showed 0 enabled unlabeled clickable nodes and labeled clickable nodes for Privacy/About, Back, Grayscale engine, Calendar selection, and Clock selection; physical TalkBack pass still pending | Partial PASS - debug APK only |
| NFR | Dynamic font size | UI remains usable at largest practical display/font settings | Android 15 debug emulator on 2026-06-12: with system `font_scale=2.0`, main controls, a scrolled app-row switch, Privacy screen, and Back action remained reachable; physical accessibility pass still pending | Partial PASS - debug APK only |
| NFR | Contrast and touch targets | Controls are legible and tappable | Android 15 debug emulator/source on 2026-06-12: fixed palette contrast tests passed for light/dark text and icon pairs; at 420 dpi, UIAutomator found 0 non-clipped enabled clickable nodes below the 126 px / 48 dp target | Partial PASS - debug APK/source only |
| NFR | Cold startup | Main screen starts in under 1 second on a modern supported device | Android 15 emulator on 2026-06-12: locally test-signed release APK returned `am start -W TotalTime` values of 854 ms, 752 ms, 782 ms, 830 ms, and 846 ms after force-stop cold launches; physical mid-range device validation still pending | Partial PASS - local release emulator only |

## Startup and App-List Measurements

| Device / artifact | Scenario | Run 1 | Run 2 | Run 3 | Run 4 | Run 5 | Max | Result |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Android 15 emulator / locally test-signed release APK | Force-stop cold launch, `am start -W TotalTime` | 854 ms | 752 ms | 782 ms | 830 ms | 846 ms | 854 ms | Partial PASS - local release emulator only |
| Android 15 emulator / locally test-signed release APK | First UIAutomator observation of `Search apps` plus launchable Calendar row after `am start -W` returned | 2481 ms | 2539 ms | 2576 ms | 2634 ms | 2789 ms | 2789 ms | Informational only - ready-screen dump baseline was 2006-2032 ms |

## Timing Measurements

Record at least five activation and five deactivation timings per supported device class.

| Device | Scenario | Run 1 | Run 2 | Run 3 | Run 4 | Run 5 | Max | Pass |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
|  | Activation selected app -> grayscale |  |  |  |  |  |  | Pending |
|  | Deactivation selected app -> home/unselected app |  |  |  |  |  |  | Pending |
| Android 15 debug emulator / debug APK | Activation selected Calendar -> current rule `STATE_TRUE` | 1148 ms | 1153 ms | 1154 ms | 1236 ms | 1250 ms | 1250 ms | Partial PASS - rule state only; visual display timing pending |
| Android 15 debug emulator / debug APK | Deactivation selected Calendar -> unselected Clock current rule `STATE_FALSE` | 951 ms | 1111 ms | 1029 ms | 984 ms | 1168 ms | 1168 ms | Partial PASS - rule state only; visual display timing pending |
| Android 15 debug emulator / debug APK | Activation selected Calendar -> `ColorDisplayService` saturation `0` | 682 ms | 1016 ms | 826 ms | 1180 ms | 1378 ms | 1378 ms | Partial PASS - display-service signal; direct visual proof pending |
| Android 15 debug emulator / debug APK | Deactivation selected Calendar -> unselected Clock `ColorDisplayService` saturation `100` | 1186 ms | 777 ms | 1026 ms | 1167 ms | 1126 ms | 1186 ms | Partial PASS - display-service signal; direct visual proof pending |

## Play Console Evidence

| Item | Evidence | Result |
| --- | --- | --- |
| Privacy policy URL is hosted and reachable | Local policy draft exists in `PRIVACY.md` and is checked by `.\tools\verify-release.ps1`; no hosted URL has been recorded yet | Pending - hosted URL required |
| Store listing states Android 15 or newer | Local listing draft in `docs/play-store.md` states "Requirements: Android 15 or newer"; verifier checks the draft text and release APK reports `sdkVersion:'35'` / `targetSdkVersion:'35'` | Partial PASS - draft only |
| Store listing states local-only/no analytics/no ads | Local listing draft in `docs/play-store.md`, Data Safety draft, and `PRIVACY.md` all state local-only behavior with no analytics, ads, or network transmission; verifier checks those drafts and release artifacts show no `INTERNET` permission | Partial PASS - draft/release artifact only |
| Data Safety answers match `docs/data-safety.md` | Local Data Safety draft says no data collected/shared/transmitted, no network access, and only private local selected-package/settings storage; verifier checks the draft, release permissions, and dependency policy | Partial PASS - draft only |
| Foreground service declaration matches `docs/play-store.md` | Local Play draft records `specialUse`, subtype string, notification text, pause actions, local-only scope, and no-network behavior; verifier checks manifest property, strings, and Play draft text | Partial PASS - draft/release artifact only |
| Setup, app list, selected apps, and enabled-engine screenshots uploaded | Local debug-emulator draft PNGs captured in `docs/play-screenshots/`: setup-required, ready app list, selected Calendar filter, and enabled engine states | Pending - Play Console upload and final release screenshots required |
| Internal testing release installs on supported device |  | Pending |
| Closed testing release, if account requires it |  | Pending |
