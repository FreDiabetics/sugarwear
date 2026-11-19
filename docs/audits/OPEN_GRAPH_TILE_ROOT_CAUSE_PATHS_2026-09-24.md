# Open graph/tile productive-path audit — 2026-09-24

## Mobile graph

- Productive settings are created in `DashboardViews.kt`, loaded by `DashboardUiPreferences.read`, and passed by `SugarliciousOverviewScreen` into `GlucoseDashboardChart` and `MetabolicDashboardChart`.
- The visible `IOB-Skala` currently writes `overview.iobProgressMaximumUnits`. That value drives both the overview progress ring and `MetabolicDashboardChart.iobMaximumUnits`; COB exposes only `overview.cobProgressMaximumGrams` for the ring and does not reach the metabolic graph. The prior axis split therefore separated calculations but did not create two graph-specific controls.
- `resolveMetabolicScales` already selects distinct `GraphAxis.IOB`, `GraphAxis.COB`, and `GraphAxis.INSULIN_ACTIVITY`. The remaining scaling defect is preference ownership/input, not CGM-coordinate reuse.
- CGM and target history both call `mapGraphTimeX`/`valuePath` with the same `start`, `end`, and `timeBounds`. The fallback target is anchored to data time. Any remaining sticky appearance must be reproduced in the bitmap/viewport path before another offset change.
- AAPS activity is read by `AapsPayloadAdapter` from `insulinActivity`, `iobActivity`, or `activity` and stored as `TherapyHistorySample.insulinActivityUnitsPerMinute`. Both CGM and metabolic charts render only when at least two finite non-negative samples survive the viewport filter. The metabolic chart still restores/persists a static activity scale, whereas the CGM overlay was changed to current-maximum scaling in `35564626`; this divergent scale path is the principal visibility suspect.
- `CgmGraphYScale` defaults to 40 mg/dL, but Mobile permits a persisted static minimum down to 20 mg/dL and passes it directly to the renderer. The productive override, not the core default, causes a static plot below 40.

## SugarWear typography

- In-app labels resolve Android `Typeface.create("sans-serif", BOLD)`.
- Tiles request ProtoLayout family `roboto` at weight 700. Commit `e45d9b80` changed only the family token from unsupported/ambiguous `sans-serif` to `roboto`; it did not establish equivalent visible metrics between Android's bold Typeface and ProtoLayout's weight rendering.
- Typography parity must therefore be validated through supported rendered weight/metrics, not string equality. Existing tests assert only token values.

## Wear tiles

- SugarWear `G7GraphTileService` reads `G7ReadingDatabase.getRange`, normalizes history, rasterizes it with `SharedWearCgmGraphRenderer`, embeds PNG bytes as an inline ProtoLayout image, and fingerprints history in the resource version. The in-app graph reads the same database but through its own bind call. The first suspected loss boundary is inline image encoding/resource delivery, not the visible ProtoLayout container.
- Sugarlicious Wear's separate graph tile also rasterizes the shared renderer, but its therapy tile still creates a label, spacer, and value inside each ring. Its three-item placement is logically two-over-one, while centering is currently assembled from independent rows rather than a measured group contract.

## Disproven assumptions from earlier fixes

- `73b0b2c5`: separate axis identities alone do not provide user-configurable graph maxima.
- `a32a25d4`: label anchoring is unrelated to target-series viewport movement.
- `9a03a766`: shared semantic tokens and approximate ring layout do not prove visible typography or optical group centering.
- `bc441773`: correcting presentation semantics does not remove the still-rendered ring headings.
- `e45d9b80`: requesting `roboto` and extending series to the live edge do not prove device-visible font parity or a visible tile bitmap curve.
- `35564626`: restoring activity in the CGM overlay does not repair the separate metabolic-chart activity scale path.
