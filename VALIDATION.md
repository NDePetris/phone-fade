# Phone Fade validation

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

PR 2 physical acceptance remains pending. Use the dedicated [README checklist](README.md#pr-2-pixel-9-pro-acceptance-checklist-pending-physical-execution).
No PR 2 physical tests were executed or claimed during this implementation.

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

PR 1 was subsequently merged as `8db2271`. PR 2 remains on its separate feature
branch and is not pushed or merged by this implementation.
