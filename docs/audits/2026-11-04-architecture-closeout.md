# Architecture closeout — 2026-11-04

## What is now easier to maintain and verify

- **Kotlin protocol implementation with parity evidence.** The G7 Java core and
  Lombok dependencies were replaced incrementally, protected by reference vectors,
  typed authentication outcomes and defensive copying. The characterized hex-parser
  behavior, including the 0x21 case, remains intentional. Crypto providers,
  algorithms and protocol sequencing were preserved rather than redesigned.
- **Reproducible Kotlin tooling.** Own Python maintenance tools were replaced by
  the shared Kotlin tool module. The tooling audit records command/semantic parity
  and pixel comparisons for all 53 hand assets. Platform XML/SVG/WFF and small
  launch wrappers remain appropriate supported formats, not a hidden Python runtime.
- **Explicit presentation ownership.** Mobile, Sugarlicious Wear and SugarWear
  use feature ViewModels, immutable StateFlow and lifecycle collection for migrated
  overview/settings paths. Storage, transport and sensor commands are outside the
  renderers. Approved custom controls remain through Compose interoperability.
- **One canonical data path.** Existing stores, source policies and update planners
  are reused. Presentation clocks do not manufacture measurement freshness, alter
  provenance or initiate a second background fan-out. Errors remain explicit.
- **Ordered, recoverable user operations.** Settings writes, palette publication,
  alarm field changes and widget publication retain order and atomic contracts.
  Rapid taps cannot launch duplicate operations; stale consent callbacks carry
  their original identity. Pairing codes and secret drafts are not saved to Bundles.
- **Lifecycle and resource boundaries.** Visible-only observation/age clocks stop
  offscreen. Collector, alarms, Tiles and Complications remain independently owned
  background/platform components. Permission and companion windows stay scoped
  to the Activity, not retained by a ViewModel.

These are concrete reviewable properties, not a guarantee that no developer will
ever find another defect. The evidence is in tests, source boundaries and the
linked audits—not in an assertion of universal or medical certification.

## Final visual corrections

The notification's monochrome symbol uses the counterpart shade of the **device**
dynamic accent: light in Light Mode, dark in Dark Mode. Its tint is independent
of the Now Bar background, which keeps its existing OS-accent selection.

The glucose bar's underlying full-length track is neutral opaque gray, derived
from the tile surface and lightened toward white. The measured-range foreground,
marker position and animations remain intact. A white surface is the natural
upper bound of the lightening operation.

## Evidence and limits

See [UI-state migration](2026-11-04-ui-state-result.md),
[settings/pairing migration](2026-11-04-settings-pairing-mvvm.md),
[language audit](2026-11-03-kotlin-language-migration-audit.md), and
[Kotlin maintenance tools](2026-11-04-kotlin-maintenance-tools.md).

The closeout reruns full local Gradle tests/builds, architecture verification,
ktlint, Detekt and Android Lint before committing. Cache/UP-TO-DATE results remain
enabled; existing non-failing Lint suggestions are not described as zero warnings.
No gate or assertion was disabled for the migration.

Final local closeout: Sugarlicious's full command passed in 3m15s (886 tasks,
36 executed); SugarWear's full command passed in 4s (368 tasks, 5 executed,
remaining results UP-TO-DATE). The preceding complete SugarWear migration/consent
run passed in 4m21s. Two additional appearance regression tests cover both device
theme polarities and neutral-track contrast against dark/light tile surfaces.

Real-device appearance/permission/collector operation and remote CI are separate
evidence. Native custom renderer replacement and overview archive-loader migration
were not silently included in the settings-controller conversion. Absolute
bug-freedom, every-device compatibility and clinical certification are not claimed.
