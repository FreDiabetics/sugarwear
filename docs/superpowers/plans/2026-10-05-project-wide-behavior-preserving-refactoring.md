# Project-wide Behavior-preserving Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce coupling, lifecycle risk and duplicated ownership across Sugarlicious while preserving every valid data, persistence, rendering and platform contract.

**Architecture:** Refactor along the repository's existing canonical boundaries rather than introducing replacement subsystems. Each task first adds an executable boundary or characterization test, then moves one responsibility into a focused owner and verifies all direct consumers before proceeding.

**Tech Stack:** Kotlin 2.4, Java 21, Android API 37, Wear OS, Compose, Glance, ProtoLayout, coroutines, SQLite/SharedPreferences, JUnit/Robolectric, Android Lint, Detekt, ktlint, Gradle 9.6.

**Spec:** `docs/superpowers/specs/2026-10-05-project-wide-behavior-preserving-refactoring-design.md`

## Global Constraints

- Preserve AndroidAPS values, units and visible rounding for every non-G7 data stream.
- Preserve G7 sensor/session, `measuredAt`, `receivedAt`, LIVE/BACKFILL, deduplication and freshness semantics.
- Preserve package IDs, exported-component contracts and persisted settings unless a tested migration is necessary.
- Keep direct G7 collection Wear-only and retain one collector, resolver and alarm engine.
- Do not introduce polling, aggressive keepalive, unbounded queues or unbounded retries.
- Do not weaken Lint, Detekt, ktlint, WFF, code-free APK, security or build gates.
- Work on `main` as explicitly requested; use small, reversible commits and keep the tree clean between tasks.
- A reproducible defect requires a failing regression test before production changes.

## Review Focus

- Process death or boot during an active G7 recovery must restore one bounded operation without duplicate scheduling; covered by Task 4 lifecycle tests.
- Duplicate or reordered AndroidAPS/Watch data must not change canonical identity, source priority or freshness; covered by Tasks 2 and 3.
- Moving graph/rendering code must not change pixels, clipping, scale ownership or 24-hour history selection; covered by Tasks 6 and 7.
- Cleanup failures and cancellation must not become user-facing collector failures or leak receiver/service ownership; covered by Tasks 4 and 5.
- Persisted settings and records from the current release must remain readable after every extraction; covered by Task 3 migration/restart tests.

---

### Task 1: Executable architecture inventory

**Files:**
- Create: `docs/refactoring/PROJECT_CHANGE_MAP_2026-10-05.md`
- Create: `core-model/src/test/kotlin/app/aapswear/model/ArchitectureContractTest.kt`
- Modify: `docs/ARCHITECTURE_INDEX.md`

**Interfaces:**
- Consumes: current Gradle module graph, Android manifests, ADR-001 through ADR-009.
- Produces: documented dependency direction and `ArchitectureContractTest` source/module ownership assertions used by later tasks.

- [ ] **Step 1: Write failing architecture tests**

Add tests named `application modules do not own canonical source policy`, `direct G7 collector stays Wear only`, and `shared modules do not depend on application modules`. Assert the exact allowed module edges and scan source/build files for forbidden ownership.

- [ ] **Step 2: Verify RED**

Run: `./gradlew :core-model:test --tests app.aapswear.model.ArchitectureContractTest --no-daemon --console=plain`

Expected: FAIL where current dependency/source ownership diverges from the documented graph, or fail because the contract fixture does not yet exist.

- [ ] **Step 3: Add the change map and minimum boundary correction**

Document every module, runtime entry point, store, transport and rendering consumer. Correct only proven dependency-direction violations; record intentionally retained Android shell dependencies.

- [ ] **Step 4: Verify GREEN**

Run: `./gradlew :core-model:test --tests app.aapswear.model.ArchitectureContractTest --no-daemon --console=plain`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `test: enforce project architecture boundaries`

### Task 2: Canonical data and resolver ownership

**Files:**
- Modify: `core-model/src/main/kotlin/app/aapswear/model/CanonicalCgmHistory.kt`
- Modify: `core-model/src/main/kotlin/app/aapswear/model/CgmSourceResolution.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/MobileCanonicalCgm.kt`
- Modify: `wear-storage/src/main/kotlin/app/aapswear/storage/CanonicalStateStore.kt`
- Test: `core-model/src/test/kotlin/app/aapswear/model/CgmSourceResolutionTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/MobileCanonicalCgmTest.kt`
- Test: `wear-storage/src/test/kotlin/app/aapswear/storage/CanonicalStateStoreTest.kt`

**Interfaces:**
- Consumes: `CanonicalCgmReading`, configured source preference, persisted canonical revision.
- Produces: one canonical resolver decision and stable reading identity based on sensor/session plus measurement time.

- [ ] **Step 1: Add failing edge-case tests**

Cover two fresh Mobile readings required for Watch-to-Mobile return, reordered delivery, same sequence at different times, LIVE/BACKFILL duplicate identity, both sources stale and invalid source data not overwriting valid state.

- [ ] **Step 2: Verify RED**

Run: `./gradlew :core-model:test :wear-storage:testDebugUnitTest :app-mobile:testDebugUnitTest --no-daemon --console=plain`

Expected: At least one new edge test fails for duplicated ownership or an unhandled transition; if all behavior already exists, add a source-ownership assertion that fails until duplicated policy is removed.

- [ ] **Step 3: Consolidate semantic ownership**

Keep source parsing in adapters, move shared validation/identity decisions to `core-model`, and reduce `MobileCanonicalCgm` to Android orchestration and persistence wiring. Do not change resolver thresholds.

- [ ] **Step 4: Verify GREEN and consumers**

Run: `./gradlew :core-model:test :wear-storage:testDebugUnitTest :app-mobile:testDebugUnitTest :app-wear:testDebugUnitTest :g7watch:testDebugUnitTest --no-daemon --console=plain`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `refactor: centralize canonical data ownership`

### Task 3: Persistence repositories and compatibility

**Files:**
- Create: `wear-storage/src/main/kotlin/app/aapswear/storage/RevisionedStateRepository.kt`
- Modify: `wear-storage/src/main/kotlin/app/aapswear/storage/CanonicalStateStore.kt`
- Modify: `wear-storage/src/main/kotlin/app/aapswear/storage/TherapyStateStore.kt`
- Modify: `wear-storage/src/main/kotlin/app/aapswear/storage/DiagnosticEventStore.kt`
- Create: `wear-storage/src/test/kotlin/app/aapswear/storage/RevisionedStateRepositoryTest.kt`
- Test: existing store tests in `wear-storage/src/test/kotlin/app/aapswear/storage/`

**Interfaces:**
- Consumes: serialized state plus monotonic revision and existing preference/database keys.
- Produces: `RevisionedStateRepository<T>` with `read`, `write`, and interrupted-write reconciliation used by canonical/therapy stores.

- [ ] **Step 1: Write failing compatibility tests**

Assert existing serialized fixtures remain readable, lower revisions cannot replace higher revisions, interrupted writes reconcile deterministically, duplicate writes are idempotent, and restart returns the same state.

- [ ] **Step 2: Verify RED**

Run: `./gradlew :wear-storage:testDebugUnitTest --tests '*RevisionedStateRepositoryTest*' --no-daemon --console=plain`

Expected: FAIL because the repository contract is absent.

- [ ] **Step 3: Implement the repository and migrate stores**

Implement `RevisionedStateRepository<T>` behind existing public store APIs. Retain all current keys and schemas; no data migration is introduced when extraction alone suffices.

- [ ] **Step 4: Verify GREEN and restart consumers**

Run: `./gradlew :wear-storage:testDebugUnitTest :app-mobile:testDebugUnitTest :app-wear:testDebugUnitTest :g7watch:testDebugUnitTest --no-daemon --console=plain`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `refactor: isolate revisioned state persistence`

### Task 4: Collector orchestration decomposition

**Files:**
- Create: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7CollectorPolicies.kt`
- Create: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7CollectorProtocolMapping.kt`
- Create: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7CollectorFailureDiagnostics.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7CollectorService.kt`
- Create: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7CollectorArchitectureTest.kt`
- Test: existing collector, scheduler, alarm, persistence and recovery tests.

**Interfaces:**
- Consumes: `G7PersistedState`, `G7ProtocolState`, collector attempt diagnostics and scheduler state.
- Produces: pure policy functions and protocol mappings; `G7CollectorService` remains the Android runtime shell.

- [ ] **Step 1: Write failing ownership and lifecycle tests**

Assert policy/mapping functions reside outside the Service source; add boot/process-death cases proving one coalesced recovery, bounded receiver completion and no duplicate scheduled window.

- [ ] **Step 2: Verify RED**

Run: `./gradlew :g7watch:testDebugUnitTest --tests '*G7CollectorArchitectureTest*' --no-daemon --console=plain`

Expected: FAIL because policies and mappings still reside in `G7CollectorService.kt`.

- [ ] **Step 3: Extract pure responsibilities**

Move existing top-level policy functions, protocol-state mappings and privacy-safe failure metadata without changing signatures or values. Keep service orchestration, Android callbacks and runtime ownership in `G7CollectorService`.

- [ ] **Step 4: Verify GREEN and collector suite**

Run: `./gradlew :g7watch:testDebugUnitTest :g7watch:assembleDebug :g7watch:lintDebug :g7watch:detekt :g7watch:ktlintCheck --no-daemon --console=plain`

Expected: PASS with no warnings/findings introduced.

- [ ] **Step 5: Commit**

Commit message: `refactor(g7watch): separate collector policies from service`

### Task 5: BLE ownership and callback boundaries

**Files:**
- Create: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7BlePolicy.kt`
- Create: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7GattGenerationRegistry.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/AndroidG7Ble.kt`
- Create: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7BleArchitectureTest.kt`
- Test: existing BLE policy, callback, GATT ownership and hardware-state tests.

**Interfaces:**
- Consumes: sensor address, advertisement evidence, callback generation and protocol deadlines.
- Produces: pure reconnect/scan policy and one generation registry; `AndroidG7Collector` owns protocol execution.

- [ ] **Step 1: Write failing boundary/race tests**

Assert BLE policy and generation ownership are separate from Android callbacks. Exercise stale callback after close, duplicate terminal callback, full callback channel, cancellation and NO_CALLBACK retry selection.

- [ ] **Step 2: Verify RED**

Run: `./gradlew :g7watch:testDebugUnitTest --tests '*G7BleArchitectureTest*' --no-daemon --console=plain`

Expected: FAIL because policy and registry remain embedded in `AndroidG7Ble.kt`.

- [ ] **Step 3: Extract policy and generation registry**

Move existing logic unchanged. Preserve channel capacities, GATT close order, direct-connect-first strategy, bounded scan fallback, backoff and telemetry codes.

- [ ] **Step 4: Verify GREEN and collector integration**

Run: `./gradlew :dexcom-g7:test :g7watch:testDebugUnitTest :g7watch:assembleDebug :g7watch:lintDebug --no-daemon --console=plain`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `refactor(g7watch): isolate BLE policy and ownership`

### Task 6: Mobile graph decomposition

**Files:**
- Create: `app-mobile/src/main/kotlin/app/aapswear/mobile/MobileGraphViewport.kt`
- Create: `app-mobile/src/main/kotlin/app/aapswear/mobile/MobileGraphSeries.kt`
- Create: `app-mobile/src/main/kotlin/app/aapswear/mobile/MobileGraphGeometry.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt`
- Create: `app-mobile/src/test/kotlin/app/aapswear/mobile/DashboardChartsArchitectureTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/DashboardChartsTest.kt`

**Interfaces:**
- Consumes: canonical CGM/therapy history, `GraphScaleStrategy`, viewport preference and appearance settings.
- Produces: focused viewport, series-transformation and geometry APIs consumed by the two existing chart views.

- [ ] **Step 1: Write failing ownership tests**

Assert `DashboardCharts.kt` contains only view/render orchestration and that viewport, interpolation/smoothing, scale resolution and geometry types live in their focused files. Add 24-hour, prediction-divider, negative IOB and live-edge tests where absent.

- [ ] **Step 2: Verify RED**

Run: `./gradlew :app-mobile:testDebugUnitTest --tests '*DashboardChartsArchitectureTest*' --tests '*DashboardChartsTest*' --no-daemon --console=plain`

Expected: FAIL on ownership assertions while existing rendering behavior remains characterized.

- [ ] **Step 3: Extract graph responsibilities**

Move functions without changing signatures, constants, rounding, antialiasing, axes or coordinate calculations. Keep `GlucoseDashboardChart` and `MetabolicDashboardChart` as Android View renderers.

- [ ] **Step 4: Verify GREEN and bitmap/layout consumers**

Run: `./gradlew :core-model:test :app-mobile:testDebugUnitTest :app-mobile:assembleDebug :app-mobile:lintDebug :app-mobile:detekt :app-mobile:ktlintCheck --no-daemon --console=plain`

Expected: PASS; existing bitmap tolerances remain unchanged.

- [ ] **Step 5: Commit**

Commit message: `refactor(mobile): separate graph policy and rendering`

### Task 7: Mobile settings, widgets and dashboard composition

**Files:**
- Create: `app-mobile/src/main/kotlin/app/aapswear/mobile/GraphAppearanceSettings.kt`
- Create: `app-mobile/src/main/kotlin/app/aapswear/mobile/ColorEditorComponents.kt`
- Create: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetGraphRenderer.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousColorSettingsPanel.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWidgets.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt`
- Create: `app-mobile/src/test/kotlin/app/aapswear/mobile/MobileUiArchitectureTest.kt`
- Test: existing appearance, widget, notification and dashboard tests.

**Interfaces:**
- Consumes: existing preference keys, palettes, canonical display models and graph policies from Task 6.
- Produces: focused settings models/components and widget graph renderer without changing UI text or persisted keys.

- [ ] **Step 1: Write failing ownership and compatibility tests**

Assert preference migration and key mapping are separate from Compose panels, bitmap widget rendering is separate from Glance composition, and old preference fixtures produce identical settings. Cover narrow notification/widget sizes.

- [ ] **Step 2: Verify RED**

Run: `./gradlew :app-mobile:testDebugUnitTest --tests '*MobileUiArchitectureTest*' --no-daemon --console=plain`

Expected: FAIL because responsibilities remain combined.

- [ ] **Step 3: Extract focused components**

Move code while preserving preference keys, color roles, graph dimensions, typography and content descriptions. Do not merge application-specific appearance stores.

- [ ] **Step 4: Verify GREEN and Mobile application**

Run: `./gradlew :app-mobile:testDebugUnitTest :app-mobile:assembleDebug :app-mobile:assembleRelease :app-mobile:lint :app-mobile:detekt :app-mobile:ktlintCheck --no-daemon --console=plain`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `refactor(mobile): isolate settings and widget rendering`

### Task 8: Wear, Tiles and Complications decomposition

**Files:**
- Create: `complications/src/main/kotlin/app/aapswear/complications/TherapyComplicationRenderer.kt`
- Create: `complications/src/main/kotlin/app/aapswear/complications/TherapyComplicationProviders.kt`
- Modify: `complications/src/main/kotlin/app/aapswear/complications/TherapyComplications.kt`
- Create: `app-wear/src/main/kotlin/app/aapswear/wear/WearTileRenderModels.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/SugarliciousTiles.kt`
- Create: `complications/src/test/kotlin/app/aapswear/complications/ComplicationArchitectureTest.kt`
- Test: existing provider, preview, trend-icon, Tile and stale-state tests.

**Interfaces:**
- Consumes: canonical state, shared formatters/trend vectors and persisted Wear display settings.
- Produces: pure provider render models plus thin Android complication/Tile services.

- [ ] **Step 1: Write failing ownership and parity tests**

Assert providers contain no source resolution, renderer output is shared by equivalent provider variants, double arrows retain source aspect ratio, and stale/signal-loss behavior preserves the last valid value according to canonical policy.

- [ ] **Step 2: Verify RED**

Run: `./gradlew :complications:testDebugUnitTest --tests '*ComplicationArchitectureTest*' --no-daemon --console=plain`

Expected: FAIL because rendering/provider declarations remain combined.

- [ ] **Step 3: Extract render models and provider declarations**

Keep manifest class names stable. Move shared rendering decisions into pure functions and retain thin service subclasses with the same fully qualified names.

- [ ] **Step 4: Verify GREEN and Wear surfaces**

Run: `./gradlew :complications:testDebugUnitTest :app-wear:testDebugUnitTest :app-wear:assembleDebug :app-wear:assembleRelease :app-wear:lint :complications:lint :app-wear:detekt :complications:detekt :app-wear:ktlintCheck :complications:ktlintCheck --no-daemon --console=plain`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `refactor(wear): separate complication and tile rendering`

### Task 9: Lifecycle, provider, security and dead-code audit

**Files:**
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7ReadingProvider.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/PersistentBridgeService.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/StateDataLayerService.kt`
- Modify: relevant manifests and dependency declarations only for proven findings.
- Create: `docs/refactoring/FINAL_REFACTORING_AUDIT_2026-10-05.md`
- Test: provider, service restart, transport deduplication and manifest/security tests in owning modules.

**Interfaces:**
- Consumes: Android provider/service callbacks and the refactored repositories/render models.
- Produces: bounded lifecycle ownership and final evidence ledger; no new public subsystem.

- [ ] **Step 1: Add failing tests for proven findings**

Audit blocking provider access, cancellation propagation, receiver completion, exported components, permission checks, backup policy and dead references. For each reproducible finding, add one failing behavior test; findings without a safe software reproduction remain documented rather than patched speculatively.

- [ ] **Step 2: Verify RED per finding**

Run the smallest owning-module test command and record the exact expected failure in the audit.

- [ ] **Step 3: Fix root causes and remove proven dead code**

Use existing repositories/scopes and typed outcomes. Do not wrap synchronous SQLite work in fake coroutine timeouts, suppress findings globally or delete WFF resources without format-aware reachability.

- [ ] **Step 4: Run affected-module gates**

Run: `./gradlew :app-mobile:testDebugUnitTest :app-wear:testDebugUnitTest :g7watch:testDebugUnitTest :app-mobile:lint :app-wear:lint :g7watch:lint :app-mobile:detekt :app-wear:detekt :g7watch:detekt ktlintCheck --no-daemon --console=plain`

Expected: PASS with zero new findings.

- [ ] **Step 5: Commit**

Commit message: `refactor: harden lifecycle and provider boundaries`

### Task 10: Repository-wide verification and integration

**Files:**
- Modify: `docs/refactoring/FINAL_REFACTORING_AUDIT_2026-10-05.md`
- Modify: `docs/ARCHITECTURE_INDEX.md`
- Modify: `docs/TEST_REPORT.md`

**Interfaces:**
- Consumes: all prior task commits and their tests.
- Produces: verified `main`, PR evidence and an explicit hardware-validation boundary.

- [ ] **Step 1: Run the complete gate**

Run: `./gradlew test assembleDebug assembleRelease lint detekt ktlintCheck --no-daemon --console=plain --max-workers=4`

Expected: BUILD SUCCESSFUL with zero failed tests or static-analysis findings.

- [ ] **Step 2: Validate Watch Face Format artifacts**

Run the repository's current WFF validator and code-free verification scripts discovered from Gradle/docs.

Expected: Every active WFF validates and every code-free watchface APK contains zero DEX files.

- [ ] **Step 3: Verify repository and test evidence**

Run `git diff --check`, count JUnit XML results, confirm a clean working tree after documentation updates, and record exact commands/results.

- [ ] **Step 4: Hardware smoke validation**

Install only affected applications on explicitly identified connected devices. Record process/service state and check crash/ANR logs. Mark long-running collector, battery and visual acceptance `HARDWARE-TEST OFFEN` unless actually observed.

- [ ] **Step 5: Final review and fixes**

Review the complete refactoring range against this plan and spec. Fix Critical/Important findings with RED-to-GREEN regression tests, rerun the complete gate, and document any deferred minor findings.

- [ ] **Step 6: Commit, PR, verify and push**

Commit final evidence, push the refactoring branch/state, create exactly one PR, attach it to the task, wait for required checks, resolve failures without weakening gates, merge when green, synchronize local `main`, and remove obsolete worktrees/branches so only `main` remains.

