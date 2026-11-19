# Mobile 24h Metabolic Graph Implementation Plan

**Goal:** Preserve every independently supplied 24-hour graph stream, unify the IOB/COB chart surface, and show the latest valid bolus beneath the IOB value.

**Architecture:** Keep `DisplayHistoryAccumulator` as the single rolling-history owner and make retention field-aware so dense updates in one therapy stream cannot evict another stream. Reuse the CGM graph color roles and viewport, expose deterministic metabolic layout geometry for tests, and derive the IOB secondary text from canonical therapy events.

## Task 1: Prove and fix 24-hour history retention

**Files:**
- Modify: `app-mobile/src/test/kotlin/app/aapswear/mobile/DisplayHistoryAccumulatorTest.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DisplayHistoryAccumulator.kt`
- Inspect/test: canonical persistence reconciliation tests

1. Add failing tests with dense, independently timestamped IOB, COB, basal and activity data across the inclusive 24-hour boundary.
2. Confirm the current global cap or merge behavior truncates at least one valid stream.
3. Implement deterministic field-aware retention and reconstruction without interpolation or invented values.
4. Add CGM boundary/session coverage and persistence/reconciliation coverage.
5. Run the targeted history and persistence tests.

## Task 2: Unify IOB/COB graph geometry and colors

**Files:**
- Modify: `app-mobile/src/test/kotlin/app/aapswear/mobile/DashboardChartsTest.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt`

1. Add failing geometry/render tests for one outer contour, square internal junction, shared axis strip, minimal separator, and CGM-equivalent color roles.
2. Extract deterministic metabolic bounds used by production rendering and tests.
3. Render one combined surface: configurable `SURFACE` axis area, configurable `GRAPH_BACKGROUND` plots, and configurable `BORDER` contour.
4. Increase usable plot height while preserving marker headroom and shared X viewport.
5. Run targeted chart tests and bitmap checks.

## Task 3: Add latest bolus beneath IOB

**Files:**
- Modify/add: `app-mobile/src/test/kotlin/app/aapswear/mobile/TherapyHeroIndicatorsTest.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/TherapyHeroIndicators.kt`

1. Add failing tests for meal, correction and SMB selection; future/invalid/non-bolus exclusion; deterministic tie handling; and localized `Bolus 0,3U · 8m` formatting.
2. Implement a pure latest-bolus presentation helper over canonical therapy events.
3. Feed the result into the existing IOB secondary line; omit it when no valid event exists.
4. Run targeted presentation tests.

## Task 4: Regression and delivery validation

1. Run all relevant mobile, core, resolver and persistence tests.
2. Run `test`, `assembleDebug`, relevant release builds, lint, detekt and ktlint checks without weakening gates.
3. Review the complete diff for source/resolver/collector boundaries and ensure G7 collector behavior is untouched.
4. Build/install the required mobile artifact only after gates are green, then report code/build/hardware status separately and provide exactly one derived Gradle installation command.
