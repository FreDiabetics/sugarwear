# G7 Collector Failure Hardening Plan

**Goal:** Prevent receiver work and unexpected collector failures from becoming opaque or indefinitely active while preserving the existing BLE state machine, alarms, gap ledger, and bounded recovery.

## Task 1: Preserve unexpected-failure provenance

- Add a small pure diagnostic descriptor for unexpected exceptions.
- Write failing tests for stable exception class, phase and privacy-safe fingerprint output.
- Feed the descriptor into the existing durable diagnostic event before `G7-INT-500` recovery.
- Never persist stack traces, sensor secrets, addresses, or pairing codes.

## Task 2: Bound BroadcastReceiver asynchronous ownership

- Extend `G7ReceiverWorkDispatcher` with an independent completion deadline.
- Ensure `PendingResult.finish()` is invoked exactly once on success, launch failure, recovery failure, or timeout.
- Keep recovery serialized and allow the existing scheduler/service invariants to repair incomplete work.
- Add race-focused tests covering completion-before-timeout, timeout-before-completion, thrown recovery and thrown launch.

## Task 3: Verify lifecycle and regression safety

- Run focused collector, receiver, persistence, runtime-reconciler and BLE policy tests.
- Run complete `test`, `assembleDebug`, `assembleRelease`, `lint`, `detekt`, and `ktlintCheck` gates.
- Install SugarWear on the connected Galaxy Watch and confirm foreground service, fresh reading, no crash/ANR, and bounded receiver completion diagnostics.
- Review the final diff for new races, duplicate completion, leaked jobs, aggressive retries, or weakened gates.

## Constraints

- No second collector or alarm engine.
- No changes to sensor/session identity, CGM values, deduplication, backfill ordering, or source resolution.
- No aggressive keepalive, scan loop, or immediate restart loop.
- `main` remains untouched until reviewed, verified integration.
