# Master phase 3 — revisioned Mobile persistence

## Findings

- Mobile wrote raw phone state and display state sequentially without a shared commit
  identity. A process death between writes could leave two plausible but divergent
  snapshots.
- Normal input, migration, Nightscout enrichment, and Watch-request reads did not all
  pass through one reconciliation boundary.
- A historical test encoded destructive cross-source collapse without sensor/session
  identity.

## Corrections

- `TherapyDisplayState` schema 9 carries a backward-compatible monotonic
  `canonicalRevision` (legacy payloads decode as revision zero).
- `CanonicalStateStore` serializes commits, writes one revision to both existing
  stores, and repairs an interrupted write by selecting the higher revision on the
  next process/read boundary.
- Legacy equal-revision divergence deliberately prefers raw phone input, preserving
  the established migration policy.
- Canonical fanout occurs only after both writes complete. Migration, normal input,
  Nightscout changes, and Watch-request reads now use the same boundary.
- Source preference selects the live source without deleting an unidentified nearby
  historical reading from another supported phone source.

## Evidence

- New tests cover monotonic commits, legacy reconciliation, interruption after the
  first write, failure visibility, and restart repair.
- `:wear-storage:test` and all 256 Mobile unit tests passed.
- Focused Android Lint, Detekt, and Ktlint checks for storage and Mobile passed.
- Combined verification completed successfully across 224 tasks.
