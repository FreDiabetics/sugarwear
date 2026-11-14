# Canonical CGM trend pipeline implementation plan

Spec: `docs/superpowers/specs/2026-09-28-canonical-cgm-trend-pipeline-design.md`

## Task 1: Establish canonical trend semantics

Add failing core tests for direction parsing, AndroidAPS and Dexcom rate boundaries, requested delta/interval matrix, monotonicity, and UNKNOWN behavior. Implement the shared policy in `core-model`; remove duplicate classification from callers.

Verification: `:core-model:test`

## Task 2: Repair source and fallback paths

Add failing adapter/G7 tests proving source-trend precedence, real measured-time calculation, out-of-order handling, session/sensor rejection, and no UNKNOWN-to-FLAT coercion. Route AndroidAPS and G7 through the shared policy.

Verification: `:data-source-aaps:test :dexcom-g7:test :app-mobile:testDebugUnitTest`

## Task 3: Verify canonical transport and presentation

Add resolver/storage/presentation tests proving glucose, delta, rate, trend, source and timestamp remain atomic across persistence, source switching, and process boundaries. Consolidate any residual semantic or icon mapping.

Verification: `:core-model:test :wear-storage:test :complications:testDebugUnitTest :app-wear:testDebugUnitTest :g7watch:testDebugUnitTest :app-mobile:testDebugUnitTest`

## Task 4: Alarms and debug observability

Add tests proving rapid alarms use validated canonical rate rather than delta, enum ordinal, or assets. Add debug-only structured pipeline diagnostics at source normalization boundaries.

Verification: `:dexcom-g7:test :g7watch:testDebugUnitTest :app-mobile:testDebugUnitTest`

## Task 5: Whole-system validation

Run relevant render/preview tests, all unit and integration tests, debug and release assemblies, lint, detekt, ktlint, `git diff --check`, and a whole-branch review. Fix every introduced warning or error without weakening a gate.

Verification: `test assembleDebug assembleRelease lint detekt ktlintCheck`

## Review focus

Check source-trend precedence, threshold inclusivity, unit/sign handling, measured-versus-received time, stale/backfill/session behavior, source tuple atomicity, UNKNOWN rendering, duplicate mapper removal, ordinal/index serialization, and alarm coupling.
