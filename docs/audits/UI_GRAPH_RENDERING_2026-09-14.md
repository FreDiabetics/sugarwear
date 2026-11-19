# Phase 10 - UI, graph, rendering, and smoothness audit

Date: 2026-09-14

## Shared rules verified

- `CgmGraphPolicy` owns canonical valid-reading filtering and the two-consecutive-reading high/low excursion rule.
- `GraphTimeWindow` owns the moving live viewport; a historical point moves left as wall time advances.
- `GraphScaleStrategy` keeps CGM, IOB, COB, and insulin-activity axes independent and separates horizontal viewport behavior from Y scaling.
- `SharedWearCgmGraphRenderer` is consumed by SugarWear app graph, SugarWear graph tile, Sugarlicious Wear graph, Wear tiles, and graph complications.
- Mobile charts, widgets, and notification graphs use the same core time-window/range policy while retaining geometry appropriate to their larger surfaces.
- Tiles and complications use measured time for LIVE and BACKFILL rather than arrival time.

## Rendering/lifecycle findings

- The blocking Sugarlicious Wear tile render path was corrected in Phase 8.
- Interactive Mobile and Wear refresh loops are scoped to visible Activity lifecycle.
- Pairing rebuilds are guarded by semantic presentation state; the one-second check exists only during CONNECTING.
- Graph bitmap generation remains off the UI thread in the tile paths.
- Compact tile geometry intentionally differs from app/watchface geometry; data filtering, freshness, range excursion, colors, and live-edge semantics do not.

## Existing regression coverage

The project contains focused suites for graph time movement, scale modes, range backgrounds, duplicate filtering, LIVE/BACKFILL measured time, mobile/widget parity, tile geometry, complication ambient rendering, and shared Wear renderer metrics.

## Visual validation boundary

Unit/bitmap tests do not prove optical acceptance on every round panel. Phase 17 retains explicit screenshot and hardware checks for Galaxy Watch Ultra and the smaller Pixel Watch in interactive, AOD, tile, complication, no-sensor, signal-loss, and sensor-error states.
