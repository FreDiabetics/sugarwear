# Project-wide refactoring audit — 2026-10-06

## Scope and invariant

Audit window: `e885e16d..HEAD`. The refactor preserves observable Mobile, Wear, Tile, Complication, watchface and G7 collector behavior. Persisted keys, wire payloads, manifest component names, resolver thresholds, alarm decisions and BLE retry timing were treated as compatibility contracts.

## Completed ownership changes

- The build now has an executable module/manifest boundary check (`verifyArchitecture`).
- Canonical CGM identity is owned by `CanonicalCgmIdentity`; sequence numbers remain transport metadata.
- Two-copy canonical persistence is owned by `RevisionedStateRepository`; all existing keys and schemas remain unchanged.
- G7 lifecycle/radio policy, protocol mapping, failure fingerprinting, BLE decision policy and GATT-generation ownership are separated from Android Service/callback shells.
- Mobile graph viewport/history policy, activity-series processing and reusable geometry are separated from the Android renderer.
- Graph appearance visibility, key mapping and legacy migration are separated from the Compose settings panel.

## Reviewed without speculative rewrites

- Complication providers already consume canonical state and shared formatting/trend assets. Their manifest-visible class names and preview/runtime paths remain stable. A bulk class move would add packaging risk without changing ownership or behavior, so it was not performed.
- Wear Tile services already delegate presentation and graph calculations to testable package functions. Existing parity, stale-state, trend-vector and graph tests remain the contract.
- `G7ReadingProvider` is read-only, closes every SQLite handle, bounds reading queries, sanitizes diagnostic metadata upstream and is not exported without its declared protection. Its synchronous `ContentProvider.query` contract makes a coroutine-only facade misleading; no fake timeout was added.
- Mobile and Wear transport services use owned supervisor scopes, bounded receiver completion and canonical persistence/deduplication. No new polling, wake lock, keepalive loop or silent fallback was introduced.
- Manifest review found the direct BLE permissions/features confined to `g7watch`; Mobile remains a bridge.

## Security and lifecycle conclusion

No reproducible new authorization bypass, exported-component exposure, unbounded retry loop, duplicate canonical writer, stale-callback ownership leak or resource leak was found in the reviewed paths. Proven lifecycle defects fixed before this audit remain covered by collector boot/process-recovery, coalescing, GATT-generation and persistence tests.

## Validation boundary

- `CODE-VALIDIERT`: ownership extractions are covered by existing and added unit tests.
- `BUILD-VALIDIERT`: the final repository-wide gate passed with 1,151 Gradle tasks. The generated JUnit reports contain 990 tests, 0 failures, 0 errors and 0 skipped tests. Debug and Release builds, architecture checks, Lint, Detekt and Ktlint passed. All four active/test WFF documents passed the official validator as format version 1, and all four Release APKs contain zero DEX files.
- `HARDWARE-TEST OFFEN`: refactoring does not itself prove BLE radio, OEM DND or process-death behavior on physical devices. Existing hardware evidence remains valid only for the previously installed build, not this final refactor until redeployed and observed.

## Residual risks

- Android/Wear OEM scheduling, BLE firmware behavior and DND routing require real-device observation.
- Large UI/service files remain candidates for future mechanical splitting, but their semantic policies now live in focused units; further movement should be driven by a behavior change or a narrowly scoped review to avoid packaging regressions.
