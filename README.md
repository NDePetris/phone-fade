# Phone Fade

Personal Android grayscale capability spike. Kotlin, Jetpack Compose, Material 3,
Gradle Kotlin DSL; application ID `app.ndplabs.phonefade`. Android 10+ (API 29),
compile/target API 36, initially intended for Pixel 9 Pro. Only **Enable Grayscale**
and **Restore Color** are implemented. There is no timer, foreground service,
notification, boot receiver, Accessibility Service, root requirement, internet
permission, analytics, or external service.

## Build and test

Install JDK 17, Android SDK Platform 36, Build Tools 35.0.0 and Platform Tools.
Accept SDK licenses through Android Studio or `sdkmanager --licenses`.
Set `ANDROID_HOME` or create an ignored `local.properties`:

```properties
sdk.dir=C\:/path/to/Android/Sdk
```

From PowerShell (use `./gradlew` on macOS/Linux):

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --console=plain
.\gradlew.bat check --console=plain
git diff --check
```

Gradle 8.13, AGP 8.13.2, Kotlin/Compose compiler 2.2.21 and dependencies are pinned.
The official wrapper distribution is SHA-256 checked. Lint treats warnings as errors
except documented pinned-version/target notices, the intentional ADB development
permission, and synchronous preferences commits whose Boolean result must be checked.
API 37 is not targeted or validated by this spike. First build requires access
to Gradle, Google Maven and Maven Central; the installed app requires no network.
Unit tests use injected settings and recovery stores; they do not simulate actual
Android secure-settings provider or display hardware behavior.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Tests: `app/build/reports/tests/testDebugUnitTest/index.html`.
Lint: `app/build/reports/lint-results-debug.html`.

## Install and grant permission

Enable Developer options and USB debugging on your phone. Connect it to a trusted
computer and accept its debugging authorization prompt. Select the correct device
with `adb -s SERIAL` if multiple devices are connected.

```sh
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant app.ndplabs.phonefade android.permission.WRITE_SECURE_SETTINGS
adb shell am start -n app.ndplabs.phonefade/.MainActivity
```

The grant is a privileged development permission, not a runtime permission dialog.
Some Android builds or managed devices reject it. The app detects missing or revoked
permission and reports failures without requiring root or Accessibility access.
Return to the app after granting: permission and setting values refresh on resume.
These instructions assume the phone's primary Android user. With work profiles or
secondary users, explicitly target the app's user ID for install/grant and every
settings command; do not restore another user's settings.

## Preflight snapshot and operation

Before the first activation, keep these outputs somewhere outside the app:

```sh
adb shell am get-current-user
adb shell settings --user current get secure accessibility_display_daltonizer_enabled
adb shell settings --user current get secure accessibility_display_daltonizer
adb shell settings --user current list secure
```

Record the Android build (`adb shell getprop ro.build.fingerprint`) and whether each
key is absent in `list secure`. `get` prints `null` for an absent row; absence is
distinct from an explicit `0`. Retain the snapshot until restored.

`GrayscaleController` touches only `accessibility_display_daltonizer_enabled` and
`accessibility_display_daltonizer`. It refuses activation unless enabled is absent
or exactly `0`, and the mode is absent or an integer string. Any active correction,
including grayscale enabled by another tool, is a conflict.

Before writing, the controller synchronously commits the exact original strings
(including absent rows) to a private, versioned recovery record. It rechecks the
snapshot, writes monochromacy mode `0` then enabled `1`, and checks every write's
Boolean result and read-back. On failure it attempts rollback. A pending record
blocks another activation; **Restore Color** completes recovery first.

Restore disables the app's transform, restores the original mode, then restores
the original enabled value, deleting originally absent rows with a null provider
write. The record is cleared only after read-back matches. Errors retain recovery
information where storage remains available. No saved record means no writes.
The screen displays the original values and corresponding emergency ADB commands.

The two settings and mode `0` are hidden AOSP conventions, not a stable public
Android feature contract. See [AOSP daltonizer modes](https://android.googlesource.com/platform/frameworks/base/+/5028fb035794c207698e52b276c54de109dd5022/core/java/android/view/accessibility/AccessibilityManager.java)
and [secure-settings API](https://developer.android.com/reference/android/provider/Settings.Secure).
Read-back verifies provider values, **not the visual effect**. A device may ignore
values or reject writes/deletions. This must be tested on your exact Android build.

## Recovery after failure or interruption

1. Keep the app installed and its data intact. Force-stop, process death or reboot
   does not automatically restore color; the saved record survives and appears
   when the app is reopened. Tap **Restore Color**.
2. If permission was revoked, grant it again using the command above and retry.
3. If settings have changed outside Phone Fade, automatic restoration refuses
   unfamiliar combinations. Inspect the current values and decide whether to keep
   the newer accessibility configuration. Do not blindly overwrite it.
4. If app restoration is unavailable, use the saved screen commands or your
   preflight snapshot. Use the same Android user that ran the app. First temporarily
   disable the Phone Fade transform, restore the original mode, then restore the
   original enabled value. For example, **only if your original snapshot was
   enabled `0`, mode `12`**:

   ```sh
   adb shell settings --user current put secure accessibility_display_daltonizer_enabled 0
   adb shell settings --user current put secure accessibility_display_daltonizer 12
   adb shell settings --user current put secure accessibility_display_daltonizer_enabled 0
   ```

   For each originally absent key, use `delete` instead of `put` when restoring it:

   ```sh
   adb shell settings --user current delete secure accessibility_display_daltonizer
   adb shell settings --user current delete secure accessibility_display_daltonizer_enabled
   ```

5. Read both settings again and verify the display visually. Once the original
   values match, tap **Restore Color** to clear a pending record without new writes.
   If a record is corrupt, restore from the external snapshot before clearing data.
6. Restore **before** uninstalling, clearing app data, or revoking permission:

   ```sh
   adb shell pm revoke app.ndplabs.phonefade android.permission.WRITE_SECURE_SETTINGS
   ```

If the original record and external snapshot are both lost, exact restoration is
impossible. Disabling color correction through Android Settings can recover a color
display but cannot reconstruct the prior values. Reboot alone is not a recovery
procedure. App data backup is disabled to avoid replaying a snapshot on another device.

Cross-process settings writes are not atomic. The controller serializes its own
operations and rejects recognizable external changes, but cannot distinguish an
external write with exactly the same values as its own. Avoid changing Android
color correction or Developer options' simulated color space while Phone Fade
owns the settings. There is no background observer or automatic reconciliation.

## Pixel 9 Pro manual checklist (not yet device verified)

- [ ] Record Android version/build and the external preflight snapshot. Use a color
  photo or home screen as a visual reference; inspect the physical screen, since
  screenshots may not capture display transforms.
- [ ] Install without a grant. Confirm setup command is selectable, permission is
  shown as missing, Enable is disabled, and Restore does not change any settings.
- [ ] Grant through ADB, resume app, confirm permission refreshes. With correction
  off, tap Enable. Confirm mode `0` and enabled `1` through ADB and actual grayscale
  across apps. Confirm unrelated secure/accessibility values were not changed by
  Phone Fade (Android itself may update other settings).
- [ ] Restore. Compare both values and key presence with the preflight snapshot;
  confirm actual color returns. Repeat Restore; values must stay unchanged.
- [ ] Repeat at least five enable/restore cycles; rapidly tap Enable and rotate the
  phone during an operation. Confirm no snapshot replacement or overlapping action.
- [ ] Force-stop the app while grayscale is active, reopen, confirm saved recovery
  commands and Restore work. Repeat after reboot; no automatic restore is expected.
- [ ] Revoke permission while grayscale is active. Restore must report the missing
  grant and retain the record. Regrant, resume and restore successfully.
- [ ] Enable Android Accessibility color correction first. Phone Fade must refuse
  control, including if another tool already enabled monochromacy. Preserve that
  configuration. Turn it off yourself before testing Phone Fade again.
- [ ] While Phone Fade owns grayscale, change to an accessibility correction mode.
  Restore must report a conflict and preserve the new configuration. Inspect and
  follow the manual recovery procedure only when you intend to restore the snapshot.
- [ ] Exercise the displayed emergency commands after force-stop, verify exact
  values, then reopen and Restore to clear the journal. Restore before uninstalling.
- [ ] Check large font, screen reader labels, portrait/landscape and light/dark
  system appearance. Ensure setup and recovery text remain reachable by scrolling.

See [VALIDATION.md](VALIDATION.md) for checks performed in the development environment.
