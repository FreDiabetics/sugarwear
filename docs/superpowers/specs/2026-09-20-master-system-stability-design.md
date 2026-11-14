# Sugarlicious Master System Stability Design

## Purpose

This design implements the user-supplied Gesamt-Masterprompt dated 2026-09-20. Sugarlicious must expose one canonical CGM truth across Mobile, Wear, SugarWear, collector, transport, alarms, tiles, complications, and watchfaces. Correctness takes priority over availability: uncertain state is `STALE` or `NO_SOURCE`, never a fabricated fresh reading.

## Binding constraints

- Mobile does not gain a Dexcom G7 BLE collector. Direct G7 collection remains Wear-only.
- `measuredAt`, not receipt time, determines freshness.
- Predictions never participate in CGM identity, freshness, resolver choice, or gap closure.
- The central resolver is authoritative. UI, tiles, complications, and watchfaces do not select sources independently.
- Phone/watch transport state, BLE state, collector phase, sensor state, and CGM freshness remain distinct.
- Recovery is event-driven and persistent. No blind timer, busy loop, artificial delay, or aggressive scan loop is introduced.
- Existing collector, resolver, persistence, graph, and presentation abstractions are repaired or extracted; no parallel replacement architecture is created.
- Historical collector readings survive sensor release while active identity, credentials, bonding/runtime/retry state are removed.
- Retired watchfaces do not remain in product packaging, selection, installers, or the active release matrix.
- Quality gates may become stricter but never weaker. Hardware-only behavior is not claimed from unit tests.

## Architecture

The canonical path is:

`source adapter -> validation -> persisted canonical candidate/state -> resolver -> transport -> presentation model -> surface renderer`

`core-model` owns source resolution, identity, freshness, signal semantics, graph semantics, and platform-neutral presentation policy. Platform modules own I/O and rendering. Stores persist sufficient revisions and resolver/collector state to reconcile process death without reviving stale runtime objects.

Mobile uses AAPS/xDrip adapters to commit one reconciled phone state and publishes that state over the existing durable-plus-low-latency Wear transport. Wear rejects duplicate transport revisions before resolver, alert, provider, tile, or complication side effects. SugarWear stores direct-watch readings locally and exposes categorized provider outcomes to the Wear resolver.

## Work packages

1. Establish a current architecture/reachability ledger and remove retired watchfaces from active packaging and release gates without deleting historical test evidence.
2. Complete canonical identity, source policy, freshness, prediction isolation, and signal-loss contracts in `core-model`.
3. Make Mobile raw-input/display persistence revisioned and deterministically recoverable after interrupted writes.
4. Retire obsolete G7 history transport compatibility paths after contract characterization; remove the generic protocol dependency on sensor implementation types.
5. Separate Wear resolver policy from provider I/O, expose categorized provider failures, and rate-limit diagnostics.
6. Harden event-driven Mobile-to-Wear propagation, receiver completion bounds, deduplication, and targeted surface invalidation.
7. Complete Wear collector lifecycle, reconnect, persistent gap ledger, deterministic LIVE-triggered backfill, and full sensor release while preserving history.
8. Centralize alarm/signal-loss semantics and verify all UI, tiles, complications, and watchfaces consume them.
9. Repair the SugarWear CGM history tile through the shared graph model used by the in-app graph; validate round-display geometry.
10. Add persistent IOB/COB/Basal tile configuration and shared presentation semantics for every 1/2/3-item layout.
11. Audit background/process-death recovery and eliminate duplicate subscriptions, unnecessary wakeups, broad invalidations, and polling.
12. Execute the complete automated quality matrix, document hardware-only acceptance, install only after green gates, then leave the branch for user testing.

## State and failure semantics

Every CGM sample identity combines source plus sensor/session identity and measurement time; sequence number alone is insufficient. Backfill retains original measurement time and cannot refresh stale data. Resolver hysteresis requires two distinct fresh Mobile measurements before switching back from Watch Direct.

Provider reads return typed outcomes (`Success`, `Empty`, `Unavailable`, `PermissionDenied`, `Malformed`) rather than collapsing infrastructure errors into empty data. Presentation remains non-throwing and maps uncertainty to `STALE`/`NO_SOURCE`; structured diagnostics are deduplicated and rate-limited.

Signal loss is derived from canonical freshness and valid source availability. A disconnected phone, Data Layer link, or transient BLE connection is diagnostic state only while a recent canonical reading remains valid.

## Testing

Each package starts with characterization or failing regression tests and ends with its focused suite. Required coverage includes resolver permutations, interrupted commits and restart reconciliation, duplicate transport orderings, provider failure categories, collector recovery causes, gap/backfill timing rules, sensor release, surface signal semantics, graph parity/visibility, tile layouts/persistence, and duplicate lifecycle ownership.

The final gate includes `test`, `assembleDebug`, product release variants, `lint`, `detekt`, `ktlintCheck`, the official WFF validator, code-free WFF verification, and project QA scripts. Any deliberate WFF-count reduction is reported as removal of retired products rather than forced reproduction of the historical 30-module count.

## Hardware boundary

Automated tests cannot prove BLE radio behavior, OEM background survival, battery impact, active/AOD appearance, sensor handoff, or backfill SLA. Available Galaxy Watch Ultra and S26 Ultra evidence is collected only after the automated gate. Pixel Watch is excluded from the initial installation and remains a later user-directed test target.

## Integration policy

Work remains on `feature/master-system-stability` until the user completes acceptance testing. No merge to `main`, push, or branch deletion occurs before explicit approval. After approval, the branch is integrated, redundant branches are removed only when they contain no unique/uncommitted work, and final `main` gates are rerun.
