# Manual Test Plan

Record device-specific results in `docs/manual-test-results.md`.

Minimum devices:

- Pixel device or emulator, Android 15 / API 35.
- Pixel device or emulator, Android 16 if available.
- Samsung device, Android 15+.

Unsupported-state verification:

- Android 14 emulator or device, if using a compatibility install path. The release build uses `minSdk 35`, so normal installs on Android 14 should be rejected by the platform.

Scenarios:

1. Fresh install.
2. Open the app with no special accesses granted.
3. Grant Usage Access.
4. Grant Notifications permission.
5. Grant Modes / Do Not Disturb access.
6. Confirm launchable apps appear and MonoFocus is absent from the list.
7. Select one social/media app.
8. Enable the grayscale engine.
9. Confirm the persistent monitoring notification appears with pause actions for 15 minutes and until tomorrow.
10. Open the selected app and confirm grayscale within 1.5 seconds.
11. Open an unselected app and confirm color returns within 1.5 seconds.
12. Return home and confirm color returns.
13. Lock and unlock the device.
14. Revoke Usage Access while the engine is enabled.
15. Revoke Notifications permission while the engine is enabled.
16. Revoke Modes / Do Not Disturb access while the engine is enabled.
17. Delete the MonoFocus Grayscale rule from system settings and restart the engine.
18. Uninstall a selected app and confirm it is not shown in the active list; if it was the only selected launchable app, confirm the engine stops.
19. Reinstall that app and confirm it appears selected again.
20. Pause from the foreground notification for 15 minutes and verify grayscale returns automatically after the pause expires.
21. Pause from the foreground notification until tomorrow and verify grayscale stays inactive until the next local day.
21. Force-stop MonoFocus and confirm grayscale is not left active.
22. Restart or power off with grayscale active and confirm the device is not left grayscale.
23. Reboot the device and confirm MonoFocus does not start monitoring silently before the app is launched.
24. Launch MonoFocus after reboot and confirm the persisted engine state is restored only if permissions are still granted and a selected launchable app exists.

Visual timing evidence:

- Confirm grayscale/color by direct observation, external camera/video, or another method that captures the final display output after Android device effects are applied.
- Do not use `adb screencap` alone as visual grayscale proof; on the Android 15 debug emulator it can show color app screenshots even while the MonoFocus rule is `STATE_TRUE`.
