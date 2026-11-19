# AndroidAPS display contract and CGM layout design

## Goal

Sugarlicious must display every non-G7 data stream with the same semantic value, visible rounding, and unit that AndroidAPS supplies. Mobile, widgets, Wear OS, Tiles, watchfaces, and complications must not disagree about the same AndroidAPS state.

The direct Dexcom G7 collector and all CGM data produced by that collector are explicitly outside this change and must remain untouched.

## Scope

The AndroidAPS-authoritative contract covers:

- IOB, bolus IOB, and basal IOB
- COB and future carbs
- insulin activity
- basal and temporary basal values, percentages, durations, and timestamps
- glucose targets and temporary targets
- loop state and loop timestamps
- profile name and DIA
- pump state, reservoir, and pump battery
- phone battery
- predictions
- bolus, SMB, carbohydrate, temporary-target, and basal therapy events
- AndroidAPS-originated CGM metadata where Sugarlicious already consumes it

For these fields, Sugarlicious may validate transport integrity but may not silently replace, clamp, derive, reinterpret, or hide a valid AndroidAPS value. Missing, non-finite, malformed, or contract-invalid data remains unavailable and must not be replaced with invented clinical data.

## Canonical AndroidAPS value contract

`AndroidAPS payload -> AapsPayloadAdapter -> TherapyDisplayState -> persistence -> phone/watch transport -> presentation model -> renderer`

Each boundary must preserve:

- raw numeric value
- AndroidAPS unit
- AndroidAPS-visible precision/rounding
- source timestamp and relevant validity interval
- source identity and capability
- explicit absence versus a real zero

Formatting is centralized by data type. Renderers consume the shared formatted presentation rather than applying their own rounding, unit conversion, null substitution, or sign filtering. A real negative value and a real zero remain distinguishable from unavailable data.

If the inbound AndroidAPS contract provides only a numeric value rather than an already formatted display string, the adapter records sufficient unit and precision metadata to reproduce AndroidAPS' visible result deterministically. Sugarlicious must not infer a different display precision per surface.

## Resolver and persistence behavior

Resolver decisions may select the canonical CGM source, but they may not replace AndroidAPS therapy, loop, pump, profile, battery, target, prediction, or event state with fields from the selected CGM source.

Persistence and history merging preserve valid zero and negative values. A missing incoming field may retain the last explicitly valid state only where the existing freshness contract permits it; it must never manufacture zero or convert a negative value to unavailable. Stale state remains labeled stale without changing its value.

## System-wide age format

Every compact user-visible age uses exactly `<whole minutes>m`, for example `0m`, `2m`, or `17m`.

Disallowed variants include:

- `vor 2 Minuten`
- `vor 2 min`
- `2 Minuten her`
- `2m her`
- `2 min`

The shared age formatter is the only producer for Mobile, notifications, widgets, Wear OS activities, Tiles, watchfaces, and complications. Longer diagnostic prose may name timestamps, but it must not introduce a competing age rendering for a data value.

## Mobile notification layout

Both collapsed and expanded custom notifications retain the existing left system/app icon and graph. The complete glucose block consists of:

- CGM value
- trend arrow
- delta
- age

The block moves left and is optically centered in the free region between the thick left icon and the graph. Its container expands to consume that free region. Value, trend, delta, and age must not be clipped or ellipsized at supported font scales and notification widths.

Layout calculations use actual available width rather than compensating with independent translations that can push child views outside a fixed container. Regression tests cover narrow notification widths, collapsed and expanded layouts, the longest supported glucose/delta/age strings, and double arrows.

## Mobile CGM Tile

The delta icon is removed from the Mobile CGM Tile. Delta remains textual. The released width is assigned to the text row so that the canonical compact age remains fully visible on narrow layouts. The glucose value and trend arrow retain their existing hierarchy and vertical alignment.

## Double-arrow geometry

Double-up and double-down use the full original shared vector geometry in:

- every trend complication
- collapsed Mobile notification
- expanded Mobile notification

They retain their native wide aspect ratio and original glyph fill. They must not be normalized into a square, padded to mimic a single arrow, uniformly reduced to fit a single-arrow box, stretched, or squeezed. Single, diagonal, and flat arrows remain unchanged.

The renderer allocates a width derived from the shared asset aspect ratio while preserving the intended arrow height. Bitmap tests assert occupied bounds, aspect ratio, and absence of transparent-padding shrinkage for both double directions.

## Error handling

- Valid AndroidAPS zero and negative values render as values.
- Only absent or invalid values render as unavailable.
- Invalid data is rejected at the adapter boundary and cannot overwrite the last valid canonical state.
- Unsupported units remain explicit rather than being silently converted.
- Staleness changes status presentation, not the stored AndroidAPS value.
- No G7 collector behavior, G7 storage, G7 alarm logic, or G7-derived CGM presentation is changed.

## Verification

Automated coverage must include:

1. Every scoped AndroidAPS field from payload parsing through canonical state.
2. Serialization and persistence of negative, zero, positive, missing, and invalid values.
3. Phone-to-watch transport equality.
4. Identical visible value, precision, and unit across Mobile, widget, Wear activity, Tile, watchface presentation, and complications.
5. No accidental therapy-field replacement during CGM resolver changes.
6. System-wide `<minutes>m` age formatting and absence of forbidden variants.
7. Mobile CGM Tile without a delta icon and without age clipping.
8. Collapsed and expanded notification layout without clipping at narrow width and supported font scales.
9. Full native double-arrow geometry in complications and both notifications.
10. Unchanged G7 collector and direct-G7 CGM regression suites.

Relevant unit, integration, rendering, persistence, resolver, transport, lint, detekt, ktlint, debug, and release gates must pass. Hardware results are reported separately as `CODE-VALIDIERT`, `BUILD-VALIDIERT`, and `HARDWARE-TEST OFFEN` or `HARDWARE-VALIDIERT`.

## Acceptance criteria

- Sugarlicious never shows a different valid non-G7 value, rounding, or unit from AndroidAPS.
- A valid negative or zero value is never shown as a dash.
- All compact ages use only the `2m` form.
- Notification text is not clipped in either layout.
- Mobile CGM Tile contains no delta icon and keeps age visible.
- Double arrows render at full original size in complications and notifications.
- The direct G7 collector and its CGM data path are unchanged.
