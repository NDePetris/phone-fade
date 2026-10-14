# Offramp Fade validation

## Quiet Road presentation — October 14, 2026

Baseline: local/live remote `main` at `48f33701218a9d687e484a908e761b9244364d2a`;
branch `feature/quiet-road-ui`. Supplied untracked attachments were preserved.
Implementation details, before/after and PR draft: [review](design/QUIET_ROAD_REVIEW.md).

### Automated checks

Using Temurin JDK 17.0.20, pinned Gradle wrapper, SDK Platform 36 and Build Tools
35.0.0, the following passed:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest testReleaseUnitTest check lintDebug lintRelease --no-watch-fs --console=plain
git diff --check
```

- **195 tests per variant, 390 executions, zero failures/errors.** All 170 baseline
  tests remain. New cases cover state/action/copy, manual versus automatic guidance,
  override eligibility and captured time, interruption placement, draft bounds/save
  eligibility, durable-save failure, restored/failure/no-op results, permission/write/
  journal-clear/completion-commit failures, external conflicts, one-shot delivery,
  spoken duration, actual theme contrast and canonical SVG/vector geometry.
- Debug APK, debug instrumentation APK, Gradle check and both lint tasks passed.
  Both lint reports: **No issues found**. Six synthetic Compose instrumentation
  tests compiled, but were **not executed** without an Android target.
- Packaged debug APK permissions remain WRITE_SECURE_SETTINGS, FOREGROUND_SERVICE,
  FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED and
  the app-local AndroidX receiver permission. **No Internet permission.** Production
  manifest, launcher, notification/service, reducer and storage schemas are unchanged.
  Standard preview/test tooling supplies a debug-only test host.
- Theme tests check actual text/container/action/outline/error pairs across surface
  roles, snackbar roles, disabled distinctness and artwork ink/veil combinations.
  They found insufficient Sky action contrast on some proposed dark surfaces;
  those surfaces and the hero were darkened before the passing run. These solid/
  composited checks do not prove physical grayscale contrast or rendered appearance.
- Canonical source SHA-256 and all paths, clip, fills, inherited stroke colors/widths,
  caps and viewport are checked against the Android vector conversion.
- Initial validation encountered sandbox cache access and a JDK 25 shell default;
  authorized runs explicitly used JDK 17. Earlier lint found the replaced in-app
  mark unused; it was removed, without altering the launcher. The existing SDK XML
  tooling-version notice did not fail the final checks.

### Manual device tests and visual evidence

**Not performed for this redesign.** Read-only ADB discovery returned no device or
emulator. No local AVD/system image was available. No install, grant, active
display-setting operation, destructive recovery, screenshot or physical observation
was performed. Model/OS/build for new testing is therefore not asserted.

Ten synthetic Compose preview states are declared in light, dark, narrow/2x-font
and landscape configurations. These are source fixtures, **not rendered screenshots**.
An independent Impeccable source review compared composition/copy with the supplied
Round 2 board. Its spoken-duration finding was fixed and reread as resolved; its
scope does not establish rendered fidelity or TalkBack acceptance. No native-inapp
visual pass is claimed from compilation or source inspection.

### Untested/blocked cases and recovery/permission risk

Complete the [Quiet Road Pixel checklist](README.md#quiet-road-pixel-9-pro-acceptance--pending)
before visual/device acceptance: actual grayscale; light/dark/large text; TalkBack,
touch targets, keyboard/landscape; draft recreation; confirmed save and restore
feedback; missing permission/recovery visibility; notification appearance/cadence.
The four historical pending safety scenarios remain pending: disable during
temporary color; override recreation/reopening; external correction conflicts;
interruption/force-stop/reboot. Historical reported Pixel passes are separate evidence.

The coordinator adds only returned transient results and a nonpersistent projection
of the last durably committed config. Existing operations and journal bookkeeping
determine success; failed completion never produces restoration success. A snapshot-
free completed command is distinguished from actual restoration. ViewModel results
are consumed once and never stored in recovery checkpoints. Snackbar interruption
after consumption is not replayed, avoiding duplicate announcements. Retained
attention/errors and recovery controls remain the durable source of failure truth.
Read-back remains distinct from observing physical color. Existing privileged grant,
external snapshot protection and documented recovery limitations remain applicable.

## Standard notification wording refinement — October 13, 2026

The user's subsequent clarification selects the existing standard Android template
and final-minute native header chronometer. This supersedes the alternative proposed
in the historical evaluation below; no custom RemoteViews or all-duration live
timer is implemented. Offramp Fade remains the title and app identity.

- Ordinary/temporary countdown bodies use `Color: X minutes remaining` and
  `Temporary color: X minutes remaining`, with rounded-up, correct plurals.
- At the existing final-minute boundary, bodies use `Color: Less than a minute
  remaining` or `Temporary color: Less than a minute remaining`; Android supplies
  live seconds in the header. The descriptor stays constant throughout that minute.
- Paused/locked countdowns use static time, such as `Color: 0:32 remaining · Paused`
  or the corresponding temporary-color label. Positive subsecond time shows `0:01`.
  Verified grayscale uses `Grayscale active`, including while locked/off.
- Expanded notifications repeat the compact status. Technical countdown wording
  and the long explanatory paragraph are removed. Historical interruption details
  remain in the app; known stopped/pending/attention states retain recovery copy.
- Native timer eligibility, monotonic deadlines, epoch projection, pause/resume,
  minute checkpoints, expiry and recovery behavior are unchanged. No polling,
  permission, icon or broader UI change is introduced. Android still controls header
  placement and may show overdue time if expiry publication is delayed.

### Automated checks

`assembleDebug testDebugUnitTest testReleaseUnitTest check lintDebug lintRelease
--no-watch-fs --console=plain` passed using JDK 17, the pinned wrapper, SDK Platform
36 and Build Tools 35.0.0. **170 tests per variant, 340 executions, zero failures or
errors**; both lint reports: **No issues found**. APK permission inspection confirms
unchanged permissions and no Internet permission. `git diff --check` passed.
Focused regressions cover ordinary/temporary labels, stable final-minute publication,
paused singular/plural and positive subsecond boundaries, lock/unlock anchors,
expired/pending/stopped/attention timer suppression and verified grayscale copy.

### Physical evidence and remaining acceptance

No physical-device testing was performed for this wording change. The user-reported
Pixel 9 Pro core/visual passes and working native countdown below remain historical
evidence. All four listed recovery/safety scenarios remain physically pending.
Check this build's collapsed and expanded notifications for both countdown labels,
the final-minute transition, paused static seconds and unlock resume; include larger
font settings and TalkBack. Shorter text reduces truncation pressure, but automated
tests do not establish actual collapsed-shade fit or accessibility acceptance.

## Notification presentation evaluation — October 13, 2026

The user reports that native countdowns work on Pixel 9 Pro, but the final-minute
header placement, “scheduled countdown” body, and long temporary-color copy are
inconsistent/truncated, matching the current implementation at `35ebe0a`.
The actual device-tested commit was not supplied. This is not a claim of new
app-wide or recovery acceptance.

### RemoteViews prototype and stop decision

A temporary isolated debug package (`app.ndplabs.phonefade.notificationpreview`)
was built and tested on the connected Pixel 9 Pro. Read-only device inspection
reported Android 17 build `CP3A.260905.009` (fingerprint
`google/caiman/caiman:17/CP3A.260905.009/16091614:user/release-keys`), physical size
960 x 2142, density 360 dpi, and font scale 1.0. This identifies this evaluation's
device; it does not retroactively identify the build of earlier user tests.
The installed Offramp package was not updated, uninstalled, or started.

The prototype used DecoratedCustomViewStyle and an elapsedRealtime-based
RemoteViews Chronometer. A separate visible/accessible label was used because
Chronometer.getContentDescription() describes only duration, ignoring the format
string's “Temporary color”/“remaining” words. Foreground-service publication was
accepted on the device using specialUse; no session monitoring was started.

Native Android inflation/measurement on that device tested a representative
300 dp content width and Android's documented 48 dp collapsed-content budget:

| Synthetic case | Native measurement | Result |
|---|---|---|
| Live temporary color, 14 sp | One line; text bottom/height 37 px; budget 108 px | Fits this constrained measurement |
| Paused temporary color, 28 sp | Two lines; last line bottom 146 px; measured view height/budget 108 px | 38 px of text cannot fit; clipping risk confirmed |

The 28 sp case was a larger-text stress test, **not** a change to the Pixel's
system font setting. These are on-device native layout measurements, not a
claim that actual collapsed-shade rendering or TalkBack passed. Inspection did
not establish complete System UI collapsed-notification acceptance. The prototype
failed the minimum-height compatibility check before shipping was considered.

Per the user's requirement 9, custom-layout implementation stopped. The prototype,
temporary debug manifest/resources/classes and build suffix were removed. No
production notification, session, grayscale, recovery, icon, dependency, or
permission change is retained. Shrinking user-selected text to fit would undermine
accessibility; assuming an expanded notification resolves collapsed clipping
would not satisfy the requested acceptance criterion.

### Preview cleanup and permission correction

The preview was installed using `adb install -g`. Contrary to the intended setup,
package inspection at cleanup showed WRITE_SECURE_SETTINGS granted to the isolated
preview package as well as POST_NOTIFICATIONS. The preview executed synthetic
notification code; its separate fresh recovery store had no snapshot, and the
application's no-snapshot restoration path does not write secure settings. No
display-setting mutation was performed. The preview package was uninstalled,
removing its grant and foreground notification. No grant was revoked from the
real app. Temporary UI-inspection files and prototype source files were removed.

### Recommended standard-template alternative (not implemented)

Keep Offramp Fade as title/identity and use the native **header chronometer for
all active unlocked countdowns**, so its position never changes at one minute.
Use short, stable body labels “Color remaining” or “Temporary color remaining”;
remove “scheduled countdown” and long explanatory copy. Paused states use compact
static remaining time and a paused label; waiting, grayscale and attention use
short state text. Keep live timers suppressed for pending, expired, stopped and
attention states. This preserves system layout/styling and avoids service polling,
but does not place live seconds within the body. Obtain the user's selection of
this alternative before implementing it; do not silently substitute it for the
requested inline presentation.

Sources: [Android custom notification constraints](https://developer.android.com/develop/ui/views/notifications/custom-notification),
[RemoteViews Chronometer API](https://developer.android.com/reference/android/widget/RemoteViews#setChronometer(int,long,java.lang.String,boolean)),
[AOSP Chronometer accessibility implementation](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/widget/Chronometer.java).

The four previously recorded recovery/safety scenarios remain physically pending.
This isolated synthetic evaluation does not validate app recreation, external
correction conflicts, disabling a real override, force-stop, or reboot recovery.

### Restored repository checks

After removing temporary build inputs, a clean build/test/lint run is used to
regenerate the real APK and prevent preview classes or package identifiers from
remaining in generated outputs. Results:

- `clean assembleDebug testDebugUnitTest testReleaseUnitTest check lintDebug
  lintRelease --no-watch-fs --console=plain` passed with JDK 17, pinned wrapper,
  SDK Platform 36 and Build Tools 35.0.0.
- **167 tests per variant, 334 executions, zero failures/errors**. Both lint
  reports: **No issues found**. `git diff --check` passed.
- The clean build regenerated `app.ndplabs.phonefade` with Offramp Fade label;
  no preview manifest component or debug suffix remains in the APK. Permissions
  are unchanged and contain no Internet permission.
- The existing native-library stripping notice (`libandroidx.graphics.path.so`
  packaged unchanged) appeared during the clean build; it did not fail checks.
- Only this validation record is committed. No production implementation change,
  push, merge or release was performed.
No new production regression test is retained because no behavioral change is
being shipped; the two focused prototype layout cases above are the evaluation
that triggered the explicit stop condition.

## PR 3 Pixel acceptance report and notification follow-up — October 13, 2026

### User-reported physical evidence

The user reports that **core temporary-color functionality and visual acceptance
passed on Pixel 9 Pro**. This is user-provided evidence, not testing executed or
independently observed by the agent. Exact Android OS/build, tested commit,
individual logs and test timestamps were not supplied, so none are inferred.
The reported remaining UX issue was coarse notification copy (“Less than a minute
remaining”) instead of seconds below one minute. These acceptance results apply
to the prior implementation, not the native countdown follow-up below.

**Four recovery/safety scenarios were explicitly not physically tested:**

1. Disabling monitoring during temporary color leaves original display settings
   restored.
2. App recreation and reopening preserve override state.
3. External Android color-correction changes are handled safely.
4. Service interruption, force-stop, and reboot follow documented recovery behavior.

All four remain **pending physical acceptance**. Automated failure-path coverage
is separate evidence and does not mark them passed. The expanded README reference
checklist is not a blanket assertion of every subcase passing.

### Notification implementation and tradeoffs

The standard Notification.Builder chronometer supplies system-managed seconds
only during the final minute of active unlocked ordinary allowance or temporary
color. Paused/locked states show a fixed seconds remainder without a chronometer.
Resume derives a new deadline from confirmed usage. Publication identity includes
the elapsed deadline, so resume/configuration changes update a timer even when its
label is unchanged; native ticks cause no app-side notification rebuilds.

The single existing service timer adds one final-minute boundary to existing
minute checkpoints and logical expiry; there is no per-second polling fallback.
Native seconds are rounded up for display with a 999 ms projection offset, without
moving the engine deadline. Protected TIME_SET refreshes the epoch projection
on a wall-clock change, leaving monotonic session/recovery state untouched.
Pending effects, expired deadlines, missing/uncertain display compatibility,
attention, startup and known stopped states suppress the native timer. A recovered
session can resume a new reliable anchor while retaining its interruption label.
A delivered service-destruction callback removes the foreground notification.

Android controls chronometer placement/format. It has no automatic clamp at zero,
no remote tick callback into our service, and no way to detect an abrupt process
termination on the app's behalf. Delayed expiry publication can briefly leave a
zero or negative overdue display. Body/expanded copy identifies a **scheduled**
countdown and does not claim physical grayscale or uninterrupted execution. An
unobserved stop cannot guarantee immediate notice removal, and app-side polling
would not solve process death either. No custom RemoteViews, timeout cancellation,
exact alarm, wake lock, new permission, UI redesign, icon change, or recovery
architecture change was introduced.

Sources reviewed:

- [Notification.Builder native chronometer API](https://developer.android.com/reference/android/app/Notification.Builder#setUsesChronometer(boolean))
- [AOSP Chronometer: elapsed base, tick visibility, and negative durations](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/widget/Chronometer.java)

### Automated and new physical validation

Base implementation commit: `409b7a1`, on
`feat/offramp-fade-everyday-experience`. With the repository's JDK 17, pinned
wrapper, SDK Platform 36 and Build Tools 35.0.0, the following passed:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest testReleaseUnitTest check lintDebug lintRelease --no-watch-fs --console=plain
git diff --check
```

- **167 tests per variant, 334 executions, zero failures/errors** (154 prior tests
  plus 13 notification countdown regressions in each variant).
- Debug APK assembly, Gradle `check`, debug/release unit tests and both lint tasks
  passed. Both lint reports: **No issues found**.
- Build Tools 35.0.0 APK permission inspection confirms the unchanged set and
  **no Internet permission**. No dependency, SDK, manifest, icon, or app-screen
  change was made.
- Native notification building rechecks expiry immediately before publication;
  a delayed build does not start a chronometer for an already-past deadline.
- No physical device commands or tests, push, or merge were performed in this
  follow-up. System UI rendering and native rounding remain device acceptance.

New deterministic tests
cover singular/plural boundaries, native eligibility, stable publication identity,
ordinary/override pause and resume, override configuration preservation, recovered
anchors, deadline expiry, pending/attention/startup/stopped suppression, display
conflicts, wall-clock projection, and economical final-minute scheduling.

**Native notification physical validation: not performed.** User-reported earlier
visual acceptance does not validate the new System UI chronometer rendering.
Follow the [native notification acceptance checks](README.md#native-notification-countdown-follow-up-physical-confirmation-pending)
on Pixel 9 Pro, alongside the four pending recovery/safety scenarios above.

## PR 3 — Product identity and everyday experience — October 13, 2026

Base: refreshed merged `main` (`69b3fdb`). Branch:
`feat/offramp-fade-everyday-experience`. No push, PR creation, merge, repository
rename, release, device installation, or physical display mutation was performed.

### Automated checks

With installed Temurin JDK 17.0.20, SDK Platform 36, Build Tools 35.0.0 and the
pinned wrapper:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
.\gradlew.bat assembleDebug testDebugUnitTest testReleaseUnitTest check lintDebug lintRelease --no-watch-fs --console=plain
git diff --check
```

- Full build/test/check/lint command passed. Both lint reports: **No issues found**.
- **154 tests per variant, 308 executions, zero failures**: 27 controller, 42
  existing coordinator, 28 existing reducer, 5 mapper, 3 store, 6 migration,
  5 countdown/presentation, 12 override reducer, 26 override coordinator.
- All 105 prior tests remain, with expected branded/plural text and the v2
  purpose-aware checkpoint fixture updated. 49 new regression tests cover override
  entry/expiry, short/qualifying breaks, locked boundaries, configuration changes,
  duplicate requests, stale timers, interrupted use, migration and malformed
  records, exact absent-key recovery, and operation-persistence failures.
- Fault tests use the real controller with deterministic fake providers, journals,
  clocks and monitoring storage. They simulate process death after each restore
  or reactivation write, death after journal clearing, restoration/clear failures,
  successful mutation followed by failed completion persistence, pending reactivation
  without writes, partial activation recovery, external changes, permission loss,
  service destruction, reboot, user-stop cancellation, and queued screen boundaries during slow restoration. They do not prove Android
  provider durability, broadcast delivery, or physical display behavior.
- Build Tools 35.0.0 `aapt2 dump permissions` confirms the unchanged permission set:
  WRITE_SECURE_SETTINGS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE,
  POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, and the app-local AndroidX receiver
  permission. **No Internet permission** or new permission was added.
- APK badging confirms `app.ndplabs.phonefade`, label **Offramp Fade**, minimum
  API 29 and target API 36. Packages, components, storage identifiers, notification
  channel ID, and ADB grant command remain stable.
- The design helper returned no mechanical findings for the Compose files; this
  is not rendered UI or accessibility acceptance.
- The initial sandboxed wrapper run could not open its external cache lock.
  Validation used approved access to the existing Gradle cache/SDK. Earlier test
  failures were stale text/fixture expectations, corrected before the passing run.

### Manual device tests and untested cases

**At initial implementation time, PR 3 physical tests were not performed.** No configured local AVD was available for
rendered UI inspection. Pixel 9 Pro OS/build is therefore not asserted. Use the
[PR 3 acceptance checklist](README.md#pr-3-pixel-9-pro-acceptance-reference)
for real display changes, upgrade/grant preservation, large text/TalkBack/themes,
foreground lifecycle, notifications, force-stop, reboot and external conflicts.
Historical PR 1/PR 2 device reports remain separate evidence below.

### Recovery and compatibility risk

Display writes still use the shared controller and independent original-settings
journal. The override begins only after verified restoration and durable completion;
reactivation verifies the exact baseline and preserves absent rows. Completion
commit failures retain pending intent and surface attention. Partial activation
recovery restores through the existing journal and requires review instead of
silently retrying. Exact original snapshots are never replaced while pending.

Checkpoint v2 preserves valid v1 configuration/confirmed usage/exhaustion and
migrates pending effects with explicit purpose/target. V1 did not retain pre-reset
usage before its pending restoration, so migration cannot reconstruct that lost
history. Downgrading to a v1-only build while v2 is stored is unsupported: that
build rejects the unknown monitoring version; the unchanged display recovery
journal still permits manual restoration. Restore and disable before downgrading;
do not clear data to work around this boundary.

Unknown downtime is not charged or treated as a break. Uncheckpointed override
usage can be lost; delayed scheduling can extend physical color beyond the logical
deadline. Force-stop/service termination/reboot do not guarantee immediate recovery.
Boot remains best effort after first unlock without starting monitoring. Observable
external changes stop automatic effects; identical cross-process writes cannot be
identified conclusively. UI text reflects verified provider state, not proof of
actual physical grayscale.

## Evidence reconciliation — October 13, 2026

PR 2 automatic monitoring merged as GitHub [PR #3](https://github.com/NDePetris/phone-fade/pull/3)
into `main` (`69b3fdb`). The historical runs below accurately record what the agent
ran at those times; “no push/merge” and “physical pending” in those entries are
historical, not current branch status. The merged PR author reported Pixel 9 Pro
core activation, short-break preservation, qualifying wake restoration before
unlock, unlock-only fresh-allowance counting, and disable restoration passing.
The exact Android build, individual logs, and timestamps were not supplied.
Not every interruption/recovery scenario was reported physically validated.
PR 3 has its own pending physical checklist and cannot inherit those pass claims.

## Automatic monitoring lock-screen reset correction — October 13, 2026

Branch: `feat/automatic-session-monitoring`. Local documents call this work PR 2;
GitHub's numbered PR #2 is the already merged engineering harness on a different
branch. At the time of this historical run, this correction was only on the monitoring branch. It subsequently merged as GitHub PR #3, commit `69b3fdb`.
The existing uncommitted unlock-broadcast fixes and debug diagnostics are preserved
with the correction and covered by the same validation.

- A qualifying continuous OFF interval now restores through the serialized
  coordinator/controller at wake, clears prior consumption/exhaustion, and leaves
  no active allowance anchor while keyguard is showing. Unlock begins counting
  from the full allowance. No-keyguard wake begins counting immediately.
- Interactive lock-screen time neither consumes allowance nor extends the OFF
  interval. Each wake ends that interval; separate short breaks preserve usage
  and grayscale. One-second state checks while LOCKED reconcile missed unlocks,
  including exhausted sessions that have no allowance deadline.
- Restore failure retains prior usage/exhaustion and the original recovery record,
  leaves RESTORE pending, clears the active anchor, and surfaces attention. Display
  writes, exact snapshots, conflict checks, and process/boot recovery still use
  the existing controller and journals. Obsolete Recover observations are ignored.
- `assembleDebug testDebugUnitTest testReleaseUnitTest check lintDebug lintRelease
  --no-watch-fs --console=plain` passed with the installed JDK 17.
- **105 tests passed in each variant (210 executions)**: 27 controller, 3 storage,
  42 coordinator, 5 screen-event mapper, and 28 reducer tests. All prior unlock
  regressions pass with the new wake-time restoration expectation. Coverage includes
  full allowance while locked, reconciliation/no-keyguard unlock, repeated wakes,
  duplicate/delayed observations, failure/conflict preservation, and recovery of
  a fresh paused allowance. Both lint reports: **No issues found**.
- `git diff --check` and staged whitespace validation passed before commit.
- The sandboxed wrapper initially could not access the external Gradle cache lock;
  validation ran with approved cache/SDK access. SDK XML tooling-version warning
  appeared; it did not fail build/tests/lint.
- Physical acceptance remains pending. Follow the updated [README checklist](README.md#pr-2-pixel-9-pro-reference-checklist)
  and the debug-log capture in BUG_INVESTIGATION.md. No device installation or
  physical display confirmation was performed. No push or merge was performed.

## PR 2 unlock bug investigation — October 13, 2026

See [the investigation and Pixel ADB capture procedure](BUG_INVESTIGATION.md).
Corrected API 33+ receiver registration to receive protected USER_PRESENT from
SystemUI's separate UID, and preserved explicit unlock evidence when an interactive
keyguard snapshot still reports locked. Added debug-only `PhoneFade` diagnostics
for receiver delivery, platform snapshots, command processing, service lifecycle,
coordinator state, notification publication, and timers. Controller ownership,
restoration, and recovery protocols are unchanged.

Final JDK 17 command: `assembleDebug testDebugUnitTest testReleaseUnitTest check
lintDebug lintRelease --no-watch-fs --console=plain`.

- Debug APK build and all requested checks passed.
- 92 tests passed in each variant (184 executions), including eight new regressions.
- Both lint reports: **No issues found**. `git diff --check` passed.
- Initial wrapper-cache sandbox denial and Java 25 configuration failure were
  resolved with external cache/SDK access and explicit JAVA_HOME for JDK 17.
- The first JDK 17 run emitted an SDK XML tooling-version warning; the final
  incremental validation passed without that warning.
- No device installation, physical reproduction, push, or merge was performed.
  Android delivery and physical restoration confirmation remain device checks.

## PR 2 implementation

Branch: `feat/automatic-session-monitoring`, based on merged PR 1 `8db2271`.
Windows validation on October 12, 2026, with installed Temurin JDK 17.0.20.
The checkout was already clean and up to date when implementation began; no
destructive reconciliation, resets, or cleanup were needed. Local ignored build
outputs, caches, and local.properties were preserved.

Validation command:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
.\gradlew.bat assembleDebug testDebugUnitTest testReleaseUnitTest check lintDebug lintRelease --no-watch-fs --console=plain
git diff --check
git diff --cached --check
```

Automated results are recorded below. Tests cover
the reducer, coordinator with the real GrayscaleController and fake platform
settings/storage/clock, checkpoint round trips and failed commits, and all 27
original controller regressions. They do not validate physical display hardware,
Android broadcast delivery, service lifecycle, actual provider durability, or
notification presentation.

### PR 2 results

- Debug APK build, debug/release unit tests, Gradle `check`, `lintDebug`, and
  `lintRelease` all passed. Both lint reports say **No issues found**.
- 84 tests passed in each variant (168 executions): 27 original controller tests,
  24 reducer tests, 30 coordinator/recovery tests, and 3 persistence/codec tests.
- Persistence coverage includes failure before activation, failure after an
  applied transform but before completion persistence, retained recovery despite
  broken monitoring storage, malformed checkpoints, pending activation/restore,
  and failed commit retention even when preferences memory was changed.
- Lifecycle coverage includes stale deadlines, late/duplicate startup acknowledgments,
  concurrent manual/automatic commands, missed screen boundaries, interrupted
  color/grayscale sessions, user stops, unknown/rebooted boot identity, external
  conflicts, and deferred activation while non-interactive.
- `git diff --check` passed. Staged diff validation was performed before commit.
- Final APK permission inspection (`aapt2 dump permissions`, Build Tools 35.0.0)
  shows WRITE_SECURE_SETTINGS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE,
  POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, and AndroidX's app-local
  DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. No internet, Usage Access, Accessibility
  Service, device-admin, or exact-alarm permission was introduced.
- A Pixel 9 Pro was listed by `adb devices -l`. PR 2 was not installed and its
  display settings were not changed for validation; physical acceptance is pending.
- The final incremental validation run emitted no new tooling warnings. Earlier
  Java 25 configuration warnings/failure were resolved by selecting JDK 17.

The merged PR author subsequently reported core Pixel 9 Pro acceptance. See the status note below; the expanded README reference checklist is not a claim that every subcase passed.

### Environment and known limitations

- An initial sandboxed Gradle run could not access its existing wrapper lock;
  the permitted run used the existing external Gradle cache/SDK.
- The default Java 25.0.3 failed Gradle/Kotlin configuration. Selecting the
  repository-required installed JDK 17 resolved it; no toolchain upgrade was made.
- PR 1's SDK XML and native-library stripping notices below are historical
  tooling warnings, separate from PR 2 lint findings.
- Recovery retains confirmed usage and ignores unobserved downtime. The last
  uncheckpointed active segment can be lost. No break is inferred across downtime.
- Foreground execution and prompt sticky restart are not guaranteed. Force-stop
  and Active apps Stop provide no restoration callback. API 29 lacks the exit
  reason API used to identify confirmed user stops.
- Boot recovery runs after first unlock from credential-encrypted storage,
  without starting monitoring. Suppressed/delayed broadcasts, permission loss,
  corrupt storage, and external changes can require manual restoration.
- Denied/blocked notifications reduce visibility; monitoring remains permitted.
- Cross-process writes of identical settings cannot be distinguished conclusively.

## PR 1 physical acceptance — user-provided evidence

The user confirmed that **all seven PR 1 physical acceptance tests passed on a
Pixel 9 Pro**. This updates the earlier development-environment record that no
device was attached during automated validation. The agent did not execute or
independently observe those physical tests. Individual test logs, exact Android
build, and execution timestamps were not provided; they are not invented here.
These results apply to PR 1 manual grayscale/recovery, not PR 2 sessions or service
behavior.

## PR 1 automated validation (historical)

Executed on Windows with JDK 17 on October 12, 2026, on branch
`feat/grayscale-capability-spike`.

## Passed

```powershell
.\gradlew.bat clean assembleDebug testDebugUnitTest lintDebug check --no-watch-fs --console=plain
```

- Clean debug APK build and Kotlin compilation for debug and release.
- 27 focused unit tests, all passed in both debug and release (54 executions).
- `lintDebug` and Gradle `check` passed. No lint baseline; warnings are errors.
  Explicit exceptions are limited to deliberate version/target pinning, the
  ADB development permission, and preferences commits requiring Boolean results.
- Packaged APK permission inspection with `aapt2 dump permissions`: only
  `WRITE_SECURE_SETTINGS` and AndroidX's app-local, signature-protected
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No internet permission.
- Official Gradle 8.13 wrapper JAR checksum matched
  `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.
  Distribution checksum is pinned in wrapper properties.
- `git diff --cached --check` passed for the staged implementation.

Unit coverage includes missing/revoked permission and permission-check exceptions,
active correction conflicts, unfamiliar modes, saving before mutation, null/absent
restoration, repeated toggles, storage/read/write failures, false write results,
read-back mismatches, partially applied writes, process restart recovery, all three
restoration write failures, failed record clearing and external settings conflicts.

## Initial failures resolved

- Initial lint run failed on pinned-version/target notices, the development-only
  permission, Windows SDK path escaping, missing backup rules/icon and intentional
  synchronous commit calls. Resources and path were corrected; deliberate choices
  have narrow documented lint exceptions.
- Follow-up lint found an unnecessary icon API qualifier and missing themed icon.
  Both were corrected. Incremental resource linking then retained stale icon state;
  the final clean rebuild above passed.
- An ADB invocation inside the sandbox could not start its daemon. A permitted
  invocation outside the sandbox succeeded and listed no attached devices.

## Warnings and checks not performed

The first build emitted an SDK XML/tooling version warning. Build Tools 35.0.0 were
installed using existing accepted SDK licenses. The clean build emitted a warning
that `libandroidx.graphics.path.so` could not be stripped and was packaged as-is.
Neither warning failed the build or lint.

No Android device or emulator was attached during that automated run. Later
user-provided PR 1 physical acceptance evidence is recorded above; it does not
establish PR 2 service, receiver, or automatic-session behavior.
The [manual checklist and emergency recovery procedures](README.md) cover those
checks. API 37 is not targeted or device-validated. No separate formatter or other
static analyzer is configured; Kotlin compilation and Android Lint are the static
checks for this minimal project.

PR 1 was subsequently merged as `8db2271`. PR 2 subsequently merged as GitHub PR #3, commit `69b3fdb`, on October 13, 2026.
