# Project Hardening Completion Design

## Objective

Close the remaining software-quality work identified by the 2026-09-14 audit
without changing medical-data semantics, destabilizing the G7 collector, or
hiding findings behind broad suppressions and baselines.

## Scope boundaries

The work covers every module in this repository. It does not add therapy
control, cloud telemetry, a new CGM source, or a replacement collector. A live
sensor is not unpaired merely to exercise a test. Production signing can be
prepared but cannot be completed without a private key supplied outside Git.

## Execution model

Work is sequential. Each block has its own red-green test or configuration
proof, focused verification, commit, and short report. Later blocks start only
from a green earlier block. No two workers edit shared modules concurrently.

## Finding classification

Every Lint, compiler and build finding receives one disposition:

1. **Defect** — fix with a failing behavioral regression test.
2. **Safe migration** — update to the supported API and verify equivalent
   output or lifecycle behavior.
3. **Intentional compatibility** — retain only at the narrowest call site with
   a reason and a test covering the compatibility contract.
4. **Generated or upstream** — record owner and upgrade path; do not patch
   generated output or globally silence the toolchain.
5. **False positive** — document concrete reachability or platform evidence;
   avoid a baseline unless the finding cannot be scoped locally.

## Ordered work blocks

### 1. Complete finding inventory

Parse every module SARIF report by rule, severity and source. Separate
application findings from WFF packaging, generated resources and upstream JDK
messages. Reproduce the G7 XML parser output with diagnostic logging and locate
its input.

### 2. Low-risk correctness and cleanup

Fix redundant nullability, obsolete API calls with direct replacements,
resource problems, manifest issues and proven dead code. Preserve reflection,
Android component, Tile, Complication and WFF reachability.

### 3. Android API migrations

Migrate BLE callbacks and GATT connection creation behind one tested
compatibility boundary. Migrate ProtoLayout image creation without changing
resource IDs, dimensions or colour filters. Migrate system-bar styling to
edge-to-edge/insets while preserving round-display geometry. Keep notification
channel behavior stable.

### 4. Static and supply-chain gates

Introduce Detekt and Ktlint with repository-owned configuration. Existing
findings must be fixed or narrowly justified; no opaque baseline. Retain full
Android Lint, dependency review, immutable action pins and SPDX SBOM. Add a
reviewed secret-scanning gate only when it is available and licence-compatible.

### 5. Reliability tests

Add deterministic tests for duplicate/reordered delivery, process rehydration,
database migrations, repeated callbacks, fault injection, retry coalescing and
bounded queues. Collector tests preserve sensor/session identity, `measuredAt`,
`receivedAt`, LIVE/BACKFILL origin and canonical gap state.

### 6. Energy and observability

Verify alarms, workers, foreground services, Data Layer fanout, Tiles,
Complications and watchface invalidation against explicit wakeup and retry
budgets. Keep diagnostics local, bounded and redacted. Do not introduce network
telemetry without a separate privacy decision.

### 7. Final software and hardware gates

Run a clean full build, tests, all-module Lint, static analysis, WFF validation
and code-free APK verification. Install only the intended packages on explicitly
identified devices. Record short smoke-test evidence separately from the
24–72-hour BLE/backfill/battery observation window.

## Acceptance criteria

- Zero build, test, Android Lint, Detekt and Ktlint errors.
- Every remaining warning has a documented, narrow external/generated or
  compatibility reason; the target is zero repository-owned warnings.
- No new unbounded queue, blocking UI call, blind retry loop or duplicate
  canonical state owner.
- CGM identity, freshness and LIVE/BACKFILL semantics remain unchanged unless a
  failing test proves a defect.
- All WFF XML validates and every distributed Watchface APK remains code-free.
- Hardware claims name exact device, package, commit and observation window.
- No automatic merge or push.
