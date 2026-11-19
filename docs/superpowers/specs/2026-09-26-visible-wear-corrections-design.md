# Visible Wear Corrections Design

## Goal

Correct the three still-visible failures: SugarWear app/tile typography parity, a genuinely populated Mobile insulin-activity curve, and a full-bleed SugarWear graph Tile with its own settings under `Farben & Darstellung`.

## Proven causes

- The app resolves Android `sans-serif` through Samsung's View stack while ProtoLayout resolves its restricted system family independently. Matching source strings and weights did not prove matching glyphs.
- The productive AndroidAPS status broadcast normally contains IOB but no `insulinActivity` field. Existing renderer tests inject a value that productive payloads do not contain, so the real series is empty.
- The in-app graph uses `G7GlucoseChart`; the Tile uses `SharedWearCgmGraphRenderer` inside a header, card, padding and second rounded surface. The two visible surfaces therefore cannot match.
- Graph preferences are split between appearance, direct-to-watch and the in-app period control. There is no independent Tile graph profile.

## Design

Use one explicitly supported Roboto family on both Android Views and ProtoLayout with the same semantic weight tokens. Validate the required comparison strings through resolved configuration and device rendering; no text rasterization is allowed.

Parse explicit AndroidAPS activity when supplied. For normal broadcasts, extract AAPS' own BGI and ISF values from the algorithm result and convert them using the AAPS relationship `activity = -BGI / (ISF * 5)`. Only finite, positive, complete inputs are accepted; missing or ambiguous input remains absent. Accumulated source samples feed the existing canonical `TherapyHistorySample` series and independent activity scale.

Replace the parallel in-app graph implementation with the shared graph input/style/geometry path. The existing Tile contour remains the outer boundary. Inside that contour the graph fills the complete area without a separate graph background, inner card, inset color rim or duplicate clipping. The renderer receives the final contour bounds and recalculates its plot geometry for those exact bounds; a pre-rendered graph is never stretched or distorted to fit. ProtoLayout cannot draw arbitrary Canvas paths, so the live shared renderer is delivered as a freshly generated inline RGB565 resource; it is not a screenshot or static asset, and its cache identity changes with history and settings.

Add an independent `WearOS-Tile Graph` section under SugarWear `Farben & Darstellung`. Persist Tile-specific hours, colors, point radius and outlines, outline width, target bands/lines/labels, time axis, contour corner radius, content-safe padding and scale-lane opacity. Settings invalidate the graph Tile immediately. In-app values remain independent and are not silently overwritten.

## Verification

Tests must begin with realistic AAPS payloads lacking an explicit activity field; prove non-empty activity history, valid coordinates and visible pixels in all four scale modes and shifted viewports. Graph tests cover full-bleed geometry, settings persistence/invalidation, fresh/stale/no-source states, gaps, history fingerprinting and multiple screen sizes. Hardware evidence remains separate from code/build evidence.
