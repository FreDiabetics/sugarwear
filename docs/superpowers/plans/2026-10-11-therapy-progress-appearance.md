# Therapy Progress Appearance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add complete, independent global and per-metric geometry controls plus one-, two-, and three-slot selection layouts for the Mobile therapy widget and Sugarlicious Wear therapy Tile.

**Architecture:** A renderer-neutral model in `core-model` owns validated settings and effective dimensions. Mobile stores a profile per widget instance and renders resolved horizontal slots; Wear stores one local therapy-Tile profile and renders resolved circular-safe slots. Existing presentation, progress semantics, colors, icons, and renderers remain authoritative.

**Tech Stack:** Kotlin, Android SharedPreferences, Jetpack Glance bitmap rendering, Wear ProtoLayout, Robolectric/JUnit, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-11-therapy-progress-appearance-design.md`

## Global Constraints

- Mobile-widget and Wear-Tile profiles remain independent.
- At least one of IOB, COB, and basal is always enabled.
- Metric order is IOB, COB, basal after visibility filtering.
- Existing data values and `TherapyProgressSemantics` are unchanged.
- Existing colors, icon identities, transparency, outline, pill shape, and light/dark behavior remain intact.
- Defaults reproduce the current three-indicator layout.
- Persisted settings are never rewritten merely because current bounds require safe render-time scaling.

## Review Focus

- Corrupt or unknown stored metric sets recover to all three metrics; Task 1 and Tasks 2/4 add explicit recovery tests.
- Unequal per-metric scales must preserve symmetric slot centers without clipping; Tasks 1, 3, and 5 test extreme combinations.
- Disabling the second-to-last metric must work while disabling the final metric must not; Tasks 2 and 4 test both transitions.
- Tiny launcher/round-Tile bounds must proportionally constrain all selected indicators instead of dropping one; Tasks 1, 3, and 5 test minimum bounds.
- Existing widget instances and Wear installations with no geometry keys must render the current defaults; Tasks 2 and 4 test migration-free defaults.

---

### Task 1: Shared therapy appearance and geometry policy

**Files:**
- Create: `core-model/src/main/kotlin/app/aapswear/model/TherapyProgressAppearance.kt`
- Test: `core-model/src/test/kotlin/app/aapswear/model/TherapyProgressAppearanceTest.kt`

**Interfaces:**
- Produces: `TherapyProgressMetric`, `TherapyMetricAppearance`, `TherapyProgressAppearance`, `ResolvedTherapyIndicator`, `TherapySlot`, `resolveHorizontalTherapyLayout(...)`, and `resolveRoundTherapyLayout(...)`.
- Defaults/ranges: group 50..150%, gap 0..24 dp, ring 50..200%, primary/secondary text 60..180%, icon 50..180%, and per-metric overall scale 60..140%; every default is 100% and gap defaults to 6 dp.

- [ ] **Step 1: Write failing model and geometry tests** covering defaults, clamping, effective global-times-individual scaling, all seven non-empty selections, symmetric one/two/three horizontal slots, triangular round slots, unequal sizes, and tiny bounds.
- [ ] **Step 2: Run** `.\gradlew.bat :core-model:test --tests "app.aapswear.model.TherapyProgressAppearanceTest"` and verify the missing interfaces fail compilation.
- [ ] **Step 3: Implement the model and pure deterministic layout resolvers** with validated copies and render-time proportional fitting.
- [ ] **Step 4: Run the targeted tests** and require PASS.
- [ ] **Step 5: Commit** the shared model and tests.

### Task 2: Mobile per-widget persistence and controls

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetInstanceConfiguration.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetInstanceConfigurationTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetPresentationTest.kt`

**Interfaces:**
- Consumes: `TherapyProgressAppearance` from Task 1.
- Produces: `WidgetInstanceConfiguration.therapyProgressAppearance` persisted under the widget instance ID, plus configuration controls and reset behavior shown only for `METABOLIC`.

- [ ] **Step 1: Write failing persistence tests** for defaults, round-trip of every global/per-metric field, independent widget IDs, corrupt values, all metric selections, and final-visible protection.
- [ ] **Step 2: Run the targeted Mobile tests** and verify failure.
- [ ] **Step 3: Add read/write support** without changing existing keys or other widget kinds.
- [ ] **Step 4: Add visibility switches, global sliders, per-metric sections, reset, and production-renderer preview** to the existing configuration activity.
- [ ] **Step 5: Run persistence and presentation tests** and require PASS.
- [ ] **Step 6: Commit** Mobile configuration changes.

### Task 3: Mobile widget dynamic slot rendering

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWidgets.kt`
- Modify: `app-mobile/src/main/res/layout/widget_metabolic_preview.xml`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetPresentationTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetInitialLayoutContractTest.kt`

**Interfaces:**
- Consumes: Task 1 horizontal layout and Task 2 per-widget profile.
- Produces: bitmap rendering for one, two, or three selected indicators with individually resolved diameter, stroke, primary/secondary text, icon size, and shared gap/group extent.

- [ ] **Step 1: Write failing rendering tests** for all seven selections, equal center spacing, equal outer margins, different per-metric sizes, icon-ring color equality, pill clipping, transparency, and minimum/maximum host bounds.
- [ ] **Step 2: Run targeted widget tests** and verify failure.
- [ ] **Step 3: Replace hard-coded 66/7/primary/secondary/icon dimensions** with resolved geometry while keeping existing values, icons, and colors.
- [ ] **Step 4: Update the static launcher preview** to reflect the default three-indicator layout without changing widget provider dimensions.
- [ ] **Step 5: Run all Mobile widget tests** and require PASS.
- [ ] **Step 6: Commit** Mobile renderer changes.

### Task 4: Wear therapy-Tile persistence and settings

**Files:**
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/WearTileAppearanceStore.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/WearSettingsActivity.kt`
- Test: `app-wear/src/test/kotlin/app/aapswear/wear/WearTileAppearanceStoreTest.kt`
- Create: `app-wear/src/test/kotlin/app/aapswear/wear/WearSettingsActivityTest.kt`

**Interfaces:**
- Consumes: `TherapyProgressAppearance` from Task 1.
- Produces: `TherapyTileGeometryStore.read/write/reset`, existing selection migrated into the profile, settings controls, and Tile refresh after committed changes.

- [ ] **Step 1: Write failing store tests** for defaults, complete round-trip, legacy selection migration, independent color storage, corrupt recovery, and non-empty selection enforcement.
- [ ] **Step 2: Run targeted Wear store tests** and verify failure.
- [ ] **Step 3: Implement the dedicated Wear geometry store** while retaining `TherapyTileSelectionStore` as a compatibility facade during migration.
- [ ] **Step 4: Extend the existing Tile 2 settings** with visibility, global sliders, individual metric sections, reset, and immediate Tile updates; reuse `sliderCard` and existing categories.
- [ ] **Step 5: Run Wear settings/store tests** and require PASS.
- [ ] **Step 6: Commit** Wear persistence and settings changes.

### Task 5: Wear Tile dynamic circular-safe rendering

**Files:**
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/SugarliciousTiles.kt`
- Test: `app-wear/src/test/kotlin/app/aapswear/wear/SugarliciousTilesTest.kt`

**Interfaces:**
- Consumes: Task 1 round layout and Task 4 Wear profile.
- Produces: ProtoLayout therapy indicators with resolved one-center, two-row, or three-triangle slots and independent diameter, stroke, primary/secondary text, icon, and gap dimensions.

- [ ] **Step 1: Write failing Tile tests** for every metric combination, symmetric slot coordinates, circular safe-area containment, unequal metric sizes, configured text/icon/stroke dimensions, and default compatibility.
- [ ] **Step 2: Run targeted Tile tests** and verify failure.
- [ ] **Step 3: Replace count-only hard-coded geometry** with the shared resolved slots and apply all effective dimensions to ProtoLayout elements.
- [ ] **Step 4: Ensure each icon remains tinted with its ring accent** and all selected metrics retain canonical values/progress.
- [ ] **Step 5: Run all Wear Tile tests** and require PASS.
- [ ] **Step 6: Commit** Wear renderer changes.

### Task 6: Cross-module verification and deployment

**Files:**
- Modify only files required by failures caused by Tasks 1-5.

**Interfaces:**
- Consumes: all preceding tasks.
- Produces: green Mobile/Wear gates and installable debug artifacts.

- [ ] **Step 1: Run relevant unit suites:** `.\gradlew.bat :core-model:test :app-mobile:testDebugUnitTest :app-wear:testDebugUnitTest`.
- [ ] **Step 2: Run builds:** `.\gradlew.bat :app-mobile:assembleDebug :app-wear:assembleDebug`.
- [ ] **Step 3: Run quality gates:** `.\gradlew.bat lint detekt ktlintCheck` and fix every new issue without weakening rules.
- [ ] **Step 4: Run `git diff --check`** and inspect the complete diff for unrelated changes.
- [ ] **Step 5: Install the Mobile and Wear debug variants on their matching connected devices using the repository-derived Gradle install tasks.**
- [ ] **Step 6: Report `CODE-VALIDIERT`, `BUILD-VALIDIERT`, installation evidence, and `HARDWARE-TEST OFFEN` separately.**
