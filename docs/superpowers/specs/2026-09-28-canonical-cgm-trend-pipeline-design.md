# Canonical CGM trend pipeline design

## Goal

Make every Sugarlicious and SugarWear surface render the trend belonging to the selected canonical CGM reading. Preserve a valid source trend, derive a fallback only from compatible measured samples, and keep delta, elapsed time, rate, trend, and visual asset separate.

## Reference semantics

- AndroidAPS `TrendArrow` is the authoritative vocabulary for Mobile input. Its current calculator preserves a supplied arrow unless smoothing requires recalculation, and classifies derived rates at -3.5, -2, -1, +1, +2, and +3.5 mg/dL/min.
- Dexcom G7 documentation defines steady, slowly changing, changing, and rapidly changing bands by change over 15 minutes. A missing arrow means unavailable, not steady.
- A valid source trend is normalized by name and never reclassified from the displayed delta.

## Architecture

The existing `Trend` enum remains canonical. A shared core policy owns explicit direction parsing and rate classification. Rate classification accepts an explicit profile: AndroidAPS-compatible fallback or Dexcom-G7 rate semantics. No UI module classifies rates.

The canonical reading carries glucose, delta, measured timestamp, source, sensor/session identity, optional source rate, and canonical trend together. Fallback selection uses `measuredAt`, requires a positive suitable interval, matching source and matching non-null sensor/session identities when those identities exist, and rejects invalid/stale/incomparable samples. It never uses `receivedAt` ordering.

`TrendVisuals` remains the only semantic trend-to-asset mapping. Platform resource lookup remains in `TrendDrawableResources`; renderers consume the shared specification and do not keep local icon tables.

## Source behavior

- AndroidAPS: parse all seven public direction names/symbols; preserve them. When absent, derive via the shared AndroidAPS-compatible fallback from canonical history.
- G7 collector: preserve a decoded source trend if the protocol supplies one. Otherwise classify the decoded rate with Dexcom semantics. Only if neither exists, derive rate from compatible local readings.
- Backfill and out-of-order values may enrich history but cannot replace the current LIVE trend merely because they arrived later.
- Resolver switches the complete reading tuple; it cannot combine glucose from one source with trend or delta from another.

## Unknown and freshness

Missing, invalid, stale, cross-session, cross-sensor, non-positive interval, or unsuitable-gap evidence yields `UNKNOWN`. `UNKNOWN` maps to no directional asset and is never coerced to `FLAT`.

## Observability

Debug-only structured diagnostics record glucose, delta, elapsed minutes, rate, source trend, canonical trend, source, and selected visual asset. Release builds do not emit the diagnostic.

## Verification

Tests cover all source directions, explicit thresholds and neighboring values, requested positive/negative deltas, 4/5/6/10 minute intervals, monotonic ordering, backfill/out-of-order input, sensor/session changes, resolver atomicity, transport round trips, all render surfaces, and rapid-rise/fall alarm inputs. The full repository quality gate must pass.
