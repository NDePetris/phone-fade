# PR 2: grayscale stays paused after unlock

Investigated October 13, 2026. User reproduction: Pixel 9 Pro, allowance/reset
both one minute, automatic grayscale active, screen off over 90 seconds, fully
unlock, still grayscale with `Paused — Grayscale active`.

Follow-up correction on October 13: restoration now occurs at the end of a
qualifying off interval, including SCREEN_ON with keyguard still visible. The
earlier findings below describe the unlock-broadcast investigation before this
follow-up. The current expected chain is SCREEN_OFF, then SCREEN_ON with exact
restoration, cleared exhaustion/usage and no active allowance anchor, then
USER_PRESENT (or an unlocked-state reconciliation) starting the full allowance.
The locked notification reads `Color: 1 minutes remaining · waiting for unlock`
for a one-minute allowance. While locked, a one-second reconciliation timer covers
missing unlock delivery, including exhausted sessions with no allowance deadline.
Failures retain the recovery record, prior consumed usage/exhaustion, pending
RESTORE and attention; no timing resumes. See the updated README device checklist
and VALIDATION.md for follow-up validation. Physical confirmation remains pending.

## Findings and correction

The strongest code-identifiable cause is receiver registration on API 33+.
`SessionMonitoringService` registered SCREEN_OFF, SCREEN_ON, and USER_PRESENT
with `RECEIVER_NOT_EXPORTED`. SCREEN_OFF/ON can arrive from system_server, but
USER_PRESENT is sent by SystemUI, which has a separate UID. Android explicitly
documents that NOT_EXPORTED excludes broadcasts from privileged apps outside the
system UID. This explains why screen boundaries can work while unlock is missing.
The exact Pixel failure has not been independently reproduced or logged here.

Sources:

- [Android receiver export guidance](https://developer.android.com/develop/background-work/background-tasks/broadcasts)
- [Android 16 SystemUI USER_PRESENT sender](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/packages/SystemUI/src/com/android/systemui/keyguard/KeyguardViewMediator.java)
- [SystemUI separate UID](https://android.googlesource.com/platform/frameworks/base/+/android16-release/packages/SystemUI/AndroidManifest.xml)
- [Protected SCREEN_OFF, SCREEN_ON, USER_PRESENT declarations](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/res/AndroidManifest.xml)

The receiver now uses `RECEIVER_EXPORTED`. Its filter contains only these three
protected system actions, and handling rejects any other action. No app-defined
command receiver, service export, permission, or dependency was added.

A second code weakness was that every action, including USER_PRESENT, was replaced
with a PowerManager/KeyguardManager snapshot. An interactive but still-LOCKED
snapshot could veto explicit unlock evidence. The new Android-free
`ScreenEventMapper` maps USER_PRESENT to UNLOCKED while interactive. If the device
is already OFF at delivery, it keeps OFF; it does not fabricate unlocked use.
This defensive path is tested, but a lagging keyguard query on this Pixel is not
claimed as an observed fact.

## Trace of the six investigation priorities

| Area | Actual implementation and result |
| --- | --- |
| Receiver lifetime and delivery | Registered in service onCreate after foreground promotion, removed in onDestroy. All three actions share one receiver. API 33+ export flag was incompatible with the SystemUI sender; corrected. |
| Paused engine | Paused is `session.activity != UNLOCKED`, rather than a separate latch. OFF followed by SCREEN_ON/LOCKED leaves activity LOCKED. An absent or vetoed USER_PRESENT leaves it there indefinitely after exhaustion. |
| Platform reconciliation | Normal observations use `isInteractive` and `isKeyguardLocked`. Screen-boundary reconciliation is retained. Explicit USER_PRESENT now survives a LOCKED snapshot while interactive; an OFF snapshot still prevents resume. |
| Earned reset | On leaving OFF, the reducer compares the continuous break against resetMs, latches resetEarned, and clears breakSince. LOCKED observations retain the flag. UNLOCKED consumes it, resets usage/exhaustion, and requests RESTORE. No reducer correction was needed. |
| Notification | Text derives from coordinator activity and controller ownership. `Paused — Grayscale active` is consistent with LOCKED/OFF plus owned grayscale. StateFlow collection updates foreground notification on changes. Tests verify it changes to color remaining after restoration, or unpaused grayscale after a short break. |
| Service and coroutines | Receiver submits captured monotonic timestamps to an application-owned unlimited channel with a single IO consumer. The service collector runs on Main; cancellation in onDestroy does not cancel the application queue. No deterministic queue/lifecycle failure was found in this reproduction path. An uncaught command exception could terminate the processor; new logs identify it without changing exception or recovery behavior. |

An exhausted session has no allowance deadline. Consequently there is no timer
left to repair a missed unlock. The correction fixes delivery and unlock handling
without adding background polling, treating downtime as a break, or bypassing the
controller.

Ownership journaling, exact original-setting restoration, persistence before
effects, external-conflict checks, and process/boot recovery are unchanged.
Stale SCREEN_ON/OFF snapshots still use Recover to avoid joining unknown off
intervals. Debug logs never include saved settings, checkpoint contents, exception
messages, identifiers, or personal data.

## Pixel confirmation and ADB capture

Use the newly built **debug** APK; release builds do not emit these diagnostic
logs. Substitute the Platform Tools path for `adb` if it is not on PATH, and
use `adb -s SERIAL` when multiple devices are connected.

First disable monitoring and restore through the app, then install the update
without clearing app data:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant app.ndplabs.phonefade android.permission.WRITE_SECURE_SETTINGS
adb shell getprop ro.build.fingerprint
adb shell getprop ro.build.version.sdk
adb logcat -v threadtime -T 1 'PhoneFade:D' 'AndroidRuntime:E' '*:S' > phonefade-unlock.log
```

Start capture before enabling monitoring. Set and save one-minute allowance and
reset, enable monitoring, wait for grayscale, screen off for over 90 seconds,
wake to the lock screen, then fully unlock. Wait a few seconds and stop capture
with Ctrl+C. Record whether color returned and the notification lost `Paused`.
Repeat with a short break to verify grayscale remains but the notification
unpauses. Do not emulate USER_PRESENT with `am broadcast`: it is protected and
that does not test genuine SystemUI delivery.

If the failure persists, immediately collect these read-only diagnostics:

```powershell
adb shell dumpsys activity services app.ndplabs.phonefade > phonefade-service.txt
adb shell dumpsys power | Select-String 'mWakefulness=|mInteractive=' > phonefade-power.txt
adb shell dumpsys window policy | Select-String 'keyguard|Keyguard' > phonefade-keyguard.txt
```

Share the filtered log, scoped diagnostics, Android build/SDK, actual off duration,
unlock method (PIN/fingerprint/face), and whether the app was open. Avoid full
notification dumps or broad settings dumps, which can contain unrelated data.

Expected log chain:

1. `receiver registered ... exported=true`, processor and collector startup.
2. SCREEN_OFF receive, matching command queued/begin/end, activity OFF and a break anchor.
3. SCREEN_ON receive and command end with `resetEarned=true`, activity LOCKED
   (or an immediate reset if wake is already unlocked).
4. USER_PRESENT receive, command end with UNLOCKED, `resetEarned=false`,
   `exhausted=false`, `ownsGrayscale=false`, no attention/pending effect.
5. `notification published` with color remaining and a new allowance timer.

Missing USER_PRESENT isolates delivery; queued without begin isolates processing;
UNLOCKED with attention/pending restore isolates restoration; correct command end
without a matching notification publication isolates the service collector.
Repeated application/service creation or `onDestroy` identifies interruptions.
Numeric generation and command IDs correlate events even if IO/Main log lines
interleave. StateFlow can conflate intermediate states; the latest publication
should still agree with the latest coordinator state.

## Automated coverage

Eight new tests cover signal mapping and coordinator/controller/notification
behavior. All original controller and recovery regressions remain in the suite.
JDK 17 validation passed: `assembleDebug testDebugUnitTest testReleaseUnitTest
check lintDebug lintRelease --no-watch-fs --console=plain`. Both variants passed
92 tests each (184 executions), both lint reports say `No issues found`, and
`git diff --check` passed. An initial sandbox wrapper-lock denial required access
to the existing external Gradle cache/SDK. The first external attempt selected
Java 25 and failed configuration; setting JAVA_HOME to the installed JDK 17
resolved it. The first JDK 17 run emitted an SDK XML tooling-version warning;
the final incremental run passed without that warning.
Local unit tests do not exercise Android receiver delivery, physical display
behavior, or actual SystemUI notification rendering; the capture above is the
remaining device confirmation.
