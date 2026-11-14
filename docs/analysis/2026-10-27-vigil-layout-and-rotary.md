# Vigil layout, defaults and rotary feedback

## Changes

- The graph is fixed 450-unit artwork without a horizontal inset, clipped to the round display boundary (center225, local y85, radius225); WFF scales that complete artwork once. Scale/age now starts at x=56, y=146 so its visible inset matches the graph surface instead of the old full-width origin.
- Fresh Vigil settings use two hours, opaque white dots in every glucose range, opaque high/low limits, no graph border and a 0.7dp current-dot outline (twice the previous 0.35dp). Other SugarWear graph families retain their own defaults. Saved preferences are preserved.
- Removed the obsolete constructor migration that force-enabled the border. Provider preferences and settings read the same defaults.
- Selector and Mobile artwork is regenerated from the real provider renderers and WFF geometry, with a fixed 22:10 clock and the handless/markerless defaults.
- The analog glucose/text layer renders before native hands. An alert and inactive-sensor message sit above the graph; a larger pairing button sits below it, under the hands. The existing graph provider includes the sensor footer; the clock turns transparent while pairing is required. This retains the native eight-slot format limit. Header image providers no longer own a full-image tap PendingIntent. A local native WFF Launch region opens SugarWear without highlighting a full-face complication.
- Crown input in SugarWear, shared Wear settings and the shared color editor delegates consumed pixel deltas to Android ScrollFeedbackProvider on API35+. Older devices use ordinary View haptic feedback without overriding system settings. No custom amplitude or forced haptic flags are used.


## Evidence and validation

The scale-position, default-values, lower pairing-guidance and header-tap regressions were observed failing before their fixes. Relevant Kotlin/native rendering and Python WFF tests then passed. Build, Detekt and ktlint passed; module Lint has no errors. Two pre-existing ui-shared warnings remain in an imported long vector path and an unrelated hardcoded number-editor label; no warning is suppressed by this change.

Both installed WFF definitions had the same 450-unit canvas and graph-slot geometry. Direct screenshots of both active displays showed the same proportional graph surface. A first Pixel comparison was ambient, which intentionally makes its background transparent; it is not evidence of a device-width defect.

Updated Vigil and SugarWear were installed on Galaxy Watch Ultra and Pixel Watch4. The native upper Launch region was tested on Pixel and opened G7WatchActivity. Sugarlicious Wear was also updated on Galaxy; installed APK hashes matched the built artifacts. Galaxy rotation was read as locked at90 degrees and reset to normal0 degrees. Its later PIN lock prevented another complete on-device guidance comparison; native-render tests cover those bounds. The primary-phone APK was subsequently installed and hash verified.

## Final display revision

The user cancelled all signal-loss masks. The graph is never overlaid or additionally colored for signal loss. A red alert icon with a black exclamation mark sits over a white Signalverlust label in the lower footer; the digital clock and analog delta text yield that space. SugarWear has the same unboxed warning above the glucose tile and no connected-status pill on its overview.

The captured Galaxy display overrides are committed as named default preferences in ui-shared. Resetting or removing overrides returns to that reference, without touching collector state, credentials or reading history. Explicit new defaults are shared low/very-low red, shared high/very-high yellow, white glucose dots, two-hour initial graphs, black menu, contour-free tiles and graph-owned overview tile fill. Own menu accent palettes derive from #22EB60. User-facing association controls use Verbinden and Trennen.


## Latest accepted presentation revision
- Vigil scale/age slot moves from x56 to x48 (450-unit reference face).
- Sensor association and pause actions are available only under SugarWear Settings > Sensor; the overview and Vigil no longer offer a connect button.
- The inactive Vigil header reads `Kein Sensor verbunden`; an associated paused collector reads `Datenempfang pausiert`. A paused collector is not classified as signal loss.
- Signal loss retains the last numerical value in opaque gray (#9E9E9E), without combining strike glyphs, a drawn strike line or a notification strike span. The warning label stays white and the alert icon retains its original red triangle/black exclamation.
- Glucose metadata uses widget-style hierarchy: bold white delta, smaller gray unit, then age on a separate line with the numerical portion highlighted. Android Views and Compose retain system font scaling.
- No signal-loss mask is drawn over graph data.

## Latest immediate SugarWear follow-up
- Refreshed display-only system defaults from the current Galaxy values in the same four appearance namespaces. Existing user settings and all sensor/session/history data remain separate.
- A user-triggered pause/unlink shows its transition phase for up to two minutes. Resume shows the same protocol phases as the connection dialog and hides them after a successful new sensor reception. Normal connected operation has no attempt banner.
- The paused title remains visible while reception is paused; the transient phase expires independently.
- Scale and age text use black/white contrast against the actually composited graph background, including enabled range fills and transparency.
- Vigil status slot is now x40 on the 450-unit canvas. Wide-marker analog metadata no longer stretches a square 450-unit image to 477 units vertically.
- Separate native seconds-hand visibility option retains hour/minute hands and markers.
- Deferred at user request: finish Mobile data sources, then enlarge Mobile therapy progress bars. System font/display scaling across both Wear apps remains a required audit item.
