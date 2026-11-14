# Systemwide CGM Dot Outlines Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the current real CGM dot permanently outlined, give historical and current dots independent outline widths everywhere, honor fully transparent Mobile very-high boundary lines, then assess and conditionally implement supported Now Bar range coloring.

**Architecture:** Extend the existing shared `WatchGraphStyle` and each platform's existing graph-style persistence rather than creating another settings layer. Every renderer resolves one canonical effective outline policy: historical enablement plus two widths, with current enablement forced true. Mobile very-high line alpha remains the configured ARGB value through rendering; Now Bar work is conditional on a documented public API.

**Tech Stack:** Kotlin, Android Canvas, Jetpack Compose, Glance, Wear OS Tiles, SharedPreferences, kotlinx.serialization, JUnit/Robolectric, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-18-systemwide-cgm-dot-outline-design.md`

## Global Constraints

- Current real CGM dot outline is always active and has no user switch.
- Historical real CGM dot outline can be disabled independently.
- Historical and current outline widths are independently persisted and clamped to `0.25f..3.0f` dp.
- Existing shared outline width migrates into both new widths; old current-disable state is ignored.
- Prediction dots and their settings remain unchanged.
- Mobile `GRAPH_VERY_HIGH_LINE` alpha `0` must render no visible boundary pixels.
- No hidden Samsung API, reflection, accessibility workaround, or false Now Bar guarantee.
- Preserve existing dirty-worktree changes and stage only files belonging to each task.

## Review Focus

- Legacy payload with only `cgmDotOutlineWidthDp`: both new widths must resolve to the legacy value.
- Legacy persisted `currentDotOutlineEnabled=false`: current real dot must still render outlined.
- Empty or single-point history: that point is current, not historical.
- Maximum outline widths on small round/tile/widget plots: clipping margins must include the larger effective width.
- Reused Android `Paint` after temporary alpha drawing: the next threshold line must use its own configured ARGB deterministically.

---

### Task 1: Shared graph-style contract and migration

**Files:**
- Modify: `wear-protocol/src/main/kotlin/app/aapswear/protocol/WearProtocol.kt`
- Modify: `ui-shared/src/main/kotlin/app/aapswear/uishared/SharedWearCgmGraphRenderer.kt`
- Test: `wear-protocol/src/test/kotlin/app/aapswear/protocol/WearProtocolTest.kt`
- Test: `ui-shared/src/test/kotlin/app/aapswear/uishared/SharedWearCgmGraphRendererTest.kt`

**Interfaces:**
- Produces: `WatchGraphStyle.cgmHistoricalDotOutlineWidthDp: Float`
- Produces: `WatchGraphStyle.cgmCurrentDotOutlineWidthDp: Float`
- Produces: shared renderer behavior that always outlines the newest real point.

- [ ] **Step 1: Write failing protocol and renderer tests**

Add tests proving legacy JSON with only `cgmDotOutlineWidthDp` resolves both widths to that value, new JSON round-trips both widths, `currentDotOutlineEnabled=false` cannot disable the newest point, a single point is current, and historical disablement affects only earlier points.

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew :wear-protocol:test :ui-shared:testDebugUnitTest --tests '*SharedWearCgmGraphRendererTest*' --no-daemon --max-workers=2`

Expected: FAIL because the two width fields and permanent-current policy do not exist.

- [ ] **Step 3: Implement the shared contract**

Add the two serializable width fields with legacy-compatible defaults. Retain legacy fields only for decoding compatibility. Update `SharedWearCgmGraphRenderer` to select width by newest-real-point identity, force current outline true, and reserve space using the largest effective width.

- [ ] **Step 4: Run tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit shared contract files**

Commit message: `feat: split shared CGM dot outline widths`

### Task 2: Mobile in-app graph settings and renderer

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/MobileAppearanceProfiles.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/GraphAppearanceSettings.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousColorSettingsPanel.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousOverviewScreen.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/CgmGraphCustomizationTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/MainActivityTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/DashboardChartsTest.kt`

**Interfaces:**
- Consumes: Task 1 outline-policy names and width range.
- Produces: Mobile preference keys `historicalDotOutlineEnabled`, `historicalDotOutlineWidthDp`, and `currentDotOutlineWidthDp` per appearance mode.

- [ ] **Step 1: Write failing Mobile migration, UI-state, and rendering tests**

Cover legacy common width migration, old disabled current outline becoming active, independent widths, no current-outline switch in the settings contract, single-point current rendering, and historical disablement.

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew :app-mobile:testDebugUnitTest --tests '*CgmGraphCustomizationTest*' --tests '*MainActivityTest*' --tests '*DashboardChartsTest*' --no-daemon --max-workers=2`

Expected: FAIL on missing separate fields/settings and shared rendering behavior.

- [ ] **Step 3: Implement Mobile persistence, settings, and Canvas rendering**

Replace the general outline switch/width with the historical switch and two width sliders. Determine the newest canonical real sample once per frame; force its outline and apply the current width. Preserve prediction behavior.

- [ ] **Step 4: Run tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit Mobile graph files**

Commit message: `feat: enforce current CGM outline on mobile`

### Task 3: Widgets, notifications, and Mobile previews

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetInstanceConfiguration.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWidgets.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/PersistentBridgeService.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/NotificationPresentation.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/ComplicationCatalog.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetInstanceConfigurationTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetPresentationTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/NotificationGraphProfilesTest.kt`

**Interfaces:**
- Consumes: Task 1 canonical fields and Task 2 preference migration rules.
- Produces: per-widget and per-notification-profile historical toggle plus independent widths.

- [ ] **Step 1: Write failing widget/notification persistence and bitmap tests**

Assert current outline is always drawn, separate widths alter only their dot class, legacy width migrates into both values, the current switch is absent, and plot insets use the larger active width.

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew :app-mobile:testDebugUnitTest --tests '*WidgetInstanceConfigurationTest*' --tests '*WidgetPresentationTest*' --tests '*NotificationGraphProfilesTest*' --no-daemon --max-workers=2`

Expected: FAIL on current-disable paths and common notification width.

- [ ] **Step 3: Implement widget and notification parity**

Reuse the existing widget configuration and notification-profile stores. Remove only the current enable controls, preserve historical controls, introduce both width values, and make previews use the same renderer.

- [ ] **Step 4: Run tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit widget and notification files**

Commit message: `feat: align widget and notification dot outlines`

### Task 4: Sugarlicious Wear and SugarWear/G7 parity

**Files:**
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/WearDisplayPreferences.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/WearSettingsActivity.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/WearGlucoseChart.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/SugarliciousTiles.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7AppearanceStore.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7AppearanceActivity.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7DirectToWatchSettingsStore.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7DirectToWatchSettingsActivity.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7GraphPresentation.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7GlucoseChart.kt`
- Test: `app-wear/src/test/kotlin/app/aapswear/wear/WearTileAppearanceStoreTest.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7AppearanceStoreTest.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7CollectorGraphViewTest.kt`

**Interfaces:**
- Consumes: Task 1 `WatchGraphStyle` and shared renderer.
- Produces: Wear/G7 persistence and UI parity across app graphs and tiles.

- [ ] **Step 1: Write failing Wear/G7 migration and settings tests**

Assert both widths persist independently, legacy common width migrates, current enable controls are removed, current resolves true, and every app/tile renderer forwards both widths.

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew :app-wear:testDebugUnitTest :g7watch:testDebugUnitTest --tests '*WearTileAppearanceStoreTest*' --tests '*G7AppearanceStoreTest*' --tests '*G7CollectorGraphViewTest*' --no-daemon --max-workers=2`

Expected: FAIL on common width/current toggle behavior.

- [ ] **Step 3: Implement Wear/G7 parity**

Migrate existing stores, update settings labels and sliders, force current outline in local G7 Canvas code, and forward independent widths through all shared renderer calls.

- [ ] **Step 4: Run tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit Wear/G7 files**

Commit message: `feat: unify wear CGM dot outlines`

### Task 5: Mobile very-high boundary alpha

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt`
- Modify if needed: `app-mobile/src/main/kotlin/app/aapswear/mobile/ui/theme/SugarliciousColors.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/OverviewGraphRegressionTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/CgmGraphCustomizationTest.kt`

**Interfaces:**
- Produces: threshold-line drawing that consumes exact configured ARGB and resets temporary `Paint.alpha` state.

- [ ] **Step 1: Write failing pixel-level alpha tests**

Render the graph with `GRAPH_VERY_HIGH_LINE` alpha values `0`, partial, and `255`. Assert the boundary pixels match those values, the fill remains independent, and a preceding temporary `Paint.alpha` operation cannot change the result.

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew :app-mobile:testDebugUnitTest --tests '*OverviewGraphRegressionTest*' --tests '*CgmGraphCustomizationTest*' --no-daemon --max-workers=2`

Expected: FAIL because the current graph path does not deterministically prove transparent boundary output.

- [ ] **Step 3: Implement deterministic line paint state**

Set each boundary line's color and paint alpha from its configured ARGB in one dedicated helper, then restore/default paint state after temporary alpha operations. Do not alter other threshold roles.

- [ ] **Step 4: Run tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit alpha fix**

Commit message: `fix: honor very-high boundary transparency`

### Task 6: Quality gates and connected-device installation

**Files:**
- Modify only regression fixes discovered by these gates.

**Interfaces:**
- Consumes: Tasks 1-5 completed behavior.
- Produces: build/test evidence and installed APKs.

- [ ] **Step 1: Run relevant full project gates**

Run the repository-supported unit, integration, lint, detekt, ktlint, debug and relevant release tasks for `app-mobile`, `app-wear`, `g7watch`, `ui-shared`, and `wear-protocol` without weakening any gate.

Expected: all selected gates PASS with zero test failures.

- [ ] **Step 2: Fix every in-scope warning or failure with RED-GREEN coverage**

Re-run the failing focused command and then the full Task 6 command.

- [ ] **Step 3: Install on connected targets and inspect**

Install Mobile on the connected phone and Sugarlicious Wear plus SugarWear/G7 on the connected watch using the repository's actual variants. Inspect settings and representative graph surfaces. Distinguish code/build evidence from hardware visual evidence.

- [ ] **Step 4: Commit gate-driven fixes if any**

Commit message: `fix: close CGM outline regressions`

### Task 7: Now Bar range-color feasibility and conditional implementation

**Files:**
- Inspect/modify conditionally: `app-mobile/src/main/kotlin/app/aapswear/mobile/SamsungLiveNotificationAdapter.kt`
- Inspect/modify conditionally: `app-mobile/src/main/kotlin/app/aapswear/mobile/NotificationPresentation.kt`
- Test conditionally: `app-mobile/src/test/kotlin/app/aapswear/mobile/LiveNotificationAdaptersTest.kt`

**Interfaces:**
- Consumes: canonical CGM range classification and existing Samsung live-notification adapter.
- Produces only if officially supported: dynamic Now Bar notification/accent color for high, in-range, and low.

- [ ] **Step 1: Verify current public APIs from primary sources**

Check current Android 17 notification/live-update APIs and official Samsung developer documentation. Record whether apps may set the visible Now Bar status/accent color dynamically and which notification field controls it.

- [ ] **Step 2: Make the support ruling**

If a public supported API exists, ledger its exact API/version and proceed. If not, ledger that no safe implementation exists and make no product-code change; do not use hidden APIs or claim support.

- [ ] **Step 3: If supported, write a failing adapter test**

Assert high, in-range, and low canonical states produce their configured notification color while stale/no-source behavior remains unchanged.

- [ ] **Step 4: If supported, implement and verify**

Use the existing live-notification builder/adapter and canonical range role; do not create a parallel Now Bar notification path. Run `:app-mobile:testDebugUnitTest`, `:app-mobile:assembleDebug`, and `:app-mobile:lintDebug`.

- [ ] **Step 5: If supported, install and inspect on the connected Samsung phone**

Classify the result as code/build validated or hardware validated. Commit supported implementation as `feat: color Now Bar by CGM range`.
