# MonoFocus

MonoFocus is a narrow Android 15+ utility that makes selected apps grayscale while they are in the foreground. It stores selected package names locally, uses Usage Access only for foreground-app detection, and uses an app-owned Automatic Zen Rule with grayscale device effects.

The v1 app intentionally does not include accounts, analytics, ads, network calls, blocking, timers, streaks, or cloud sync.

## Requirements

- Android Studio or Android SDK command-line tools.
- Android SDK Platform 35.
- Android SDK Build-Tools 34.0.0 or newer compatible with Android Gradle Plugin 8.7.3.
- JDK 17.

## Build

From the repository root:

```powershell
.\gradlew.bat lint testDebugUnitTest assembleDebug assembleRelease bundleRelease
```

Outputs:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk` when signing is configured; otherwise `app/build/outputs/apk/release/app-release-unsigned.apk`
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`

## Play Upload Signing

`bundleRelease` signs the release AAB when local signing properties are configured.
Keep key material and passwords out of git.

Add this to ignored `local.properties`:

```properties
monofocus.signing.properties=G:/My Drive/mobile-app-keys/play-upload-key.properties
```

The signing properties file should contain:

```properties
storeFile=monofocus-upload.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Relative `storeFile` paths are resolved from the signing properties file's folder.
Run `.\gradlew.bat bundleRelease`; the Play Console upload artifact is
`app/build/outputs/bundle/release/app-release.aab`.
When local signing is configured, `.\tools\verify-release.ps1` also checks that
the AAB contains upload-signature entries.

## Verification Notes

For the full automated local gate, including build, lint, unit tests, release artifact creation, Gradle release configuration checks, APK identity and permission inspection, AAB base-module structure and manifest marker inspection, custom permission surface checks, launcher-only package visibility checks, app-owned manifest component checks, cleanup-only boot receiver checks, required UI/privacy copy checks, Play Store/Data Safety draft checks, Play screenshot draft artifact checks, foreground-service string checks, unsupported-approach scans, manifest privacy checks, launcher icon checks, source policy scans, and release dependency checks:

```powershell
.\tools\verify-release.ps1
```

To recheck existing release artifacts without rebuilding:

```powershell
.\tools\verify-release.ps1 -SkipBuild
```

The verifier also checks the release APK output metadata, v1 UI scope, and the
manual validation template that tracks device and Play Console evidence.

The release artifact should not request network or broad package visibility permissions. To inspect the APK manually:

```powershell
$env:ANDROID_HOME = "<path-to-android-sdk>"
& "$env:ANDROID_HOME\build-tools\34.0.0\aapt.exe" dump permissions app/build/outputs/apk/release/app-release.apk
```

Expected Android permissions:

- `android.permission.PACKAGE_USAGE_STATS`
- `android.permission.ACCESS_NOTIFICATION_POLICY`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`
- `android.permission.RECEIVE_BOOT_COMPLETED`

AndroidX may also add an app-private dynamic receiver permission.

Record release-blocking device and Play Console validation in
`docs/manual-test-results.md`. The local verifier cannot prove Android 15+
grayscale timing, notification delivery while grayscale is active, OEM behavior,
TalkBack usability, or Play Console submission state.

Current automated coverage includes 101 JVM unit tests for app filtering,
launchable-app session caching and load-failure handling,
app-list search/sort/selection presentation and selected-only filtering,
active selected-app counting and package-removal decisions, including
short-circuiting unrelated package removals, for uninstalled/reinstalled
package behavior, foreground inference including timestamp ordering, foreground
polling/debounce/fail-safe timing, grayscale activation decisions, permission-state
mapping, DataStore-backed settings persistence, package-name normalization, and
defensive normalization of malformed stored values, and the default-off reboot autostart setting,
cleanup-only boot receiver action filtering,
engine flow behavior, persisted enabled-state handling, engine service
start/stop/resume/preflight and foreground-monitoring promotion decisions, pause
window calculation and paused-engine behavior, permission
revocation handling, fatal engine cleanup including cleanup-failure handling,
bounded grayscale cleanup timeout behavior, zen-rule reconciliation and
deactivation planning, and fixed-theme contrast targets.
