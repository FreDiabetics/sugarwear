# Graph Scale and Appearance Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct Mobile IOB/COB axis placement, retain predictions in the full 24-hour view, and make shared appearance settings and widget scale placement consistent with Mobile.

**Architecture:** Preserve the existing graph renderers and per-surface persistence. Extract pure geometry/policy functions for testable axis placement and 24-hour viewport behavior, extend the existing `AppearanceTerminology` into the canonical metadata source, and route existing Mobile/Wear/G7/widget controls through shared labels and numeric contracts without creating a second settings system.

**Tech Stack:** Kotlin, Android Views/Canvas, Jetpack Compose, Wear OS, Robolectric/JUnit, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-18-graph-scale-and-appearance-consistency-design.md`

## Global Constraints

- Mobile terminology and control semantics are authoritative for shared appearance settings.
- Appearance scope only; Collector, sensor, alarm, background and data-source settings remain functionally separate.
- IOB and COB keep independent scales and existing scale modes.
- Full history remains 24 hours; enabled predictions add a future lane instead of reducing history.
- Existing non-24-hour live scale-side interpolation remains unchanged.
- CGM outline widths use `0.25–3.0 dp` everywhere.
- Preserve existing preference keys and valid stored values.

## Review Focus

- Mixed positive/negative IOB ranges: max, min and zero ticks must share the curve transform.
- Large COB treatment labels: labels may overflow upward but must not alter COB ticks or scale bounds.
- 23.99h versus exactly 24h: only the full 24-hour state forces predictions and the left scale.
- Empty or disabled predictions at 24h: do not create a fake future lane.
- Narrow widgets and large fonts: right-lane labels remain readable and adjacent to the plot boundary.

---

### Task 1: Correct metabolic scale geometry

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/DashboardChartsTest.kt`

**Interfaces:**
- Consumes: `GraphAxisScale`, `mapAxisY(value, scale, plot)`.
- Produces: `metabolicAxisTickLayout(plot: RectF, range: GraphAxisScale, textMetrics: Paint.FontMetrics): MetabolicAxisTickLayout` used by both IOB and COB drawing.

- [ ] Write failing tests asserting maximum/minimum/zero tick centers equal `mapAxisY` for linear, dynamic, logarithmic and negative IOB ranges.
- [ ] Run `:app-mobile:testDebugUnitTest --tests '*DashboardChartsTest*'`; expect new assertions to fail on baseline-offset geometry.
- [ ] Implement the pure layout helper and make `drawMetabolicScale` center labels around mapped tick positions.
- [ ] Add a regression proving bolus/carb label amounts do not affect tick coordinates.
- [ ] Re-run the focused tests; expect PASS.
- [ ] Commit the task files.

### Task 2: Preserve predictions in the full 24-hour viewport

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousOverviewScreen.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/ChartViewportRegressionTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/DashboardChartsTest.kt`

**Interfaces:**
- Consumes: `ChartViewport`, `PredictionDisplayTimeline.futureWindowMs`, `predictionScaleRightBlend`.
- Produces: `resolvePredictionViewportPolicy(historyHours: Float, predictionsEnabled: Boolean, futureWindowMs: Long): PredictionViewportPolicy` with `effectiveFutureWindowMs` and `scaleRightBlendOverride`.

- [ ] Write failing tests for tapped 24h, pinch-zoomed 24h, 23.99h, disabled/empty predictions and preserved historical duration.
- [ ] Run the two focused test classes; expect 24h future-window assertions to fail.
- [ ] Implement the policy so exactly full 24h retains history plus enabled prediction future time and forces the scale left (`0f`).
- [ ] Keep `predictionScaleRightBlend` unchanged for all non-24h viewports and add a regression for its current interpolation.
- [ ] Re-run focused tests; expect PASS.
- [ ] Commit the task files.

### Task 3: Canonicalize appearance terminology and numeric contracts

**Files:**
- Modify: `core-model/src/main/kotlin/app/aapswear/model/AppearanceTerminology.kt`
- Create: `core-model/src/main/kotlin/app/aapswear/model/AppearanceControlContract.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousColorSettingsPanel.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetInstanceConfiguration.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/WearSettingsActivity.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7AppearanceActivity.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7DirectToWatchSettingsActivity.kt`
- Test: `core-model/src/test/kotlin/app/aapswear/model/AppearanceTerminologyTest.kt`
- Test: existing settings contract/layout tests in Mobile, Wear and G7.

**Interfaces:**
- Consumes: existing preference keys and surface-specific setters.
- Produces: `AppearanceControlContract` metadata for labels, `0.25f..3.0f` outline range, common steps and value formatting.

- [ ] Write failing catalog tests enumerating shared semantic IDs and asserting identical names/ranges across registered surfaces.
- [ ] Run focused core/settings tests; expect mismatched labels and outline ranges to fail.
- [ ] Extend the existing terminology catalog using Mobile wording, including `CGM-Dot-Kontur`, boundary lines and separate current/history outline widths.
- [ ] Replace duplicated shared labels/ranges in Mobile widgets, Wear and G7 with catalog values while retaining their own persistence.
- [ ] Route shared Colorpicker, slider, dropdown and preview configurations through the same metadata; leave functional platform-only settings unchanged.
- [ ] Run focused settings tests; expect PASS.
- [ ] Commit the task files.

### Task 4: Align widget scale labels with the plot boundary

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetPresentation.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWidgets.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetPresentationTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetGraphRendererContractTest.kt`

**Interfaces:**
- Consumes: widget plot/scale-lane geometry.
- Produces: one canonical label anchor gap matching the Mobile graph and used by productive widgets plus previews.

- [ ] Write failing pixel/geometry tests for high/low labels adjacent to the Now/plot boundary at supported widget sizes.
- [ ] Run focused widget tests; expect current excessive right offset to fail.
- [ ] Move anchors left to the canonical gap without clipping text, contour or edge; apply the same geometry to settings previews.
- [ ] Re-run focused widget tests; expect PASS.
- [ ] Commit the task files.

### Task 5: System verification and device installation

**Files:**
- Modify tests only if a newly exposed product defect requires a regression.

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces: green repository and installed debug builds.

- [ ] Run focused Mobile, Widget, Wear, G7, protocol and renderer tests.
- [ ] Run `test assembleDebug :app-mobile:assembleRelease :app-wear:assembleRelease :g7watch:assembleRelease lint detekt ktlintCheck`; expect BUILD SUCCESSFUL.
- [ ] Run `git diff --check`; expect no errors.
- [ ] Perform a fresh whole-change code review; fix Critical/Important findings with RED→GREEN tests.
- [ ] Install `app-mobile` on the phone and `app-wear` plus `g7watch` on the watch using explicit ADB serials; verify all package paths.
- [ ] Report CODE-VALIDIERT, BUILD-VALIDIERT and HARDWARE-TEST OFFEN separately.
