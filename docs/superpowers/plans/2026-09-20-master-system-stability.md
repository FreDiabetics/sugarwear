# Sugarlicious Master System Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver one persistent, deterministic, event-driven CGM state across Sugarlicious Mobile, Wear, SugarWear, collector, tiles, complications, watchfaces, and alarms.

**Architecture:** Strengthen the existing canonical model and storage boundaries, then make every transport and surface consume them. Platform I/O remains separate from pure policy; collector recovery remains event-driven and session-aware.

**Tech Stack:** Kotlin 2.4, Android/Compose, Wear OS, ProtoLayout Tiles, DataStore, SQLite, Google Play Services Wearable, BLE/GATT, Gradle 9.6.1, AGP 9.4.1, JUnit/Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-20-master-system-stability-design.md`

## Global Constraints

- No Mobile G7 BLE collector and no parallel replacement implementation.
- Freshness uses `measuredAt`; predictions never count as real readings.
- The resolver alone chooses the active source.
- Recovery and updates are event-driven; no blind polling or aggressive BLE scans.
- Sensor release preserves history while removing all active sensor state.
- Legacy watchfaces remain absent from active product packaging and release gates.
- Existing quality gates may not be disabled, suppressed globally, or weakened.
- Hardware-dependent behavior is reported only from real-device evidence.

## Review Focus

- Process death between sequential store writes must recover one committed canonical revision without losing valid history.
- Duplicate DataItem/Message delivery must not repeat resolver, alarm, database, tile, or complication side effects.
- Transport or BLE disconnect with a fresh reading must never become CGM signal loss.
- Backfill from another sensor/session must never close or refresh the active session's gap.
- Tile rendering with empty, malformed, or clipped data must remain visible, truthful, and non-crashing.

---

### Task 1: Reachability and active product matrix

**Files:**
- Modify: `tools/watchface-catalog.ps1`
- Modify: `tools/verify-codefree-watchfaces.ps1`
- Modify: `.github/workflows/build.yml`
- Modify: `settings.gradle.kts` only when reachability proves retired modules removable
- Test: PowerShell catalog/DEX verifier and Gradle WFF tasks

**Interfaces:** Produces the authoritative active WFF module list used by all later gates.

- [ ] Add a failing catalog test proving retired modules are absent from product/release collections and Digital/Vigil remain.
- [ ] Verify all runtime, packaging, preview, marketplace, and installer references before deletion.
- [ ] Remove retired modules from active catalogs, CI release tasks, generated assets, and installers; delete modules only when unreachable.
- [ ] Run catalog, active WFF release, official validator, and code-free checks.
- [ ] Commit the product-matrix correction and the Phase-1 evidence report.

### Task 2: Canonical CGM and resolver contracts

**Files:**
- Modify: `core-model/src/main/kotlin/app/aapswear/model/*`
- Test: `core-model/src/test/kotlin/app/aapswear/model/*`

**Interfaces:** Produces canonical identity, freshness, signal status, resolver state, and graph input contracts.

- [ ] Add failing tests for sensor/session identity, sequence collision, measured/received time, prediction isolation, all resolver states, and two-distinct-reading recovery hysteresis.
- [ ] Consolidate duplicated freshness/signal decisions behind the existing core policy.
- [ ] Make invalid/out-of-order/cross-session candidates deterministic and non-destructive.
- [ ] Run the complete `core-model` test suite and commit.

### Task 3: Revisioned Mobile persistence and source policy

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/MobileCanonicalCgm.kt`
- Modify: `wear-storage/src/main/kotlin/app/aapswear/storage/*`
- Test: matching Mobile and storage tests

**Interfaces:** Consumes Task 2 identity; produces one reconciled committed revision for fanout.

- [ ] Characterize every source preference and legacy migration input.
- [ ] Add interruption tests for failure before/after each sequential store write and process restart.
- [ ] Add a shared monotonic revision/reconciliation record without adding another competing state store.
- [ ] Align explicit xDrip/AAPS selection and history filtering with the existing supported-source policy.
- [ ] Dispatch consumers only after a reconciled commit; run focused suites and commit.

### Task 4: Retired G7 history transport removal

**Files:**
- Modify: `wear-protocol/src/main/kotlin/app/aapswear/protocol/WearProtocol.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/MobileDataLayerService.kt`
- Modify: Wear service call sites and Gradle dependencies
- Test: protocol and old/new compatibility contract tests

**Interfaces:** Produces a generic Wear protocol without sensor implementation types.

- [ ] Add characterization tests for supported peer behavior and unknown retired paths.
- [ ] Remove normal-runtime sync/ack triggers and clearing-only compatibility storage.
- [ ] Remove retired DTO/path types and the `wear-protocol -> dexcom-g7` dependency when no supported peer requires them.
- [ ] Prove local SugarWear history is unaffected; run protocol/Mobile/Wear tests and commit.

### Task 5: Wear resolver I/O boundary and observability

**Files:**
- Modify/extract: `complications/.../G7LocalReadingResolver.kt`
- Modify: appropriate Wear storage/integration module
- Test: provider outcome, resolver, and diagnostics tests

**Interfaces:** Consumes Task 2 resolver contracts; produces typed provider outcomes and one persisted resolver memory.

- [ ] Add failing tests for success, empty, unavailable, denied, malformed cursor, restart, and diagnostic coalescing.
- [ ] Separate pure source resolution from Android provider I/O without duplicating resolver state.
- [ ] Emit bounded structured diagnostics while keeping renderer fallbacks non-throwing.
- [ ] Run Wear/complication suites and commit.

### Task 6: Event-driven transport and fanout

**Files:**
- Modify: Mobile/Wear Data Layer services, canonical fanout, update planners
- Test: duplicate ordering, therapy-only revision, timeout, and affected-surface tests

**Interfaces:** Consumes Task 3 revision and Task 5 outcome; produces idempotent delivery and targeted invalidation.

- [ ] Add tests for message-first/data-first duplicates, restart, equal glucose with newer therapy, unavailable Play services, and consumer failure isolation.
- [ ] Reject applied revisions before expensive/provider/resolver/alarm work.
- [ ] Bound receiver lifetime while retaining durable eventual delivery.
- [ ] Update only affected tiles/complications/watchfaces and commit after focused suites.

### Task 7: Collector, gap ledger, backfill, and release

**Files:**
- Modify: existing `g7watch` collector/reconnect/ledger/unlink classes
- Test: existing and new collector recovery integration tests

**Interfaces:** Consumes Task 2 identity/freshness; produces persistent session-aware collector recovery.

- [ ] Add failing tests for GATT 133, NO_CALLBACK, scan fallback, restart, Bluetooth restart, long offline period, duplicate LIVE, session mismatch, and sensor release.
- [ ] Ensure one runtime owner, bounded queues, explicit backoff/jitter, and no parallel pairing/backfill cycles.
- [ ] Trigger coalesced recovery immediately after successful LIVE when a recoverable gap is open and retry by the second suitable contact if still open.
- [ ] Preserve historical readings while clearing credentials, bond/runtime/retry/scan/session state on release.
- [ ] Run collector suites and commit.

### Task 8: Unified signal and alarm presentation

**Files:**
- Modify: core alarm/presentation policy and all platform adapters
- Test: alarms, complications, tiles, Wear/Mobile status, and watchface provider tests

**Interfaces:** Consumes Tasks 2, 5, and 7; produces one surface-neutral status model.

- [ ] Add tests for fresh/aging/stale/NO_SOURCE across transport disconnect, BLE reconnect, source switch, and sensor error.
- [ ] Remove local source/freshness/signal derivations from renderers.
- [ ] Ensure alarm dedupe/debounce/ack/repeat keys are sensor/session aware.
- [ ] Verify only real signal loss triggers its alarm/status; commit.

### Task 9: Shared SugarWear graph and visible history tile

**Files:**
- Modify: `ui-shared/.../SharedWearCgmGraphRenderer.kt`
- Modify: `g7watch` graph model/view/tile classes
- Test: graph model, renderer, tile bitmap/layout, and parity tests

**Interfaces:** Consumes canonical history/status; produces one semantic graph model with platform render adapters.

- [ ] Add a failing end-to-end tile test proving multiple stored values yield visible graph pixels inside round-display bounds.
- [ ] Extract/reuse shared window, scale, target band, gaps, colors, and geometry for app and tile.
- [ ] Preserve newest-dot time motion and truthful empty/stale states.
- [ ] Run graph/tile tests at Galaxy and smaller round dimensions; commit.

### Task 10: Persistent IOB/COB/Basal tile configuration

**Files:**
- Modify: Wear settings, persistent store, Tile service, shared therapy presentation
- Test: all 1/2/3 selection layouts, ordering, persistence, and restart

**Interfaces:** Consumes canonical therapy state; produces a persisted non-empty selection and deterministic layout model.

- [ ] Add failing tests for each singleton, pair, required IOB/COB order, three-item layout, invalid empty selection, and process rehydration.
- [ ] Reuse Mobile presentation semantics/tokens where platform-neutral; keep ProtoLayout rendering platform-specific.
- [ ] Request targeted updates only when relevant therapy/config revisions change.
- [ ] Validate round-display bounds and commit.

### Task 11: Background, lifecycle, and energy hardening

**Files:**
- Modify only lifecycle/services/schedulers identified by tests and tracing
- Test: restart, boot, duplicate ownership/subscription, cancellation, and wake scheduling

**Interfaces:** Consumes all prior persistent states; produces deterministic process-death recovery without polling.

- [ ] Inventory all loops, alarms, jobs, WakeLocks, scans, service starts, and invalidations with owner/cancellation/backoff.
- [ ] Add failing tests for every confirmed duplicate owner, lost restoration, unbounded retry, or broad invalidation.
- [ ] Fix root causes and record CPU/BLE/wakeup implications without inventing percentage savings.
- [ ] Run lifecycle/background suites and commit.

### Task 12: Final gates, documentation, and controlled hardware handoff

**Files:**
- Modify: audits, test report, known limitations, release checklist
- Test: complete project matrix and device smoke checks

**Interfaces:** Produces the candidate branch for user acceptance; does not merge it.

- [ ] Run clean tests, all debug/product release builds, lint, Detekt, Ktlint, official WFF validation, code-free verification, and all QA scripts.
- [ ] Compare exact counts to baseline and explain legitimate count changes.
- [ ] Install the green candidate on requested hardware only and collect truthful smoke evidence.
- [ ] Document root causes, changes by subsystem, performance assessment, hardware evidence, limitations, branch, HEAD, commits, and working tree.
- [ ] Leave the branch unmerged for user acceptance; merge and delete only after explicit approval and a final `main` gate.
