# Adaptive Mobile Widget Families Implementation Plan

**Goal:** Add shared, size-adaptive glucose, graph, and therapy widget families with direct picker presets, isolated settings, atomic updates, and no regressions to the existing compact widget.

**Architecture:** Keep the existing four logical widget kinds and introduce provider presets as metadata-only receiver subclasses. Classify measured host dimensions into supported family layouts, feed those layouts into focused bitmap renderers, and keep all data sourced from canonical `TherapyDisplayState`. Use per-instance actions and persistence for graph scale cycling.

**Tech Stack:** Kotlin, Android AppWidget/Glance, Compose configuration UI, Android graphics, JUnit/Robolectric, Gradle.

## Global constraints

- Preserve all current uncommitted widget fixes and per-instance configuration.
- Do not alter Wear, SugarWear, G7 collector, complications, notifications, or Mobile in-app graphs.
- Never show a loading placeholder; retain the last published widget until the next render is complete.
- Reuse the current canonical graph, trend, threshold, freshness, and color architecture.

## Review focus

- Samsung host dimensions near size boundaries must not oscillate between layouts.
- Existing provider class names and widget IDs must continue to resolve after update.
- A scale-label click must not also open the configured app.
- Missing TIR history must render unavailable rather than zero.
- Resize must preserve per-instance configuration and update only that widget.

### Task 1: Size classes and glucose presentation contracts

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetPresentation.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWidgets.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetPresentationTest.kt`

**Interfaces:**
- Produce `WidgetFamilyLayout` and deterministic `widgetFamilyLayout(widthDp, heightDp)`.
- Produce pure glucose text/TIR/layout models consumed by the renderer.

- [ ] Write failing boundary, 2x1, 2x2, 4x2, text, and TIR tests.
- [ ] Run the focused tests and confirm failures represent missing contracts.
- [ ] Implement size classification and pure presentation models.
- [ ] Run focused tests and confirm green.

### Task 2: Picker presets and provider resolution

**Files:**
- Modify: `app-mobile/src/main/AndroidManifest.xml`
- Create/modify: `app-mobile/src/main/res/xml/*_widget_info.xml`
- Modify: `app-mobile/src/main/res/values/strings.xml`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWidgets.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetInstanceConfiguration.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetCatalogContractTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetInstanceConfigurationTest.kt`

**Interfaces:**
- Provider presets delegate to existing logical `ConfigurableWidgetKind` values.
- Existing receiver names remain registered.

- [ ] Write failing provider-resolution and metadata contract tests.
- [ ] Verify red.
- [ ] Add preset receivers, XML metadata, labels, and family resolution.
- [ ] Verify green.

### Task 3: Adaptive glucose renderer

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWidgets.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetPresentationTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/GlucoseWidgetContractTest.kt`

**Interfaces:**
- Render 2x1 pill, 2x2 detail, and 4x2 split layouts from `WidgetFamilyLayout`.
- Reuse `renderWidgetGraph` for the 4x2 right side.

- [ ] Write failing geometry/render assertions for all three layouts.
- [ ] Verify red.
- [ ] Implement the three adaptive render modes, divider, delta icon, age, and TIR.
- [ ] Verify green and retain the old bitmap publication path.

### Task 4: Graph labels, contrast, and scale-cycle action

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetColors.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetInstanceConfiguration.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWidgets.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetUpdateCoordinator.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetPresentationTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetInstanceConfigurationTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetUpdateCoordinatorTest.kt`

**Interfaces:**
- Add a per-instance high-region graph-label color.
- Add an `ActionCallback` that cycles the canonical scale modes with wraparound and refreshes only its instance.

- [ ] Write failing color, label, cycle, isolation, and action tests.
- [ ] Verify red.
- [ ] Implement label rendering, settings/migration, separate click target, persistence, and update dispatch.
- [ ] Verify green.

### Task 5: Therapy capacity layouts and settings previews

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWidgets.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetInstanceConfiguration.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetPresentationTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetInstanceConfigurationTest.kt`

**Interfaces:**
- Therapy renderer consumes the size class and returns centered one-, two-, or 1+2-slot geometry.
- Settings preview uses the selected provider preset/measured widget dimensions.

- [ ] Write failing therapy slot and preview tests.
- [ ] Verify red.
- [ ] Implement capacity rules, geometry, and size-aware previews.
- [ ] Verify green.

### Task 6: Shared widget settings action pill

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetInstanceConfiguration.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetInstanceConfigurationTest.kt`

**Interfaces:**
- One bottom-centered floating pill exposes `Abbrechen | Speichern` for every widget kind.
- Edits remain local until save; cancel/back discard the session; save persists and refreshes exactly one widget instance.

- [x] Write a failing save/cancel state-contract test.
- [x] Remove eager/on-pause persistence and implement explicit save semantics.
- [x] Add the shared dark pill with vertical divider and verify the focused tests.

### Task 7: Automatic family shape and legacy catalog separation

**Files:**
- Modify: `app-mobile/src/main/AndroidManifest.xml`
- Modify: `app-mobile/src/main/res/values/strings.xml`
- Create: family-specific 2x1/2x2 provider metadata under `app-mobile/src/main/res/xml/`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWidgets.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetInstanceConfiguration.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetCatalogContractTest.kt`

- [x] Register family-specific providers without removing legacy provider classes.
- [x] Force pill geometry only at 2x1 and Samsung rounded geometry above 2x1.
- [x] Hide the manual form selector for automatic families.
- [x] Prefix all legacy picker entries with `Developer Widgets`.

### Task 8: Regression and device validation

**Files:**
- Modify tests only if verification exposes a real uncovered regression.

- [ ] Run all Mobile unit tests and inspect counts.
- [ ] Run `:app-mobile:assembleDebug`, lint, detekt, and ktlintCheck.
- [ ] Run `git diff --check` and inspect the complete diff for scope and migrations.
- [ ] Install the debug APK on the connected Samsung phone.
- [ ] Verify package version/update time and open representative configuration activities without crashes.
- [ ] Report CODE-VALIDATED, BUILD-VALIDATED, and HARDWARE-TEST OPEN separately.
