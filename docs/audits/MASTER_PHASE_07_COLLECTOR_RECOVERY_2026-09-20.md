# Master phase 7 — collector, gap recovery, and release

## Verified architecture

- One runtime registry coalesces simultaneous automatic triggers; bounded callback
  queues and generation checks prevent parallel/stale GATT work.
- NO_CALLBACK receives one clean direct retry, GATT 133 uses bounded stack cooldown,
  and long outages retain a timed future retry while reducing scan pressure.
- The persistent sensor/session-aware expected-window ledger selects only open,
  technically recoverable gaps. A successful LIVE contact requests the oldest gap;
  an incomplete response leaves it open and the second suitable contact retries it.
- One response may close multiple matching windows, never another session. Tests prove
  the second-contact recovery stays inside the ten-minute SLA model.
- Explicit release cancels the active owner, cleans scanning, stops the service,
  requests Android bond removal, clears credentials/runtime/session/retry/alarm state,
  closes old session gaps, and preserves CGM history.

## Added correction

- A Bluetooth-adapter restart now has an explicit recovery path. Only `STATE_ON` for
  an enabled collector with a configured sensor cleans stale scan state, reconciles
  runtime ownership, and re-establishes one durable collector schedule.

## Evidence

- Existing suites cover GATT 133, NO_CALLBACK, scan fallback, process restart, long
  outage, duplicate LIVE/backfill, session separation, coalescing, second-contact
  backfill, and sensor release/history retention.
- New Bluetooth-restart policy coverage passed.
- SugarWear tests, debug assembly, Android Lint, Detekt, and Ktlint completed
  successfully across 184 tasks.
