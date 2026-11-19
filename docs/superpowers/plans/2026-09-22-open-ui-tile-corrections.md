# Open UI and tile corrections implementation plan

**Spec:** `docs/superpowers/specs/2026-09-22-open-ui-tile-corrections-design.md`

1. Write failing Mobile tests for visible-prediction scale-side selection;
   correct the active `GlucoseDashboardChart` layout decision and verify.
2. Write failing typography parity tests; centralize family/weight/baseline
   semantics and apply them to every G7 Collector tile text builder.
3. Write failing graph-tile pixel/data/refresh tests; repair the productive
   database-to-bitmap path without adding a second source or renderer.
4. Write failing therapy-ring geometry and icon-state tests; extract the shared
   semantic spec, convert angles correctly for ProtoLayout, register/reuse the
   Mobile icons and preserve all seven layouts.
5. Run the complete affected test/build/static-analysis matrix, document the
   evidence, commit the candidate and leave merge/push/install gated on user
   acceptance.
