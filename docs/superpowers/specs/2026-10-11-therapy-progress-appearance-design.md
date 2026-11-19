# Therapy Progress Appearance Settings Design

## Goal

Provide complete, independent appearance controls for the IOB, COB, and basal progress indicators in:

1. the Sugarlicious Mobile therapy-progress home-screen widget; and
2. the Sugarlicious Wear therapy Tile.

Users can scale the whole indicator group and then tune every indicator individually. They can show any non-empty combination of IOB, COB, and basal. Every one-, two-, or three-indicator combination remains centered, balanced, unclipped, and legible.

## Scope

This change affects the existing widget configuration flow, Mobile widget persistence and bitmap renderer, Wear settings flow, Wear Tile persistence and ProtoLayout renderer, previews, and regression tests. It reuses the existing canonical therapy presentation, colors, icons, and `TherapyRingGeometry`; it does not create a second therapy-data path.

Mobile-widget and Wear-Tile appearance profiles are independent. A change on one surface must not modify the other surface.

## Configuration model

Each surface stores one `TherapyProgressAppearance` profile containing:

- enabled metrics: IOB, COB, and basal, with at least one required;
- group scale;
- inter-indicator gap;
- global ring thickness;
- global primary-text size;
- global secondary-text size;
- global icon size; and
- one `TherapyMetricAppearance` override for each metric, containing individual scale factors for indicator size, ring thickness, primary text, secondary text, and icon size.

Effective dimensions are calculated from the surface defaults, the global setting, and the metric-specific factor. Values are clamped through one shared policy before rendering. Persisted user values are not overwritten when a renderer must clamp them for the current available bounds.

All dimensions use bounded integer percentages in settings and are converted to density-independent dimensions only by the rendering geometry layer. Existing installations migrate to defaults that reproduce the current layout.

## Settings behavior

### Mobile widget

The existing per-widget configuration screen receives a dedicated therapy-progress section when configuring the therapy widget. It contains:

- IOB, COB, and basal visibility switches;
- group-size and spacing controls;
- global ring, primary text, secondary text, and icon controls;
- collapsible IOB, COB, and basal sections with the five individual controls; and
- a live preview rendered by the production widget renderer.

These values belong to the individual widget instance, consistent with existing widget configuration. Reset restores only that widget instance's therapy appearance defaults and does not reset colors, data, or another widget.

### Wear Tile

The existing Sugarlicious Wear settings area for the therapy Tile receives the equivalent controls and a production-path preview. Its values are stored in a dedicated therapy-Tile appearance store, separate from Mobile and separate from color settings. Reset restores only Wear therapy geometry defaults.

The existing Wear metric-selection UI is reused and extended rather than duplicated.

Both settings flows prevent disabling the final visible metric and explain why the last switch remains active.

## Layout geometry

Metric order is always IOB, COB, basal after filtering by visibility.

### Mobile 2x1 widget

- One metric: centered horizontally and vertically.
- Two metrics: one centered horizontal row with equal outer margins and equal spacing.
- Three metrics: one centered horizontal row with equal center spacing and equal outer margins.

The group-scale setting controls the maximum occupied group extent. The gap setting controls only inter-indicator spacing. The geometry resolver computes the largest safe effective indicator sizes within the widget bounds. No indicator, text, icon, ring stroke, or outline may be clipped.

### Round Wear Tile

- One metric: centered.
- Two metrics: one centered horizontal row.
- Three metrics: a centered triangular layout with IOB and COB above and basal below.

The geometry resolver accounts for the circular safe area. Different per-metric sizes do not move the logical slot centers away from their symmetric layout. Oversized settings are proportionally constrained only for rendering.

## Rendering

The Mobile bitmap renderer and Wear ProtoLayout renderer consume resolved geometry rather than embedding independent hard-coded dimensions. Shared policy code owns defaults, ranges, effective multiplication, visibility validation, and slot placement inputs; platform renderers remain responsible for Canvas versus ProtoLayout primitives.

Each metric keeps its existing canonical value and progress semantics. Primary and secondary text remain separate. Icons use the exact corresponding progress-ring color on both surfaces. Background transparency, pill shape, widget outline, theme profiles, and existing color customization continue to work.

## Validation and error handling

- Corrupt or out-of-range persisted values fall back to or clamp against documented defaults.
- Unknown future fields do not invalidate the whole profile.
- Empty metric selections recover to all three defaults when reading corrupt storage; the UI itself never writes an empty selection.
- Geometry is deterministic for the same bounds and profile.
- Tiny host bounds degrade by proportional scaling, never by clipping or dropping a selected metric.

## Testing

Automated coverage must include:

- default-profile backward compatibility;
- independent Mobile and Wear persistence;
- global and per-metric effective sizing;
- all seven non-empty metric combinations;
- one-, two-, and three-slot symmetry and centering;
- equal gaps and safe outer margins;
- circular Wear safe-area constraints;
- minimum and maximum setting values;
- corrupt-storage recovery;
- last-visible-metric protection;
- primary and secondary text sizing independently;
- icon tint matching ring color;
- widget transparency, pill shape, and outline regression coverage;
- Mobile widget and Wear Tile previews using production geometry; and
- rendering at representative small and large host dimensions.

Relevant Mobile and Wear unit tests, build variants, lint, detekt, and ktlint must pass. Device installation proves deployment only. Final visual behavior remains `HARDWARE-TEST OFFEN` until inspected on the actual phone launcher and round Wear OS device.

## Definition of done

- Widget and Wear Tile have separate persisted profiles.
- Global controls and all five per-metric controls work on both surfaces.
- IOB, COB, and basal can be independently enabled while preventing an empty selection.
- Every non-empty selection uses its defined centered slot layout.
- No selected indicator is clipped at supported host sizes.
- Icons match their ring colors.
- Existing therapy values, progress semantics, transparency, pill shape, colors, and outlines are preserved.
- Production previews reflect saved settings.
- Automated gates are green and device validation status is reported precisely.
