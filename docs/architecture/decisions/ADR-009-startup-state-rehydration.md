# ADR-009: Startup state rehydration

Status: Accepted

## Problem

Process recreation, reboot, package replacement and Watch reconnect must not leave a visible
surface empty until the next CGM measurement. A restore must also not make an old measurement look
new or replay measurement-driven alarms and debounce transitions.

## Decision

`TherapyStateStore` remains the persisted canonical display snapshot and graph-history store.
Mobile and Wear surfaces read it immediately. Wear additionally resolves the independent persisted
G7 Collector rows through the existing canonical resolver.

At Wear runtime startup the shared `WearStartupStateCoordinator` reads the stored inputs, evaluates
freshness against the current clock, and invalidates the open Wear UI, every managed complication
provider and every Sugarlicious tile. It does not save the result or deliver an alarm. Complication
requests and tiles render from the same resolver-backed snapshot.

At Mobile boot or package replacement the persistent bridge is restored and every widget is
invalidated after the stored snapshot becomes readable. The Mobile activity and notification
already observe the same store immediately.

On Watch/phone reconnect, Wear explicitly requests the current phone snapshot before independently
sending any pending Collector history. Existing timestamp/source acceptance and history dedupe
rules remain authoritative, so a repeated snapshot is idempotent and an older phone state cannot
replace a newer valid Watch Direct state.

The original `measuredAtEpochMs`, `receivedAtEpochMs`, source, quality, sensor/session and sequence
identity are retained. Freshness, age and trend/delta visibility are calculated at render time from
the original event time. Backfill remains a later Collector-owned history repair and is never a
prerequisite for startup rendering.

## Consequences

- A persisted fresh or stale reading is visible without waiting for a new CGM event.
- Stale data stays stale after reboot; restore cannot reset its age.
- Restore does not replay HIGH/LOW or signal-loss alarms and does not advance debounce counters.
- Empty stores remain explicit `NO_DATA`/`NO_SOURCE`; no glucose, range, loop or trend defaults are
  promoted to measurements.

## Non-goals

- Creating a second transport protocol or history database.
- Running periodic refresh polling as a substitute for lifecycle invalidation.
- Using G7 backfill to manufacture a current reading.
