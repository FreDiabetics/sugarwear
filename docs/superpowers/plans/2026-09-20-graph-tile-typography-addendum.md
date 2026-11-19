# Graph, Therapy Tile and Typography Addendum Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the new Mobile graph, IOB/COB scaling, circular Wear therapy tile and SugarWear typography requirements without regressing the green master-stability candidate.

**Architecture:** Extend existing graph scale/time-axis and therapy presentation abstractions instead of creating parallel renderers. Mobile and Wear keep platform-native drawing while sharing values, normalization and semantic typography tokens.

**Tech Stack:** Kotlin 2.4, Android Canvas/Compose, Wear ProtoLayout, SharedPreferences, JUnit/Robolectric, Gradle 9.6.1.

**Spec:** `docs/superpowers/specs/2026-09-20-graph-tile-typography-addendum-design.md`

## Global Constraints

- Preserve the canonical CGM/resolver/collector implementation already completed on this branch.
- Use measured time, one shared X transform and independent vertical axis scales.
- Do not add polling, a replacement graph, a second collector or UI-side source selection.
- Follow RED → GREEN → refactor for every behavior change.
- Do not merge or push before user hardware acceptance.

## Review Focus

- Panning and zooming must apply the horizontal offset exactly once to targets and therapy streams.
- The newest point must remain fully inside the plot with and without predictions.
- Large irrelevant historical carb entries must not inflate the current COB viewport.
- Every Wear therapy combination must reserve equal circular bounds independent of label length.
- ProtoLayout font fallback must preserve the SugarWear app's visible weight hierarchy.

---

### Task 1: Mobile graph coordinate geometry

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/DashboardChartsTest.kt`

**Interfaces:** Produces one viewport X mapping and derived rounded-plot/Now geometry used by all Mobile graph layers.

- [ ] Add failing tests for 250-line tangent geometry, newest-dot containment, target-step panning and zoom/pan parity.
- [ ] Verify each test fails for the expected current geometry.
- [ ] Implement derived tangent/Now bounds and remove any duplicate target offset.
- [ ] Run Mobile graph tests, assembly, lint, Detekt and ktlint.
- [ ] Commit the verified graph-coordinate correction.

### Task 2: IOB and COB scale semantics

**Files:**
- Modify: `core-model/src/main/kotlin/app/aapswear/model/GraphScaleStrategy.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/TherapyHeroIndicators.kt`
- Modify: existing Mobile settings/preferences files reached from `DashboardViews.kt`
- Test: matching core and Mobile tests

**Interfaces:** Produces shared IOB/ring normalization, persisted COB ring maximum and relevant-carb-aware COB graph bounds.

- [ ] Add failing tests for negative/high IOB, shared IOB maximum, COB 0/50/100 percent, relevant carb +50 g and exclusion of irrelevant meals.
- [ ] Verify RED results.
- [ ] Implement one IOB maximum path, separate persisted COB progress maximum and dynamic COB graph requirement values.
- [ ] Run core and Mobile focused gates.
- [ ] Commit the scale/settings correction.

### Task 3: Circular Wear therapy tile

**Files:**
- Modify: shared therapy presentation source as required
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/SugarliciousTiles.kt`
- Test: `app-wear/src/test/kotlin/app/aapswear/wear/SugarliciousTilesTest.kt`
- Test: `app-wear/src/test/kotlin/app/aapswear/wear/WearTileAppearanceStoreTest.kt`

**Interfaces:** Consumes Task 2 progress semantics and produces equal-size circular ProtoLayout indicators for all seven valid selections.

- [ ] Add failing structural tests for circular rings, equal diameter/stroke, deterministic placement and Mobile-equivalent progress/value semantics.
- [ ] Verify RED results for the current rectangular cards.
- [ ] Replace therapy cards with one reusable circular component and responsive group layout.
- [ ] Run Wear tests, assembly, lint, Detekt and ktlint.
- [ ] Commit the circular tile implementation.

### Task 4: SugarWear typography parity

**Files:**
- Modify/create shared semantic Wear typography token file in `core-model` or `ui-shared`
- Modify: active SugarWear app cards and G7 tile renderers under `g7watch`
- Test: G7 app/tile presentation and layout tests

**Interfaces:** Produces named semantic typography tokens used by SugarWear app and every active G7 tile.

- [ ] Add failing tests proving shared glucose/trend/delta/age/status/heading tokens and deterministic fallback weights.
- [ ] Verify current local constants fail the shared-token contract.
- [ ] Apply the tokens without changing SugarWear app's established visual hierarchy.
- [ ] Render existing visual QA previews and run the G7 focused gate.
- [ ] Commit typography consolidation.

### Task 5: Regression, cleanup and controlled handoff

**Files:**
- Modify: final audit report and only reachability-proven cleanup files

**Interfaces:** Produces a clean unmerged candidate for hardware acceptance.

- [ ] Re-scan active modules/resources/scripts and remove only newly proven dead paths.
- [ ] Run clean `test assembleDebug assembleRelease lint detekt ktlintCheck`.
- [ ] Run product-matrix QA, official WFF validation and DEX-free verification.
- [ ] Document exact baseline comparison and non-hardware-validated cases.
- [ ] Install Mobile and Galaxy Watch packages only after the gates pass; leave Pixel untouched unless explicitly requested.
- [ ] Commit the final evidence without merging or pushing.
