# Capability spike validation

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

No Android device or emulator was attached. Installation, actual ADB permission
grant/revocation, physical grayscale/color effects, provider deletion semantics,
durability across force-stop/reboot, and Pixel 9 Pro UI behavior remain unverified.
The [manual checklist and emergency recovery procedures](README.md) cover those
checks. API 37 is not targeted or device-validated. No separate formatter or other
static analyzer is configured; Kotlin compilation and Android Lint are the static
checks for this minimal project.

The implementation remains on its feature branch; it has not been merged into main.
