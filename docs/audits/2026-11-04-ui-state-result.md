# First integrated MVVM overview slice — 2026-11-04

## Implemented ownership

| Product | Durable input | Presentation owner | UI collection |
| --- | --- | --- | --- |
| Sugarlicious Mobile | TherapyStateStore, dashboard preferences, diagnostics | DashboardViewModel / DashboardUiState | Compose overview and Watch screen collectAsStateWithLifecycle; legacy chrome uses repeatOnLifecycle |
| Sugarlicious Wear | TherapyStateStore, canonical invalidations, display preferences | WearOverviewViewModel / WearOverviewUiState | Compose lifecycle host with AndroidView for the approved round-screen layout |
| SugarWear | G7SensorStateStore, G7ReadingProvider notifications, G7ReadingDatabase, credential-presence flag, transition preferences | G7ViewModel / G7OverviewState | Compose lifecycle host with AndroidView for existing overview geometry |

No process-local G7 database StateFlow is treated as a shared event bus. The
standalone repository reloads durable state after provider/preference invalidation.
Only application contexts are retained by repositories. Credential content is not
part of screen state. Constructors expose repository/clock/scope seams for tests.

## Integrity and lifecycle

- Overview SQL/provider/source-resolution work runs on IO, outside render code.
- UI-only clocks and observers stop when the last visible subscriber leaves.
  Replay expires immediately; returning screens reload durable input.
- Wear's existing canonical resolver is reevaluated with advancing visible time,
  using the original raw phone snapshot. Expired samples disappear and future
  samples can become eligible without a new storage event. Existing PHONE policy
  is preserved; no source policy is reimplemented in a ViewModel.
- Read failures are explicit typed presentation states. Retained samples keep
  their original timestamps/provenance and are invalidated or hidden locally;
  persisted canonical data is never rewritten as a UI error projection.
- Resolution failures do not kill the sharing coroutine. The same failed input
  is not retried on every clock tick; explicit refresh, new input or resubscription
  permits recovery. Cancellation and programming errors propagate.
- A paused/unconfigured SugarWear overview has no perpetual UI age timer. A
  pending transition expires at its exact deadline, then its timer stops.
- Collector/BLE/crypto/alarms remain owned by existing background components,
  independent of whether an Activity or ViewModel is alive.

## Tiles, complications and widgets

These platform entry points continue reading durable state through their existing
services/repositories and semantic update planners. No ViewModel creates a second
fan-out path. Existing planner tests cover no-change/transport-only invalidation,
glucose-only and therapy-only updates. UI clocks never write new measurement
timestamps or request Tile/Complication updates.

This establishes lifecycle behavior in code/tests, not a measured device battery
saving or proof of Bluetooth reliability.

## Tests and review

18 added tests cover the three ViewModel/repository boundaries, including
offscreen cancellation/reload, error recovery, clock/provenance isolation, source
expiry/future eligibility and exact paused-transition expiry. Existing Activity
and planner tests remain enabled. Robolectric Activity tests now attach Compose
windows and explicitly destroy their lifecycle owners after each test.

An independent review found the time-only resolver regression; the added real
resolver tests reproduce and guard it. A fault-injected resolution-error test
also failed before the recovery fix. The final review found no remaining
Critical/Important issues after the resubscription reset was included.

Full local gates passed: architecture verification (Sugarlicious), Detekt, ktlint,
unit/Robolectric tests, debug builds and Android Lint. No lint baseline or test
assertion was removed.

- Sugarlicious final run: `:app-wear:ktlintFormat verifyArchitecture detekt
  ktlintCheck test assembleDebug lint`; BUILD SUCCESSFUL, 878 tasks, 2m03s.
- SugarWear full run: `:g7watch:ktlintFormat detekt ktlintCheck test assembleDebug
  lint`; BUILD SUCCESSFUL, 368 tasks, 9m36s.

These are local Gradle results; UP-TO-DATE/cache outcomes remain enabled.
The old Fragment 1.1 transitive dependency from Play Services was upgraded on
Sugarlicious Wear to match Mobile's Fragment 1.9.1 for Activity Result support.
Existing non-failing Lint modernization suggestions are not a zero-warning claim.

## Deliberate remaining migration scope

This is the requested first integrated overview slice, not a claim that every
screen is now Compose/MVVM. Legacy settings/navigation/platform permission and
pairing-consent controllers remain. Mobile feature-owned archive loaders in
OverviewRangeCard and GlucoseBarStats remain. Existing Wear view renderers are
hosted through Compose interop rather than rewritten, preserving geometry.
Those can be migrated as later separately tested slices.

No device installation, sensor operation, remote CI run or visual hardware
verification was performed for this architecture step.
