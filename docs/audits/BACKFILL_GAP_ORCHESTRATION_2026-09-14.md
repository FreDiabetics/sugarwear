# Phase 7 - Backfill and gap orchestration

Date: 2026-09-14

## Result

The current collector already implements the required event-driven recovery rule; no periodic "backfill every ten minutes" timer is used or needed.

On every collector cycle it:

1. loads the oldest persisted recoverable gap for the active sensor/session;
2. closes ledger gaps already satisfied by a valid database reading;
3. selects a sensor-clock anchor for the oldest remaining gap;
4. commits LIVE immediately without waiting for history;
5. requests the coalesced history range on that same successful connection;
6. records request, response, commit, outcome, opportunity time, and attempt count;
7. leaves an incomplete gap recoverable so the next successful contact retries it;
8. closes every matching same-session window returned by one response;
9. marks old-session gaps terminal on explicit unlink/handoff.

This satisfies the deterministic first-contact request and second-contact retry behavior while avoiding parallel or blind requests. The request is part of the single coalesced collector cycle and the runtime registry prevents simultaneous automatic cycles.

## Verification

The following focused suites passed:

- `G7BackfillOrchestratorTest`
- `G7ReadingDatabaseTest`
- `G7RuntimeReconcilerTest`

They cover persisted attempts across restart, incomplete response retry, second-contact recovery within ten minutes, multi-gap response coalescing, session isolation, late first LIVE history recovery, and automatic-trigger coalescing.

## Hardware boundary

The previously observed SLA distribution cannot be replaced by unit-test evidence. Phase 17 must measure the same persisted ledger fields on real hardware and require 100 percent of technically recoverable gaps within ten minutes when suitable sensor contact existed.
