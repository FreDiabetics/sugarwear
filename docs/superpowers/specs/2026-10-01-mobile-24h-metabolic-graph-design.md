# Mobile 24-hour history and metabolic graph design

## Goal

Sugarlicious Mobile must retain and render every valid, real AndroidAPS sample available inside the rolling 24-hour window. The CGM and metabolic graphs must show the same complete time range when the user zooms out to 24 hours. The IOB/COB presentation must read as one combined graph surface, use the same plot-versus-axis color contract as the CGM graph, maximize useful data area, and show the latest real insulin delivery beneath the IOB value in the hero indicator.

The direct G7 Watch Collector and its CGM data remain outside this change.

## Current architecture and observed failure modes

AndroidAPS External Companion Apps broadcasts are normalized by `AapsPayloadAdapter`, accumulated by `DisplayHistoryAccumulator`, committed through `CanonicalStateStore`, and rendered by `GlucoseDashboardChart`, `MetabolicDashboardChart`, and the Compose overview.

The existing cache is nominally bounded to 24 hours and 2,000 points. The implementation nevertheless treats a `TherapyHistorySample` as a timestamp-wide aggregate and reduces samples that share an exact timestamp. Partial broadcasts, different timestamps for related streams, process recovery, and repeated canonical commits must therefore be tested as separate streams rather than assuming that one combined list guarantees equal retention. The renderer also filters independently by the active viewport, so both persisted history and viewport coverage must be verified.

The current metabolic renderer draws two separately rounded lane clips separated by a fixed 14 dp gap. It paints the entire outer container with `GRAPH_BACKGROUND`, unlike the CGM graph, which paints the outer content/axis surface with `SURFACE` and only the plot with `GRAPH_BACKGROUND`. The fixed gap and per-lane rounding waste vertical area and visually split one logical graph.

The hero IOB indicator currently has no secondary presentation. Real bolus events already exist as canonical `TherapyEvent` values for `SMB`, `MANUAL_CORRECTION`, and `MEAL_BOLUS`.

## Data retention contract

1. `DisplayHistoryAccumulator` remains the sole Mobile owner of rolling display history.
2. Each valid field in every `TherapyHistorySample` is retained independently for 24 hours. A partial incoming sample must not erase another field at the same or a different timestamp.
3. CGM samples continue through `CanonicalCgmHistory`; therapy samples use deterministic per-timestamp field merging and stable chronological ordering.
4. The point cap must be derived from the 24-hour contract and be high enough for the fastest supported real publication cadence. It must never be the reason a valid stream covers less than 24 hours.
5. The exact cutoff is inclusive. Future samples beyond the existing tolerance remain rejected.
6. Canonical reconciliation and process restart must preserve the same oldest and newest timestamps. No renderer may mutate or truncate stored history.
7. Missing historical values are not invented or interpolated. The public AAPS broadcast cannot retroactively provide therapy history that Sugarlicious never received.
8. Therapy events use the same rolling 24-hour cutoff and canonical deduplication by event identity.

## Shared metabolic graph geometry

`MetabolicDashboardChart` remains one custom view and receives one outer rounded container.

- The outer container has one rounded contour matching the CGM graph.
- The plot and axis regions are calculated once for the combined container.
- IOB and COB remain independent Y axes and independent scale modes.
- The IOB and COB plot lanes have square internal meeting edges. Only the outermost top and bottom corners inherit the container rounding.
- The fixed 14 dp empty gap is replaced by the minimum separator required for zero-line labels and treatment markers. Marker headroom is computed from marker bounds instead of reserving the same oversized block unconditionally.
- Both lanes share the same X viewport, time axis, prediction divider, and outer clipping path.
- Treatment markers stay clipped to their semantic lane and never overlap the axis region.

This changes presentation geometry only; it does not merge IOB and COB values or scales.

## Color and settings contract

The metabolic graph adopts the CGM graph color hierarchy:

- combined outer/axis region: `SugarliciousColorRole.SURFACE`;
- IOB and COB plot regions: `SugarliciousColorRole.GRAPH_BACKGROUND`;
- outer contour: `SugarliciousColorRole.BORDER`;
- axis labels and ticks retain `GRAPH_LABEL` and `GRAPH_AXIS_TICK`.

No new duplicate color preference is introduced. The existing configurable Surface and Graph Background color pickers therefore affect CGM and metabolic graph regions identically in the active light/dark palette.

## Latest bolus presentation

The IOB hero indicator receives an optional secondary line derived from the latest canonical insulin event among:

- `MEAL_BOLUS`;
- `MANUAL_CORRECTION`;
- `SMB`.

The event must have a finite positive amount and a timestamp not later than the existing future tolerance. Selection is deterministic by timestamp, with canonical event identity as a stable tie-breaker.

The visible format is:

`Bolus 0,3U · 8m`

Formatting uses the same locale-aware insulin rounding and compact age convention already used by Sugarlicious. The line uses the existing secondary text styling used by the basal percentage. If no valid event exists, the secondary line is omitted. The current IOB value remains unchanged and AndroidAPS-authoritative.

## Error and freshness behavior

- Invalid, non-finite, zero, negative, or future bolus events are not shown as a latest bolus.
- A stale glucose state does not erase valid therapy history or the last bolus presentation; their own timestamps remain authoritative.
- An empty stream renders its existing no-data state without fabricating zero.
- Existing source provenance, resolver behavior, predictions, treatment deduplication, and G7 collector paths remain unchanged.

## Verification

Automated regression coverage must include:

1. CGM points spanning the complete inclusive 24-hour boundary.
2. Independently sparse and dense IOB, COB, basal, and insulin-activity streams retaining their full valid window.
3. Partial updates at equal and different timestamps without sibling-field loss.
4. Canonical store reconciliation/process restart preserving the complete history.
5. A 24-hour viewport rendering oldest and newest CGM and metabolic points.
6. One shared metabolic outer contour, square internal lane junction, reduced separator, and increased plot height.
7. `SURFACE` axis region and `GRAPH_BACKGROUND` plot region matching the CGM color contract.
8. Latest-bolus selection across meal, correction, and SMB events, including invalid/future/no-event cases.
9. Exact secondary text formatting and omission behavior.
10. Existing graph scaling, prediction, marker, persistence, lint, detekt, and formatting suites.

The final gate is the repository's full unit/integration suite, debug and relevant release builds, lint, detekt, ktlint, and a real Mobile installation. Code/build validation is reported separately from visual hardware validation.

## Definition of done

- All valid received CGM and therapy samples remain available for the rolling 24-hour window.
- Zooming to 24 hours shows every persisted stream over its complete available interval.
- IOB and COB form one visually continuous container without rounded internal meeting corners.
- Plot area is maximized without clipping labels or treatment markers.
- Metabolic plot and axis backgrounds follow the same configurable roles as the CGM graph.
- The IOB hero indicator shows the latest real bolus as `Bolus <amount>U · <age>m`.
- No G7 collector behavior changes.
- Regression tests and all repository quality gates pass.
