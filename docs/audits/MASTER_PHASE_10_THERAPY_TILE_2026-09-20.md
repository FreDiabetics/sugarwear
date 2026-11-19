# Master phase 10 — configurable therapy tile

## Result

- The Wear OS therapy tile now independently supports IOB, COB and basal.
- Every non-empty combination is valid and persists across process restarts.
- One metric is centered, two metrics share the upper row, and three metrics use IOB/COB above basal.
- The last selected metric cannot be disabled, so the tile never enters an empty state.
- Existing single-metric IOB, COB or basal choices migrate without silently changing the user's selection.
- Retired therapy and pump branches in the glucose-tile content model were removed.

## Verification

- All seven valid metric combinations build a real tile in tests.
- Persistence, ordering, layout and legacy migration have regression coverage.
- `:app-wear:testDebugUnitTest`, `assembleDebug`, `lintDebug`, `detekt` and `ktlintCheck` passed.
- 271 tasks completed successfully; `git diff --check` found no whitespace errors.
