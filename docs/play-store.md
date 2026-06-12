# Play Store Draft

## Short Description

Make distracting apps grayscale while you use them.

## Full Description

MonoFocus is a minimal Android utility that helps reduce compulsive app use by making selected apps appear in grayscale while they are open.

Select the apps you want to make less stimulating. When one of them is active, MonoFocus applies Android's grayscale display effect. When you leave the app, color returns automatically.

MonoFocus does not block apps, show streaks, collect analytics, use ads, or send your data anywhere.

Requirements: Android 15 or newer. Usage Access and Modes / Do Not Disturb access are required for the core feature.

## Data Safety Notes

- Data collected: none by MonoFocus.
- Data shared: none by MonoFocus.
- Local app data: selected package names are stored locally in private app storage.
- Network access: none. The app does not declare `INTERNET`.
- Analytics and ads: none.
- Notifications permission: used only to show the persistent monitoring notification and pause actions while the user-enabled engine is active.
- Boot completed permission: used only to deactivate a stale app-owned grayscale rule after reboot; it does not start monitoring.
- Detailed worksheet: `docs/data-safety.md`.

## Foreground Service Declaration Notes

- Foreground service type: `specialUse`.
- Special-use subtype string: "Watches the current foreground package while the user-enabled grayscale engine is active."
- User-visible notification text: "MonoFocus is watching selected apps".
- User control: the foreground notification includes pause actions for 15 minutes and until tomorrow; pausing deactivates grayscale temporarily without turning the engine off.
- Service scope: runs only while the user-enabled grayscale engine is active and local preflight checks pass.
- Network behavior: no network operations; the app does not declare `INTERNET`.
- Purpose: required so MonoFocus can keep detecting the current foreground app while the user opens other apps and can apply or remove the app-owned grayscale rule promptly.

## Target API Evidence

- Current build config: `minSdk = 35`, `compileSdk = 35`, `targetSdk = 35`.
- Current official Play target API check, verified on 2026-06-12: Android Developers states that starting August 31, 2025, new apps and app updates must target Android 15 / API 35 or higher for Google Play submission. Source: https://developer.android.com/google/play/requirements/target-sdk

## Screenshot Checklist

- Setup required state.
- App list.
- Selected apps.
- Engine enabled state.
