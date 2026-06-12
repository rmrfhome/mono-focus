# Google Play Data Safety Draft

This draft reflects the current MonoFocus v1 implementation and should be rechecked in Play Console immediately before submission.

## Summary

- Data collected by MonoFocus: none.
- Data shared by MonoFocus: none.
- Data processed off device: none.
- Network access: none. The app does not declare `android.permission.INTERNET`.
- Analytics, ads, crash-reporting SDKs, remote config, accounts, and cloud sync: none.

## Local-Only App Data

MonoFocus stores the following values only in private app storage:

- Selected package names.
- Engine-enabled preference.
- MonoFocus automatic zen rule ID.
- Onboarding/supporting settings such as last known supported API.

These values are not transmitted, sold, shared, or used for advertising or analytics.

## Android Accesses

- Usage Access: used only to infer the current foreground package. MonoFocus does not read app content, text, images, messages, files, browsing history, or screen contents.
- Notifications permission: used only to show the persistent foreground-service notification and Stop action while the user-enabled monitoring engine is active.
- Modes / Do Not Disturb access: used only to manage MonoFocus's own automatic rule for Android's grayscale display effect. The rule uses interruption filter `ALL` so MonoFocus does not silence notifications.
- Boot completed permission: used only to deactivate MonoFocus's own grayscale rule after reboot. It does not start foreground monitoring or collect data.

## Play Console Form Guidance

Recommended answers for the current implementation:

- Does the app collect or share any required user data types? No.
- Is all user data encrypted in transit? Not applicable; MonoFocus does not transmit user data.
- Can users request data deletion? Not applicable for server-side data; local data is removed when the app is uninstalled or app storage is cleared.
- Is the app committed to following the Play Families Policy? Only answer yes if the app is later submitted to a Families program or targets children. MonoFocus v1 is a general utility and does not include child-directed features.

## Consistency Checks Before Submission

- Run `.\tools\verify-release.ps1` from the repository root after creating the release APK/AAB.
- Confirm the verifier still passes its backup/export and manifest privacy checks.
- Confirm the release APK/AAB still does not declare `INTERNET`.
- Confirm release dependency metadata still has no analytics, ads, crash-reporting SDKs, or network SDKs.
- Confirm `PRIVACY.md`, Play Store listing text, and Play Console Data Safety answers all say no data leaves the device.
- Recheck this worksheet if any dependency or permission is added.
