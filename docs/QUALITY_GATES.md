# Quality gates and developer workflow

## Local gate

The supported CI toolchain is JDK 21 with Android SDK 37. Before a change is
declared complete, run from the repository root:

```powershell
.\gradlew.bat verifyArchitecture detekt ktlintCheck test assembleDebug lint :watchfaces:test-wff:assembleRelease --continue --no-daemon --no-problems-report
.\gradlew.bat :tools:maintenance:test
.\tools\tests\watchface-product-matrix.tests.ps1
.\tools\wff-validator\validate.ps1
.\tools\verify-codefree-watchfaces.ps1
```

Use focused tests while developing, but do not substitute them for the complete
gate. The `--no-problems-report` flag avoids a known Windows report-file ACL
collision and does not suppress compiler, test or Android Lint findings.

Maintenance raster tests require Cairo 1.18.4 and its native dependencies;
see `tools/maintenance/README.md`. Project-owned tools and tests use Kotlin.

## Pull-request gate

GitHub Actions performs the following independent checks:

1. reject newly introduced dependencies with moderate-or-higher known
   vulnerabilities;
2. run all unit tests and debug assemblies;
3. build the release variants required for code-free WFF validation;
4. run Android Lint across every Android module;
5. validate all WFF XML and reject Watchface APKs containing executable DEX;
6. publish visual-QA artifacts and an SPDX JSON software bill of materials.

New third-party security actions are pinned to immutable commit hashes. Updating
them requires verifying the upstream release tag and reviewing the action diff.

## Change discipline

- Verify the runtime owner before editing a renderer or state holder.
- Put shared data, freshness, graph and classification rules in `core-model`.
- Keep source/protocol adapters source-specific; never duplicate canonical
  resolution in a UI module.
- Use stable measurement identity (`sensor/session + measuredAt`) rather than a
  sequence number alone.
- Make retries durable, coalesced and event-driven. Do not add blind periodic
  BLE or history polling.
- Add a regression test that fails for the observed defect before changing the
  implementation when the defect is reproducible in software.
- Treat process death, duplicate delivery, reordered input and repeated success
  callbacks as normal lifecycle cases.
- Never claim hardware, battery or visual acceptance from compilation alone.

## Hardware evidence

Every hardware result records the commit, APK hash, device model, Wear/Android
version, package, start/end time and whether actual sensor state was changed.
Collector testing additionally preserves LIVE/BACKFILL origin, `measuredAt`,
`receivedAt`, sensor/session identity and the complete intended observation
window. Pairing or unlinking a live medical sensor requires an explicit test
window and must not be inferred from a general build request.

## Release and rollback

- Build releases from a clean, identified commit with a private production key.
- Archive checksums, SBOM, test reports, WFF validation and device evidence.
- Roll out in stages and monitor crash, ANR, collector recovery and battery
  budgets before widening distribution.
- Persisted schema changes must be monotonic and tested for upgrade, downgrade
  handling where supported, process restart and rollback safety.
