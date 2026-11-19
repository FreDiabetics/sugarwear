# Project Hardening Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve or explicitly own every remaining repository quality finding and validate the result in software and on identified hardware.

**Architecture:** Preserve the existing canonical data and collector boundaries. Work sequentially from inventory through safe cleanup, platform migrations, quality gates, reliability/energy tests and final deployment so each change is independently reviewable and reversible.

**Tech Stack:** Kotlin, Java, Android SDK 36, Wear OS, ProtoLayout, Coroutines, Gradle 9.6.1, JUnit/Robolectric, Android Lint, PowerShell WFF tooling, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-14-project-hardening-completion-design.md`

## Global Constraints

- No therapy control, cloud telemetry or replacement G7 collector.
- No broad warning suppression or generated-output patching.
- Preserve sensor/session identity, `measuredAt`, `receivedAt` and LIVE/BACKFILL origin.
- No live-sensor unlink without an explicit hardware test window.
- No automatic merge or push.
- Execute tasks sequentially and commit only after their focused gate is green.

---

### Task 1: Build the authoritative finding ledger

**Files:**
- Create: `docs/audits/QUALITY_FINDING_LEDGER_2026-09-14.md`
- Modify: none
- Test: generated Android Lint SARIF reports for every module

**Interfaces:**
- Consumes: `**/build/reports/lint-results-debug.sarif`, Gradle compiler output
- Produces: rule/source/disposition ledger used by Tasks 2–6

- [ ] Run all-module Lint and confirm exit status.
- [ ] Parse every SARIF result and group by rule, module and source ownership.
- [ ] Re-run G7 Lint diagnostically and identify the XML parser input or record a minimal reproducer.
- [ ] Write the ledger with counts, priorities and exact dispositions.
- [ ] Commit the ledger independently.

### Task 2: Remove safe repository-owned findings

**Files:**
- Modify: exact files listed by Task 1 under safe migration and cleanup
- Test: nearest existing unit test for each affected module

**Interfaces:**
- Consumes: Task 1 safe-fix entries
- Produces: warning-free low-risk application/resource code

- [ ] For each behavioral finding, add one focused test that fails for the finding.
- [ ] Run the focused test and verify the expected red failure.
- [ ] Apply the smallest production correction.
- [ ] Run the focused module tests and Lint to green.
- [ ] Commit one coherent warning class at a time.

### Task 3: Modernize G7 Bluetooth compatibility

**Files:**
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/AndroidG7Ble.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7BlePolicyTest.kt`

**Interfaces:**
- Consumes: Android Bluetooth APIs and existing attempt/generation ownership
- Produces: one version-aware callback and connection compatibility boundary

- [ ] Add tests for legacy notification copying and modern callback ownership.
- [ ] Verify the new tests fail against the current compatibility path.
- [ ] Introduce version-aware value extraction and GATT connection creation without changing timeouts or retry policy.
- [ ] Run G7 policy, collector and pairing suites plus G7 Lint.
- [ ] Commit the BLE migration.

### Task 4: Modernize Tiles and system chrome

**Files:**
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/SugarliciousTiles.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7CollectorTileService.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7GraphTileService.kt`
- Modify: G7 Activities identified by Task 1
- Test: existing Tile/layout/round-display suites

**Interfaces:**
- Consumes: existing resource IDs, dimensions, palettes and round-display geometry
- Produces: supported ProtoLayout and edge-to-edge API usage with identical rendering contracts

- [ ] Add or tighten tests for image resource, size, tint and content-safe bounds.
- [ ] Verify the tests detect an intentionally wrong resource or inset.
- [ ] Replace deprecated builders and system-bar setters at one shared boundary.
- [ ] Run Tile, activity, preview and screenshot tests.
- [ ] Commit Tiles and system chrome separately if either can be reviewed alone.

### Task 5: Add static-analysis gates

**Files:**
- Modify: root `build.gradle.kts`, version catalog and `.github/workflows/build.yml`
- Create: repository Detekt/Ktlint configuration only when required
- Test: Gradle configuration and complete static-analysis tasks

**Interfaces:**
- Consumes: all Kotlin source sets
- Produces: reproducible local and CI static-analysis commands

- [ ] Add pinned, centrally configured plugins without a baseline.
- [ ] Run each new task and capture the initial red findings.
- [ ] Fix findings by rule and module; use narrow documented exclusions only for generated code.
- [ ] Add the green tasks to CI.
- [ ] Commit configuration and remediations in reviewable groups.

### Task 6: Expand deterministic reliability and energy tests

**Files:**
- Modify: focused tests in `core-model`, `wear-storage`, `app-mobile`, `app-wear` and `g7watch`
- Modify production only when a new red test proves a defect

**Interfaces:**
- Consumes: existing clocks, stores, schedulers and collector state machines
- Produces: repeatable process-death, duplicate, fault and wakeup-budget coverage

- [ ] Add permutation and repeated-delivery properties for canonical state.
- [ ] Add rehydration and migration failure tests using real persisted formats.
- [ ] Add callback timeout, coalescing and bounded-queue pressure tests.
- [ ] Add scheduler assertions that reject blind periodic recovery.
- [ ] Run every test red before its corresponding correction, then green.
- [ ] Commit each independent reliability correction.

### Task 7: Execute final gates and deployment

**Files:**
- Modify: `docs/audits/FINAL_REPORT_2026-09-14.md`
- Test: complete repository and explicitly identified devices

**Interfaces:**
- Consumes: Tasks 1–6 and current connected-device inventory
- Produces: reproducible final evidence and explicit residual-risk list

- [ ] Run `clean test assembleDebug lint` plus Detekt/Ktlint and required release variants.
- [ ] Run the WFF validator and code-free APK verifier.
- [ ] Confirm `git diff --check` and inspect the complete branch diff.
- [ ] Identify phone/watch by model and install only intended packages with data retention.
- [ ] Run non-destructive smoke tests and record hardware limitations separately.
- [ ] Update the report with exact commands, results, commit and working-tree state.
- [ ] Do not merge or push automatically.
