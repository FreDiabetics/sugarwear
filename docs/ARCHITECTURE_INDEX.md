# Sugarlicious architecture index

## Runtime data path

| Concern | Canonical location |
|---|---|
| CGM source adapters | `data-source-aaps`, `dexcom-g7` |
| Canonical Mobile resolution | `app-mobile/MobileCanonicalCgm.kt` |
| Shared domain and presentation policy | `core-model` |
| Persisted observable therapy state | `wear-storage/TherapyStateStore.kt` |
| Startup restore and consumer invalidation | `ADR-009-startup-state-rehydration.md`, `app-wear/WearStartupStateCoordinator.kt` |
| Cross-device contract | `wear-protocol` |
| Shared trend vector assets | `ui-shared` |
| Structured event ledger | `wear-storage/DiagnosticEventStore.kt` |
| Redacted support bundle | `app-mobile/DiagnosticBundleExporter.kt` |
| Screenshot comparison | `tools/screenshot-comparator` |

## Renderer ownership

Mobile, Widgets, Notifications, Wear, G7 Collector, Tiles, Complications and Watchfaces own platform rendering only. Shared classification, freshness, time, graph and glucose geometry policy belongs in `core-model`.

## Change checklist

1. Read the applicable ADR in `docs/architecture/decisions`.
2. Change shared semantic policy before changing individual renderers.
3. Add a deterministic contract test using an injected `AppClock` where time matters.
4. Record state transitions with stable diagnostic codes.
5. Update visual references intentionally and retain a diff for visual changes.
6. Increment the applicable schema version and add a monotonic migration for persisted settings changes.

## Executable boundary gate

Run `verifyArchitecture` before and after changing module dependencies or Android manifests. The gate enforces that shared modules never depend on application shells, Mobile cannot acquire the direct G7 BLE collector, and SugarWear remains a BLE-capable Wear-only package.

The detailed module, runtime-entry-point and state-owner map is maintained in `docs/refactoring/PROJECT_CHANGE_MAP_2026-10-05.md`.
