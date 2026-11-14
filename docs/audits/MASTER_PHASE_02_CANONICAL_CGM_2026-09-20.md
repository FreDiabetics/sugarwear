# Master phase 2 — canonical CGM contracts

## Findings

- The production resolver in `core-model` already owns source failover, freshness,
  recovery hysteresis, and `NO_SOURCE` decisions.
- A second resolver and freshness implementation in `dexcom-g7` was reachable only
  from its own tests and could silently diverge from the production policy.
- Measurement deduplication still treated a sequence collision, or a close
  timestamp/value pair without complete identity, as proof of equality.

## Corrections

- Cross-source deduplication now requires matching non-null sensor and session IDs,
  the same measurement timestamp, and a compatible value.
- Sequence numbers are retained as metadata and never establish measurement identity.
- Same-session LIVE/history copies inside the documented tolerance still collapse;
  distinct five-minute measurements remain distinct.
- Unknown or partial cross-source identities are kept separate instead of risking
  destructive data loss.
- The unused G7-local resolver, freshness evaluator, and gap detector were removed;
  production keeps the single core resolver and the persistent collector gap ledger.

## Evidence

- The three new regression tests failed before the implementation change.
- `:core-model:test` passed with 105 tests after the correction.
- `:core-model:test :dexcom-g7:test :dexcom-g7:detekt` completed successfully.
- Existing tests continue to cover measured/received timestamp separation,
  out-of-order and cross-session inputs, prediction-axis isolation, all resolver
  states, and two-distinct-reading recovery hysteresis.
