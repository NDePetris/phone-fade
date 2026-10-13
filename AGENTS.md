# Offramp Fade — Repository Instructions

These instructions apply throughout this repository unless a deeper `AGENTS.md` provides more specific guidance. Read the relevant source code, `README.md`, and `VALIDATION.md` before changing behavior.

## Product and UX
Offramp Fade is a privacy-first, local-first Android/Kotlin app that encourages intentional breaks from phone use through reversible grayscale. It belongs to the Offramp family, but Android behavior, implementation, testing, and releases are owned in this repository.

- Use clear, conventional navigation and action labels. Driving-inspired language belongs only in optional secondary copy or subtle visuals.
- Prioritize reliability, accessibility, transparency, user control, and visible recovery actions.
- Avoid unnecessary data collection, permissions, network services, telemetry, and third-party SDKs. The app currently has no Internet permission; do not add one without explicit approval.
- Keep user-facing status honest when Android limits monitoring accuracy, foreground-service execution, notification visibility, or restoration.

## Critical safety boundary: grayscale and restoration
System color correction uses privileged `WRITE_SECURE_SETTINGS` development access and Android settings not guaranteed to behave identically on every device/build. Treat display settings as user-owned state.

- Preserve and verify the user's exact original settings, including absent keys, before changing them. Do not replace an existing recovery snapshot or discard pending recovery records on failure.
- Reject unrecognized states and external color-correction conflicts rather than overwriting them. Keep restoration and emergency ADB guidance available.
- A successful secure-settings write/read-back is **not** proof of the physical visual effect. Always distinguish unit tests from physical device validation.
- Never assume an Android force-stop, service termination, reboot, boot broadcast, or notification denial guarantees prompt restoration. Do not add silent resets, aggressive retries, or new background-start assumptions to conceal interruption.
- Changes to settings writes, checkpoint persistence, timers, lifecycle, service permissions, and boot recovery require explicit failure-path/regression coverage and a manual-device acceptance plan.
- Do not clear app data, uninstall, revoke the grant, or exercise destructive ADB recovery steps against a user's active grayscale state without protecting the external settings snapshot and obtaining explicit approval.

## Repository status
PR 1 manual grayscale and PR 2 automatic session monitoring are merged into `main`. PR 2 was merged as GitHub PR #3 (`69b3fdb`) on October 13, 2026; GitHub PR #2 was the engineering harness. Core Pixel 9 Pro behavior was reported validated in the merged PR, but not every interruption/recovery case. Read `README.md` and `VALIDATION.md` for the distinction between historical evidence and new feature acceptance. PR 3 temporary color and everyday experience require their own physical acceptance; passing automated tests is not physical validation.

Do not silently elevate feature status, change permission requirements, or claim successful physical acceptance based on simulations. Future code and branch state must be rechecked rather than inferred from this note.

## Development workflow
1. Inspect the implementation and documentation relevant to the task. For significant or high-risk behavior, describe the intended design, failure modes, and acceptance criteria before editing.
2. Favor cohesive, meaningful changes over artificially small PRs. Split work when architectural risk, dependencies, or review complexity justify it.
3. Write regression tests for meaningful behavior changes, especially restoration, settings conflicts, storage failures, elapsed time, and interrupted lifecycle.
4. Maintain clear separation between pure Kotlin logic, Android integrations, persistent recovery state, and UI. Prefer deterministic fake providers/clocks for unit testing, while recognizing their limitations.
5. Update existing README/VALIDATION documentation when behavior, permissions, limitations, or evidence changes. Do not duplicate extensive existing docs unnecessarily.
6. Do not merge, publish, deploy, release, or bypass a documented safety gate without explicit authorization.

## Build and validation
Use JDK 17, the pinned Gradle wrapper, Android SDK Platform 36, and Build Tools 35.0.0. From Windows PowerShell:

```powershell
./gradlew.bat assembleDebug testDebugUnitTest testReleaseUnitTest check lintDebug lintRelease --no-watch-fs --console=plain
```

From Unix-like environments, replace `./gradlew.bat` with `./gradlew`.

CI runs the same build/test/lint family on Linux and checks the APK for the absence of Internet permission. CI cannot validate Pixel 9 Pro display behavior, ADB grants, service lifecycle, notification delivery, force-stop, or reboot recovery.

In PRs, report separately: **automated checks**, **manual device tests (model and OS/build)**, **untested/blocked cases**, and **recovery or permission risk**. Never infer real-device safety from simulated test counts.
