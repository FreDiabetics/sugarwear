# ApeX Watchface Final Geometry Design

## Goal

Restore ApeX as a selectable code-free Watch Face Format package and correct its visible geometry, bottom glucose ranged-value renderer, graph delivery, and three selectable hand packages.

## Source of truth

- The original Watch Face Studio project uses a 450 x 450 canvas and remains the source for slot topology, text direction, graph aspect ratio, and provider placement.
- The shipped WFF uses a 512 x 512 canvas.
- The user's real-device observations override the original WFS values where the literal 450-to-512 conversion places strokes too close to the physical display edge.
- Existing Sugarlicious complication provider identities and the canonical data layer remain unchanged.

## Geometry

### Outer ranged-value segments

All outer segments use one shared center at `(256,256)` and one shared geometry:

- progress diameter: `388`
- progress stroke: `22`
- progress sweep: `42` degrees
- round caps
- track and value arc have exactly identical start, sweep, direction, diameter, center, and stroke
- upper-left start: `288` degrees clockwise
- upper-right start: `18` degrees clockwise
- lower-right start: `108` degrees clockwise
- lower-left retains curved text and does not invent a ranged arc absent from the WFS scene

This moves the rings inward, strengthens them, prevents bezel clipping, and widens the spaces between segments.

### Bottom glucose ranged-value complication

The bottom slot remains centered at the WFS-derived location. Its renderer uses:

- center `(75,75)` in the local slot
- arc diameter `124`
- stroke `16`
- start angle `232`
- sweep `256` degrees clockwise
- a background track and value arc with exactly matching geometry
- the provider's monochromatic trend icon rendered inside the bottom open segment
- glucose text remains centered and cannot overlap the icon

The icon is part of the `RANGED_VALUE` renderer. A separate `MONOCHROMATIC_IMAGE` renderer is not a substitute because Wear OS selects one complication type renderer at a time.

## Graph

The graph stays data-driven through `GlucoseGraphComplication` and `SMALL_IMAGE`. WFF provides the slot, clipping and composition; the provider provides the current graph bitmap. A dynamic history graph is not hard-coded because WFF expressions do not expose a time-series collection suitable for plotting.

The graph preserves the WFS `400:140` visual ratio, uses the established `(59,63,394,138)` slot, and renders the bitmap without non-uniform stretching. Empty history remains an explicit no-history state.

## Hand packages

Restore exactly three persisted `handStyle` options:

1. Standard (`hour_hand`, `minute_hand`, `second_hand`)
2. Transparent (`hour_hand_transparent`, `minute_hand_transparent`, `second_hand_transparent`)
3. Black/gray (`hour_hand_tblack`, `minute_hand_tblack`, `second_hand_tblack`)

All three include hour, minute and second hands. Second hands are hidden in ambient mode. Existing raster assets are restored from the last revision where the three packages were complete.

## Delivery

- Restore only the ApeX module and its required picker/push wiring, not the retired legacy watchface matrix.
- Application ID remains `app.aapswear.watchfacepush.analog` so existing installations update in place.
- Preserve existing complication provider class names and package bindings.
- The selectable ApeX APK and any picker/default copy must originate from the same freshly built artifact.

## Verification

- Geometry tests cover shared outer radius, stroke, sweep and safe canvas inset.
- Bottom renderer tests cover matched track/value arcs and icon presence inside `RANGED_VALUE`.
- Graph tests cover provider binding, aspect-preserving image bounds and no hard-coded medical data.
- Hand-style tests cover all three options and their resources.
- Run relevant unit tests, module Release build, mobile/wear compile checks, lint/static checks, WFF validation, code-free APK validation and signing verification.
- Real-watch appearance remains `HARDWARE-TEST OFFEN` until installed and visually inspected on connected hardware.
