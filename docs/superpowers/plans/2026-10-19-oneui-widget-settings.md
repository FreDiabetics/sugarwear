# One UI widget settings implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Execute inline; user explicitly excludes repeated approval loops.

**Goal:** Match the supplied Samsung widget settings reference while preserving all configuration behavior.

**Architecture:** A shared One UI shell and controls wrap the existing WidgetConfigurationActivity. Production renderers and persistence remain canonical. Public wallpaper window composition supplies the preview backdrop without reading wallpaper data.

**Tech Stack:** Kotlin, Compose, Android window API, native SeekBar, existing Glance rendering.

**Spec:** `docs/superpowers/specs/2026-10-19-oneui-widget-settings-design.md`

## Global constraints
- No G7/Wear/data-source changes; no hidden APIs or new permissions.
- Existing widget instances and Save/Cancel semantics remain intact.
- Original native picker names/direct Developer selection; Samsung controls sorting.

## Review focus
- Native versus legacy resize ratio: preview aspect and dimensions remain faithful.
- Scroll/offscreen preview: wallpaper never leaks outside its aperture.
- System light/dark versus edited palette: controls stay readable.
- Slider keyboard/TalkBack: integer values and interaction remain available.
- Cancel/back and failed save: no silent persistence or lost edits.

### Task 1: Shared shell and controls
Files: create `app-mobile/src/main/kotlin/app/aapswear/mobile/OneUiWidgetSettings.kt`; test `OneUiWidgetSettingsTest.kt`.
- [x] Add failing geometry/window tests for uniform preview fitting and public wallpaper flags.
- [x] Implement theme tokens, transparent wallpaper aperture, header, reference cards, native accessible slider and action capsule.
- [x] Run focused tests, confirm geometry and window behavior.

### Task 2: Integrate existing widget settings
File: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetInstanceConfiguration.kt`.
- [x] Wrap existing controls in the shared shell; retain all controls and isolated edit state.
- [x] Render production preview in a fixed-height wallpaper frame with native/legacy resize ratio.
- [x] Replace control presentation with shared One UI cards/rows/sliders/actions; harden save to stage rendering before persistence.
- [x] Run configuration, preview and presentation regression tests.

### Task 3: Validate and install
- [x] Run Mobile test/build/lint/detekt/ktlint gates proportionate to the UI change.
- [x] Install on the explicitly identified connected Mobile target.
- [x] Capture settings screenshot, compare wallpaper/geometry/actions to Samsung reference; verify Save and Cancel on the actual widget instance.
- [x] Record exact hardware coverage and remaining limits; do not label untested states hardware validated.

Validation 2026-10-19: 122 widget tests passed; assembleDebug, lint, detekt and ktlintCheck passed. Independent code review clear after restoring legacy radius control and staging rendering before save. Installation successful on SM_S948B. Final visual/Save/Cancel hardware checks await device unlock; native picker sorting/grouping is explicitly accepted as Samsung-controlled.

Hardware follow-up after unlock: actual wallpaper preview confirmed on therapy and glucose4x2 settings. Cancel preserved the complete preferences hash; Save changed therapy opacity immediately on launcher. Original 100% opacity restored and verified. Coverage does not establish pixel-exact Samsung-control identity or every widget/size/theme combination.
