# Project-wide behavior-preserving refactoring design

Date: 2026-10-05

## Purpose

Refactor the complete Sugarlicious repository so that ownership, lifecycle, concurrency, persistence, data flow, rendering and testing become easier to reason about without changing correct observable behavior. Reproducible defects discovered during the audit are fixed at their root cause only after a failing regression test exists.

The refactoring must reduce future defect probability rather than merely move code between files. It must preserve the product architecture: AndroidAPS is the authoritative non-G7 source, the direct Dexcom G7 collector exists only on Wear OS, all consumers use canonical state, and no UI independently resolves sources or invents medical values.

## Non-negotiable guarantees

- Preserve package IDs, supported Android/Wear versions, public component contracts and persisted user settings unless a separately tested migration is required.
- Preserve valid AndroidAPS values, units and visible rounding end to end.
- Preserve G7 sensor/session identity, `measuredAt`, `receivedAt`, LIVE/BACKFILL origin, deduplication and freshness semantics.
- Preserve the central resolver rule and canonical alarm path.
- Preserve existing tiles, complications, watchfaces, widgets, notification surfaces and user-configurable appearance behavior.
- Do not introduce polling, aggressive keepalive, unbounded queues, unbounded retries or duplicate engines.
- Do not weaken Lint, Detekt, ktlint, WFF, code-free APK, security or build gates.
- Do not treat compilation as hardware, battery, visual or medical-data validation.

## Current system boundary

The repository contains 17 Gradle modules and approximately 66,500 Kotlin/Java lines. The intended dependency direction is:

```text
source adapters / dexcom protocol
              |
              v
         core-model
              |
       canonical stores
              |
      resolver + transport
              |
Mobile / Wear / SugarWear / Tiles / Complications / Watchfaces
```

Canonical semantic policy belongs in `core-model`. `data-source-*` and `dexcom-g7` adapt external protocols. `wear-storage` owns durable observable state. `wear-protocol` owns cross-device messages. `ui-shared` owns platform-independent rendering assets and policy adapters. Application modules own Android lifecycle and platform rendering, not competing business rules.

## Refactoring strategy

The work proceeds as a sequence of independently reviewable vertical slices. Every slice starts and ends green. A slice may be reverted without invalidating later persisted data.

### Stage 0: Executable baseline and change map

Create an authoritative inventory of runtime entry points, module dependencies, persistence schemas, exported Android components, background scheduling, source-resolution consumers and rendering consumers. Record the current full-gate result and test inventory. Add characterization tests where a critical behavior is not executable in isolation.

No production structure changes occur until the relevant behavior is protected by tests.

### Stage 1: Canonical data and source boundaries

Trace AndroidAPS and Watch-direct data from ingestion through validation, persistence, resolution, transport and presentation. Remove semantic duplication only by routing consumers to existing canonical policies. Keep source-specific parsing in adapters and keep G7 transport details out of shared UI/state modules.

Validate missing, invalid, stale, duplicate, out-of-order, sensor-change, process-restart and mixed-source cases. No fallback source is selected unless the configured resolver says so.

### Stage 2: Persistence and migration boundaries

Give each persisted record and preference family one owner. Separate serialization from state transition logic. Consolidate duplicated read/modify/write behavior behind tested repositories without creating a new parallel store.

Every schema or key change requires upgrade tests, interrupted-write tests, restart tests and explicit rollback behavior. Historical CGM data is never deleted by refactoring or sensor lifecycle operations.

### Stage 3: Lifecycle, concurrency and background execution

Audit Activities, Services, providers, receivers, alarms, WorkManager jobs, BLE callbacks, coroutine scopes, WakeLocks and process recovery. Each asynchronous operation receives a documented owner, cancellation rule, deadline and exactly-once completion contract.

Replace blocking or process-global work only when ownership and lifecycle can be proved better. Preserve event-driven recovery, backoff and jitter. Avoid speculative coroutine rewrites that merely exchange one race for another.

### Stage 4: Collector decomposition

Split `G7CollectorService` and `AndroidG7Ble` by existing responsibilities rather than inventing a second collector:

- orchestration and state transitions;
- scheduling and expected-window ownership;
- BLE connection generation and callback translation;
- authentication/protocol execution;
- reading/backfill persistence;
- diagnostics and recovery policy.

The Android service remains the runtime shell. Extracted units are pure or dependency-injected where practical and remain driven by the existing state machine. Every extraction is behavior-preserving and protected by state-transition, timeout, duplicate-callback and restart tests.

### Stage 5: Mobile and Wear presentation decomposition

Split oversized graph, dashboard, settings, tile and complication files into cohesive render models, geometry/policy objects and platform views. Shared semantics move only to existing shared modules; platform drawing stays in its owning application.

Preserve pixel geometry, accessibility, round-display clipping, AOD behavior, user settings and independent app-specific preferences. Golden/bitmap tests and narrow-width layout tests protect visual contracts.

### Stage 6: Error model and diagnostics

Replace ambiguous broad failure handling with typed outcomes at subsystem boundaries. Cancellation remains cancellation. Unexpected failures retain privacy-safe provenance and stable diagnostic codes. User-facing states stay separate from technical diagnostic detail.

No exception message, sensor code, BLE address or medical payload enters an unredacted support artifact. Recovery decisions must be testable from typed state rather than inferred from display strings.

### Stage 7: Build, dependency and security hygiene

Review dependency ownership, exported components, intent resolution, permissions, backup policy, notification channels, cryptographic use and supply-chain configuration. Vendored Dexcom protocol/crypto code is changed only for a demonstrated correctness, compatibility, security or licensing issue; ordinary style refactoring is kept outside that sensitive protocol surface.

Dependency upgrades are isolated from structural refactors and require release-note review plus the complete gate.

### Stage 8: Dead code and duplication removal

Delete code only after repository-wide reachability checks and replacement tests. WFF resources use WFF-aware reachability, not Android Lint alone. Duplicated behavior is consolidated into the existing canonical owner, never into a new generic utility without a clear domain contract.

## Large-file policy

Line count identifies review candidates, not automatic split points. A file is split only when it contains multiple independently describable responsibilities or forces unrelated dependencies into the same test fixture.

Initial candidates include:

- `DashboardCharts.kt`;
- `G7CollectorService.kt`;
- `SugarliciousColorSettingsPanel.kt`;
- `DashboardViews.kt`;
- `SugarliciousWidgets.kt`;
- `AndroidG7Ble.kt`;
- `TherapyComplications.kt`;
- `G7WatchActivity.kt`.

Extracted names must describe domain responsibility. Arbitrary `Utils`, `Helpers`, `Manager` or numbered-part files are not acceptable endpoints.

## Test and verification model

Each implementation slice follows red-green-refactor:

1. Add or identify a test that protects the behavior being moved.
2. For a defect, demonstrate the expected failure before modifying production code.
3. Make the smallest production change.
4. Run focused tests and static checks for affected modules.
5. Run dependent-module tests and assemblies.
6. Review the diff for accidental behavior, resource and persistence changes.
7. Run the repository-wide gate at each stage boundary.

The stage-boundary gate is:

```text
test
assembleDebug
assembleRelease
lint
detekt
ktlintCheck
WFF validation
code-free watchface verification
git diff --check
```

Test inventory must cover canonical data, resolver hysteresis, data identity, migrations, restart/process death, receiver completion, collector state transitions, alarm deduplication, graph policies, settings persistence, transport compatibility and representative rendering sizes.

## Hardware and visual validation

Code and build validation are distinct from hardware validation. Relevant stages produce installable Mobile, Sugarlicious Wear and SugarWear artifacts, but installation scope follows the changed modules.

Hardware evidence records commit, APK hash, package, device/OS, observation interval and actual sensor operations. Collector reliability requires a real observation window across normal readings, display off, background operation, reconnect and restart. Visual changes require captured comparisons on representative round Wear displays. No live sensor is released, unbonded or re-paired solely for refactoring validation without an explicit test requirement.

## Commit and integration discipline

- Work stays on `main` as explicitly requested, but each stage uses small, reversible commits.
- Do not mix dependency upgrades, formatting sweeps, architectural extraction and behavioral fixes in one commit.
- A failing stage stops further refactoring until the regression is understood and fixed.
- Do not push or create a PR unless requested for the resulting refactoring stage.
- Preserve a clean tree between stages and document changed contracts and remaining risks.

## Completion criteria

The project-wide refactoring is complete only when:

- every runtime data path has one documented semantic owner;
- source adapters, canonical state, persistence, resolver, transport and renderers have enforceable boundaries;
- critical asynchronous work has explicit ownership, cancellation, deadline and completion behavior;
- identified oversized files have been evaluated and split where responsibility boundaries justify it;
- duplicated business rules and unreachable code are removed with evidence;
- persistence and transport compatibility are tested across restart and upgrade;
- the full quality gate passes without weakened rules or new warnings;
- hardware-dependent claims are either validated on the required devices or marked `HARDWARE-TEST OFFEN`;
- the final report separates `CODE-VALIDIERT`, `BUILD-VALIDIERT` and hardware evidence and lists all residual risks.

