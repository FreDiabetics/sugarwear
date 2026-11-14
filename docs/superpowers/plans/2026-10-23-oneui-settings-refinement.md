# One UI Settings Refinement Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline, with targeted TDD.

**Goal:** Apply screenshot-faithful scrolling, appearance/unit cards and reset icons.
**Architecture:** Shared presentation geometry and controls; existing native/Compose
bridges, widget stores and event-driven updates remain authoritative.
**Tech Stack:** Kotlin, Android Views/Compose, Robolectric/JUnit.
**Spec:** docs/superpowers/specs/2026-10-23-oneui-settings-refinement-design.md

## Global constraints

- Work on existing main per user instruction; preserve dirty unrelated work.
- No source/resolver/collector/medical calculation changes; no publish/PR requested.
- Widget settings remain isolated; save immediate, cancel non-mutating.
- Native system font and wallpaper-derived accent colors; no hardcoded blue controls.
- English deferred as explicitly permitted, no partially translated language pack.

## Review focus

- Device mode changes while app is closed must refresh system-following widgets.
- Legacy widgets must retain current appearance after adding a mode field.
- Last-card padding must work with short pages, tall cards and large font scaling.
- Collapsed titles must not overlap cards/back or disappear prematurely.
- Reset controls must remain labelled for TalkBack and restore the same value.

### Task 1: Shared appearance/unit/reset controls and widget mode persistence

**Files:** new OneUiSettingsChoices.kt; DashboardViews.kt; OneUiWidgetSettings.kt;
WidgetInstanceConfiguration.kt; SugarliciousWidgets.kt; OneUiWidgetColorPicker.kt;
SugarliciousColorSettingsPanel.kt; MobileAppearanceModeTest.kt; relevant widget tests.

- [x] Test LIGHT/DARK/SYSTEM resolution, system-switch disabling, instance store
  round-trip and legacy fallback without changing global or other-widget settings.
- [x] Implement illustrated choice card, three-unit radio card, shared reset icon.
- [x] Replace all textual reset UI, retaining accessible action names/location.
- [x] Add per-widget display mode; preserve separate color-palette editing.
- [x] Cover system configuration refresh without polling, preserve save/cancel.
- [x] Run targeted tests and relevant formatting checks.

### Task 2: Shared scrolling header and bottom extent

**Files:** MobileSettingsScrollHeader.kt; MainActivity.kt; DashboardViews.kt;
WidgetInstanceConfiguration.kt; OneUiWidgetSettings.kt; MobileSettingsScrollHeaderTest.kt;
MainActivityTest.kt; OneUiWidgetSettingsTest.kt.

- [x] Test expanded/collapsing/compact/out-of-view title, back-circle threshold,
  negative scroll and last-card top reachability on short/tall viewports.
- [x] Replace fading-only title with shrink/alignment/flow geometry in native pages.
- [x] Apply shared geometry to widget pages, retaining wallpaper preview/action pill.
- [x] Add viewport-aware bottom extent so last card reaches the header.
- [x] Verify navigation, large font safety and scroll reversibility.

### Task 3: Verification, review and installation

- [x] Run affected tests, Mobile build, lint/format/static checks; fix actual regressions.
- [x] One focused fresh review per executing-plans; no repeated review loops.
- [x] Install on explicit phone serial; compare appearance, units, reset and scrolling.
- [x] Record evidence and remaining limitations without claiming pixel identity.

## Ledger

User follow-up: first upward drag snaps cards while the heading briefly anchors;
category cards also look oversized. Native updateSettingsScrollHeader changed
TextView textSize/padding on every scroll, requesting layout of the ScrollView
child. Reproduced by a real MainActivity regression (heading.isLayoutRequested).
Replaced those mutations with RenderNode scale/translation, preserving measured
geometry throughout the gesture. Compose already keeps a fixed title box.
Samsung device hierarchy measures normal category rows at 182 px / 3.5 = 52 dp;
Sugarlicious used 64 dp plus additional outer vertical padding. Category rows now
use 52 dp and their containing cards no duplicate vertical padding. Control rows,
font sizes, settings values, resolver and widget publication are unchanged.
Both new regression tests failed before implementation and passed afterward;
three shared header geometry tests also passed. Final device retest follows.

Snap follow-up completed: five focused tests passed. Final assembleDebug,
lintDebug, detekt and ktlintCheck succeeded (settings-snap-final-gates.log).
Installed on SM-S948B. Two identical slow 120px gestures moved the first category
text from y=1143 to 1051 to 959: equal 92px changes, no first-drag jump or snap.
Verified continued collapse, compact title and reversing to expanded state.
Screenshots: TEMP/settings-compact-cards.png, settings-small-drag-fixed.png,
settings-header-scaled.png. Runtime crash log empty; saved values unchanged.
Initial combined format/check run had a missing test blank line; corrected and
the separate final check is green. No checks were disabled or bypassed.

2026-10-23 follow-up hardware verification on SM-S948B: inspected the actual
Mobile overview/general/glucose-unit pages and existing widget 91 configuration.
Screenshots in TEMP: settings-overview.png, settings-collapse.png,
settings-compact.png, settings-bottom.png, settings-general.png,
settings-units.png, widget-settings-top.png, widget-compact-title.png,
widget-colorpicker.png. Expanded/compact/out-of-view headings, back-circle onset,
last-card reachability, illustrated SYSTEM/manual cards, palette-only manual card,
three-column units, wallpaper aperture and floating actions are visually verified.
No saved user setting was changed; widget page exited with Back, not Save.
Large-font layout remains covered by geometry tests, not a modified system-font
setting on the user's phone. Full TalkBack walkthrough is not claimed.

Residual audit found ComplicationCatalog's textual system-default action using
the old Button path. The regression test `complication defaults use the shared
reset icon instead of a text button` failed before replacing it with the shared
OneUiResetIcon; the existing reset callback is unchanged.

Follow-up final: that regression is GREEN; all 14 focused appearance/header/UI
tests passed (zero failures/errors). Mobile assembleDebug, lintDebug, detekt and
ktlintCheck passed in settings-reset-green.log. Final APK installed successfully
on the explicit SM-S948B serial and launched; bounded AndroidRuntime log check
reported no crash. No additional review loop, commit, push or unrelated cleanup.

2026-10-23 verification: 50 targeted Mobile tests passed, including the newly
reproduced legacy isolated-profile migration failure. Mobile assembleDebug,
lintDebug, detekt and ktlintCheck passed. Mobile installed successfully on SM-S948B.
One focused review completed; mode freezing, large-font header geometry and the
remaining system-default reset label were corrected. System-following refresh is
event-driven while the application process exists and reconciles on restart;
Android does not deliver callbacks to a force-stopped application. English GB/US
remains deferred as permitted. Full native settings visual comparison remains open;
the phone was in AndroidAPS, and no unrelated user settings were changed.

Queued hands follow-up executed: native picker still displayed the disputed hands.
Applied the explicitly authorized ApeX-only fallback: three choices, new dedicated
configuration key to retire previous IDs, matching picker and Mobile previews.
Orbit 0.1.6/code 7: 14 generator tests, WFF 5 schema, build and lint passed;
installed and activated on SM-L705F. The refreshed native picker visibly shows
the canonical outlined ApeX hands. Other configuration keys are unchanged.

Pre-flight: Tasks 1/2 share settings controls/theme; geometry must not drive persistence.
Ruling: Keep the existing dirty main checkout, as explicitly requested previously;
do not commit unrelated work or create competing replacement implementations.
Task 0 complete: Orbit 0.1.5, Wear and Mobile installed before starting these changes.

Queued user follow-up after this settings work: native hands still look unchanged.
Close/reopen the native editor and reactivate updated Orbit to rule out stale editor
art; verify an actual Ultra 2 choice, not one of unchanged ApeX sets. If still wrong,
user explicitly authorizes removing all non-ApeX hand sets and keeping only the
three ApeX choices. Do not interrupt current settings work for this check.
