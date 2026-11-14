# Sugarlicious/SugarWear architecture audit (2026-09-14)

## Intended architecture

The accepted ADRs define these boundaries:

- `core-model` owns shared CGM identity, freshness, source resolution, graph, alarm, and presentation policy.
- Mobile source adapters produce candidates; Mobile publishes one canonical phone-fed display state.
- `TherapyStateStore` is the persisted observable display boundary.
- renderers, widgets, tiles, complications, and watchfaces do not independently select sources.
- SugarWear collector history is local to the standalone collector and does not become a Mobile CGM source.
- startup rehydration preserves original measurement identity and recalculates only time-derived freshness.

The implementation substantially follows this architecture, but the following mismatches and ownership risks require targeted follow-up.

## Confirmed architecture findings

### A-01: Retired SugarWear-history transport remains wired through runtime services

Status: confirmed architecture debt and energy/reliability candidate.

Evidence:

- `G7BackfillSync` states that SugarWear history is private and implements `sendPending` and `acknowledge` as no-ops.
- `StateDataLayerService` still invokes these no-ops at service startup, peer reconnect, explicit sync request, and acknowledgement receipt.
- `WearProtocol` still exposes G7 reading, batch, acknowledgement, and sync paths and serializable batch types.
- `MobileDataLayerService` still decodes incoming G7 batches, clears the retired Mobile backfill store, constructs an acknowledgement, transmits it, and records an ignored-batch diagnostic.
- Superseded 2026-09-20: the clearing-only `MobileG7BackfillStore` and its retired
  batch/ack protocol were removed after the one-time migration was retained directly.

Required resolution:

1. Establish whether any currently supported installed version still sends these messages.
2. Define the compatibility window and protocol-version behavior.
3. Characterize old/new peer interactions with contract tests.
4. Remove runtime calls and transport types that are unreachable after the compatibility window, or isolate a bounded compatibility decoder without normal-runtime triggers.
5. Verify that removing this path cannot delete SugarWear's collector-local history or break Watch diagnostics.

### A-02: Phone input and display state are persisted in two sequential stores

Status: confirmed consistency risk; no observed corruption is claimed yet.

`MobileCanonicalStateCoordinator.savePhoneInput` saves `PhoneTherapyStateStore` and then `TherapyStateStore`, followed by consumer dispatch. A process death or write failure between the two DataStore transactions can leave raw phone input and display state at different revisions. Startup contains fallback logic, but no shared revision or atomic reconciliation contract currently proves which store wins after an interrupted write.

Required resolution:

- add a stable state/event revision,
- test interruption between writes,
- define deterministic reconciliation,
- avoid introducing a third state store,
- preserve immediate observable display behavior.

### A-03: Wear source resolution owns persistence and Android provider access inside `complications`

Status: boundary risk.

`G7LocalReadingResolver` both queries the SugarWear content provider, selects the canonical source, merges history, and persists resolver hysteresis in `SharedPreferences`. It is consumed by Wear UI/tiles/complications, but resides in the complication-provider module. This makes the complication module an API-exporting runtime/domain integration layer rather than a renderer-only module.

Required resolution:

- determine whether the module boundary is intentional,
- keep one resolver implementation,
- separate pure resolution from Android I/O if this can be done without duplicating state,
- retain the same provider contract and resolver memory during any extraction.

### A-04: Resolver I/O failures collapse silently to empty source data

Status: observability and failure-classification risk.

Content-provider status/history reads and resolver-memory parsing use `runCatching(...).getOrNull/getOrDefault`. This prevents crashes, but permission, schema, process, and malformed-data failures become indistinguishable from a legitimately empty collector. The UI can therefore show no source without durable evidence of why the source disappeared.

Required resolution:

- preserve non-crashing fallback,
- emit bounded structured diagnostics by failure category,
- prevent repeated identical diagnostic spam,
- test provider unavailable, permission denied, malformed cursor, and empty database separately.

### A-05: Wear protocol exposes sensor-domain implementation types

Status: coupling candidate.

`wear-protocol` declares an API dependency on `dexcom-g7` because retired G7 reading batch types contain `CgmReading`. All Mobile and Wear protocol consumers therefore inherit the sensor-specific module even where only generic display state is required.

Required resolution:

- resolve A-01 first,
- if G7 batch transport is retired, remove the dependency after contract verification,
- otherwise define a transport-owned DTO and keep sensor protocol internals out of the generic cross-device API.

### A-06: Mobile source naming and filtering do not match the available source model

Status: clarity and potential behavior risk requiring tests before modification.

- `DataSourcePreference` still includes `AUTOMATIC`, `ANDROID_APS`, `XDRIP_PLUS`, and a legacy Watch source.
- xDrip input is accepted and saved by `MobileCanonicalStateCoordinator`.
- `mobileAndroidApsOnly` filters displayed history to AndroidAPS samples and is used by `MainActivity`, while its broader coordinator also handles xDrip.
- `migrateLegacyForcedG7Source` currently returns `ANDROID_APS` for every input; tests cover G7 and AndroidAPS but not automatic or xDrip selections.

Required resolution:

- add characterization tests for every enum input and migration state,
- decide whether Mobile is truly AndroidAPS-only or supports xDrip fallback,
- align names, history filtering, migration, settings, and documentation with the intended behavior,
- never silently overwrite an explicit xDrip selection during a legacy G7 migration.

## Structural hotspots

Large files are not automatically defects, but these central files combine several responsibilities and need test-protected decomposition review:

- `G7CollectorService`: service lifecycle, cycle orchestration, wake locks, notification, recovery, persistence, and dispatch.
- `AndroidG7Ble`: scan ownership, GATT lifecycle, bonding, protocol exchange, retries, and timing.
- `StateDataLayerService`: foreground lifecycle, state receive/persist, config, Watch Face Push, G7 compatibility hooks, diagnostics, and complication invalidation.
- `PersistentBridgeService`: foreground lifecycle, state observation, external-surface refresh, notifications, and fanout.
- `DashboardCharts`, `SugarliciousWidgets`, `ComplicationCatalog`, and complication files combine substantial geometry, policy adaptation, resource selection, and rendering.

Decomposition is warranted only where characterization tests demonstrate a stable seam. Shared semantic policy remains in `core-model`; platform rendering remains in the platform modules.

## Dependency direction

The Gradle project dependency graph is acyclic in the inspected configuration. The main direction is broadly sound: model → adapters/protocol/storage/shared UI → platform apps. The principal exceptions are intentional or candidate API exposures (`wear-protocol` → `dexcom-g7`, `complications` exporting storage-backed resolution) and must be addressed by behavior-preserving contract changes, not file moves alone.

## Phase-3 outcome

The audit found six concrete architecture workstreams. The highest priority for data-flow analysis is the dual-store commit/reconciliation path, followed by retired G7 history transport, resolver error collapse, and the ambiguous Mobile xDrip/AndroidAPS policy. No production source was changed during architecture analysis.
