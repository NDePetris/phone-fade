# Quiet Road implementation review

Branch: `feature/quiet-road-ui`; baseline: local and live remote `main` at `48f33701218a9d687e484a908e761b9244364d2a`. The only baseline untracked files were the user-supplied attachments, left intact. No reset, push, PR, merge or release.

## Before / after against Round 2

| Baseline | Implementation |
|---|---|
| Small split-circle mark and mostly plain canvas | Exact canonical family vector, compact identity row, Warm Paper canvas |
| Combined status/countdown with interruption suffix | Stable monitoring label, dominant display/countdown, separate persistent history |
| Plain operational column | Rounded illustrated hero with layered hills, cloud, grove and winding road |
| Filled disable action in ordinary monitoring | Outlined disable; filled eligible temporary-color/manual recovery/start actions |
| Manual mode receives automatic-break guidance | Explicit automatic-breaks-off and restoration guidance |
| Always-expanded timing fields | Saved summary plus one native editing disclosure; drafts survive closing |
| No explicit confirmed operation feedback | Durable-result snackbar; restoration distinguishes complete/failure/no-op |
| Incomplete color-role overrides | Explicit light/dark Material roles; dark shades adjusted for contrast |

The board's duplicate timing disclosures, routine display-verification line, filled ordinary disable and mockup-only features are intentionally not copied, per the implementation brief. Artwork and proportions still require rendered comparison.

## Artwork and canonical assets

`QuietRoadLandscape.kt` uses a bounded static Compose Canvas: distant hills, near hill, foreground, small grove, cloud, widened curved road, shoulder and dashed centerline. Theme-dependent tonal colors and a reading-area veil keep text above decorative artwork. Width scales; illustration height is bounded independently of naturally expanding hero content. No raster mockup, animation, library or session state is embedded.

`design/assets/offramp-primary.svg` is the unchanged canonical source from the prior handoff. SHA-256: `486aff70b2b4c2ae7b3cafa10da8822beca150e52b35bd54fff9f6ff7268ffe7`. `ic_offramp_family.xml` directly converts its paths, clip, fills, strokes and 256:266 viewport. A regression compares the conversion to the hashed source. The in-app mark is 40 x 41.5625 dp. Optical-small/reversed variants were not supplied; small-size/dark appearance needs native inspection. Existing launcher and notification icons remain unchanged.

Read the recovered shared `OFFRAMP_DESIGN.md` (SHA-256 `223f3584ea9c2470e975c9a5b5c70996391405704dabf8ccc84374f150c07131`). It remains a family handoff, not duplicated here. DESIGN.md records app-specific implementation choices; native acceptance remains pending.

## State and integration details

`QuietRoadPresentation` is in-app-only; existing notification formatters remain unchanged. Active ordinary color and temporary color show the existing rounded minutes/subminute formatter. Locked/off states retain static time. Manual grayscale never promises automatic reset. Automatic grayscale offers temporary color only through the unchanged predicate. Pending effects never claim success; attention shows the retained actual cause and prioritized recovery. Interrupted monitoring remains actionable; historical reconciliation is subdued supporting text with unchanged flag lifetime.

Coordinator `configure` and `disable` return small transient results after existing command execution and bookkeeping. Restoration success requires a prior original snapshot, no remaining pending operation or attention, and no storage failure. A snapshot-free completed command produces "No saved settings to restore." Temporary-color restoration is not announced as a disable operation. No checkpoint schema, settings-write sequence, session/reducer, boot/service or notification change supports feedback.

A nonpersistent `savedConfig` presentation value tracks only successful existing durable loads/commits. It prevents an unsuccessful in-memory configuration from replacing the active summary. Recovery data remains in existing journals. ViewModel-owned channel results are buffered while the screen is not resumed, consumed once, and not persisted/replayed across process recreation. If recreation interrupts a snackbar already consumed, it is not replayed; this favors avoiding duplicate success announcements. Failure remains in durable attention/diagnostics, not a false transient success.

Drafts stay separate from active values, survive disclosure/recreation through `rememberSaveable`, and never drive countdowns. Save is explicit, unavailable when invalid/unchanged/attention, and retains the 1–120 bounds. No autosave or discard was added.

## Preview and screenshot evidence

`QuietRoadPreviews.kt` declares ten synthetic screen variants across light, dark, 320 dp/2x font and landscape configurations. Open its Compose previews in Android Studio. These are **preview source fixtures, not rendered screenshots**. No screenshot is supplied or fabricated: ADB reports no target, and no configured AVD/system image is installed. Preview fidelity and large-font layout have not been visually accepted.

The focused Impeccable finish review examined source and the approved board. Its one source finding, predictable spoken seconds, was corrected and reread as resolved. Its source disposition does not establish native visual acceptance. No HTML detector ran because the app is native.

## Accessibility and validation limits

Native headings, disclosure expanded/collapsed state, numeric errors, selectable recovery commands, 48 dp action heights, scroll and safe/IME insets are present. Only stable status and actual attention causes use polite live regions. Countdown heading exposes a single title/duration description, using "1 second"/"32 seconds" without tick announcements. State does not rely on hue; artwork is decorative. No custom motion or simulated grayscale mode.

Actual token/solid-color/composited-veil contrast checks accompany unit coverage, but disabled Material state opacity is not treated as active text. Physical grayscale, focus order, TalkBack, pressed/disabled appearance and large-text reflow remain device checks. See VALIDATION.md for actual automated results and README.md for the Pixel checklist. The six Compose instrumentation cases are compiled but not executed without a target.

## Changed files and tradeoffs

- `FadeScreen`, `FadeTheme`, `QuietRoadLandscape`, `QuietRoadPresentation`, `QuietRoadPreviews`: five-area composition, themed static artwork, state copy and preview fixtures.
- `MainActivity`, `PhoneFadeCoordinator`: transient results and last durably committed configuration projection.
- Family vector/canonical source and seven small functional icon resources; removed obsolete in-app mark only.
- Gradle: standard Compose preview/tooling and test dependencies, instrumentation runner. UI-test manifest supplies a debug-only test host, not a product destination; production manifest is unchanged.
- New pure regression tests and focused synthetic Compose tests. README/VALIDATION plus small product/design references.

The landscape deliberately uses a few inspectable paths rather than a reusable rendering framework. No general component system, new font, dependency for illustration or new app architecture. Real rendering is the outstanding acceptance gate.

## Recommended PR

Title: **Implement Quiet Road presentation and confirmed operation feedback**

Description:

Replace the plain single-screen UI with the approved Quiet Road composition: canonical family mark, warm native theme, layered static landscape hero, state-specific guidance/action hierarchy, compact active timing summary and one draft-preserving editor. Keep recovery and permission instructions accessible; move interruption history below the countdown. Report save/restore completion through transient acknowledged results, including failure and no-op distinctions, without changing checkpoint schemas, display operations, session timing, foreground service or notifications.

**Automated checks:** See the latest VALIDATION.md entry for JDK 17 build/unit/check/lint, instrumentation APK compilation, permission inspection and actual test counts.

**Manual device tests:** Not performed for this redesign. No connected device/emulator or local AVD; preview fixtures are not rendered evidence.

**Untested/blocked:** Round 2 rendered fidelity; light/dark/large-text/IME/TalkBack; actual grayscale; confirmed-operation feedback on Android; notification appearance. Historical four recovery/safety gaps remain pending.

**Recovery/permission risk:** Privileged secure-settings grant and physical-display limitations unchanged. Exact snapshots and recovery operations retained. Keep external snapshot protection and explicit authorization before destructive recovery/device state tests.
