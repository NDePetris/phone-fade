# Offramp Fade validation

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
