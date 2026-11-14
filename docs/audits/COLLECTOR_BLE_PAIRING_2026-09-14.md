# Phase 6 - Collector, BLE, pairing, and handoff

Date: 2026-09-14

## Existing safeguards verified

- Collector attempts carry persisted attempt identifiers.
- Every GATT connection receives a monotonically increasing ownership generation.
- Callbacks are accepted only for the currently active ownership and matching `BluetoothGatt` instance.
- Cleanup invalidates ownership before disconnect/close and is idempotent.
- Pairing has a persisted attempt/deadline, a single in-flight start gate, asynchronous execution, cancellation, and a one-shot success completion gate.
- Explicit unlink cancels the active cycle before bond removal, clears credentials and sensor state, preserves reading history, and invalidates provider views.
- Restart tests retain sensor/session/history while resetting volatile runtime.

## Fixed stale write callback failure

A late failed callback for a different characteristic on the current GATT was classified as stale but still threw `G7-GATT-214`, aborting the current write. Stale successes were already ignored, so failure handling was asymmetric.

Both successful and failed callbacks for stale characteristics are now ignored. Only a failure for the characteristic currently being awaited can fail that operation. Generation and GATT-instance checks remain the outer ownership boundary.

## Verification

- New regression test failed before implementation because the stale-failure policy did not exist.
- `:g7watch:testDebugUnitTest --tests app.aapswear.g7watch.G7BlePolicyTest` passed after the fix.

## Remaining validation boundary

Unit tests prove transition and callback policy, not radio behavior. Pairing, cancellation, bond removal, and two-watch handoff still require the Phase 17 Galaxy Watch Ultra and Pixel Watch hardware matrix.
