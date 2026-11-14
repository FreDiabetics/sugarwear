# Collector ANR and restart recovery design

## Goal

Prevent Collector process death and stranded recovery after boot, signal-loss, source-selection and watchdog broadcasts without changing G7 protocol, scheduling cadence, persisted schemas or alarm semantics.

## Proven failure

Three hardware ANRs show the signal-loss pending broadcast overlapping synchronous source-control work while runtime reconciliation repeatedly decodes and rewrites the full expected-window ledger under one lock. The existing coroutine timeout cannot interrupt blocking locks, JSON work or Binder calls.

## Design

- Reconstruct every missing expected window from one immutable ledger snapshot and persist the merged retained result once.
- Make all non-trivial Collector broadcast receivers use one bounded application receiver dispatcher. `onReceive` may validate primitives and acquire a short wake handoff only; persistence, database, reconciliation, alarm and notification work runs off the main thread.
- Serialize recovery-class receiver work through the existing shared recovery lock so boot, Bluetooth, reconnect, signal-loss, watchdog and source-control cannot perform competing repairs.
- Keep receiver completion idempotent and deadline-bounded. A deadline releases Android's pending broadcast but does not pretend blocking work was cancelled.
- Keep Collector service ownership unchanged. Receivers request or repair service work; they do not implement a second Collector state machine.

## Compatibility

No package/component names, BLE protocol behavior, five-minute slot calculation, alarm decisions, database schema, SharedPreferences keys, source resolver rules or sensor/session identity change.

## Acceptance

- Large outage reconstruction performs one ledger load and one save, preserves canonicalization and retention, and creates all expected gaps.
- Source-control, signal-loss and watchdog work does not execute inline in `onReceive`.
- Concurrent recovery broadcasts are serialized and every pending result finishes once.
- Existing Collector, BLE, alarm, persistence and lifecycle tests remain green.
- Build, lint, Detekt and Ktlint are green; hardware validation remains explicit.
