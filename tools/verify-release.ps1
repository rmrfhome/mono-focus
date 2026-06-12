#requires -Version 5.1

[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [string]$SdkDir = $env:ANDROID_HOME
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path

function Write-Step {
    param([string]$Message)
    Write-Host "[verify-release] $Message"
}

function Fail {
    param([string]$Message)
    throw "[verify-release] $Message"
}

function Require-File {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Fail "Missing file: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Get-ConfiguredSdkDir {
    if (-not [string]::IsNullOrWhiteSpace($SdkDir)) {
        return $SdkDir
    }

    $localProperties = Join-Path $repoRoot "local.properties"
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match "^sdk\.dir=(.+)$" } |
            Select-Object -First 1
        if ($sdkLine -match "^sdk\.dir=(.+)$") {
            return $Matches[1]
        }
    }

    Fail "Android SDK path not found. Set ANDROID_HOME or create local.properties with sdk.dir."
}

function Find-Aapt {
    param([string]$AndroidSdkDir)

    $buildToolsDir = Join-Path $AndroidSdkDir "build-tools"
    if (-not (Test-Path -LiteralPath $buildToolsDir -PathType Container)) {
        Fail "Android SDK build-tools directory not found: $buildToolsDir"
    }

    $aapt = Get-ChildItem -LiteralPath $buildToolsDir -Filter "aapt.exe" -Recurse -File |
        Sort-Object FullName -Descending |
        Select-Object -First 1

    if ($null -eq $aapt) {
        Fail "aapt.exe not found under $buildToolsDir"
    }

    return $aapt.FullName
}

function Invoke-Checked {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        Fail "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
    }
}

function Select-PolicyMatches {
    param(
        [string[]]$Path,
        [string[]]$Pattern,
        [switch]$SimpleMatch
    )

    if ($Path.Count -eq 0) {
        return @()
    }

    if ($SimpleMatch) {
        return @(Select-String -LiteralPath $Path -Pattern $Pattern -SimpleMatch)
    }

    return @(Select-String -LiteralPath $Path -Pattern $Pattern)
}

function Require-ContentContains {
    param(
        [string]$Content,
        [string]$Expected,
        [string]$Description
    )

    if (-not $Content.Contains($Expected)) {
        Fail "$Description is missing required text: $Expected"
    }
}

function Get-BigEndianUInt32 {
    param(
        [byte[]]$Bytes,
        [int]$Offset
    )

    return [uint32](
        ([uint32]$Bytes[$Offset] -shl 24) -bor
        ([uint32]$Bytes[$Offset + 1] -shl 16) -bor
        ([uint32]$Bytes[$Offset + 2] -shl 8) -bor
        [uint32]$Bytes[$Offset + 3]
    )
}

function Require-PngScreenshot {
    param(
        [string]$Path,
        [string]$Description
    )

    $resolvedPath = Require-File $Path
    $file = Get-Item -LiteralPath $resolvedPath
    if ($file.Length -lt 50000) {
        Fail "$Description screenshot is unexpectedly small: $resolvedPath"
    }

    $bytes = [System.IO.File]::ReadAllBytes($resolvedPath)
    $pngSignature = [byte[]](0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    if ($bytes.Length -lt 33) {
        Fail "$Description screenshot is too short to be a valid PNG: $resolvedPath"
    }
    for ($index = 0; $index -lt $pngSignature.Length; $index++) {
        if ($bytes[$index] -ne $pngSignature[$index]) {
            Fail "$Description screenshot does not have a PNG signature: $resolvedPath"
        }
    }

    $firstChunkType = [System.Text.Encoding]::ASCII.GetString($bytes, 12, 4)
    if ($firstChunkType -ne "IHDR") {
        Fail "$Description screenshot first PNG chunk is not IHDR: $resolvedPath"
    }

    $width = Get-BigEndianUInt32 $bytes 16
    $height = Get-BigEndianUInt32 $bytes 20
    $bitDepth = $bytes[24]
    $colorType = $bytes[25]
    if ($width -lt 1080 -or $height -lt 1920 -or $height -le $width) {
        Fail "$Description screenshot must be a portrait phone screenshot at least 1080x1920; got ${width}x${height}: $resolvedPath"
    }
    if ($bitDepth -ne 8 -or $colorType -notin @(2, 6)) {
        Fail "$Description screenshot must be an 8-bit RGB/RGBA PNG; got bitDepth=$bitDepth colorType=${colorType}: $resolvedPath"
    }
}

function Get-AppOwnedManifestComponents {
    param(
        [string]$ManifestContent
    )

    return @(
        [regex]::Matches(
            $ManifestContent,
            '<(?<tag>activity|activity-alias|service|receiver|provider)\b[^>]*android:name="(?<name>com\.monofocus\.app[^"]+)"'
        ) | ForEach-Object {
            [pscustomobject]@{
                Tag = $_.Groups["tag"].Value
                Name = $_.Groups["name"].Value
            }
        }
    )
}

function Get-ZipEntryNames {
    param([string]$ZipPath)

    $zip = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
    try {
        return @($zip.Entries | ForEach-Object { $_.FullName })
    } finally {
        $zip.Dispose()
    }
}

function Get-ZipEntryText {
    param(
        [string]$ZipPath,
        [string]$EntryName
    )

    $zip = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
    try {
        $entry = $zip.GetEntry($EntryName)
        if ($null -eq $entry) {
            Fail "Missing zip entry in ${ZipPath}: $EntryName"
        }

        $stream = $entry.Open()
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8, $true)
        try {
            return $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
    } finally {
        $zip.Dispose()
    }
}

if (-not $SkipBuild) {
    Write-Step "running Gradle lint, unit tests, and release builds"
    $gradlew = Require-File (Join-Path $repoRoot "gradlew.bat")
    Invoke-Checked $gradlew @("lint", "testDebugUnitTest", "assembleDebug", "assembleRelease", "bundleRelease")
}

$apkPath = Require-File (Join-Path $repoRoot "app/build/outputs/apk/release/app-release-unsigned.apk")
$aabPath = Require-File (Join-Path $repoRoot "app/build/outputs/bundle/release/app-release.aab")
$apkMetadataPath = Require-File (Join-Path $repoRoot "app/build/outputs/apk/release/output-metadata.json")
$dependencyMetadataPath = Require-File (Join-Path $repoRoot "app/build/outputs/sdk-dependencies/release/sdkDependencies.txt")
$releaseManifestPath = Require-File (Join-Path $repoRoot "app/build/intermediates/packaged_manifests/release/processReleaseManifestForPackage/AndroidManifest.xml")
$dataExtractionRulesPath = Require-File (Join-Path $repoRoot "app/src/main/res/xml/data_extraction_rules.xml")
$launcherIconPath = Require-File (Join-Path $repoRoot "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
$roundLauncherIconPath = Require-File (Join-Path $repoRoot "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")
$launcherForegroundPath = Require-File (Join-Path $repoRoot "app/src/main/res/drawable/ic_launcher_foreground.xml")
$launcherMonochromePath = Require-File (Join-Path $repoRoot "app/src/main/res/drawable/ic_launcher_monochrome.xml")
$stringsPath = Require-File (Join-Path $repoRoot "app/src/main/res/values/strings.xml")
$appBuildGradlePath = Require-File (Join-Path $repoRoot "app/build.gradle.kts")
$uiSourcePath = Require-File (Join-Path $repoRoot "app/src/main/java/com/monofocus/app/ui/MonoFocusApp.kt")
$privacyPolicyPath = Require-File (Join-Path $repoRoot "PRIVACY.md")
$playStoreDraftPath = Require-File (Join-Path $repoRoot "docs/play-store.md")
$dataSafetyDraftPath = Require-File (Join-Path $repoRoot "docs/data-safety.md")
$manualTestResultsPath = Require-File (Join-Path $repoRoot "docs/manual-test-results.md")

$resolvedSdkDir = Get-ConfiguredSdkDir
$aaptPath = Find-Aapt $resolvedSdkDir
$expectedApplicationId = "com.monofocus.app"
$expectedVersionCode = "1"
$expectedVersionName = "0.1.0"

Write-Step "checking release APK permissions"
$permissions = & $aaptPath dump permissions $apkPath
if ($LASTEXITCODE -ne 0) {
    Fail "aapt dump permissions failed for $apkPath"
}

$forbiddenPermissions = @(
    "android.permission.INTERNET",
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.WRITE_SECURE_SETTINGS",
    "android.permission.BIND_ACCESSIBILITY_SERVICE",
    "android.permission.BIND_VPN_SERVICE",
    "android.permission.WAKE_LOCK",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.CAPTURE_VIDEO_OUTPUT",
    "android.permission.MEDIA_CONTENT_CONTROL",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE"
)

foreach ($permission in $forbiddenPermissions) {
    if ($permissions -match [regex]::Escape($permission)) {
        Fail "Forbidden permission found in release APK: $permission"
    }
}

$allowedAndroidUsesPermissions = @(
    "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.ACCESS_NOTIFICATION_POLICY",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.RECEIVE_BOOT_COMPLETED"
)

$usesPermissions = @(
    $permissions |
        Where-Object { $_ -match "^uses-permission: name='([^']+)'" } |
        ForEach-Object { [regex]::Match($_, "^uses-permission: name='([^']+)'").Groups[1].Value }
)

$unexpectedAndroidPermissions = @(
    $usesPermissions |
        Where-Object { $_ -like "android.permission.*" -and $_ -notin $allowedAndroidUsesPermissions }
)

if ($unexpectedAndroidPermissions.Count -gt 0) {
    Fail "Unexpected Android uses-permission entries: $($unexpectedAndroidPermissions -join ', ')"
}

$allowedNonAndroidUsesPermissions = @(
    "com.monofocus.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
)
$unexpectedNonAndroidPermissions = @(
    $usesPermissions |
        Where-Object { $_ -notlike "android.permission.*" -and $_ -notin $allowedNonAndroidUsesPermissions }
)

if ($unexpectedNonAndroidPermissions.Count -gt 0) {
    Fail "Unexpected non-Android uses-permission entries: $($unexpectedNonAndroidPermissions -join ', ')"
}

Write-Step "checking release APK sdk levels"
$badging = & $aaptPath dump badging $apkPath
if ($LASTEXITCODE -ne 0) {
    Fail "aapt dump badging failed for $apkPath"
}
$badgingText = $badging -join "`n"

if ($badgingText -notmatch "package: name='$([regex]::Escape($expectedApplicationId))'") {
    Fail "Release APK package name is not $expectedApplicationId."
}
if ($badgingText -notmatch "versionCode='$expectedVersionCode'") {
    Fail "Release APK versionCode is not $expectedVersionCode."
}
if ($badgingText -notmatch "versionName='$([regex]::Escape($expectedVersionName))'") {
    Fail "Release APK versionName is not $expectedVersionName."
}
if ($badgingText -notmatch "sdkVersion:'35'") {
    Fail "Release APK does not report minSdk 35."
}
if ($badgingText -notmatch "targetSdkVersion:'35'") {
    Fail "Release APK does not report targetSdk 35."
}

Write-Step "checking release APK output metadata"
$apkMetadata = Get-Content -LiteralPath $apkMetadataPath -Raw | ConvertFrom-Json
if ($apkMetadata.applicationId -ne $expectedApplicationId) {
    Fail "Release APK output metadata applicationId is not $expectedApplicationId."
}
if ($apkMetadata.variantName -ne "release") {
    Fail "Release APK output metadata variantName is not release."
}
if ([int]$apkMetadata.minSdkVersionForDexing -ne 35) {
    Fail "Release APK output metadata minSdkVersionForDexing is not 35."
}
$apkMetadataElements = @($apkMetadata.elements)
$apkMetadataElement = $apkMetadataElements |
    Where-Object { $_.outputFile -eq "app-release-unsigned.apk" } |
    Select-Object -First 1
if ($null -eq $apkMetadataElement) {
    Fail "Release APK output metadata does not list app-release-unsigned.apk."
}
if ([int]$apkMetadataElement.versionCode -ne [int]$expectedVersionCode) {
    Fail "Release APK output metadata versionCode is not $expectedVersionCode."
}
if ($apkMetadataElement.versionName -ne $expectedVersionName) {
    Fail "Release APK output metadata versionName is not $expectedVersionName."
}

Write-Step "checking release AAB structure"
$aabEntryNames = Get-ZipEntryNames $aabPath
$requiredAabEntries = @(
    "BundleConfig.pb",
    "BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties",
    "BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb",
    "base/manifest/AndroidManifest.xml",
    "base/dex/classes.dex",
    "base/resources.pb",
    "base/native.pb",
    "base/res/xml/data_extraction_rules.xml",
    "base/res/mipmap-anydpi-v26/ic_launcher.xml",
    "base/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    "base/res/drawable/ic_launcher_foreground.xml",
    "base/res/drawable/ic_launcher_monochrome.xml"
)
$missingAabEntries = @(
    $requiredAabEntries | Where-Object { $_ -notin $aabEntryNames }
)
if ($missingAabEntries.Count -gt 0) {
    Fail "Release AAB is missing required entries: $($missingAabEntries -join ', ')"
}

$aabModuleManifests = @(
    $aabEntryNames | Where-Object { $_ -match '^[^/]+/manifest/AndroidManifest\.xml$' }
)
if ($aabModuleManifests.Count -ne 1 -or $aabModuleManifests[0] -ne "base/manifest/AndroidManifest.xml") {
    Fail "Release AAB must contain exactly one base module manifest."
}

$aabManifestText = Get-ZipEntryText $aabPath "base/manifest/AndroidManifest.xml"
$requiredAabManifestMarkers = @(
    $expectedApplicationId,
    "minSdkVersion",
    "targetSdkVersion",
    "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.ACCESS_NOTIFICATION_POLICY",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "com.monofocus.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    "android.intent.action.MAIN",
    "android.intent.category.LAUNCHER",
    "android.app.action.AUTOMATIC_ZEN_RULE",
    "android.intent.action.BOOT_COMPLETED",
    "com.monofocus.app.MainActivity",
    "com.monofocus.app.service.FocusMonitorService",
    "com.monofocus.app.platform.PackageChangeReceiver",
    "com.monofocus.app.platform.BootCleanupReceiver",
    "data_extraction_rules",
    "ic_launcher",
    "ic_launcher_round"
)
foreach ($marker in $requiredAabManifestMarkers) {
    Require-ContentContains $aabManifestText $marker "Release AAB manifest"
}
foreach ($permission in $forbiddenPermissions) {
    if ($aabManifestText.Contains($permission)) {
        Fail "Forbidden permission found in release AAB manifest: $permission"
    }
}

$aabAppMetadata = Get-ZipEntryText $aabPath "BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties"
Require-ContentContains $aabAppMetadata "appMetadataVersion=" "Release AAB app metadata"
Require-ContentContains $aabAppMetadata "androidGradlePluginVersion=" "Release AAB app metadata"

Write-Step "checking manifest policy"
$releaseManifest = Get-Content -LiteralPath $releaseManifestPath -Raw
if ($releaseManifest -notmatch '(?s)<application\b[^>]*android:allowBackup="false"') {
    Fail "Release manifest does not disable Android backup."
}
if ($releaseManifest -notmatch '(?s)<application\b[^>]*android:fullBackupContent="false"') {
    Fail "Release manifest does not disable full backup content."
}
if ($releaseManifest -notmatch '(?s)<application\b[^>]*android:dataExtractionRules="@xml/data_extraction_rules"') {
    Fail "Release manifest does not reference data extraction rules."
}
if ($releaseManifest -notmatch '(?s)<application\b[^>]*android:icon="@mipmap/ic_launcher"') {
    Fail "Release manifest does not reference the adaptive launcher icon."
}
if ($releaseManifest -notmatch '(?s)<application\b[^>]*android:roundIcon="@mipmap/ic_launcher_round"') {
    Fail "Release manifest does not reference the round adaptive launcher icon."
}
if ($releaseManifest -match 'android:usesCleartextTraffic="true"') {
    Fail "Release manifest enables cleartext traffic."
}
if ($releaseManifest -match 'android:requestLegacyExternalStorage="true"') {
    Fail "Release manifest requests legacy external storage."
}
if ($releaseManifest -match 'android:debuggable="true"') {
    Fail "Release manifest is debuggable."
}
if ($releaseManifest -match 'android\.intent\.action\.LOCKED_BOOT_COMPLETED') {
    Fail "Release manifest declares locked-boot handling; v1 boot work must wait for normal boot completion."
}
$bootCompletedActions = @([regex]::Matches($releaseManifest, 'android\.intent\.action\.BOOT_COMPLETED'))
if ($bootCompletedActions.Count -ne 1) {
    Fail "Release manifest must declare exactly one BOOT_COMPLETED action for cleanup-only rule deactivation."
}
if ($releaseManifest -notmatch '(?s)<permission\b[^>]*android:name="com\.monofocus\.app\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"[^>]*android:protectionLevel="signature"\s*/>') {
    Fail "Release manifest is missing the expected app-private AndroidX dynamic receiver permission."
}
$declaredPermissions = @(
    [regex]::Matches($releaseManifest, '<permission\b[^>]*android:name="([^"]+)"') |
        ForEach-Object { $_.Groups[1].Value }
)
$unexpectedDeclaredPermissions = @(
    $declaredPermissions |
        Where-Object { $_ -ne "com.monofocus.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" }
)
if ($unexpectedDeclaredPermissions.Count -gt 0) {
    Fail "Unexpected custom permission declarations: $($unexpectedDeclaredPermissions -join ', ')"
}

Write-Step "checking package visibility and component surface"
$queriesMatches = @([regex]::Matches($releaseManifest, '(?s)<queries\b.*?</queries>'))
if ($queriesMatches.Count -ne 1) {
    Fail "Release manifest must declare exactly one package visibility queries block."
}
$queriesBlock = $queriesMatches[0].Value
if ($queriesBlock -notmatch '(?s)<queries>\s*<intent>\s*<action\s+android:name="android\.intent\.action\.MAIN"\s*/>\s*<category\s+android:name="android\.intent\.category\.LAUNCHER"\s*/>\s*</intent>\s*</queries>') {
    Fail "Release manifest package visibility is not limited to launcher intent queries."
}
if ($queriesBlock -match '<(package|provider|service)\b') {
    Fail "Release manifest declares broad package visibility entries outside launcher intent queries."
}

$expectedAppOwnedComponents = @(
    "activity:com.monofocus.app.MainActivity",
    "service:com.monofocus.app.service.FocusMonitorService",
    "receiver:com.monofocus.app.platform.PackageChangeReceiver",
    "receiver:com.monofocus.app.platform.BootCleanupReceiver"
)
$appOwnedComponents = @(
    Get-AppOwnedManifestComponents $releaseManifest |
        ForEach-Object { "$($_.Tag):$($_.Name)" }
)
$unexpectedAppOwnedComponents = @(
    $appOwnedComponents | Where-Object { $_ -notin $expectedAppOwnedComponents }
)
$missingAppOwnedComponents = @(
    $expectedAppOwnedComponents | Where-Object { $_ -notin $appOwnedComponents }
)
if ($unexpectedAppOwnedComponents.Count -gt 0) {
    Fail "Unexpected app-owned manifest components: $($unexpectedAppOwnedComponents -join ', ')"
}
if ($missingAppOwnedComponents.Count -gt 0) {
    Fail "Missing app-owned manifest components: $($missingAppOwnedComponents -join ', ')"
}
if ($releaseManifest -notmatch '(?s)<activity\b[^>]*android:name="com\.monofocus\.app\.MainActivity"[^>]*android:exported="true"') {
    Fail "MainActivity is missing or is not exported=true in the release manifest."
}
if ($releaseManifest -notmatch '(?s)<receiver\b(?=[^>]*android:name="com\.monofocus\.app\.platform\.PackageChangeReceiver")(?=[^>]*android:exported="true")(?=[^>]*android:permission="android\.permission\.BROADCAST_PACKAGE_REMOVED")[^>]*>.*<action\s+android:name="android\.intent\.action\.PACKAGE_REMOVED"\s*/>.*<data\s+android:scheme="package"\s*/>') {
    Fail "PackageChangeReceiver is missing, is not exported=true with BROADCAST_PACKAGE_REMOVED sender permission, or does not handle package removals."
}
if ($releaseManifest -notmatch '(?s)<receiver\b[^>]*android:name="com\.monofocus\.app\.platform\.BootCleanupReceiver"[^>]*android:exported="false"[^>]*>.*<action\s+android:name="android\.intent\.action\.BOOT_COMPLETED"\s*/>') {
    Fail "BootCleanupReceiver is missing, is not exported=false, or does not handle BOOT_COMPLETED."
}
if ($releaseManifest -notmatch '(?s)<activity\b[^>]*android:name="com\.monofocus\.app\.MainActivity"[^>]*>.*<action\s+android:name="android\.app\.action\.AUTOMATIC_ZEN_RULE"\s*/>.*<category\s+android:name="android\.intent\.category\.DEFAULT"\s*/>') {
    Fail "MainActivity is missing the Automatic Zen Rule configuration intent filter."
}
if ($releaseManifest -notmatch '(?s)<activity\b[^>]*android:name="com\.monofocus\.app\.MainActivity"[^>]*>.*<meta-data\s+android:name="android\.service\.zen\.automatic\.ruleType"\s+android:value="@string/automatic_rule_type"\s*/>') {
    Fail "MainActivity is missing Automatic Zen Rule type metadata."
}
if ($releaseManifest -notmatch '(?s)<activity\b[^>]*android:name="com\.monofocus\.app\.MainActivity"[^>]*>.*<meta-data\s+android:name="android\.service\.zen\.automatic\.ruleInstanceLimit"\s+android:value="1"\s*/>') {
    Fail "MainActivity is missing Automatic Zen Rule instance limit metadata."
}
if ($releaseManifest -notmatch '(?s)<service\b[^>]*android:name="com\.monofocus\.app\.service\.FocusMonitorService"[^>]*android:exported="false"[^>]*android:foregroundServiceType="specialUse"') {
    Fail "FocusMonitorService is missing exported=false or specialUse foreground service type in the release manifest."
}
if ($releaseManifest -notmatch 'android\.permission\.FOREGROUND_SERVICE_SPECIAL_USE') {
    Fail "Release manifest is missing FOREGROUND_SERVICE_SPECIAL_USE permission."
}
if ($releaseManifest -notmatch '(?s)<property\b[^>]*android:name="android\.app\.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"[^>]*android:value="@string/foreground_service_special_use"') {
    Fail "FocusMonitorService is missing the specialUse foreground service subtype property."
}

$dataExtractionRules = Get-Content -LiteralPath $dataExtractionRulesPath -Raw
if ($dataExtractionRules -notmatch '(?s)<cloud-backup\b[^>]*>.*<exclude\s+domain="root"\s+path="\."\s*/>.*</cloud-backup>') {
    Fail "Data extraction rules do not exclude app-private state from cloud backup."
}
if ($dataExtractionRules -notmatch '(?s)<device-transfer\b[^>]*>.*<exclude\s+domain="root"\s+path="\."\s*/>.*</device-transfer>') {
    Fail "Data extraction rules do not exclude app-private state from device transfer."
}

$strings = Get-Content -LiteralPath $stringsPath -Raw
if ($strings -notmatch '<string\s+name="automatic_rule_type">Selected app grayscale</string>') {
    Fail "Automatic Zen Rule type string is missing or unexpected."
}
if ($strings -notmatch '<string\s+name="foreground_service_special_use">Watches the current foreground package while the user-enabled grayscale engine is active\.</string>') {
    Fail "Foreground service special-use subtype string is missing or unexpected."
}
if ($strings -notmatch '<string\s+name="monitor_notification_text">MonoFocus is watching selected apps</string>') {
    Fail "Foreground monitoring notification text is missing or unexpected."
}
Require-ContentContains $strings '<string name="monitor_notification_paused_until">MonoFocus is paused until %1$s</string>' "Foreground monitoring notification paused text"
Require-ContentContains $strings '<string name="monitor_notification_pause_15_minutes">Pause 15 min</string>' "Foreground monitoring notification pause action"
Require-ContentContains $strings '<string name="monitor_notification_pause_until_tomorrow">Until tomorrow</string>' "Foreground monitoring notification pause action"
Require-ContentContains $strings '<string name="monitor_notification_resume">Resume</string>' "Foreground monitoring notification resume action"

Write-Step "checking Gradle release configuration"
$appBuildGradle = Get-Content -LiteralPath $appBuildGradlePath -Raw
if ($appBuildGradle -notmatch 'namespace\s*=\s*"com\.monofocus\.app"') {
    Fail "Gradle namespace is not com.monofocus.app."
}
if ($appBuildGradle -notmatch 'applicationId\s*=\s*"com\.monofocus\.app"') {
    Fail "Gradle applicationId is not com.monofocus.app."
}
if ($appBuildGradle -notmatch 'compileSdk\s*=\s*35') {
    Fail "Gradle compileSdk is not 35."
}
if ($appBuildGradle -notmatch 'minSdk\s*=\s*35') {
    Fail "Gradle minSdk is not 35."
}
if ($appBuildGradle -notmatch 'targetSdk\s*=\s*35') {
    Fail "Gradle targetSdk is not 35."
}
if ($appBuildGradle -notmatch 'versionCode\s*=\s*1') {
    Fail "Gradle versionCode is not 1."
}
if ($appBuildGradle -notmatch 'versionName\s*=\s*"0\.1\.0"') {
    Fail "Gradle versionName is not 0.1.0."
}
if ($appBuildGradle -notmatch '(?s)release\s*\{.*isMinifyEnabled\s*=\s*true.*\}') {
    Fail "Release build type does not enable R8/minification."
}

Write-Step "checking required user-facing copy"
$uiSource = Get-Content -LiteralPath $uiSourcePath -Raw
$requiredUiCopy = @(
    "MonoFocus",
    "Make distracting apps grayscale while you use them.",
    "Grayscale engine",
    "Search apps",
    "No launchable apps found.",
    "Select apps that should appear in grayscale.",
    "Setup required before grayscale can work.",
    "Usage Access required",
    "MonoFocus needs Usage Access to know which app is currently open. It does not read app content, messages, text, images, or browsing activity.",
    "Open Usage Access Settings",
    "Modes permission required",
    "Android uses Modes / Do Not Disturb access to allow apps to apply grayscale display effects. MonoFocus will not silence notifications.",
    "Open Modes Permission Settings",
    "MonoFocus works locally on your device.",
    "It stores only the package names of apps you select.",
    "It uses Usage Access only to detect which app is currently open.",
    "It does not read app content, messages, notifications, text, images, or browsing activity.",
    "It does not send data to any server.",
    "When a selected app is active, Android may apply grayscale to the whole display, including split-screen, picture-in-picture, and overlays.",
    "Android 15 or newer required",
    "MonoFocus uses Android's public grayscale device effect API. This feature is available only on Android 15 or newer."
)
foreach ($copy in $requiredUiCopy) {
    Require-ContentContains $uiSource $copy "MonoFocus UI source"
}

Write-Step "checking v1 UI scope"
$forbiddenUiComponentTokens = @(
    "NavigationBar(",
    "NavigationRail(",
    "TabRow(",
    "ModalNavigationDrawer(",
    "BottomAppBar("
)
$uiComponentMatches = @(Select-PolicyMatches -Path @($uiSourcePath) -Pattern $forbiddenUiComponentTokens -SimpleMatch)
if ($uiComponentMatches.Count -gt 0) {
    Fail "Out-of-scope navigation component found in v1 UI: $($uiComponentMatches[0].Path):$($uiComponentMatches[0].LineNumber): $($uiComponentMatches[0].Line.Trim())"
}

$uiCopyFiles = @($uiSourcePath, $stringsPath)
$forbiddenUiCopyPatterns = @(
    "(?i)\busage statistics\b",
    "(?i)\banalytics dashboard\b",
    "(?i)\bweb dashboard\b",
    "(?i)\bblocking\b",
    "(?i)\btimers?\b",
    "(?i)\bschedules?\b",
    "(?i)\bfocus sessions?\b",
    "(?i)\bstreaks?\b",
    "(?i)\bachievements?\b",
    "(?i)\bgamification\b",
    "(?i)\bsocial sharing\b",
    "(?i)\baccounts?\b",
    "(?i)\bcloud sync\b",
    "(?i)\brecommendations?\b",
    "(?i)\bAI-generated\b",
    "(?i)\bdopamine detox\b"
)
$uiCopyScopeMatches = @(Select-PolicyMatches -Path $uiCopyFiles -Pattern $forbiddenUiCopyPatterns)
if ($uiCopyScopeMatches.Count -gt 0) {
    Fail "Out-of-scope v1 product copy found in app UI/resources: $($uiCopyScopeMatches[0].Path):$($uiCopyScopeMatches[0].LineNumber): $($uiCopyScopeMatches[0].Line.Trim())"
}

$privacyPolicy = Get-Content -LiteralPath $privacyPolicyPath -Raw
$requiredPrivacyPolicyCopy = @(
    "MonoFocus works locally on your device.",
    "MonoFocus stores only the package names of apps you select.",
    "MonoFocus uses Android Usage Access only to detect which app is currently open.",
    "MonoFocus does not read notifications, messages, text, images, browser history, screen content, or files from other apps.",
    "MonoFocus does not transmit data to any server.",
    "MonoFocus does not use analytics, advertising SDKs, remote configuration, accounts, cloud sync, or crash-reporting SDKs.",
    "MonoFocus does not sell or share user data.",
    "MonoFocus requires Android 15 or newer because it uses Android's public grayscale device effect API."
)
foreach ($copy in $requiredPrivacyPolicyCopy) {
    Require-ContentContains $privacyPolicy $copy "Privacy policy"
}

Write-Step "checking Play Store and Data Safety drafts"
$playStoreDraft = Get-Content -LiteralPath $playStoreDraftPath -Raw
$requiredPlayStoreCopy = @(
    "Make distracting apps grayscale while you use them.",
    "MonoFocus is a minimal Android utility that helps reduce compulsive app use by making selected apps appear in grayscale while they are open.",
    "MonoFocus does not block apps, show streaks, collect analytics, use ads, or send your data anywhere.",
    "Requirements: Android 15 or newer. Usage Access and Modes / Do Not Disturb access are required for the core feature.",
    "- Data collected: none by MonoFocus.",
    "- Data shared: none by MonoFocus.",
    '- Network access: none. The app does not declare `INTERNET`.',
    "- Analytics and ads: none.",
    '- Foreground service type: `specialUse`.',
    '- Special-use subtype string: "Watches the current foreground package while the user-enabled grayscale engine is active."',
    '- User-visible notification text: "MonoFocus is watching selected apps".',
    "- User control: the foreground notification includes pause actions for 15 minutes and until tomorrow; pausing deactivates grayscale temporarily without turning the engine off.",
    '- Current build config: `minSdk = 35`, `compileSdk = 35`, `targetSdk = 35`.',
    "- Setup required state.",
    "- App list.",
    "- Selected apps.",
    "- Engine enabled state."
)
foreach ($copy in $requiredPlayStoreCopy) {
    Require-ContentContains $playStoreDraft $copy "Play Store draft"
}

$dataSafetyDraft = Get-Content -LiteralPath $dataSafetyDraftPath -Raw
$requiredDataSafetyCopy = @(
    "- Data collected by MonoFocus: none.",
    "- Data shared by MonoFocus: none.",
    "- Data processed off device: none.",
    '- Network access: none. The app does not declare `android.permission.INTERNET`.',
    "- Analytics, ads, crash-reporting SDKs, remote config, accounts, and cloud sync: none.",
    "MonoFocus stores the following values only in private app storage:",
    "- Selected package names.",
    "- Engine-enabled preference.",
    "- MonoFocus automatic zen rule ID.",
    "These values are not transmitted, sold, shared, or used for advertising or analytics.",
    "- Usage Access: used only to infer the current foreground package. MonoFocus does not read app content, text, images, messages, files, browsing history, or screen contents.",
    "- Notifications permission: used only to show the persistent foreground-service notification and pause actions while the user-enabled monitoring engine is active.",
    '- Modes / Do Not Disturb access: used only to manage MonoFocus''s own automatic rule for Android''s grayscale display effect. The rule uses interruption filter `ALL` so MonoFocus does not silence notifications.',
    "- Does the app collect or share any required user data types? No.",
    "- Is all user data encrypted in transit? Not applicable; MonoFocus does not transmit user data.",
    '- Confirm `PRIVACY.md`, Play Store listing text, and Play Console Data Safety answers all say no data leaves the device.'
)
foreach ($copy in $requiredDataSafetyCopy) {
    Require-ContentContains $dataSafetyDraft $copy "Data Safety draft"
}

Write-Step "checking manual validation template"
$manualTestResults = Get-Content -LiteralPath $manualTestResultsPath -Raw
$requiredManualTestCopy = @(
    "Do not mark an item passed without the device, OS version, app version, build artifact, date, and tester recorded.",
    'Artifact: `app/build/outputs/bundle/release/app-release.aab`',
    'Verifier command: `.\tools\verify-release.ps1`',
    "Pixel device or emulator | Android 15 / API 35",
    "Pixel device or emulator | Android 16, if available",
    "Samsung device | Android 15+",
    "Android 14 device or emulator | API 34",
    "Fresh install with missing accesses",
    "Return from Usage Access settings",
    "Return from Modes / Do Not Disturb settings",
    "Launchable app list",
    "Selection persistence",
    "Selected app activation",
    "Unselected app deactivation",
    "Home/recents deactivation",
    "Notification delivery",
    "Usage Access revoked",
    "Modes access revoked",
    "Notifications permission revoked",
    "MonoFocus rule deleted",
    "Duplicate rule cleanup",
    "Shutdown or power off while active",
    "Foreground notification pause actions",
    "Reboot before app launch",
    "App launch after reboot",
    "Selected app uninstall",
    "Selected app reinstall",
    "Android 14 release install",
    "TalkBack labels",
    "Dynamic font size",
    "Contrast and touch targets",
    "Activation selected app -> grayscale",
    "Deactivation selected app -> home/unselected app",
    "Privacy policy URL is hosted and reachable",
    "Store listing states Android 15 or newer",
    "Store listing states local-only/no analytics/no ads",
    'Data Safety answers match `docs/data-safety.md`',
    'Foreground service declaration matches `docs/play-store.md`',
    "Setup, app list, selected apps, and enabled-engine screenshots uploaded",
    "Internal testing release installs on supported device",
    "Closed testing release, if account requires it"
)
foreach ($copy in $requiredManualTestCopy) {
    Require-ContentContains $manualTestResults $copy "Manual validation template"
}

Write-Step "checking Play screenshot draft artifacts"
$playScreenshotDir = Join-Path $repoRoot "docs/play-screenshots"
$requiredPlayScreenshots = @(
    [pscustomobject]@{
        FileName = "01-setup-required.png"
        Description = "Setup required"
    },
    [pscustomobject]@{
        FileName = "02-app-list-ready.png"
        Description = "Ready app list"
    },
    [pscustomobject]@{
        FileName = "03-selected-apps.png"
        Description = "Selected apps"
    },
    [pscustomobject]@{
        FileName = "04-engine-enabled.png"
        Description = "Enabled engine"
    }
)
foreach ($screenshot in $requiredPlayScreenshots) {
    Require-PngScreenshot (Join-Path $playScreenshotDir $screenshot.FileName) $screenshot.Description
    Require-ContentContains $manualTestResults "docs/play-screenshots/$($screenshot.FileName)" "Manual validation template"
}

Write-Step "checking launcher icon resources"
foreach ($adaptiveIconPath in @($launcherIconPath, $roundLauncherIconPath)) {
    $adaptiveIcon = Get-Content -LiteralPath $adaptiveIconPath -Raw
    if ($adaptiveIcon -notmatch '<adaptive-icon\b') {
        Fail "Launcher icon is not an adaptive icon: $adaptiveIconPath"
    }
    if ($adaptiveIcon -notmatch '(?s)<foreground\b[^>]*android:drawable="@drawable/ic_launcher_foreground"') {
        Fail "Adaptive icon does not reference ic_launcher_foreground: $adaptiveIconPath"
    }
    if ($adaptiveIcon -notmatch '(?s)<monochrome\b[^>]*android:drawable="@drawable/ic_launcher_monochrome"') {
        Fail "Adaptive icon does not reference ic_launcher_monochrome: $adaptiveIconPath"
    }
}

foreach ($vectorPath in @($launcherForegroundPath, $launcherMonochromePath)) {
    $vector = Get-Content -LiteralPath $vectorPath -Raw
    if ($vector -notmatch '<vector\b') {
        Fail "Launcher drawable is not a vector resource: $vectorPath"
    }
}

Write-Step "checking source policy"
$sourceFiles = @(
    Get-ChildItem -LiteralPath (Join-Path $repoRoot "app/src") -Recurse -File |
        Where-Object { $_.Extension -in @(".kt", ".xml", ".kts", ".properties", ".pro") } |
        ForEach-Object { $_.FullName }
)
$sourceFiles += @(
    Join-Path $repoRoot "app/build.gradle.kts"
    Join-Path $repoRoot "build.gradle.kts"
    Join-Path $repoRoot "settings.gradle.kts"
    Join-Path $repoRoot "gradle.properties"
) | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf }

$forbiddenSourceTokens = @(
    "android.permission.INTERNET",
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.WRITE_SECURE_SETTINGS",
    "android.permission.BIND_ACCESSIBILITY_SERVICE",
    "android.permission.BIND_VPN_SERVICE",
    "android.permission.WAKE_LOCK",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.CAPTURE_VIDEO_OUTPUT",
    "android.permission.MEDIA_CONTENT_CONTROL",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "AccessibilityService",
    "android.accessibilityservice",
    "android.webkit.WebView",
    "WebView",
    "android.media.projection",
    "MediaProjection",
    "MediaProjectionManager",
    "android.app.admin.DevicePolicyManager",
    "android.net.VpnService",
    "androidx.work",
    "WorkManager",
    "JobScheduler",
    "JobService",
    "AlarmManager",
    "SCHEDULE_EXACT_ALARM",
    "USE_EXACT_ALARM",
    "CountDownTimer",
    "AccountManager",
    "BillingClient",
    "FirebaseRemoteConfig",
    "RemoteConfig",
    "DownloadManager",
    "android.os.PowerManager.WakeLock",
    "PowerManager.WakeLock",
    "newWakeLock",
    "FLAG_KEEP_SCREEN_ON",
    "setKeepScreenOn",
    "keepScreenOn",
    "TYPE_APPLICATION_OVERLAY",
    "ACTION_MANAGE_OVERLAY_PERMISSION",
    "canDrawOverlays",
    "getExternalFilesDir",
    "getExternalCacheDir",
    "Environment.getExternalStorageDirectory",
    "Environment.getExternalStoragePublicDirectory",
    "java.net.HttpURLConnection",
    "java.net.URLConnection",
    "java.net.Socket",
    "HttpURLConnection",
    "URLConnection",
    "Shizuku",
    "Runtime.getRuntime",
    "ProcessBuilder",
    "Class.forName",
    "getDeclaredMethod",
    "getDeclaredField",
    "setAccessible",
    "Settings.Global",
    "Settings.Secure",
    "Settings.System",
    "LOCKED_BOOT_COMPLETED",
    "BootReceiver",
    "android:usesCleartextTraffic=`"true`"",
    "android:requestLegacyExternalStorage=`"true`"",
    "android:debuggable=`"true`""
)

$sourcePolicyMatches = @(Select-PolicyMatches -Path $sourceFiles -Pattern $forbiddenSourceTokens -SimpleMatch)
if ($sourcePolicyMatches.Count -gt 0) {
    Fail "Forbidden source token found: $($sourcePolicyMatches[0].Path):$($sourcePolicyMatches[0].LineNumber): $($sourcePolicyMatches[0].Line.Trim())"
}

$bootCompletedSourceMatches = @(
    Select-PolicyMatches -Path $sourceFiles -Pattern @("BOOT_COMPLETED") -SimpleMatch |
        Where-Object {
            $_.Path -notmatch '\\app\\src\\main\\AndroidManifest\.xml$' -and
            $_.Path -notmatch '\\app\\src\\main\\java\\com\\monofocus\\app\\platform\\BootCleanupReceiver\.kt$' -and
            $_.Path -notmatch '\\app\\src\\test\\java\\com\\monofocus\\app\\platform\\BootCleanupReceiverTest\.kt$'
        }
)
if ($bootCompletedSourceMatches.Count -gt 0) {
    Fail "BOOT_COMPLETED may only be used by the cleanup receiver: $($bootCompletedSourceMatches[0].Path):$($bootCompletedSourceMatches[0].LineNumber): $($bootCompletedSourceMatches[0].Line.Trim())"
}

$mainKotlinFiles = @(
    Get-ChildItem -LiteralPath (Join-Path $repoRoot "app/src/main/java") -Recurse -Filter "*.kt" -File |
        ForEach-Object { $_.FullName }
)
$loggingMatches = @(Select-PolicyMatches -Path $mainKotlinFiles -Pattern @("Log\.", "println\(", "Timber", "System\.out", "printStackTrace"))
if ($loggingMatches.Count -gt 0) {
    Fail "Release-source logging call found: $($loggingMatches[0].Path):$($loggingMatches[0].LineNumber): $($loggingMatches[0].Line.Trim())"
}

Write-Step "checking dependency policy"
$forbiddenDependencyPatterns = @(
    "analytics",
    "firebase",
    "crashlytics",
    "sentry",
    "appcenter",
    "retrofit",
    "okhttp",
    "ktor",
    "admob",
    "play-services-ads",
    "room-runtime",
    "hilt-android"
)
$dependencyMatches = @(Select-PolicyMatches -Path @($dependencyMetadataPath, (Join-Path $repoRoot "app/build.gradle.kts"), (Join-Path $repoRoot "build.gradle.kts"), (Join-Path $repoRoot "settings.gradle.kts")) -Pattern $forbiddenDependencyPatterns)
if ($dependencyMatches.Count -gt 0) {
    Fail "Forbidden dependency marker found: $($dependencyMatches[0].Path):$($dependencyMatches[0].LineNumber): $($dependencyMatches[0].Line.Trim())"
}

Write-Step "verified release APK: $apkPath"
Write-Step "verified release AAB: $aabPath"
Write-Step "PASS"
