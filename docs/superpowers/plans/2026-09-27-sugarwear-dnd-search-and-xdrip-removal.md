# SugarWear DND, Persistent Search, and xDrip Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove xDrip as a Sugarlicious source, make SugarWear’s DND alarm configuration use the public Wear OS path, and make user-started G7 discovery durable across UI, process, and boot lifecycle changes.

**Architecture:** Existing canonical source, alarm, and collector layers remain authoritative. Source removal includes backward-compatible migration; DND capability is exposed by a focused system gateway feeding the existing notifier/settings screen; persistent search is a collector-owned command executed through the existing foreground service, runtime reconciler, alarm scheduler, and filtered PendingIntent scan.

**Tech Stack:** Kotlin, Android/Wear OS APIs, foreground services, BLE `BluetoothLeScanner`, `NotificationManager`, Robolectric/JUnit, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-27-sugarwear-dnd-search-and-xdrip-removal-design.md`

## Global Constraints

- Do not add a second resolver, alarm engine, notifier, or collector.
- Preserve historical glucose measurements while migrating legacy xDrip identifiers.
- Use public Android/Wear OS APIs only; no hidden settings components.
- UI lifecycle and display state must not own or terminate sensor search.
- Scan/retry behavior must be bounded and battery-conscious.
- Do not weaken tests, lint, detekt, ktlint, or branch protections.
- Separate CODE-VALIDIERT, BUILD-VALIDIERT, and HARDWARE-TEST OFFEN.

## Review Focus

- Legacy serialized `XDRIP_PLUS` or preference strings must not crash after enum/module removal and must not reactivate xDrip.
- A settings activity that resolves but throws on launch must fall through to the next public intent.
- Existing user-modified notification channels must not be falsely reported as DND-capable merely because policy access exists.
- An expired scan window must retain an active search command and schedule backoff rather than emit “Sensor nicht gefunden.”
- Process/boot recovery must resume exactly one active search without duplicate services, scans, or alarm schedules.

---

### Task 1: Remove xDrip as a Sugarlicious data source

**Files:**
- Delete: `data-source-xdrip/`
- Delete: `app-mobile/src/main/kotlin/app/aapswear/mobile/XdripStatusReceiver.kt`
- Modify: `settings.gradle.kts`
- Modify: `app-mobile/build.gradle.kts`
- Modify: `app-mobile/src/main/AndroidManifest.xml`
- Modify: `core-model/src/main/kotlin/app/aapswear/model/TherapyDisplayState.kt`
- Modify: `core-model/src/main/kotlin/app/aapswear/model/TherapyDisplayFormatter.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/AapsStatusReceiver.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetLaunchTargetStore.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/WearActivity.kt`
- Modify: `complications/src/main/kotlin/app/aapswear/complications/TherapyComplications.kt`
- Modify: `wear-storage/src/main/kotlin/app/aapswear/storage/PersistentPredictionCache.kt`
- Test: relevant existing tests plus new migration/source-absence tests in `core-model`, `app-mobile`, and `wear-storage`

**Interfaces:**
- Consumes: existing `DataSourceId`, `DataSourcePreference`, persisted dashboard preferences, and serialized therapy state.
- Produces: supported source model without `XDRIP_PLUS`; `migrateDataSourcePreference(raw: String?): DataSourcePreference`; backward-compatible state migration that maps legacy xDrip identity to `OTHER` while preserving samples.

- [ ] **Step 1: Write failing source-removal and migration tests**

Add tests asserting no selectable xDrip source/launch target remains, legacy `XDRIP_PLUS` preferences resolve to `ANDROID_APS`, and legacy historical samples decode/migrate without deletion.

- [ ] **Step 2: Run focused tests and verify RED**

Run affected `core-model`, `app-mobile`, and `wear-storage` tests. Expected: failures identify the still-present enum, selection, module, or missing migration behavior.

- [ ] **Step 3: Remove the integration and implement migrations**

Remove the module/receiver/dependency/manifest visibility and all xDrip source branches. Keep the direct G7 authentication provenance code and license. Update tests/docs that described xDrip as a supported source.

- [ ] **Step 4: Run focused tests and repository text audit**

Expected: tests pass; remaining `xDrip` references are limited to required G7 authentication provenance/license or explicit external-collector conflict guidance.

- [ ] **Step 5: Commit**

Commit message: `refactor: remove xDrip data source integration`

### Task 2: Repair DND system navigation and truthful status

**Files:**
- Create: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7AlarmSystemAccess.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7AlarmSettingsActivity.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7CgmAlarms.kt`
- Modify: `g7watch/src/main/AndroidManifest.xml`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7AlarmSystemAccessTest.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7AlarmSettingsActivityTest.kt`

**Interfaces:**
- Consumes: `NotificationManager`, `PackageManager`, notification permission, ringer/alarm volume, and existing alarm settings.
- Produces: `G7AlarmSystemAccess.snapshot(context): G7AlarmSystemSnapshot`; `openPolicySettings(activity): G7SettingsOpenResult`; observable UI status refreshed from `onResume`.

- [ ] **Step 1: Write failing intent/status tests**

Cover the public policy-access action, resolution filtering, launch-exception fallback, unsupported result, granted/not-granted snapshots, notification permission, blocked app/channel, and Activity resume refresh.

- [ ] **Step 2: Run focused tests and verify RED**

Expected: tests fail because the focused gateway and typed outcomes do not exist and the current private detail action is selected.

- [ ] **Step 3: Implement the public settings gateway and UI state**

Use ordered public intents, actual system queries, precise user-facing copy, and the existing button/card. Do not persist authorization. Preserve the failure toast only for a true exhausted/failed outcome.

- [ ] **Step 4: Run focused tests and verify GREEN**

Expected: all navigation and status tests pass.

- [ ] **Step 5: Commit**

Commit message: `fix: open Wear OS DND policy settings reliably`

### Task 3: Unify and migrate critical alarm delivery

**Files:**
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7CgmAlarms.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7AlarmSettingsActivity.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7CgmAlarmsTest.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7AlarmSystemAccessTest.kt`

**Interfaces:**
- Consumes: `G7AlarmSystemSnapshot`, all `CgmAlarmType` values, existing engine events and deduplication/repeat stores.
- Produces: one versioned channel family; `deliver(context, G7AlarmDelivery)` common real/test path; test delivery with no real alarm-state mutation.

- [ ] **Step 1: Write failing channel and delivery-path tests**

Assert eight channel definitions, high importance, alarm category, vibration, DND bypass capability reporting, alarm audio attributes, one-shot legacy migration, common real/test delivery, unchanged alarm/deduplication state after tests, and all alarm types selecting the same policy.

- [ ] **Step 2: Run focused tests and verify RED**

Expected: tests expose the current split direct-call behavior or insufficient channel/status semantics.

- [ ] **Step 3: Implement the common notifier path and channel migration**

Route production and test notifications through a typed delivery entry point. Keep per-alarm assets with `USAGE_ALARM` only if the shared path controls playback. Version immutable channel changes once and retain acknowledgement/repeat behavior.

- [ ] **Step 4: Run alarm/engine tests and verify GREEN**

Expected: notifier, engine, repeat, acknowledgement, and migration tests pass without warnings.

- [ ] **Step 5: Commit**

Commit message: `fix: harden SugarWear alarm delivery policy`

### Task 4: Persist collector-owned sensor-search commands

**Files:**
- Create: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7SearchCommand.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7StateStore.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7CollectorService.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/AndroidG7Ble.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7WatchActivity.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7SearchCommandTest.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7LifecyclePolicyTest.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7BlePolicyTest.kt`

**Interfaces:**
- Consumes: existing sensor credentials, persisted collector state, foreground service, BLE scanner, and reconnect scheduler.
- Produces: `G7SearchCommandStore`; `G7SearchCommandState`; explicit `start`, `recordWindowResult`, `scheduleRetry`, `complete`, and `cancelByUser` transitions observed by the UI.

- [ ] **Step 1: Write failing state-machine/lifecycle tests**

Cover one durable command, UI pause/stop/destroy neutrality, scan-window miss to bounded backoff, explicit cancellation, terminal error classification, reopen/observe behavior, and background discovery continuing to connection.

- [ ] **Step 2: Run focused tests and verify RED**

Expected: failures show absent durable command ownership or current timeout-to-terminal behavior.

- [ ] **Step 3: Implement durable command ownership and bounded scan/backoff**

Move search termination decisions into collector transitions. The Activity sends commands and renders state only. A scan callback/window completion cannot create “not found” while the durable command remains active.

- [ ] **Step 4: Run focused collector tests and verify GREEN**

Expected: lifecycle, BLE, and state-machine tests pass; retry schedule remains bounded.

- [ ] **Step 5: Commit**

Commit message: `fix: persist SugarWear sensor discovery`

### Task 5: Restore active search and alarm operation after process/boot recovery

**Files:**
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7RuntimeReconciler.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7RecoveryReceivers.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7AdvertisementWake.kt`
- Modify: `g7watch/src/main/kotlin/app/aapswear/g7watch/G7CollectorService.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7RuntimeReconcilerTest.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7ManifestLifecycleTest.kt`
- Test: `g7watch/src/test/kotlin/app/aapswear/g7watch/G7LifecyclePolicyTest.kt`

**Interfaces:**
- Consumes: `G7SearchCommandStore`, runtime health assessment, boot/package broadcasts, PendingIntent scan results.
- Produces: deterministic one-shot recovery decision that resumes an active search and existing alarm monitoring without duplicate scan/service scheduling.

- [ ] **Step 1: Write failing process/boot recovery tests**

Assert process death does not mark search failed, boot/package replacement resumes a valid command, PendingIntent discovery advances the same command, duplicate recovery is idempotent, and alarm monitoring remains service-driven.

- [ ] **Step 2: Run focused tests and verify RED**

Expected: recovery assertions fail against current in-memory/pairing-deadline behavior.

- [ ] **Step 3: Implement recovery integration**

Reconcile persisted commands into scheduled/foreground work using existing receivers and the filtered scan wake path. Do not add periodic polling or restart loops.

- [ ] **Step 4: Run recovery and alarm tests and verify GREEN**

Expected: all recovery tests pass and no duplicate schedule is observed.

- [ ] **Step 5: Commit**

Commit message: `fix: resume G7 search after process recovery`

### Task 6: Full validation, documentation, and hardware handoff

**Files:**
- Modify: `docs/analysis/G7_WATCH_COLLECTOR_COMPLETE_ANALYSIS_2026-09-25.md`
- Modify: relevant changelog/release documentation
- Test: all repository modules and static gates

**Interfaces:**
- Consumes: completed Tasks 1–5.
- Produces: validated repository state and explicit hardware matrix labelled CODE-VALIDIERT, BUILD-VALIDIERT, and HARDWARE-TEST OFFEN.

- [ ] **Step 1: Audit runtime paths and repository references**

Trace Settings -> intent -> system state, alarm engine -> notifier -> channel/audio, UI -> search command -> collector -> BLE -> recovery, and verify xDrip source integration is absent.

- [ ] **Step 2: Run focused suites**

Run all affected source, resolver, persistence, alarm, collector, lifecycle, and recovery tests. Expected: all pass without warnings introduced by this work.

- [ ] **Step 3: Run full quality gate**

Run `test assembleDebug assembleRelease lint detekt ktlintCheck`. Expected: `BUILD SUCCESSFUL`; fix every introduced error/warning without weakening a gate.

- [ ] **Step 4: Perform fresh whole-branch review and one fix pass**

Review against the spec and Review Focus. Any Critical/Important issue receives a failing regression test, one fix, and a green suite.

- [ ] **Step 5: Commit final documentation/validation adjustments**

Commit message: `docs: record SugarWear alarm and search validation`

- [ ] **Step 6: Prepare hardware validation**

Report the exact connected device/package/build and retain DND/search matrix items as `HARDWARE-TEST OFFEN` until physically exercised.
