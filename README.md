# Phone Fade

Personal Android automatic grayscale application. Kotlin, Jetpack Compose, Material 3,
Gradle Kotlin DSL; application ID `app.ndplabs.phonefade`. Android 10+ (API 29),
compile/target API 36, initially intended for Pixel 9 Pro. Automatic monitoring
counts accumulated unlocked interactive use, applying grayscale when the color
allowance expires. Short breaks preserve the session. Manual controls and exact
restoration remain available. No Accessibility Service, Usage Access, root,
internet permission, analytics, or external service is used.

## Automatic monitoring

Grant the secure-settings permission below, choose settings, tap **Save settings**,
then **Enable automatic monitoring**. Defaults: 10 minutes of color, 5 minutes for
a reset. Both inputs accept whole minutes from 1 to 120. Notification permission
is requested on Android 13+; denying it still permits monitoring, but the service
notice appears in Android's Active apps list rather than the notification drawer.
Blocked notifications/channels are also reported in the app on resume.

Only interactive use with keyguard absent consumes allowance. Screen-off starts a
continuous non-interactive break, including **display timeout**. Screen-on ends
that break even if the lock screen remains visible. A break of at least the reset
interval earns a reset, applied when unlocked use resumes. Separate short breaks
never add up. Grayscale remains applied during short breaks and a qualifying long
break; original settings are restored at the next unlocked use. No exact wakeup
at the reset instant is required. These are interactive-state proxies, not a
complete keyguard history; always-on display and unusual device lock behavior can
differ from the physical display's power state.

For a 5-minute allowance: use 3 minutes, turn off for 2 minutes, then resume with
2 minutes left. After exhaustion, a 4-minute break preserves grayscale; a continuous
5-minute break earns fresh color time at the next unlock.

Settings never clear consumed usage. Reducing allowance can exhaust it immediately;
increasing it gives a still-color session only the difference. Exhaustion stays
latched despite allowance increases until a qualifying break or disable/re-enable.
A changed reset interval applies to the current break; an already earned reset
remains earned. Manual activation is disabled during automatic monitoring.
**Disable and restore original settings** cancels monitoring and resets the session.
If restoration fails, the app retains the snapshot and displays a recovery path.

## Architecture and lifecycle

`SessionEngine` is a pure Kotlin reducer with explicit events, separate session and
device-activity state, monotonic timestamps, and generation-checked deadlines.
`PhoneFadeApplication` owns one `PhoneFadeCoordinator`, one controller, and one
recovery store. A single command queue serializes UI commands, service observations,
timer callbacks, and boot recovery off the main thread; synchronized coordinator
methods also protect direct callers. Every display write goes through the existing
`GrayscaleController`.

The coordinator commits one versioned monitoring checkpoint to separate private
preferences before effects, then records their verified result. The original
display snapshot remains in `grayscale_recovery`; a session checkpoint never
replaces it. Commit failures stop automatic changes and retain display recovery.
The two journals are not a single transaction: recovery reconciles pending effects
against actual settings before any new mutation. An applied owned transform is
recognized without overwriting its snapshot. Partial operations and external
conflicts retain a manual recovery path.

The user-started foreground service dynamically registers SCREEN_OFF, SCREEN_ON,
and USER_PRESENT, observes the two display-setting URIs, and schedules the next
deadline. At most one timer is active; minute checkpoints update confirmed usage
and notification presentation without one-second polling. Delayed callbacks use
elapsedRealtime rather than granting additional logical allowance. Actual grayscale
application can still be delayed by Android scheduling. There are no exact alarms,
background reset alarms, WorkManager jobs, or wake locks.

Permissions added in PR 2: FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE,
POST_NOTIFICATIONS, and RECEIVE_BOOT_COMPLETED. The non-exported `specialUse`
service includes its subtype justification. Start it from the visible app;
background-start exceptions are not assumed. START_STICKY permits system recovery,
not uninterrupted execution or a guaranteed restart time. Google Play reviews
specialUse declarations separately. See Android's [service types](https://developer.android.com/develop/background-work/services/fgs/service-types),
[background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start),
and [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

Ordinary process recovery preserves **confirmed accumulated usage** and resumes at
a new reliable observation. Unobserved downtime is never charged as unlocked use,
and a break spanning that gap is never inferred. Stale screen boundaries that
disagree with current interactive state also trigger conservative reconciliation,
rather than joining separate breaks. The last uncheckpointed active
segment (normally less than a minute) can be lost; longer scheduling delays can
increase undercounting. The app/notification marks recovery as interrupted.
Reopening and explicitly enabling an interrupted, compatible session resumes its
confirmed usage; disabling first deliberately starts clean next time.

Confirmed user-requested stops, when available through ApplicationExitInfo on API
30+, disable the saved session and require explicit re-enabling. API 29 cannot
identify that exit reason. Force-stop and Android's Active apps Stop can terminate
the process without callbacks; grayscale cannot be restored at that instant.
Reopen the app to restore. A dismissed activity task is not a request to disable
monitoring. See [user-initiated stopping](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping).

BOOT_COUNT invalidates old monotonic anchors. A minimal BOOT_COMPLETED receiver
tries exact restoration and clears the old session **without restarting monitoring**.
The journal is credential-encrypted, so restoration occurs after first unlock,
not before it. No direct-boot journal migration is introduced. Delivery can be
delayed or suppressed (including force-stopped apps); revoked permission, corrupt
storage, or external color-correction changes can prevent restoration. Opening
the app also reconciles reboot/unknown boot identity and offers manual recovery.

## Build and test

Install JDK 17, Android SDK Platform 36, Build Tools 35.0.0 and Platform Tools.
Accept SDK licenses through Android Studio or `sdkmanager --licenses`.
Set `ANDROID_HOME` or create an ignored `local.properties`:

```properties
sdk.dir=C\:/path/to/Android/Sdk
```

From PowerShell (use `./gradlew` on macOS/Linux):

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest testReleaseUnitTest check lintDebug lintRelease --no-watch-fs --console=plain
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

1. Keep the app installed and its data intact. Process death or force-stop may leave
   grayscale applied. Reboot recovery is best effort after first unlock as described
   above. Reopen the app and tap **Restore original settings** if needed.
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
owns the settings. While monitoring, a settings observer detects recognizable
changes and reports attention rather than repeatedly overwriting them.

## PR 1 reference checklist

The user reports seven PR 1 acceptance tests completed successfully on Pixel 9 Pro.
See VALIDATION.md for the evidence distinction. The expanded reference list below
is not a claim that every listed subcase was executed.

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
  commands and Restore work. For PR 2 reboot expectations, use the checklist below.
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

## PR 2 Pixel 9 Pro acceptance checklist (pending physical execution)

Record Android build, allowance/reset values, timestamps, original key presence and
values, observed notification states, and physical color/grayscale results. Inspect
the physical screen: screenshots need not capture the display transform.

- [ ] Set both intervals to 1 minute; verify automatic grayscale after unlocked use.
- [ ] Use 20 seconds, turn off for 20 seconds, unlock: approximately 40 seconds remain.
- [ ] Exhaust allowance; verify a short break retains grayscale without new snapshot.
- [ ] Take a continuous break of at least 1 minute; unlock: exact original settings
  return and a full new allowance starts. Test the exact boundary with automated
  tests; device timing needs tolerance for broadcast receipt.
- [ ] Repeat several shorter breaks; they must never combine into a reset.
- [ ] Wake to the lock screen before the reset interval, wait there, then unlock:
  the awake locked time must not extend the break. Test display timeout and AOD.
- [ ] Change allowance/reset during use/break; verify documented preservation and
  exhaustion latching. Duplicate/rapid enable, disable, lock, and unlock commands
  must not replace the snapshot or re-enable after disabling.
- [ ] Disable while grayscale: verify exact values/absence and color. Re-enable:
  a new full allowance begins. Test missing/revoked secure-settings permission.
- [ ] Start with Android correction already active: activation must refuse it.
  Change correction externally during monitoring: attention must appear and the
  unfamiliar configuration must remain protected on attempted restoration.
- [ ] Reopen/rotate/dismiss the activity while monitoring; the service continues.
  Test ordinary process termination separately from force-stop: a debug device
  can send SIGKILL to the app PID using `adb shell run-as app.ndplabs.phonefade kill -9 PID`.
  Confirm sticky recovery preserves confirmed usage/owned grayscale and reports
  interruption. A restart is not guaranteed; if absent, reopen and enable to resume.
- [ ] Test `adb shell am force-stop app.ndplabs.phonefade` and
  `adb shell cmd activity stop-app app.ndplabs.phonefade` separately. Reopen:
  no automatic monitoring after a confirmed user stop; original settings restore
  if possible. Do not use force-stop as proof of ordinary process recovery.
- [ ] Reboot while grayscale; after first unlock verify restoration and monitoring
  disabled. Repeat force-stopped/revoked-permission cases; verify retained manual
  recovery when boot delivery/restoration is unavailable.
- [ ] Test notification permission allow/deny and channel blocking; verify reduced
  visibility messaging, accurate paused/grayscale/attention state, and tap-to-app.
- [ ] Exercise repeated sessions during ordinary usage; confirm no unexpected
  grayscale ownership or lost snapshot. Check portrait/landscape, large font,
  screen reader, light/dark appearance, and scrolling to all recovery controls.

Temporary-color overrides, exclusions, analytics, tiles, widgets, daily budgets,
schedules, cloud sync, and automatic monitoring at boot remain out of scope.
