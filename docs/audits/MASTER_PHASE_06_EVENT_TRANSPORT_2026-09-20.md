# Master phase 6 — event-driven transport and fanout

## Correction

- Wear rejects an equal or older non-zero canonical revision before history merge,
  provider access, alarm work, persistence, or surface invalidation. Legacy revision
  zero peers retain the timestamp compatibility policy.
- Protocol event identity now includes the canonical revision, so a therapy-only
  commit with unchanged glucose/timestamps remains a distinct durable event.
- Message-first and DataItem-first delivery converge through the same serialized
  application path; a second copy of an applied revision is discarded.
- Glucose-only changes update the glucose tile, therapy-only changes update the
  therapy tile, and transport-only metadata changes update neither. Explicit settings
  and startup invalidation can still request both.
- The existing transport continues to launch durable DataClient and bounded immediate
  MessageClient delivery independently; consumer failures remain isolated by the
  existing supervised fanout.

## Evidence

- New regression tests first failed for duplicate/older revisions and therapy-only
  event identity, then passed after implementation.
- Tile planner tests cover glucose-only, therapy-only, and revision-only changes.
- Protocol and complete Wear unit suites passed across 179 tasks.
