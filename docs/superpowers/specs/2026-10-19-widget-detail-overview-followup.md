# Mobile widget/details and overview follow-up

User-directed continuation on the existing dirty main checkout; no collector, Wear, source, or therapy-value changes.

- Glucose 2x2/4x2: aligned delta/age/TIR icon/value rows, no verbal labels/colon. Use original supplied 66x66 SVG geometry **with circles** (latest user correction); independently configurable circle/symbol colors and per-instance font weights.
- Glucose 4x2: graph occupies 70% of widget height, inset horizontally, aligned with the left text block. Header shows history scale only, not age.
- Other widget text containers retain independent font-weight controls, including all six therapy primary/secondary slots; widget data and preferences remain instance-isolated.
- Settings: device font, dynamic system accent colors, normal/smaller dropdown secondary text without chevrons, inset slider end ticks, smaller action labels. Action capsule overlays scroll content; bottom spacing belongs to content, not an opaque footer.
- Overview: 42sp centered wordmark, unchanged app logo, compact connection pill adjacent to settings. Former watch hero/model/status lives at the top of Watch menu. Hero and cards share the same selection/preset/editor transition.
- Watchface catalog migration is explicitly executed before MainActivity UI/listener setup. Reading the selection during composition is pure, preventing preference-triggered disposal while composing.

## Very-high boundary root cause

On-device dark settings had identical RANGE_HIGH and RANGE_VERY_HIGH colors (-210629 / RGB252,201,59), with GRAPH_VERY_HIGH_LINE alpha0. The visible seam was not the configured line. Device screenshot `mobile-line-check-unlocked.png` showed row1865 RGB200,160,51 between rows1864/1866 RGB252,201,59. Independently antialiased adjacent fractional rectangles blend twice against the background and leave a dark hairline.

The native-render regression reproduced the seam at y55 before the fix. Range-band rectangles now render without edge antialiasing; antialiasing is restored before dots/curves and outer rounded clipping remains unchanged. Independent line alpha/color settings are preserved.

## Validation

Initial full Mobile suite exposed the composition/migration defect; after correction all428 tests passed. Added native range-seam regression subsequently; final combined tests/build/static-gate and device verification results are recorded below after execution. No gates weakened.

Final verification: 429 Mobile tests, zero failures; assembleDebug, lint, detekt and ktlintCheck all passed (`mobile-ui-package-final.log`, BUILD SUCCESSFUL5m20). New native seam test observed red before the range-fill correction and green in the full suite. Independent source review found no remaining actionable issues after shared hero/card selection repair. Picker glucose previews regenerated from production renderers.

APK installed successfully on SM_S948B via WLAN debugging. Before-fix seam was physically reproduced and pixel-measured. After-install visual verification of the new header/settings/footer/graph is HARDWARE-TEST OFFEN: device had relocked. No user preferences were changed to bypass the lock or keep the screen awake. CODE-VALIDIERT and BUILD-VALIDIERT; do not interpret these as comprehensive hardware validation.

User clarification: preserve the original lower overview position; replace only the former large watch area with a centered wordmark. Its replacement slot now derives from the same watch frame/footer/pill dimensions and measured status text height, including the former section gap. No lower card geometry changes. Focused layout/activity tests and assembleDebug/lint/detekt/ktlintCheck passed (`overview-position-green.log`,4m35), APK reinstalled. On-device screenshots confirm the 4x2 icon/graph layout, content scrolling behind the floating pill, and watch hero in Watch menu. Restored overview captured as `overview-position-restored.png`; final physical comparison performed against `mobile-line-check-unlocked.png`.
Measured logo bounds: original y1207..1289, restored y1206..1288 at physical1440x3120. Thus original lower overview positioning is restored within one physical pixel of layout rounding, not a claim of pixel-identical raster output.

## Color/menu follow-up 2026-10-20

Widget color rows now contain only labels/swatches. HEX, exact-ARGB clipboard copy and reset live in the existing picker; reset updates HSV, alpha, HEX and the staged instance together. Other Mobile color-picker callers keep their existing behavior. Slider endpoint ticks move a further4dp inward, while thumb occlusion uses the actual progress position. Updated supplied TIR SVG replaces all three symbol paths, retaining its circular background and independent circle/symbol colors. Picker previews regenerated.

Device evidence for Samsung palette mismatch: system_primary_dark=#94b8ff while selected system_accent1_200=#b5cace and system_accent1_600=#466368. Samsung widget settings now use these public selected-system tonal resources rather than the stale Material primary alias; non-Samsung remains on dynamic Material3. No user theme, background or CGM preferences are overwritten. Watch menu remains reachable by tapping the overview connection pill; no overview layout changes in this follow-up.

Color follow-up validated: 136 focused tests, zero failures; assembleDebug, lint, detekt and ktlintCheck passed (`widget-picker-cleanup-final.log`). Lint retains two unrelated pre-existing notification warnings. APK installed on SM_S948B. Hardware confirmed simplified rows, selected system palette, HEX/copy/reset controls and reset to #FF000000. Change/reset/cancel left persisted instance preferences unchanged (SHA256 before/after 4AA56EDF7922DC7EACB4A29C9B3B414F12C7FCC1E04AE0CF06211C9EE72B6F6D).

## Switch alignment and genuine Samsung picker availability

All widget switches route through the shared row and now occupy a fixed 56dp trailing column, with 48dp height and identical end alignment. Labels can wrap without displacing switches. Regression contract failed before the change.

An official reusable One UI 9 color-picker API could not be established. Samsung's public dialog guide documents standard Android dialogs, not a color-picker SDK (https://developer.samsung.com/one-ui/comp/dialog.html). The available OneUIProject library explicitly targets One UI 4.x and requires replacing AndroidX dependencies (https://github.com/OneUIProject/oneui-core/blob/sesl4/README.md); it is not evidence of an official One UI 9 component. Connected-device PICK_COLOR resolution found no activity. The settings manifest includes a fingerprint-icon color settings activity, not a documented generic color-result contract; it is not invoked. No private APIs, decompiled components or dependency replacements introduced. The existing system-palette-aware picker remains; genuine Samsung One UI 9 picker integration is NOT COMPLETE.

Hardware exposed differing optical insets in OEM checked/unchecked switch drawables despite the fixed view column. Shared switches now use explicitly sized track/thumb drawables via the public native Switch API, preserving native accessibility, interaction and dynamic tint. Final screenshot `widget-switch-final.png` confirms identical horizontal track bounds for checked and unchecked rows. Final verification (`widget-switch-final.log`): 7 focused tests, zero failures; assembleDebug/lint/detekt/ktlintCheck passed, BUILD SUCCESSFUL3m53. Corrected APK installed successfully and settings reopened on SM_S948B. No widget setting saved or changed during this visual check. Two unrelated existing notification lint warnings remain.

## User-approved own picker reconstruction, 2026-10-20

The user subsequently requested an original reconstruction rather than integration of Samsung's private component. Existing widget ColorEditorDialog callers route to one shared bottom-aligned, rounded, system-palette/device-font dialog. It provides Farbfelder/Spektrum tabs, fixed opening-color vs new-color previews with alpha checkerboards, 42 geometric swatches, HSV, opacity, exact ARGB HEX input/copy, reset, and a fixed completion row. Content scrolls independently when screen/keyboard space is constrained. Widget changes remain staged until the existing outer Save; non-widget color callers retain their dialog. No Samsung code/assets, hidden APIs, external dependencies, or altered source/collector behavior. This is One-UI-inspired, not a claim of pixel-identical One UI 9 reproduction without a supplied picker reference.

Opening ARGB is preserved bit-for-bit; changing swatches/HSV preserves alpha, changing alpha preserves RGB, and hue remains independently adjustable for black/grayscale. Opening preview is anchored to dialog lifetime, not the parent's recomputed initialArgb. Incomplete/invalid HEX does not emit a replacement color. Shared canvas controls also accept taps, with endpoint markers inset to prevent clipping. Targeted color/state tests and source-only independent review accompany the change; review identified missing accessibility actions, to be resolved before final validation/install.

Accessibility review resolved: spectrum has independent labeled saturation/brightness actions and state; hue/opacity expose range/setProgress semantics with finite/clamped values. Independent follow-up found no important remaining issue. Final tests/build/static gates passed (`widget-color-reconstruction-final.log`, BUILD SUCCESSFUL2m28); 6 new picker tests plus existing settings/color tests passed. APK installed on SM_S948B. Device verified swatch selection, unchanged opening preview vs new red, spectrum tab, tap-to-adjust50% transparency producing #80FF0000, and scrollable content with visible reset/completion controls above the keyboard (`widget-color-rebuilt.png`, `widget-color-spectrum.png`, `widget-color-ime.png`). Initial whole-file hash changed during the session; this alone cannot establish cancellation correctness, so a controlled repeat is required. Actual TalkBack navigation remains HARDWARE-TEST OFFEN; semantics are code/test-reviewed.

## Subsequent complete UI follow-up

- Restore native device-default DayNight Switch drawables/animation; fixed trailing column remains, but no custom track/thumb geometries or forced tints. On Samsung this delegates appearance to the installed OS rather than claiming a handmade exact One UI 9 switch.
- Adaptive glucose: glyph-bounds centering removes the extra4dp compact translation; value and supplement groups use measured widths and equal outer margins. Details baseline moves5dp upward. The 4x2 graph top anchors to actual rendered glucose glyph bounds, not a hardcoded fraction. Only its header uses6dp horizontal inset, matching its6dp top inset. Other graph headers retain contour-safe placement.
- Detail icon circles remain independently configurable; symbols stay black. Obsolete symbol color controls are hidden while legacy keys remain readable for persistence compatibility.
- Compact gains Samsung native2x2 metadata; legacy developer labels become Zusätzliche Widgets. Native small previews use fitEnd so artwork can align at the bottom of the host's available preview viewport. Launcher-owned size captions/card aggregation cannot be positioned by preview XML; original independently selectable/free-resize legacy providers are preserved rather than silently replaced by a new aggregate provider.
- Mobile menu expansion/dropdown arrow indicators removed, navigation chevrons retained; menu controls follow public device palette resources without altering CGM/therapy/data color roles.

## Disappearing widget root causes and recovery

Installed Glance1.2 source confirms same-ID compose must be sequential; managed updateAll returns before publication and does not rerun provideGlance's captured configuration/state for an active session. This conflicted with direct composition used for Save and scale taps. A Glance error could also replace the valid frame with an error layout. All productive platform update/resize/locale/debug signals now use the shared completed direct-composition path. A bounded32-mutex per-ID transaction spans compose→commit→publish; settings, data/family and scale paths share it. Only a successful full RemoteViews composition is published. A failing instance does not block other instances; fallback error UI does not overwrite the previous surface. actionRunCallback is retained (not lambda actions, which would create managed sessions). Direct compose's shouldPublish=false error path propagates independently of onCompositionError; source verified to avoid no-output hangs.

State cache publication is revision-monotonic and happens once per canonical update, preventing old family requests from replacing newer cached data. Removed the now-unused fire-and-forget instance dispatcher. Scale actions await publication within their broadcast lifetime. Both header and parent clickables use a transparent ripple override, keeping the accessible140×48dp target without flashing its rectangular bounds.

Review additionally found cancellation could wedge a coordinator lane: a failing-then-fixed regression covers a canceled composition with pending newer work. Canceled slots now settle waiters and restart newest pending work on an active parent; diagnostic-reporting failure cannot skip lane finalization. Locale broadcast delivery is declared for all12 providers. Source follow-up review found no important remaining cancellation issue. Hardware disappearance reproduction remains distinct from these confirmed code defects.

## Final local verification, 2026-10-20

- Mobile testDebugUnitTest: 450 tests, zero failures/errors. Debug and release APKs, lint, detekt and ktlintCheck passed together (`widget-ui-final-palette-gates.log`, BUILD SUCCESSFUL 4m16). Architecture verification passed in the preceding full test run. No gates weakened.
- Menu accent foreground now uses the matching public device palette tone, with dark/light contrast assertions. Widget icon circles use their selected colors; their symbols remain black.
- Updated Mobile debug APK installed successfully on connected SM_S948B (Android17). Final visual inspection remains HARDWARE-TEST OFFEN because the device was locked; installation alone is not visual or disappearance-recovery validation.
- Two pre-existing notification lint warnings remain outside this widget change (UselessParent and UseKtx). Architecture verification reports its existing configuration-cache incompatibility. These are not represented as warning-free verification.
- Samsung launcher owns provider-card grouping and size-caption placement. Existing independently selectable additional providers are preserved, respecting the user's earlier choice; a single aggregated additional-provider card is not claimed as implemented.
