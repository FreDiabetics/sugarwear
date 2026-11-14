# Graph, therapy tile and typography addendum — 2026-09-21

## Implemented

- Mobile CGM uses one inset time coordinate system for history, target steps,
  predictions, overlays and the current-time label. The inset is derived from
  the largest point extent, so the newest point remains fully visible.
- The visible current-time divider was removed. The graph maximum reference is
  positioned at the upper rounded-corner tangent.
- The existing IOB maximum now also constrains the regular IOB graph.
- A separately persisted `COB Progressbar Maximum` controls only the COB ring.
- Dynamic COB scaling includes visible COB and the largest still-relevant carb
  entry plus 50 g headroom; expired historic meals are excluded.
- Mobile and Wear use shared IOB, COB and basal progress semantics.
- The Wear therapy tile now renders overlapping circular background/progress
  arcs for all seven valid one-, two- and three-metric selections.
- SugarWear app/collector/graph typography is represented by shared semantic
  roles for glucose value, metadata, title and status.

## Verification

- Focused Mobile graph, preference and therapy-indicator tests: passed.
- Wear therapy tile tests, including all seven selections: passed.
- G7 collector typography/presentation tests: passed.
- Combined gate: 574 tasks, successful.
- Included: `core-model:test`, Mobile/Wear/G7 unit tests, all three debug APKs,
  all three Android Lint tasks, Detekt and Ktlint.
- A clean G7 Lint rerun removed stale binary partial-result files that had been
  misread as XML by the incremental toolchain; the clean rerun completed without
  those parser messages.
- `git diff --check`: clean (line-ending notices only).

## Hardware status

No ADB target was connected at the final device check. No APK was installed and
no Pixel Watch was contacted. Hardware/optical acceptance therefore remains a
separate user-visible gate before any merge.
