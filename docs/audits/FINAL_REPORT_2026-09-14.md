# Project hardening final report — 2026-09-14

## Scope and repository state

- Branch: `feature/sugarwear-product-and-vigil-recovery`
- Starting commit: `5e30c411c423a0753977c4bac3f6d8de5c96f8b0`
- Implementation commits are listed in the Git history following that commit.
- No merge, push or automatic change to `main` was performed.

## Implemented corrections

- Repeated Wear Data Layer delivery is rejected before persistence, resolver,
  alert, tile and complication side effects while therapy-only changes remain
  deliverable.
- GATT callbacks are isolated by connection generation and expected
  characteristic; stale write results cannot fail the active write. Formerly
  unlimited callback queues now have explicit bounds.
- Wear Tile requests no longer block their callback thread with `runBlocking`;
  lifecycle-owned coroutines complete cancellable futures instead.
- The retired `G7BackfillSync` no-op and its call paths were removed.
- Canonical CGM history now has a deterministic total order across input
  permutations, including equal measurement timestamps from different sensor
  sessions.
- Low-risk Kotlin and Android deprecation warnings were removed or narrowly
  documented where a compatibility inspection has no replacement. Five WFF
  modules no longer request ineffective minification for debuggable builds.
- CI now runs Android Lint for every module, reviews newly introduced vulnerable
  dependencies, scans full history for committed secrets and emits an SPDX JSON
  SBOM. Third-party Actions are commit-pinned.
- All Android modules compile against API 37; Mobile targets API 37. Runtime and
  test dependencies were updated, and an unused G7 PKIX dependency was removed.
- Detekt was migrated to the Gradle-9/JDK-25-compatible v2 plugin and schema.

## Verification evidence

- Baseline before edits: `test assembleDebug` plus Mobile/Wear/G7 lint succeeded
  across 1,565 tasks.
- Clean final matrix after functional changes: 2,754 tasks, successful in
  15 minutes 23 seconds. It included all unit tests, all debug assemblies,
  complete Android Lint and the CI release-WFF matrix.
- Post-clean targeted warning regression: 528 tasks, successful in 12 minutes
  29 seconds after correcting the deliberately observed red notification test.
- Current end-state matrix: `test assembleDebug lint`, 1,865 tasks, successful
  in 17 minutes 7 seconds after the API/dependency migration.
- All 38 Android Lint reports contain zero issues. `detekt` and `ktlintCheck`
  also pass; Gradle configuration emits no Gradle-10 deprecation warning.
- Official WFF validator accepted every Watch Face Format XML document.
- Code-free verification passed for all 30 release Watchface APKs.
- `git diff --check` reported no whitespace errors on the final working tree.

## External release gates

- Production signing credentials, store rollout/rollback rehearsal and hosted
  crash/ANR telemetry require repository/store ownership and a privacy decision;
  they cannot be safely fabricated in source control.
- Real-device BLE, battery, AOD and sensor-handoff acceptance remains a hardware
  gate. Build and simulator evidence is not reported as physical-device proof.

## Hardware and release status

No ADB device was connected during the final check. Consequently this work does
not claim real-device BLE recovery, battery, AOD, round-display, crown scrolling
or sensor handoff acceptance. A live sensor was not unpaired as a side effect of
software verification.

Repository integration status is recorded from live Git/PR state at handoff;
closed-but-unmerged work is never treated as complete merely because it is old.
