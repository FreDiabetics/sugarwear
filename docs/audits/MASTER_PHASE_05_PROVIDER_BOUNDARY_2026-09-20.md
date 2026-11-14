# Master phase 5 — Wear provider boundary

## Correction

- Android cursor access is isolated in `AndroidDirectCgmProvider`; the canonical
  resolver consumes a typed snapshot outcome and remains the only resolver state
  machine.
- Provider results distinguish success, genuinely empty data, unavailable provider,
  denied access, and malformed schema. Infrastructure failures are not converted into
  sensor errors and renderer callers remain non-throwing.
- Provider diagnostics retain only the most recent structured outcome and timestamp,
  coalescing repeats for five minutes instead of producing an unbounded event stream.
- Resolver memory remains in the existing single preference store and therefore keeps
  failover/recovery hysteresis across resolver recreation.

## Evidence

- Tests cover a successful snapshot, empty result, unavailable provider, permission
  denial, malformed cursor, and every corresponding resolver outcome.
- Full complication tests, Wear tests, Android Lint, Detekt, and Ktlint passed in the
  242-task focused gate; the added cursor-classification test also passed separately.
