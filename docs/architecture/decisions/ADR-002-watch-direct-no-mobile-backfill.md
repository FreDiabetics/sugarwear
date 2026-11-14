# ADR-002: Watch Collector exclusively owns sensor backfill

## Status
Accepted

## Problem
Sensor history requests must not be triggered by presentation surfaces or by Mobile. Validated
history still needs to be usable across Sugarlicious without creating a second collector.

## Decision
Only `:g7watch` may issue Dexcom G7 history requests. It stores history with `origin=BACKFILL`, the
historical event timestamp and a separate receive timestamp. Wear transports already persisted,
validated rows to Mobile as a consumer-only operation. Mobile may merge that history and use the
central automatic source resolver; it never scans, pairs, authenticates or asks the sensor for data.

## Rationale
This preserves BLE ownership while allowing one canonical, deduplicated history. Backfill rows do
not update current-reading freshness, alarms, range debounce or pairing success.

## Consequences
Cross-device messages retain origin. Acknowledgements mark Watch rows synced only after Mobile has
persisted them. Sensor/session/sequence identity prevents duplicates and cross-session mixing.

## Explicit non-goals
No Mobile-to-sensor backfill command, no polling loop, and no backfill implementation in graphs,
watchfaces, tiles, widgets or complications.
