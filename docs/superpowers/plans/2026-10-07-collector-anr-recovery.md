# Collector ANR and restart recovery implementation plan

## Files

- Modify `G7ExpectedWindowLedger.kt`: batch reconstruction and single persistence.
- Modify `G7RecoveryReceivers.kt`: shared bounded dispatcher/coordinator.
- Modify heavy receiver files: delegate work to the shared dispatcher.
- Modify/add `g7watch` tests: large-gap persistence, deferral, serialization and completion.

## Tasks

1. Add a failing large-gap test that counts durable ledger writes and asserts one write for many missing slots.
2. Implement snapshot-based merge and one retained write; run ledger/backfill tests.
3. Add failing receiver-dispatch tests for source-control, signal-loss and watchdog deferral plus shared serialization; move heavy work behind the common dispatcher.
4. Run focused lifecycle/collector tests, complete `g7watch` gates, then repository-wide tests/build/static gates and install the verified Collector on the watch.

## Review focus

- Timeout completion must remain exactly-once even when work finishes later.
- Recovery serialization must not block the Android main thread.
- Batch reconstruction must not overwrite concurrent window updates.
- Reboot and source-selection must preserve `collectorEnabled` and sensor/session state.
- Alarm acknowledgement and signal-loss behavior must remain unchanged.
