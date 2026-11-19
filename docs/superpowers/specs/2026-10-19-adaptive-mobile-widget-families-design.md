# Adaptive Mobile Widget Families

## Goal

Sugarlicious Mobile shall offer size-aware glucose, graph, and therapy widgets that use one canonical renderer per family. Resizing changes the information architecture without losing the previous rendered state or showing a loading placeholder. Existing compact widgets and existing per-instance settings remain isolated and intact.

## Scope

This change affects only Mobile home-screen widgets, their configuration screens, picker metadata, rendering, persistence, click handling, previews, migrations, and tests. Wear OS, complications, SugarWear, the G7 collector, notifications, and Mobile in-app graphs remain unchanged.

## Widget picker and provider model

Android exposes one picker entry per `AppWidgetProvider`; it does not expose several preset sizes for one provider entry. Sugarlicious therefore registers lightweight provider presets that all delegate to shared family renderers and shared configuration models.

The picker offers these entries:

- `Glukose` — 2x1 preset
- `Glukose` — 2x2 preset
- `Glukose` — 4x2 preset
- `Glukose + Graph kompakt` — the existing compact combined widget
- `Glukosegraph` — 2x1 preset
- `Glukosegraph` — 2x2 preset
- `Glukosegraph` — 4x2 preset
- the existing therapy widget family

The three glucose presets are one logical family. The three graph presets are one logical family. Provider classes contain no rendering logic.

Android launchers may still permit intermediate physical sizes. The renderer maps the measured host bounds deterministically to the nearest supported layout class instead of stretching a layout. It never crops content to pretend that an unsupported size is supported.

## Glucose family

### 2x1 compact layout

- The outer shape is always a pill, independent of the prior per-instance shape setting.
- Glucose value and canonical trend arrow occupy the left portion.
- The right portion contains age above delta, for example `5m` and `-5`.
- No glucose unit is shown.
- Age and delta remain individually configurable using the existing per-instance size, position, and color controls.

### 2x2 detail layout

- Glucose value and trend arrow are centered in the upper section.
- A centered delta row appears underneath with the existing canonical delta icon on the left, for example `+10 mg/dL`.
- The following centered row displays `5m her`.
- The last centered row displays the current-day in-range percentage as `Im Ziel: 89%`.
- TIR uses canonical history samples from the local calendar day, validated against the configured glucose target range. If there are no valid samples, the row displays `Im Ziel: --` rather than inventing a value.
- The 2x2 layout uses the Android/Samsung system widget corner radius, not the compact pill override.

### 4x2 glucose plus graph layout

- A subtle vertical separator divides the widget at its visual center.
- The left side uses the 2x2 glucose/detail information style: glucose, trend, icon plus delta/unit, age, and TIR.
- The right side renders the existing canonical widget graph without stretching.
- The graph reuses the same thresholds, sample validation, prediction separation, axis handling, dot styles, and per-instance appearance settings as the graph family.
- The 4x2 layout uses the system widget corner radius.

### Resize behavior

- The measured host bounds determine the layout on every render and after `APPWIDGET_UPDATE_OPTIONS`.
- Resizing never starts a second widget instance and never resets settings.
- App widgets cannot provide a reliable frame-by-frame resize animation across launchers. Sugarlicious instead performs an atomic content replacement: the old render remains visible until the complete new bitmap is ready. No loading screen or blank state is inserted.

## Existing compact combined widget

The existing `GlucoseGraphWidgetReceiver` remains a separate family and is labeled `Glukose + Graph kompakt`. Its rendering, settings isolation, and stored instances are preserved unless a shared bug fix is required by the new family infrastructure.

## Graph family

### Supported layouts

The picker exposes 2x1, 2x2, and 4x2 graph presets. Intermediate launcher dimensions map to the nearest of these three aspect/layout classes. The graph always uses the maximum safe plot area for the selected class.

### Scale and age label

- Every graph layout displays a combined scale/age label at the top-left of the graph area, aligned consistently with the current compact 2x2 graph treatment.
- The label uses the existing short age format such as `5m`.
- When the confirmed high-region background is active and yellow, the label switches to a dedicated per-instance high-background text color. The default is near-black; settings expose the same system color picker used elsewhere.
- Outside the yellow high region, the normal configured graph-label color is used.

### Click behavior

- The scale/age label has its own click target and cycles through the existing supported graph scale modes in canonical order. After the last mode it returns to the first.
- The selected scale mode is persisted per widget instance and the affected instance refreshes immediately.
- Every point outside that click target opens the per-instance configured launch application.
- Click targets remain separate and do not overlap.

## Therapy family

- A 2x1 host displays at most two enabled indicators. They are equal in size, centered, symmetric, and use equal outer and inner spacing.
- A 2x2 host displays at most three enabled indicators. One is centered in the top row; two are centered in the bottom row. All three use equal sizing and balanced spacing.
- The existing per-indicator enable switches determine which indicators are used in the existing canonical order.
- If only one indicator is enabled it is centered. If two are enabled they are centered symmetrically in either size. Disabled indicators do not leave empty slots.
- Existing colors, icons, progress geometry, values, and settings remain unchanged.

## State, persistence, and migration

- Every physical widget instance retains independent settings.
- New provider presets resolve to the correct logical family without duplicating preference namespaces.
- Existing widget IDs and provider classes remain valid after update.
- New settings receive explicit defaults through a versioned migration. Missing keys are handled defensively and cannot crash the settings activity.
- Resizing does not alter unrelated settings.
- The graph scale-cycle action updates only its target widget ID.

## Rendering and freshness

- All displayed values come from the existing canonical `TherapyDisplayState` and validated history. No renderer computes an alternative glucose source.
- Stale and signal-loss presentation continues to preserve the last valid value and trend according to the existing system contract.
- New rendering is completed off the visible state and then published atomically. The old bitmap remains visible while work is in progress.
- Widget updates remain event-driven; resizing or scale cycling triggers only the affected instance.

## Settings and previews

- Settings remain per widget instance.
- The glucose family settings preview follows the currently measured/configured preset size and renders the appropriate 2x1, 2x2, or 4x2 form.
- Graph previews include the scale/age label and high-background contrast color.
- Existing Mobile control styles, color picker, sliders, reset behavior, and naming are reused.
- The 2x1 glucose pill rule is presented as fixed rather than as an ineffective shape option.
- The new glucose, graph, and therapy families use automatic geometry: 2x1 is always a pill; every larger size uses the Samsung-provided rounded widget radius, matching the compact 2x2 reference. Their settings do not expose a pill/standard selector.
- Pre-existing provider entries remain available for compatibility and are clearly prefixed `Developer Widgets` in the picker; the new families use their product labels.
- Every widget settings screen ends with one floating dark pill containing `Abbrechen | Speichern`, separated by a vertical divider.
- Settings edits remain local while the screen is open. `Abbrechen` discards the complete edit session; `Speichern` persists the complete configuration for exactly that widget instance, refreshes it immediately, and closes the screen.
- Back navigation behaves like cancellation and never silently persists a partial edit.
- Saving composes the existing production Glance widget directly and publishes its RemoteViews through AppWidgetManager before closing settings. No periodic update is required.
- Family settings are selected from the bound provider and current host dimensions. The 2x1 therapy configuration permits at most two metrics; glucose graph controls appear only in the wide glucose layout.
- Samsung size tiles use the installed Samsung Health metadata contract (`widgetSize`, `widgetStyle`, and the small/medium/large preview layouts). Legacy Developer providers retain one original preview and unrestricted resizing.
- One UI 9 additionally reads `samsung.appwidget.colorful.info`. Its numeric fields are hexadecimal strings: glucose/graph use `0x1a` (2x1, 2x2, 4x2), therapy uses `0x0a` (2x1, 2x2). Decimal-looking strings are not equivalent: `26` was interpreted as `0x26`, and `10` as `0x10`, causing the incorrect sizes reported on the physical phone.
- Physical Samsung SM-S948B verification on 2026-10-19 confirmed the three-size glucose/graph cards and the two-size therapy card. A shared label does not combine separate legacy providers into one native card. The initial decision retained direct Developer selection.
- Superseding decision, 2026-10-20: the user approved one shared additional-provider card with Glucose / Graph / Therapy selection when adding. AdditionalWidgetReceiver is the visible provider; all three legacy providers remain enabled but hidden from the picker. Selection is persisted per instance in AppWidgetManager options; existing legacy instances keep their original kind. Settings, updates, resize and graph actions use the resolved instance kind. Existing saved configurations are not migrated or erased. Additional providers use unrestricted Android resize metadata, not Samsung fixed-size family metadata. When Samsung supplies native host dimensions, no second compact inset is subtracted, including on legacy instances.
- Latest requested order: Glukose, Glukosegraph, Therapie, Glukose kompakt (its own card), Developer Widgets. This supersedes the earlier request to include the compact provider inside Glukose.
- Native picker artwork is generated at each supported aspect ratio by `WidgetPickerPreviewTest` using the production renderers, stored in `drawable-nodpi`, and displayed with `fitCenter`. Neither text nor trend assets are independently enlarged by the launcher, and graph dots are not stretched with a vector illustration.
- Every newly created widget starts with a fully opaque background in both themes. Existing explicitly saved instance transparency remains unchanged.
- Samsung native family hosts (`semWidgetSize`, `hsResizeRatio=1`) already report the usable surface. Applying the legacy 8dp horizontal / 9dp vertical compact inset again reduced 154.29x68.86dp to 138.29x50.86dp. Native family rendering and settings previews now bypass that second inset; legacy/Developer host sizing remains unchanged. Picker artwork uses the observed 154x69, 154x175 and 345x175dp sizes.
- Physical before/after screenshots measured the same therapy instance at 484x178px before and 540x241px after this correction. The latter matches the Samsung host surface. Icon rectangles additionally respect the rounded contour; their open-ring anchor previously extended 22% of the icon below the fitted ring, so fitting the ring alone did not prevent clipping.
- Final Mobile APK installed successfully after green assembleDebug, lint, detekt and ktlintCheck. Physical 2x1 therapy inspection confirmed the corrected outer surface and complete lower icons. Targeted configuration/catalog/presentation/axis/preview suites passed (80 tests), followed by the new icon-contour regression and preview regeneration (2 tests). Final native-picker artwork inspection and physical 2x2 therapy resizing remain open; do not confuse generated-render checks with those hardware checks.
- The scale/age click advances the displayed history period through 1h, 2h, 3h, 6h, 12h, and 24h, wrapping to 1h.

## Testing

Automated tests shall cover:

- provider-to-family resolution for every new preset;
- picker metadata, labels, target dimensions, and existing-provider compatibility;
- deterministic measured-size classification including boundary values;
- forced 2x1 glucose pill and system-radius 2x2/4x2 layouts;
- exact 2x1 age/delta text contract without unit;
- exact 2x2 delta icon/unit, `5m her`, and TIR rows;
- TIR calculation for current-day valid samples and the unavailable state;
- 4x2 divider and left/right region geometry;
- graph scale/age label geometry and yellow-background contrast selection;
- per-instance scale cycling, wraparound, isolated persistence, and immediate refresh;
- non-label widget click retaining the configured launch target;
- therapy layouts for one, two, and three enabled indicators;
- no loading placeholder during resize or update;
- configuration migration and missing-color resilience;
- explicit save versus cancel behavior for the shared settings action pill;
- render smoke tests at Samsung-reported 2x1, 2x2, and 4x2 dimensions.

## Validation

Before installation, run Mobile unit tests, affected integration/render tests, `assembleDebug`, lint, detekt, and ktlint. Install on the connected Samsung phone and verify picker entries, all three glucose sizes, all three graph sizes, scale-label cycling, launch-target clicking, settings isolation, and therapy layouts. Hardware observations remain separate from code/build validation.

## Acceptance criteria

The work is complete when the three glucose presets and three graph presets are directly selectable, every family renders the required information at each size without clipping or loading placeholders, scale cycling is isolated and persistent, therapy layouts obey their capacity rules, existing instances and compact widgets remain intact, settings cannot crash during migration, all relevant gates pass, and the built APK has been installed for hardware validation.
