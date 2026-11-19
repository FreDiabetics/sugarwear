# Settings and pairing ownership migration

## Scope and invariants

This follows the overview-state slice. Existing uncommitted overview changes are
preserved in both repositories. Synced project sources and SugarWear's pinned
shared submodule are untouched. No collector/protocol/cryptography/alarm evaluation
implementation is migrated together with the UI controllers.

The established One UI/One UI Watch control builders and geometry remain. Existing
Compose forms now consume feature StateFlow; established native controls are
hosted through lifecycle-collected Compose AndroidView interop. This is controller
migration, not a new visual design or a claim that every custom View became a
Compose drawing primitive.

## Ownership map

| Surface | Controller / data boundary |
| --- | --- |
| Mobile settings navigation, preference actions, backup/restore, manual sync | MobileSettingsViewModel / existing-store adapter |
| Mobile data sources and credential drafts | MobileSourceSettingsViewModel / existing source stores |
| Mobile, widget-default and notification appearance panels | target-scoped AppearanceSettingsViewModel / ordered IO repository |
| Color and numeric editor drafts | AppearanceColorEditorViewModel, AppearanceNumberEditorViewModel |
| Individual widget configuration | WidgetConfigurationViewModel / atomic existing render transaction |
| Nightscout treatment setup, test and permission continuation | NightscoutTreatmentSettingsViewModel / existing Nightscout integration |
| Sugarlicious Wear settings, palettes, Tile content/geometry | WearSettingsViewModel / existing settings and update planners |
| SugarWear appearance and Vigil configuration | feature ViewModels with immutable snapshots and G7SettingsWorker |
| SugarWear alarms, setup, permission review | feature access/navigation ViewModels and ordered IO worker |
| SugarWear status, diagnostic snapshots, pause/resume | G7SystemStatusViewModel / canonical runtime commands |
| SugarWear connect/unlink presentation | G7PairingViewModel / AndroidG7PairingCommands |
| Android permission and companion consent windows | Activity-scoped launchers; no Activity retained in ViewModels |

## Preservation and failure handling

- Existing canonical stores, migrations, fan-out planners and source policies are
  reused; no parallel settings database or BLE state machine is introduced.
- Pairing's visible refresh is still one second; successful connection is still
  shown for three seconds, including a newly restored success screen. These are
  presentation deadlines, not changed sensor/BLE deadlines.
- Pending platform requests carry generation tokens and no sensor code. A new
  window can reclaim pending consent; obsolete callbacks cannot start collection.
- Pairing/source secrets remain transient and are not placed in Bundles. Native
  secret fields opt out of Android view-state saving. Reveal-only status details
  are cleared on leaving their screen.
- Companion results keep their own per-launch registry identity. A delayed
  cancellation cannot be attributed to a newer request, including replacement
  windows that reuse a backend request number. Activity destruction unregisters
  the old callbacks; pending ViewModel consent uses a fresh generation.
- Repeated start/unlink/send/save taps are guarded. Existing full unlink and
  history-preservation behavior remains in canonical commands.
- Ordered writes preserve palette identity and queued independent edits. Watch
  publication follows pending palette writes. Widget publication retains the
  existing prepare/commit/publish/rollback transaction and cancellation boundary.
- Storage/operation errors are explicit; expected exceptions log only safe class
  names. Cancellation is not converted into a successful operation. No UI-owned
  polling loop or observer is added to the collector background path.

## Regression evidence

Tests were added before their implementations. Specific red/green checks cover
navigation, palette-specific queued edits, editor drafts, visibility-bound source
observation, credential validation/clearing, consent denial/retry, repeated taps,
paired-session matching, stale callbacks, recreation, exact success dismissal,
atomic widget save/retry, read failures and preloaded Compose-host startup.

Review found and prompted regression tests for palette publication overtaking a
write, restored pairing success failing to dismiss, and asynchronous whole-object
edits overwriting another queued field. Existing layout/behavior assertions remain;
tests now attach Compose windows, await observable conditions and destroy their
lifecycle owners even on failure.

The complete test suites additionally exposed sandbox-specific cached Application
factories/Main Loopers. AndroidViewModel owners now use explicit current-Application
factories; Robolectric UI rules bind coroutine Main to each test's actual Android
Looper. Lifecycle tests await the existing WhileSubscribed cancellation/replay-reset
barrier before publishing a hidden update. No assertion, test, baseline or quality
gate was disabled to accommodate these transitions.

## Verification record

SugarWear full local run passed:
`:g7watch:ktlintFormat detekt ktlintCheck test assembleDebug lint` — BUILD SUCCESSFUL,
368 tasks, 4m21s. This includes the per-launch consent adapter and its two new
late-result/replacement-window regression tests.

Sugarlicious full local run passed:
`:app-mobile:ktlintFormat :app-wear:ktlintFormat verifyArchitecture detekt
ktlintCheck test assembleDebug lint` — BUILD SUCCESSFUL, 886 tasks, 2m18s.
This includes the complete Mobile/Wear suites, source/appearance/widget settings,
source resolver/planner contracts, watchface checks and the retained core tests.

Both repositories passed `git diff --check`; the pinned shared submodule remains
clean. The additional redacted Mobile source secret scan passed. Independent
reviews' Important findings were corrected and re-reviewed, including late
companion results and alarm/palette write ordering.

UP-TO-DATE/cache results remain enabled. Existing non-failing Lint modernization
suggestions are not a zero-warning claim.

No device installation, hardware visual/consent test or remote CI execution was
performed. Existing custom controls/graphs continue through supported Compose
interop. Mobile overview feature-owned archive loaders remain outside this
settings/pairing migration, as documented in the preceding overview slice.
