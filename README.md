# Offramp Fade

Personal Android automatic grayscale application. Kotlin, Jetpack Compose, Material 3,
Gradle Kotlin DSL; application ID `app.ndplabs.phonefade`. Android 10+ (API 29),
compile/target API 36, initially intended for Pixel 9 Pro. Automatic monitoring
counts accumulated unlocked interactive use, applying grayscale when the color
allowance expires. Short breaks preserve the session. Manual controls and exact
restoration remain available. No Accessibility Service, Usage Access, root,
internet permission, analytics, or external service is used.

## Repository and validation status

PR 1 manual recovery and PR 2 automatic monitoring are merged into `main`. PR 2
was GitHub [PR #3](https://github.com/NDePetris/phone-fade/pull/3), merge `69b3fdb`.
Its author reported core Pixel 9 Pro activation, short-break preservation,
qualifying-wake restoration before unlock, unlock-only counting, and disable
restoration passing. The exact OS/build and individual logs were not supplied;
not every lifecycle/recovery subcase was reported physically validated. The
reference checklists below are not blanket pass claims. Historical automated
results remain in VALIDATION.md. The user reports PR 3 core temporary-color and
visual acceptance passing on Pixel 9 Pro; four recovery/safety scenarios remain
untested (see VALIDATION.md). The notification countdown follow-up still requires
its own physical confirmation.

## Temporary color and countdowns

When automatic grayscale is verified active, **Use temporary color** restores
exact original settings and grants the configured unlocked-use duration (default
5 minutes, independently configurable from 1–120). The override starts only after
verified restoration and a successful durable completion checkpoint. It leaves
the ordinary session exhausted and preserves its accumulated usage. Manual
grayscale and ordinary color do not offer this action; repeated taps cannot extend
or stack overrides. Changing duration affects the next override.

Short screen-off breaks and interactive lock-screen time pause override use. A
qualifying continuous screen-off break ends the override at wake and grants a
fresh ordinary allowance; its counting begins after unlock. No live break timer
runs. Otherwise override expiry reapplies grayscale through the same controller,
only if the exact restored display baseline still matches. If expiry is discovered
at a locked/off boundary, reapplication waits for unlock; a qualifying break takes
precedence. External changes or permission loss stop automatic effects and surface
attention. Disabling cancels the override and monitoring; if an external change
occurred after successful restoration, disabling does not overwrite that change.

Visible countdowns round whole minutes up: a new 2-minute allowance stays at
2 minutes until one minute remains. Below one minute, the visible app shows
`0:SS`, with `0:01` for positive subsecond time and `0:59` at the upper boundary.
Exhaustion shows verified grayscale, pending application/unlock, or attention;
never a zero/negative countdown. Seconds refresh only while the UI is resumed.
Notifications keep rounded-up, correctly pluralized minutes above one minute.
During the final minute of active unlocked use, Android's native notification
chronometer shows seconds in its standard template. The body labels it a
**scheduled countdown**, not proof of grayscale. The service publishes once at the
final-minute boundary and at existing checkpoints/state changes/expiry; System UI
supplies the ticks without per-second service wakeups or notification rebuilds.
A locked/off session shows a fixed seconds remainder with paused/waiting copy;
unlock projects a new end from the remaining confirmed usage. Both ordinary
allowance and temporary color use their own monotonic engine deadlines.

Native display has limits: Android controls placement and timer formatting, may
briefly show zero (or negative overdue time) if the expiry callback is delayed,
and cannot detect an app termination that supplies no callback. Expanded copy
explains the scheduled estimate and recovery path. Attention, startup, pending
writes, expiry, and known stopped states do not publish a running chronometer;
service destruction removes the notice when its callback is delivered. A prior
interruption is still labelled on resumed confirmed usage. No countdown is proof
that monitoring remains uninterrupted or that grayscale physically returned.

The standard API adapts the elapsed deadline to Notification.when at publication;
a protected time-change broadcast refreshes that wall-clock projection without
changing session anchors. A 999 ms display offset compensates native downward
second rounding; the engine deadline is unchanged. The existing single timer adds
at most one final-minute boundary wake per active segment. No polling fallback,
custom notification layout, exact alarm, wake lock, or permission was introduced.

Checkpoint version 2 adds override usage, its captured duration, the exact expected
baseline (including absent keys), and pending operation purpose/target. Version 1
loads with a 5-minute default and no override; prior configuration, confirmed
usage, exhaustion and pending operations are preserved. The display recovery
journal remains version 1 in `grayscale_recovery`, separate from monitoring.
V1 already persisted reset targets before restoration; migration cannot recreate
prior consumption that V1 never recorded. Corrupt or unknown checkpoints stop
monitoring and retain controller/manual recovery. Downgrading to a v1-only build
with a v2 checkpoint is unsupported; disable and restore first, and do not clear
app data as a workaround. The independent display recovery format is unchanged.

Override entry closes the existing owned display operation by restoring and
verifying its original snapshot, then clearing that journal. During temporary
color, the monitoring baseline is only a conflict guard, not a recovery writer.
Reactivation saves a fresh controller snapshot before writes. Pending targets
commit separately from confirmed state; interrupted operations reconcile actual
settings/journal before resuming. Restoration or journal-clear failure does not
start an override. Successful display mutation followed by failed monitoring
persistence surfaces attention instead of a success claim. Screen signals captured
during synchronous display writes are conservatively reconciled in capture order,
without charging the unobserved gap or inventing a qualifying break.

Ordinary process recovery keeps confirmed override usage, discards timing anchors,
and does not charge unknown downtime or infer a break across it. Uncheckpointed
usage may be lost, extending the remainder (normally under a minute, potentially
more with delayed scheduling). Reboot/unknown boot identity and confirmed user
stops cancel the override/session, with best-effort recovery and no automatic
monitoring restart. Android cannot guarantee timely expiry, service restart,
force-stop restoration, or boot restoration. Read-back is not physical acceptance.

## Automatic monitoring

Grant the secure-settings permission below, choose settings, tap **Save settings**,
then **Enable monitoring**. Defaults: 10 minutes of color, 5 minutes for
a reset. All three timing inputs accept whole minutes from 1 to 120; temporary
color defaults to 5 minutes. Notification permission
is requested on Android 13+; denying it still permits monitoring, but the service
notice appears in Android's Active apps list rather than the notification drawer.
Blocked notifications/channels are also reported in the app on resume.

Only interactive use with keyguard absent consumes allowance. Screen-off starts a
continuous non-interactive break, including **display timeout**. Screen-on ends
that break even if the lock screen remains visible. A break of at least the reset
interval restores original settings immediately at screen-on, even on the lock
screen, and makes the full fresh allowance available. Counting starts only when
unlocked use resumes. Separate short breaks never add up. Grayscale remains
applied during short breaks and while the screen is off. No exact wakeup
at the reset instant is required. These are interactive-state proxies, not a
complete keyguard history; always-on display and unusual device lock behavior can
differ from the physical display's power state.

For a 5-minute allowance: use 3 minutes, turn off for 2 minutes, then resume with
2 minutes left. After exhaustion, a 4-minute break preserves grayscale; a continuous
5-minute break restores color at wake and leaves fresh color time waiting for unlock.

Settings never clear consumed usage. Reducing allowance can exhaust it immediately;
increasing it gives a still-color session only the difference. Exhaustion stays
latched despite allowance increases until a qualifying break or disable/re-enable.
A changed reset interval applies to the current break; an already earned reset
remains available. Manual activation is disabled during automatic monitoring.
**Disable and restore** cancels monitoring and resets the session.
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
application can still be delayed by Android scheduling. While interactive and
locked, one timer checks the current interactive/keyguard state each second to
reconcile a missed unlock; USER_PRESENT remains the primary unlock evidence.
No allowance is charged during these locked checks. There are no exact alarms,
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
blocks another activation; **Restore original settings** completes recovery first.

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
3. If settings have changed outside Offramp Fade, automatic restoration refuses
   unfamiliar combinations. Inspect the current values and decide whether to keep
   the newer accessibility configuration. Do not blindly overwrite it.
4. If app restoration is unavailable, use the saved screen commands or your
   preflight snapshot. Use the same Android user that ran the app. First temporarily
   disable the Offramp Fade transform, restore the original mode, then restore the
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
   values match, tap **Restore original settings** to clear a pending record without new writes.
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
color correction or Developer options' simulated color space while Offramp Fade
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
  Offramp Fade (Android itself may update other settings).
- [ ] Restore. Compare both values and key presence with the preflight snapshot;
  confirm actual color returns. Repeat Restore; values must stay unchanged.
- [ ] Repeat at least five enable/restore cycles; rapidly tap Enable and rotate the
  phone during an operation. Confirm no snapshot replacement or overlapping action.
- [ ] Force-stop the app while grayscale is active, reopen, confirm saved recovery
  commands and Restore work. For PR 2 reboot expectations, use the checklist below.
- [ ] Revoke permission while grayscale is active. Restore must report the missing
  grant and retain the record. Regrant, resume and restore successfully.
- [ ] Enable Android Accessibility color correction first. Offramp Fade must refuse
  control, including if another tool already enabled monochromacy. Preserve that
  configuration. Turn it off yourself before testing Offramp Fade again.
- [ ] While Offramp Fade owns grayscale, change to an accessibility correction mode.
  Restore must report a conflict and preserve the new configuration. Inspect and
  follow the manual recovery procedure only when you intend to restore the snapshot.
- [ ] Exercise the displayed emergency commands after force-stop, verify exact
  values, then reopen and Restore to clear the journal. Restore before uninstalling.
- [ ] Check large font, screen reader labels, portrait/landscape and light/dark
  system appearance. Ensure setup and recovery text remain reachable by scrolling.

See [VALIDATION.md](VALIDATION.md) for checks performed in the development environment.

## PR 2 Pixel 9 Pro reference checklist

Record Android build, allowance/reset values, timestamps, original key presence and
values, observed notification states, and physical color/grayscale results. Inspect
the physical screen: screenshots need not capture the display transform.

- [ ] Set both intervals to 1 minute; verify automatic grayscale after unlocked use.
- [ ] Use 20 seconds, turn off for 20 seconds, unlock: approximately 40 seconds remain.
- [ ] Exhaust allowance; verify a short break retains grayscale without new snapshot.
- [ ] Exhaust allowance, turn the screen off for over 90 seconds, then wake without
  unlocking (avoid face/fingerprint auto-unlock). Verify physical color returns on
  the lock screen immediately and both settings/absence match the original snapshot.
  Notification: `Color: 1 minute remaining · waiting for unlock`.
- [ ] Keep the lock screen interactive for over 90 seconds (wake before timeout if
  necessary). The full allowance must remain available. Unlock: color remains for
  a full minute of unlocked use before grayscale returns. Use diagnostic timestamps
  for timing; the exact reset boundary is covered by automated tests.
- [ ] After exhaustion, wake after a 20-second break without unlocking. Grayscale
  and consumed usage must remain. Wait on the lock screen for over a minute and
  unlock: grayscale still remains. Repeat off/wake cycles below the reset threshold;
  their combined off time must never reset usage. Also repeat before exhaustion to
  verify that partially consumed allowance resumes with only its remainder.
- [ ] Test genuine PIN, fingerprint, and face unlock and a device configured without
  an active keyguard. Wake without keyguard starts the full allowance immediately;
  duplicate broadcasts must not grant another reset. Verify a genuine unlock missed
  by the receiver is reconciled within ordinary scheduling tolerance. Do not spoof
  USER_PRESENT with `am broadcast`; it is a protected system action.
- [ ] With grayscale active, take a qualifying break and revoke WRITE_SECURE_SETTINGS
  before wake. Wake must surface attention, retain the original snapshot, and avoid
  announcing a fresh allowance or counting after unlock. Regrant and use Disable
  and restore; confirm exact restoration. Repeat the external-conflict recovery
  checks below; newer color settings must never be overwritten automatically.
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

Exclusions, analytics, tiles, widgets, daily budgets,
schedules, cloud sync, and automatic monitoring at boot remain out of scope.

## PR 3 Pixel 9 Pro acceptance reference

Core temporary-color and visual tests were reported successful by the user on
Pixel 9 Pro. Exact OS/build, tested commit and individual logs were not supplied.
The expanded reference items below remain unchecked because individual subcase
results were not supplied; this is not a claim that all core tests are still
unperformed. VALIDATION.md records the four explicitly untested safety scenarios.
The native notification change requires the additional checks at the end.

Record Android OS/build fingerprint, app commit, timing values, event timestamps,
original values and key presence, notification visibility, and physical display
observations. Keep the external preflight snapshot until exact restoration is
confirmed. Obtain explicit approval before destructive recovery, data clearing,
uninstall, or revoking permission against an active grayscale state.

- [ ] Upgrade install over PR 2 without uninstall/data clearing. Confirm the same
  application ID, grant, saved configuration, usage, and recovery snapshot persist.
- [ ] Confirm Offramp Fade name, split-circle normal/themed launcher mark,
  notification glyph/title, light/dark themes, portrait/landscape, large text,
  TalkBack, numeric errors, touch targets, and reachable setup/recovery controls.
- [ ] With 2-minute allowance, verify rounded-up minutes then `0:SS` only in visible
  UI. Background the activity; the native final-minute notification counts seconds and expiry still occurs.
- [ ] Exhaust automatically, request 1-minute temporary color, visually confirm
  color and exact restored values/absence; ordinary allowance stays exhausted.
- [ ] Use 20 seconds, screen off 20 seconds, wait on lock screen, unlock: only the
  remaining override usage counts. Repeated taps/configuration edits do not extend it.
- [ ] Expire while unlocked: grayscale is verified active with the original
  baseline captured anew. Disable: restore exact values/absence and physical color.
- [ ] Take a qualifying uninterrupted break during the override. Wake without
  unlocking: fresh ordinary allowance is available; lock-screen time does not count.
  Repeat below-threshold breaks and AOD/display timeout; no combined reset.
- [ ] Test PIN, fingerprint, face unlock, and no-keyguard wake. If expiry coincides
  with locking, reactivation waits for unlock; qualifying wake takes precedence.
- [ ] Terminate the process normally during override and reactivation; inspect
  retained confirmed usage, interruption label, journal, and recovery. Exercise
  interrupted restoration/reactivation only with protected external snapshot.
- [ ] Separately test force-stop, Active apps Stop, service restart, and reboot.
  No prompt restart/restoration is guaranteed; reopening exposes recovery. Boot
  recovery runs after first unlock and does not start monitoring.
- [ ] With explicit approval and external snapshot protected, test permission
  loss and external correction changes during override/transition. No unfamiliar
  settings are overwritten; recovery records survive failed restoration.
- [ ] Test notification allow/deny and channel blocking; state/tap-to-app remains
  honest. Repeat sessions and restore before uninstalling or clearing data.

Storage/provider fault injection and exact millisecond thresholds are automated
regressions, not claims of physically reproduced Android failures.
### Native notification countdown follow-up (physical confirmation pending)

- [ ] Check collapsed/expanded shade and lock-screen notifications on the exact
  Pixel OS/build: correctly pluralized minutes above one minute, native seconds
  during the final minute for ordinary color and temporary color, and no stale
  “Less than a minute remaining” alongside a running countdown.
- [ ] Lock/turn off in the final minute: timer is removed, fixed seconds do not
  decrease. Unlock: only unlocked-use remainder resumes; short breaks retain usage.
  Qualifying wake shows a fresh allowance waiting for unlock.
- [ ] Change wall-clock time during the final minute. The rendered end is refreshed,
  while allowance consumption and logical expiry remain based on elapsedRealtime.
- [ ] Expire: countdown is replaced with verified grayscale or honest pending/
  attention. Check scheduling delays; native zero/overdue rendering is not a success
  claim. Notification denial/channel blocking must remain honestly reported.
- [ ] With external snapshot protected and explicit approval for destructive steps,
  verify known service interruption removes the timer and reopening reconciles
  state. Abrupt death/force-stop/reboot cannot promise immediate notification
  removal or restoration; keep these in the outstanding recovery/safety category.