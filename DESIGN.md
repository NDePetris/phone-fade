---
name: Offramp Fade — Quiet Road
description: App-specific reference for the implemented native Android visual system.
colors:
  light-primary-moss: "#2E5A4F"
  light-paper: "#F6F3EC"
  light-ink: "#1F2B28"
  light-hero: "#DFE9E3"
  light-card: "#FAF7F1"
  light-secondary-sky: "#355D70"
  light-setup: "#DCEAF0"
  light-outline: "#6B776F"
  light-error: "#943E38"
  light-error-container: "#FBE9E7"
  dark-primary-sky: "#79AFC9"
  dark-background: "#1F2B28"
  dark-paper: "#F6F3EC"
  dark-hero: "#263F35"
  dark-card: "#263832"
  dark-secondary-moss: "#A4C4AE"
  dark-setup: "#30463E"
  dark-muted-text: "#C8BCA7"
  dark-outline: "#7F9B89"
  dark-error: "#F1A59D"
  dark-error-container: "#4A2725"
typography:
  native:
    fontFamily: "Android system default via Material 3"
rounded:
  hero: "28dp"
  card: "20dp"
  attention: "12dp"
spacing:
  screen-horizontal: "20dp"
  screen-vertical: "24dp"
  sections: "16dp"
  hero-padding: "24dp"
  hero-rhythm: "12dp"
components:
  primary-action:
    height: "minimum 48dp"
  outlined-action:
    height: "minimum 48dp"
---

# Design System: Offramp Fade — Quiet Road

## Overview

**Creative North Star: “Quiet Road.”** Warm paper, moss, and a restrained illustrated landscape support a calm native Android experience. This is the app's implemented reference, not authority for the wider Offramp family. The user approved the Round 2 direction; the values here reflect the final code, including darker dark-mode hero and card surfaces.

Sources: [FadeTheme.kt](app/src/main/java/app/ndplabs/phonefade/FadeTheme.kt), [FadeScreen.kt](app/src/main/java/app/ndplabs/phonefade/FadeScreen.kt), and [QuietRoadLandscape.kt](app/src/main/java/app/ndplabs/phonefade/QuietRoadLandscape.kt). No rendered native screenshots or physical acceptance establish these values as validated. Native acceptance is still required. See [PRODUCT.md](PRODUCT.md) for the brief and [README.md](README.md) / [VALIDATION.md](VALIDATION.md) for behavior, safety, and evidence.

## Colors

Light mode uses Moss for primary actions, warm Paper for the canvas, and Ink for text. The pale hero carries dark text; the near-paper cards separate supporting controls. Sky gives permission setup its own tonal surface.

Dark mode uses Sky for primary actions, Paper for text, and distinct dark green background, card, and hero surfaces. Muted text uses the warm neutral. Errors use dedicated error containers in both themes. Follow the explicit Material color roles in `FadeTheme.kt`; the frontmatter records the main reused screen colors, not every theme role or illustration shade.

## Typography

Use native Material 3 typography and Android system type; no custom font is installed. Countdown numerals use bold `displayLarge`; countdown headings use bold `headlineSmall`, other hero headings bold `headlineMedium`. The app title uses bold `titleLarge`; timing titles use semibold `titleMedium`. Supporting text uses `bodyMedium` and `bodySmall`; status and disclosures use `labelLarge`. ADB commands use monospace.

## Layout

One vertically scrolling column respects safe drawing insets and the keyboard. Content is centered with a maximum width (600dp). Use the screen padding and section rhythm above. Rows of timing values and countdown fragments wrap through native `FlowRow`; avoid fixed text heights. All action buttons and disclosure controls have a minimum height (48dp).

## Elevation & Depth

The screen uses tonal surfaces rather than custom shadows. The hero illustration is a static Compose Canvas (280dp high), with a canvas-colored horizontal veil and lower fade protecting text. Native Material component behavior remains in place; no custom motion system is established.

## Shapes

Use the generous hero and card corners above, with smaller attention-container corners. Buttons and outlined fields retain Material 3 shapes. The exact canonical family logo is `ic_offramp_family`, displayed at its preserved aspect ratio (40 × 41.5625dp). Reuse the asset; do not redraw, reinterpret, or invent its geometry.

## Components

- **Status hero:** illustrated tonal surface, status badge, heading/countdown, guidance, and state-specific primary/outlined actions. The landscape is decorative and carries no status information. Only stable status/cause announcements use polite live regions; countdown ticks do not.
- **Timing settings:** tonal card with saved values and a disclosure for outlined numeric fields. Native error/supporting text and disabled states remain explicit. Unsaved edits have a visible label.
- **Recovery and diagnostics:** disclosed tonal card with selectable commands and full-width outlined actions. Attention opens it automatically.
- **Permission setup:** contrasting tonal card with selectable monospace command text.
- **Controls:** native filled, outlined, and text buttons. Outlined action borders use the theme outline (1dp). Disclosure icons indicate expanded/collapsed state through semantics as well as appearance.

## Do's and Don'ts

- **Do** preserve native text scaling, wrapping, semantic headings, visible state labels, and recovery controls.
- **Do** keep illustration subordinate to readable content and use the final explicit light/dark theme values.
- **Don't** substitute a bitmap mockup, custom display font, or reconstructed family logo.
- **Don't** treat code inspection or automated checks as rendered or physical Android acceptance.
