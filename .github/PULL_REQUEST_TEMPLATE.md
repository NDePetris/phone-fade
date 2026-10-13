## Change and scope
What user behavior or maintenance outcome does this change provide? What is intentionally excluded?

## Validation
- [ ] Automated build, debug/release unit tests, Gradle `check`, and Android lint pass (include exact command/result)
- [ ] Regression and failure-path tests added/updated when behavior changes, or explain why not
- [ ] Physical Android testing documented with device and OS/build, **or explicitly marked not performed**
- [ ] README/VALIDATION updated if behavior, permissions, limitations, or test evidence changed

## Android safety and privacy
- Does this affect grayscale writes/restoration, persistent recovery, foreground service, timers, boot handling, notifications, permissions, or data handling?
- If yes: document failure cases, safe recovery path, relevant user-facing limitations, and actual validation evidence.
- Confirm whether the APK permissions changed. Do not introduce Internet permission or background data collection without explicit approval.

## Review notes
Assumptions, remaining risks, known limitations, and any manual acceptance required. Automated tests are not proof of visual restoration or Android lifecycle guarantees.
