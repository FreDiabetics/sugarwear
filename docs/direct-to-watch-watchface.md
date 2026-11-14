# Vigil Watchface

`Vigil` is the renamed, in-place successor to the former G6-style package. The application id and face-selection index intentionally remain stable so existing installations and selections migrate without being reset.

The face is deliberately bound to `WATCH_DIRECT` and never uses AndroidAPS or Mobile CGM as a fallback. It consumes the validated SugarWear collector model, timestamps, trend, delta, quality and freshness. Missing, stale and sensor-error data are shown explicitly; no synthetic glucose or delta is created.

The fixed layout is:

- glucose and the shared Sugarlicious trend arrow at the top;
- delta followed by the unit in secondary text directly below;
- the shared CGM graph in the middle;
- the face-local graph duration and real event age below the graph;
- time at the bottom, with no content below it.

The graph reuses `SharedWearCgmGraphRenderer`, including the Wear-only 40–400 mg/dL scale, confirmed HIGH/TARGET/LOW excursion fills, range classification, axes, rounded clipping, colors and dots. Target labels sit inside the plot without ticks, leaving equal outer spacing on both sides. Tapping the graph or its information row cycles the existing `1h`, `3h`, `6h`, `12h`, and `24h` durations and stores the value only in the SugarWear preference surface.

Tapping the complete glucose block opens the exported launcher activity of `app.aapswear.g7watch` explicitly. It does not open the Sugarlicious Wear main screen.

Trend appearance uses the shared `TrendArrowStyle` and `TrendArrowStylePreferences` schema (fill, outline enabled, outline color, outline thickness, size and alpha) with a separate SugarWear preference file. Light and dark profiles resolve independently, and the profile selected in this screen is persisted and consumed by Vigil. Trend size is proportional: 70% renders at 0.7x, 100% at 1x and 200% at 2x on a stable non-clipping canvas. Values below 40 display `NIEDRIG`, values above 400 display `HOCH`, without changing the raw stored reading. In ambient mode Vigil uses dedicated grayscale header and graph providers: high/low areas and auxiliary scale/age are transparent, while glucose/trend, target band and lines, CGM dots and time remain visible. Complication payloads remain valid while their renderer changes the semantic state without inventing data.

SugarWear settings contain a dedicated `SugarWear` category with the subtitle `Watchface`. Its graph controls cover the time scale, CGM point size and outline, graph background, LOW/TARGET/HIGH areas, LOW/IN-RANGE/HIGH points, boundary lines, axes, now line and border. Graph border, time-axis scale and target ticks are independent switches; all three are off in the Vigil default except the target labels themselves, which sit immediately above/below their range lines. Trend size, fill, outline, outline color, thickness and alpha are configurable there as separate Light/Dark profiles. The values are transferred through a signature-protected explicit app channel and affect only Vigil.

Every control updates one field of the latest persisted value instead of writing an old screen snapshot back over neighboring fields. The settings screen keeps one `ScrollView`; normal slider, toggle, color, unit and duration changes update their own row without rebuilding the page. Reset and Light/Dark profile changes may rebuild the row content while restoring the same scroll position.

The settings path is `G7DirectToWatchSettingsActivity` → `G7DirectToWatchSettingsStore` (`direct_to_watch`) → explicit signature-protected `DirectToWatchSettingsReceiver` → face-local `DirectToWatchPreferences` → shared renderers. Historical-dot and current-dot outlines are persisted independently. The obsolete target-tick preference is removed during migration. Graph colors and style, target thresholds, unit/bold glucose, duration, and all `TrendArrowStyle` fields are consumed by the runtime complication renderers; the WFF preview remains a static representative asset.

Range background coloring can be disabled independently. When enabled, `CgmGraphPolicy` counts only unique, valid, chronologically new CGM events. Two consecutive HIGH/VERY_HIGH or LOW/VERY_LOW events activate the respective area; the first valid IN_RANGE event clears it. Duplicate, invalid, stale/connection refresh and out-of-order/backfill events do not advance or reset the semantic sequence, and a sensor/session/source change starts a new sequence.

> This watchface is intentionally bound to WATCH_DIRECT and does not use AndroidAPS/Mobile as a CGM fallback.
