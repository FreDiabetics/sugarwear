# Visible Wear Corrections Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver visibly matching SugarWear typography, real AndroidAPS insulin activity, and a configurable full-bleed graph Tile.

**Architecture:** Keep canonical data and the shared graph renderer. Remove the parallel in-app graph path, add a Tile-specific persisted style profile, and source activity from real AAPS algorithm data rather than fabricated IOB decay.

**Tech Stack:** Kotlin, Android Views/Canvas, Wear ProtoLayout/Tiles, SharedPreferences, Robolectric/JUnit, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-26-visible-wear-corrections-design.md`

## Global Constraints

- No text-as-bitmap solution.
- No invented medical values.
- No static graph screenshots or polling.
- Preserve LIVE/BACKFILL identity and freshness semantics.
- Do not weaken quality gates or merge.

## Review Focus

- Samsung font fallback must not diverge between View and ProtoLayout.
- Localized/changed AAPS reason strings must fail closed instead of producing false activity.
- Full-bleed geometry must remain safe on multiple round-display sizes.
- Tile settings must not overwrite in-app graph settings.
- Activity remains visible through all scale modes, scroll and zoom.

---

### Task 1: Typography parity

**Files:**
- Modify: `core-model/src/main/kotlin/app/aapswear/model/WearGlucoseCardPresentation.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7WatchActivity.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7CollectorTileService.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7GraphTileService.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7CollectorTilePresentationTest.kt`

**Interfaces:** Produces one explicit supported family and semantic weights consumed by app and both Tiles.

- [ ] Add failing parity tests for `188`, `→`, `-3 mg/dL · 2m`, and `VERBUNDEN` configuration.
- [ ] Run focused tests and confirm the current default-family mismatch.
- [ ] Apply the explicit shared family to View and ProtoLayout paths.
- [ ] Run focused tests and commit.

### Task 2: Productive insulin-activity source

**Files:**
- Modify: `data-source-aaps/src/main/kotlin/app/aapswear/datasource/aaps/AapsPayloadAdapter.kt`
- Modify: `data-source-aaps/src/test/kotlin/app/aapswear/datasource/aaps/AapsPayloadAdapterTest.kt`
- Modify: `app-mobile/src/test/kotlin/app/aapswear/mobile/DashboardChartsTest.kt`

**Interfaces:** Produces validated `TherapyHistorySample.insulinActivityUnitsPerMinute` values consumed by existing accumulation and renderers.

- [ ] Add a failing realistic-broadcast test containing AAPS BGI/ISF but no explicit activity.
- [ ] Confirm it fails with an empty activity value.
- [ ] Parse validated AAPS algorithm values and calculate the AAPS activity value fail-closed.
- [ ] Add all-mode, viewport and pixel tests using adapter-produced samples.
- [ ] Run source, accumulator and chart tests and commit.

### Task 3: Shared full-bleed graph and Tile profile

**Files:**
- Modify: `ui-shared/src/main/kotlin/app/aapswear/uishared/SharedWearCgmGraphRenderer.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7GlucoseChart.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7GraphTileService.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7DirectToWatchSettingsStore.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7AppearanceActivity.kt`
- Test: relevant `ui-shared` and `g7watch` tests.

**Interfaces:** Produces one shared render input/geometry plus an independent persisted Tile style.

- [ ] Add failing tests proving the graph fills the existing Tile contour, preserves aspect-correct data geometry, uses responsive safe padding and persists independent settings.
- [ ] Confirm current header/card geometry and shared settings fail them.
- [ ] Route the in-app View through the shared renderer.
- [ ] Render directly for the final Tile-contour bounds, remove the inner card/background/rim and retain one outer clip without stretching the rendered graph.
- [ ] Add the `WearOS-Tile Graph` settings section and immediate Tile invalidation.
- [ ] Test gaps, freshness states, history/settings cache identity and multiple sizes; commit.

### Task 4: Verification and deployment

**Files:** all changed production and test files.

**Interfaces:** Consumes Tasks 1-3 and produces validated APKs and hardware evidence.

- [ ] Run focused source/Mobile/G7/shared-renderer tests.
- [ ] Run relevant full unit/integration suites, assemblies, release builds, lint, detekt and ktlintCheck.
- [ ] Review the whole branch for regressions.
- [ ] If devices are connected, install exact packages and capture visible font, graph and activity evidence separately.
