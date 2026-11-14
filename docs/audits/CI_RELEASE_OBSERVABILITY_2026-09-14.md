# CI, release and observability audit — 2026-09-14

## Implemented in this work package

- Android Lint now runs for the complete multi-module project instead of only
  Mobile and Wear. The same `lint` task passed locally across all modules.
- Pull requests receive dependency-review enforcement at moderate severity.
- The dependency-review and SBOM actions are pinned to immutable release
  commits.
- CI produces an SPDX JSON software bill of materials for each successful run.
- Existing unit, assembly, WFF XML, code-free APK and visual-QA gates remain in
  place.

## Existing observability

- Collector attempts, outcomes and durable gap state use stable diagnostic
  codes and bounded retention.
- Support export redacts sensor credentials and excludes complete health
  payloads.
- The G7 foreground notification is a process-liveness surface, not a verbose
  collector event stream; it updates with newly committed readings.

## Deliberately not fabricated

- No crash/ANR SaaS was introduced because that would add network, privacy,
  consent and operations requirements.
- No unlicensed or unavailable secret-scanning binary was made a required gate.
  Repository secret scanning should be enabled through the hosting platform or
  a reviewed scanner whose licensing and availability match the project.
- Hardware reliability and battery budgets remain evidence gates, not CI claims.

## Remaining release requirements

Production signing, staged rollout, rollback rehearsal, API-compatibility policy,
long-duration battery measurement and crash/ANR collection with an approved
privacy model are release-management decisions and remain explicit pre-release
work.
