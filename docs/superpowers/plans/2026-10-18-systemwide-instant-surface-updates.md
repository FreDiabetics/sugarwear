# Systemwide Instant Surface Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every Mobile and Wear surface show each newest canonical revision immediately while retaining the last valid rendering until its replacement is complete.

**Architecture:** Add latest-wins coordinators at the Mobile-widget and Wear-surface boundaries. Pass committed state directly into rendering, perform independent surface invalidations concurrently, and use persisted last-known-good content for cold/rebind paths. Settings persist continuously but only the newest configuration is rendered, with a mandatory flush when the settings Activity leaves the foreground.

**Tech Stack:** Kotlin, coroutines/Flow, Android DataStore, Jetpack Glance AppWidget, Wear OS Tiles, Wear Complication Data Sources, Google Play Services Wearable Data/Message clients, JUnit/Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-18-systemwide-instant-surface-updates-design.md`

## Global Constraints

- No new polling loop, short timer, aggressive wake lock, or duplicate state store.
- A committed newer revision can never be replaced by an older render or transport delivery.
- Existing resolver, source-validation, freshness, signal-loss, and G7 separation semantics remain unchanged.
- A valid visible state is replaced only after the new render is complete.
- Loading, spinner, temporary empty, and loading-dash states are forbidden on productive surfaces.
- Existing unrelated dirty working-tree changes must be preserved.

## Review Focus

- Rapid AAPS bursts while a graph bitmap is still rendering: newest revision must win without a long queue.
- Settings Activity receives `onStop` during a slider drag: the Launcher must receive the final persisted configuration.
- Message and delayed DataItem contain different revisions: Watch must remain on the newer revision.
- One Tile or complication update request throws or stalls: all other surfaces must still be invalidated.
- Process death before any fresh post-restart value: last-known-good remains visible, not Loading or blank.

---

### Task 1: Latest-wins Mobile widget coordinator

**Files:**
- Create: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetUpdateCoordinator.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWidgets.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/CanonicalDataChangeFanOut.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetUpdateCoordinatorTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/CanonicalDataChangeFanOutTest.kt`

**Interfaces:**
- Produces: `WidgetUpdateRequest(revision: Long, state: TherapyDisplayState?, target: WidgetUpdateTarget)`.
- Produces: `WidgetUpdateCoordinator.submit(request)` and `suspend fun flush(target): Unit`.
- Produces: `SugarliciousWidgets.update(context, state)` that invalidates the four widget families concurrently.

- [ ] Write failing tests proving: widget families start concurrently; active revision plus only newest pending revision render; an older completion cannot publish after a newer revision; a slow widget cannot delay Wear push.
- [ ] Run `:app-mobile:testDebugUnitTest --tests '*WidgetUpdateCoordinatorTest*' --tests '*CanonicalDataChangeFanOutTest*'`; expect behavioral assertion failures.
- [ ] Implement the coordinator and pass the committed state from `dispatchCanonicalDataChanged` instead of re-reading it for the event-driven path.
- [ ] Run the focused tests; expect PASS.
- [ ] Commit `fix(mobile): make widget updates latest-wins`.

### Task 2: Settings persistence and foreground-exit flush

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetInstanceConfiguration.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetInstanceConfigurationTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetUpdateCoordinatorTest.kt`

**Interfaces:**
- Consumes: `WidgetUpdateCoordinator.submit` and `flush` from Task 1.
- Produces: `WidgetSettingsUpdateController.onChanged(configuration)` and `suspend fun onLeaving(): Unit` (or equivalent lifecycle adapter).

- [ ] Write failing tests proving slider bursts persist every latest value but coalesce render work, and `onLeaving` waits for the last target instance revision.
- [ ] Run the focused tests; expect behavioral assertion failures.
- [ ] Replace the per-value full `SugarliciousWidgets.update` call with coordinator submission; make `onStop` persist and flush the final instance update without allowing stale overwrite.
- [ ] Run the focused tests; expect PASS.
- [ ] Commit `fix(mobile): flush final widget settings immediately`.

### Task 3: Last-known-good widget cold/rebind presentation

**Files:**
- Create or modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/WidgetSnapshotStore.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWidgets.kt`
- Modify: `app-mobile/src/main/res/layout/widget_*_initial.xml`
- Modify/delete: loading-only widget resources if still referenced
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetInitialLayoutContractTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetSnapshotStoreTest.kt`

**Interfaces:**
- Produces: persisted `WidgetSurfaceSnapshot` metadata keyed by widget instance and revision.
- Consumes: completed render publication from Task 1.

- [ ] Write failing tests proving no productive layout/resource contains Loading/spinner/loading dash, a valid snapshot survives render failure/restart, and neutral no-data is used only without history.
- [ ] Run focused tests; expect failures from current initial/restore behavior.
- [ ] Persist publication metadata/content needed for immediate recovery and ensure new content is swapped only after complete rendering.
- [ ] Run focused tests; expect PASS.
- [ ] Commit `fix(mobile): retain last widget surface during refresh`.

### Task 4: Parallel Wear surface fan-out

**Files:**
- Create: `app-wear/src/main/kotlin/app/aapswear/wear/WearSurfaceUpdateCoordinator.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/StateDataLayerService.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/G7ReadingUpdateReceiver.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/WearActivity.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/SugarliciousTiles.kt`
- Modify: relevant complication presentation paths under `complications/src/main/kotlin`
- Test: `app-wear/src/test/kotlin/app/aapswear/wear/WearSurfaceUpdateCoordinatorTest.kt`
- Test: `app-wear/src/test/kotlin/app/aapswear/wear/StateDataLayerServiceTest.kt`
- Test: relevant complication tests

**Interfaces:**
- Produces: `WearSurfaceUpdate(revision, oldState, newState)` and `WearSurfaceUpdateCoordinator.dispatch(update)`.
- Consumes: accepted and atomically persisted Watch state.

- [ ] Write failing tests proving persistence precedes invalidation, Wear-App/Tile/complication consumers start independently, one failure cannot block others, direct G7 uses the same fan-out, and duplicate/older payloads create no update.
- [ ] Run focused Wear/complication tests; expect behavioral assertion failures.
- [ ] Implement the shared post-commit fan-out with supervised concurrent consumers and revision diagnostics.
- [ ] Run focused tests; expect PASS.
- [ ] Commit `fix(wear): fan out canonical state instantly`.

### Task 5: Remove all productive loading branches and verify recovery

**Files:**
- Modify: Mobile/Wear/complication presentation files identified by `rg -n 'Loading|loading|spinner'`.
- Modify: associated tests.

**Interfaces:**
- Consumes: last-known-good contracts from Tasks 1-4.
- Produces: no productive surface path capable of replacing a valid state with Loading/blank.

- [ ] Add/extend contract tests that scan productive layouts and assert presentation functions retain previous valid state across null, decode failure, cancellation, and process recovery.
- [ ] Run focused contract tests; expect current loading branches/resources to fail.
- [ ] Remove or reroute each productive loading branch to last-known-good/no-data semantics without changing stale/signal-loss semantics.
- [ ] Run focused tests; expect PASS.
- [ ] Commit `fix: remove transient loading surfaces`.

### Task 6: Full quality gate and device validation

**Files:**
- Modify tests only if a newly exposed real regression requires a root-cause fix.

**Interfaces:**
- Consumes all prior tasks.

- [ ] Run relevant focused Mobile, Wear, storage, protocol, Tile, and complication tests.
- [ ] Run `test assembleDebug :app-mobile:assembleRelease :app-wear:assembleRelease :g7watch:assembleRelease lint detekt ktlintCheck`; expect `BUILD SUCCESSFUL`.
- [ ] Install current Mobile debug APK on the identified phone and Wear/G7 debug APKs on the identified Watch; verify package paths and versions.
- [ ] Trigger/observe a real new value where possible and classify each surface as `HARDWARE-VALIDIERT` or `HARDWARE-TEST OFFEN`; never infer hardware success from builds.
- [ ] Commit any final verified corrections as `fix: close instant surface update regressions`.
