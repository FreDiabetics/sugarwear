# Phase 9 — Shared SugarWear graph and visible history tile

## Result

- SugarWear's in-app graph and Gewebeglukose-Verlauf tile use the same `SharedWearCgmGraphRenderer` and the same `g7SharedGraphInput` adapter.
- Window, canonical validation, scale, thresholds, range tinting, gaps, palette, axes and newest-dot time movement therefore have one implementation.
- The graph adapter now runs local readings through `CanonicalCgmHistory`; sequence metadata cannot create a duplicate dot for one sensor measurement.
- Removed the unused parallel `G7GraphPolicy` and legacy `G7GraphLayout` compatibility geometry.
- Removed ignored target arguments from the in-app graph binding.
- Extracted a testable tile bitmap boundary without changing the ProtoLayout image adapter.

## Verification

- Added an end-to-end native bitmap regression proving three stored readings create visible graph pixels at 192×112 and 454×220 render sizes.
- Added a regression proving differing sequence metadata cannot duplicate one measured timestamp.
- Existing tests continue to prove that a fixed newest point moves left as wall-clock time advances, stale history stays visible, colors/thresholds are shared and responsive square layout remains inside round-display bounds.
- `ui-shared` tests, Lint, Detekt and Ktlint passed.
- `g7watch` tests, debug APK assembly, Lint, Detekt and Ktlint passed (209 tasks).

The known raw MP4 parser diagnostics are unchanged and remain assigned to the final warning-free gate.
