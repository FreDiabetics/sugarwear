# Phase 5 - Data integrity and deduplication

Date: 2026-09-14
Baseline: `feature/sugarwear-product-and-vigil-recovery` at `5e30c411c423a0753977c4bac3f6d8de5c96f8b0`

## Verified canonical measurement identity

The existing canonical history and source-resolution tests establish that transport sequence numbers are metadata, not identity. The effective identity and conflict checks include sensor/session identity and measurement time, retain distinct sensor sessions, and collapse LIVE/BACKFILL representations of the same measurement without allowing invalid, stale, or impossible-future samples into history.

Covered suites include `CanonicalCgmHistoryTest`, `CgmSourceResolutionTest`, `ReleaseCandidateSourceJourneyTest`, and `G7FoundationTest`.

## Fixed duplicate Data Layer delivery

Mobile intentionally sends full state through both the durable DataClient path and the low-latency MessageClient path. Wear previously accepted an equal receive timestamp, rebuilt history, queried the local provider, updated resolver memory, and evaluated alert mode before detecting that the merged state was semantically unchanged.

The meaningful-state gate now runs immediately after the required history/prediction merge and before provider, resolver, alert, persistence, complication, and tile side effects. A change in `receivedAtEpochMs` alone is transport metadata and does not reapply the state. A real therapy change with unchanged glucose remains accepted.

## Deferred consistency migration

`PhoneTherapyStateStore` and `TherapyStateStore` are still sequentially written. Collapsing them requires an explicit upgrade migration that preserves the newest valid state from both existing DataStore files. This is not safe to perform as an incidental edit because the first store may contain the only newest state after a historic process death. The migration is tracked as a separate persistence work package rather than silently discarding that recovery source.

## Verification

- Red test: `StateDeliveryPolicyTest` failed to compile before the semantic gate existed.
- Targeted test after fix: passed.
- Integrated gate: `:core-model:test :dexcom-g7:test :app-wear:testDebugUnitTest :app-wear:lintDebug` passed.
- No DataClient or MessageClient transport was removed.
