# Open Graph and Tile Root-Cause Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair the remaining Mobile graph and Wear tile defects at their productive render and persistence paths.

**Architecture:** Mobile graph work extends the existing `GraphScaleStrategy`, `GraphViewportState`, and `GlucoseDashboardChart` pipeline; it does not introduce another graph renderer. Wear work extends the existing shared typography and therapy-indicator presentation contracts and keeps ProtoLayout geometry derived from available bounds.

**Tech Stack:** Kotlin, Android Views/Canvas, Wear ProtoLayout, SharedPreferences, Robolectric/JUnit, Gradle Android Lint.

**Spec:** `docs/superpowers/specs/2026-09-24-open-graph-tile-root-cause.md`

## Global Constraints

- Preserve CGM freshness, resolver, collector, BLE, and data-integrity behavior.
- Keep time-to-X shared while CGM, IOB, COB, and activity Y transforms remain independent.
- Do not add fixed per-device offsets or a parallel renderer.
- Automated success does not substitute for real-device visual acceptance.
- Continue from one phase to the next without requesting approval; stop only for a genuinely unresolved product decision or a protected external side effect.

## Review Focus

- Migrating existing preference values must not conflate progress-ring maxima with graph maxima.
- Negative IOB and large COB values must remain visible without affecting CGM bounds.
- Pan/zoom must transform targets and CGM identically at both viewport extremes.
- ProtoLayout must not silently ignore the requested font family.
- Valid canonical history must survive the tile state/cache/resource path and create visible curve pixels for every freshness class that retains displayable history.
- Two-metric and one-metric therapy tile selections must remain valid while correcting the three-metric layout.

---

### Task 1: Prove productive paths and previous failed assumptions

**Files:**
- Inspect: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt`
- Inspect: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousOverviewScreen.kt`
- Inspect: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt`
- Inspect: `core-model/src/main/kotlin/app/aapswear/model/GraphScaleStrategy.kt`
- Inspect: `core-model/src/main/kotlin/app/aapswear/model/WearGlucoseCardPresentation.kt`
- Inspect: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7CollectorTileService.kt`
- Inspect: `app-wear/src/main/kotlin/app/aapswear/wear/SugarliciousTiles.kt`

- [ ] Trace Settings -> preference keys -> dashboard state -> bind arguments -> Canvas scale resolution.
- [ ] Trace target samples and CGM samples through the exact viewport/time transform.
- [ ] Trace AAPS insulin activity from adapter data into the rendered activity path.
- [ ] Trace app and tile typography through actual Android Typeface and ProtoLayout font-family resolution.
- [ ] Compare commits `73b0b2c5`, `9a03a766`, `a32a25d4`, `bc441773`, `e45d9b80`, and `35564626`; record the disproven assumptions in the task commit.

### Task 2: Independent IOB and COB graph maxima

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousOverviewScreen.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/MainActivityTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/DashboardChartsTest.kt`

**Interfaces:**
- Produces: separately persisted IOB/COB graph maximum preferences supplied to the productive renderer.

- [ ] Add failing persistence tests distinguishing ring maxima from graph maxima and proving restart restoration.
- [ ] Add failing rendering tests proving CGM-bound changes cannot change IOB/COB pixel Y and that large/negative values remain visible.
- [ ] Add visible settings controls `IOB Graph Maximum` and `COB Graph Maximum` with bounded unit-aware values.
- [ ] Feed those values into the existing `GraphScaleSession` as explicit IOB/COB bounds, retaining one scale per axis.
- [ ] Run focused Mobile tests and commit the independently verified result.

### Task 3: Shared target and CGM horizontal viewport

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/DashboardChartsTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/CgmGraphCustomizationTest.kt`

**Interfaces:**
- Consumes: `GraphViewportState` and the graph's canonical time bounds.
- Produces: one time-to-X function used by CGM and target-value samples.

- [ ] Add failing pixel/coordinate tests for left pan, right pan, large pan, and zoom plus pan.
- [ ] Remove any fixed-overlay or independently derived target X coordinate.
- [ ] Route target history through the exact same time bounds and canvas transform as CGM history.
- [ ] Verify no counter-offset remains and commit.

### Task 4: Insulin activity data-to-pixel restoration

**Files:**
- Modify if proven necessary: AAPS adapter producing `TherapyHistorySample.insulinActivity`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/DashboardChartsTest.kt`

**Interfaces:**
- Consumes: real finite `insulinActivity` samples.
- Produces: visible activity path using the existing AndroidAPS-relative 0.8 height factor.

- [ ] Add a failing end-to-end test from adapter/state samples to non-empty visible pixels.
- [ ] Identify whether filtering, time bounds, scale, layer order, or clipping removes the line.
- [ ] Apply the minimal correction at the proven failing layer; do not synthesize activity from IOB.
- [ ] Verify activity remains independent from IOB/COB maxima and commit.

### Task 5: Static CGM lower boundary at 40 mg/dL

**Files:**
- Modify: `core-model/src/main/kotlin/app/aapswear/model/CgmGraphYScale.kt`
- Modify if required: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt`
- Test: `core-model/src/test/kotlin/app/aapswear/model/CgmGraphYScaleTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/DashboardChartsTest.kt`

- [ ] Add failing tests that STATIC maps 40 to ratio 0 and the lower plot edge.
- [ ] Assert dynamic and logarithmic-dynamic behavior remains unchanged.
- [ ] Remove any Mobile override that lowers the productive static CGM minimum below 40.
- [ ] Run core and Mobile graph tests and commit.

### Task 6: Real SugarWear typography parity

**Files:**
- Modify: `core-model/src/main/kotlin/app/aapswear/model/WearGlucoseCardPresentation.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7WatchActivity.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7CollectorTileService.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7GraphTileService.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7CollectorTilePresentationTest.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7GraphTileTest.kt`

- [ ] Add a failing contract test proving the app Typeface and ProtoLayout family/weight resolve to supported equivalent metrics rather than names alone.
- [ ] Determine the ProtoLayout-supported common family and weight for value, arrow, and meta glyphs.
- [ ] Use the shared semantic typography token in app and both tiles.
- [ ] Render representative `188`, arrow, and delta/age strings and inspect the generated layout/screenshot evidence.
- [ ] Commit typography separately from layout changes.

### Task 7: Gewebeglukose-Verlauf tile curve restoration

**Files:**
- Modify if proven necessary: canonical Wear history repository/state path
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7GraphTileService.kt`
- Modify if proven necessary: shared SugarWear graph model/renderer
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7GraphTileTest.kt`
- Test: shared history/graph tests owning the proven loss point

**Interfaces:**
- Consumes: the same canonical ordered CGM history, window, scale, gaps, thresholds, colours, and freshness semantics as SugarWear in-app.
- Produces: a ProtoLayout-compatible graph image/resource containing visible curve pixels and event-driven refresh on a new canonical value.

- [ ] Trace canonical history through repository, filtering, graph model, tile state/cache, renderer, resource packaging, and ProtoLayout; record point counts, bounds, and finite coordinates at each boundary.
- [ ] Add failing tests for normal multi-point history, one point, multi-hour history, gaps, out-of-order input, FRESH, AGING, STALE, NO_SOURCE, and new-value refresh.
- [ ] Add a failing rendering assertion that valid multi-point history produces curve-coloured pixels inside the frame, not merely a non-null model or visible contour.
- [ ] Correct the first proven loss/visibility boundary without adding a second data source, static curve, or polling loop.
- [ ] Verify app and tile consume equivalent canonical history/scale semantics and commit.

### Task 8: Geometric Wear therapy-tile composition

**Files:**
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/WearTileAppearanceStore.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/SugarliciousTiles.kt`
- Test: `app-wear/src/test/kotlin/app/aapswear/wear/WearTileAppearanceStoreTest.kt`
- Test: `app-wear/src/test/kotlin/app/aapswear/wear/SugarliciousTilesTest.kt`

**Interfaces:**
- Produces: bounds-derived placements for equal therapy rings and a value-only ring content model.

- [ ] Add failing structural tests proving no IOB/COB/Basal heading elements remain.
- [ ] Add failing geometry tests for equal diameters, top pair alignment, consistent vertical spacing, total-group centering, bottom gap, and icon placement.
- [ ] Replace label-plus-value column content with an optically centered value-only container.
- [ ] Calculate the two-over-one group bounding box from tile bounds, diameter, gap, and spacing; center that box as one unit.
- [ ] Keep one- and two-selection layouts valid and preserve BR/TBR icon semantics.
- [ ] Render round-display previews at representative small and large sizes, then commit.

### Task 9: Integrated verification and device acceptance handoff

**Files:**
- Update: relevant audit/report documentation only with measured evidence.

- [ ] Run focused red-green tests for every repaired root cause.
- [ ] Run `:core-model:test`, `:app-mobile:testDebugUnitTest`, `:app-mobile:assembleDebug`, and `:app-mobile:lintDebug`.
- [ ] Run `:app-wear:testDebugUnitTest`, `:app-wear:assembleDebug`, and `:app-wear:lintDebug`.
- [ ] Run `:g7watch:testDebugUnitTest`, `:g7watch:assembleDebug`, and `:g7watch:lintDebug`.
- [ ] Run rendering/preview checks, including a curve-pixel check for `Gewebeglukose-Verlauf`, and `git diff --check`.
- [ ] Install only the explicitly requested packages on explicitly identified devices.
- [ ] Report automated evidence separately from real-device visual acceptance; do not merge or push without instruction.
