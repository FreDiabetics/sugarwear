# Open Graph and Tile Root-Cause Specification

## Goal

Resolve the remaining visible Sugarlicious graph and Wear tile defects through the productive runtime paths, without adding competing renderers or cosmetic counter-offsets.

## Required outcomes

- Mobile IOB and COB use independent Y transforms and separately persisted, user-configurable graph maxima.
- The target-value time series uses the same time-to-X viewport transform as CGM history during pan and zoom.
- Real AAPS insulin-activity samples remain visible with the intended AndroidAPS-relative scale.
- Static CGM scaling maps 40 mg/dL to the lower plot boundary; dynamic and logarithmic-dynamic modes retain their semantics.
- SugarWear in-app and ProtoLayout tile text resolve to the closest actually supported common typography, without a silent unsupported-family fallback.
- The Wear OS tile `Gewebeglukose-Verlauf` renders a real CGM curve from the same canonical history and graph semantics as the SugarWear in-app graph; a visible frame without curve pixels is a failure.
- The Sugarlicious therapy tile removes the IOB/COB/Basal headings, centers only the values inside equal rings, preserves the bottom icon gap, and centers the complete two-over-one composition geometrically.

## Architecture constraints

- One shared horizontal viewport owns all time-dependent graph layers.
- CGM, IOB, COB, and insulin activity retain separate vertical scale contexts.
- Graph maximum settings are distinct from therapy progress-ring maximum settings.
- Existing state, persistence, typography tokens, therapy semantics, icons, and ProtoLayout services must be extended rather than duplicated.
- Each subsystem is completed and verified before the next begins.
- Work continues between phases without approval prompts; user input is requested only for a genuine unresolved product decision.

## Gewebeglukose-Verlauf tile contract

- Trace the full productive path: canonical CGM history -> Wear repository/state -> shared graph model -> time/filter/scale -> tile state -> renderer -> ProtoLayout resource -> visible pixels.
- Prove that history survives source/session and freshness handling, is ordered deterministically, produces finite coordinates in non-zero bounds, and is not lost in a stale tile cache.
- Share history, window, gap detection, glucose scaling, thresholds, semantic colours, target range, and freshness/NO_SOURCE semantics with SugarWear in-app. Only the ProtoLayout-compatible final rendering may differ.
- A new canonical CGM value must invalidate/update the tile event-driven; periodic polling is not an acceptable substitute.
- Cover normal history, one point, multi-hour history, a gap, out-of-order input, FRESH/AGING/STALE/NO_SOURCE, new-value refresh, app/tile history parity, and actual curve pixels inside the graph frame.

## Acceptance evidence

- Focused red-green regression tests for each root cause.
- Mobile bitmap/render tests covering pan, zoom, clipping, activity, and the 40 mg/dL static boundary.
- ProtoLayout structural tests covering font selection and therapy-ring layout geometry.
- Tile rendering evidence proving valid history produces curve pixels rather than only a container outline.
- Relevant unit tests, debug assemblies, and lint tasks pass.
- Real-device visual acceptance is reported separately from automated evidence and is not inferred from a green build.
