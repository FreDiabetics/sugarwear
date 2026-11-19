# Mobile settings: Samsung reference alignment

Reference: user's four-screen One UI 9 collage (Volume, Sounds/Vibration,
Connections, main Settings), supplied 2026-10-20.

Scope: Mobile settings only. Preserve therapy values, source/collector logic,
stored preferences, exports and permission flows. No private Samsung API,
copied proprietary code/assets or replacement dependencies.

## Implementation

- Public DeviceDefault DayNight theme supplies native font, radios, switches,
  dialogs and accent behavior; no app-level forced sans family/green accent.
- Main settings has a large centered title, borderless 28dp cards, 18dp inner
  padding, regular 18sp row titles, secondary text and inset separators.
- Categories open dedicated pages instead of multiple inline accordions.
  Toolbar/Back/gesture return from category to settings before overview.
  Category state survives Activity reconstruction. No app exit action added.
- Native and Compose settings share unscaled device switches and the rounded
  14dp slider track/20dp outlined thumb. Widget-specific discrete ticks remain
  available; continuous Mobile controls do not add artificial tick dots.
- Radio choice rows wrap vertically when measured content will not fit; long
  labels must not be squeezed into fixed-width chips.
- Menu controls use selected device palette and system font; therapy/graph
  preview colors stay user-owned. All color editors use the existing shared
  reconstructed picker (not a claim to use Samsung's private component).
- Nightscout configuration retains its existing save/authentication/network
  permission behavior with the same settings card/text presentation.
- Restrict the app's supplementary Back swipe to the left24dp edge so dragging
  a slider across the page cannot navigate away.

## Verification boundary

Regression checks retain notification permission and persistence assertions,
but enter the new category pages first. Added native-switch binding/no-write
and category Back tests; Activity test covers category recreation and ordering.
Screenshots are the visual reference, not a pixel-identical validation claim.
Final real-device comparison is required, including dark/light mode and large
font. Never mark exact Samsung visual parity based on compilation alone.

Source review found and resolved two follow-ups: non-touch slider input must
commit without waiting for a touch-end event, and native controls must receive
a copied configuration matching the app's selected dark/light mode. Tests
cover immediate non-touch persistence, batched touch persistence, no writes
from programmatic binding, and preserving the original device configuration.
Follow-up read-only review found no additional concrete regression.

Live comparison on SM_S948B revealed DeviceDefault's activated-control alias
still used framework blue. API31+ control overlays now explicitly use public
accent1_200 (dark) / accent1_600 (light), with pre31 fallback. A regression
failed without the overlay and passes with it. Widget/native Compose controls
must not wrap the supplied context with the base theme again, which would
erase that overlay. The large heading is height-limited in landscape. Header
accent, chevron, card spacing and child-page top inset follow the reference.

## Verified handoff

- 456 Mobile tests passed, zero failures/errors. Debug/release APKs, lint,
  detekt and ktlintCheck passed together (`mobile-oneui-handoff-gates.log`,
  BUILD SUCCESSFUL 3m45). New warnings resolved without disabling checks.
- API26 context/theme fallback is tested; API29 DayNight theme and API31
  public palette resources are version-qualified. Two existing notification
  warnings remain (UselessParent and UseKtx), outside this menu change.
- Final debug APK installed successfully on SM_S948B Android17.
- Live Samsung reference and initial Sugarlicious root/category rendering
  were inspected. Final palette-corrected visual comparison remains open:
  the device relocked during the later attempt. Full light-mode/large-font
  hardware matrix is not claimed as validated.
- Existing additional widget providers remain directly selectable per the
  user's explicit decision; no aggregate provider introduced.

## Late styling override root cause

Physical comparison additionally exposed the obsolete custom
SugarliciousDashboardContent.onViewAdded pass: it overwrote headings, fonts,
card geometry, divider alpha and Switch tints after the factory configured
them, using user therapy colors. The only XML use is replaced with a plain
LinearLayout and the obsolete class removed. A regression using red primary
and green therapy text failed with a green settings heading before removal,
then passed with the device accent. Native settings values/persistence remain
unchanged. Row ripple feedback is explicitly owned by the menu factory now.

The shared native Switch keeps Android interaction/accessibility while using
the measured reference geometry: 16dp knob within a 20dp-high, 32dp-wide track.
The thumb has explicit vertical insets; matching public track padding prevents
Android Switch optical-inset handling from shrinking the painted track. The
measure/layout/draw regression detected both the initial 16x20 oval and the
subsequent track shrink, then passed with a 16x16 knob and 2dp top gap. No hidden
APIs/assets are used. Final root-cause results below supersede earlier counts.

## Final root-cause verification

- 458 Mobile tests, zero failures/errors. Debug/release APKs, lint, detekt and
  ktlintCheck passed together (`mobile-oneui-complete-gates.log`, 2m08).
  Two test formatting violations were corrected; no checks were weakened.
- Installed successfully on SM_S948B Android17. Final root, Allgemein and
  Übersicht und Graphen pages were inspected on the physical device, including
  palette, typography, radio rows, sliders and switch geometry. No setting
  values were changed for inspection. Light-mode/large-font hardware matrix
  remains open; exact private Samsung component parity is not asserted.
- Read-only follow-up review found no further concrete geometry/regression
  issue after accounting for Switch optical insets. Two pre-existing
  notification lint warnings remain outside this menu change.

## Follow-up: Watch menu, picker actions and graph behavior

- Mobile Watch navigation now reuses the device-palette One UI theme, header,
  rounded surfaces and shared native sliders/switches. FaceDial/CGM preview
  colors, selection, presets and synchronization remain canonical/unmodified.
- All color reset/HEX-copy/color-transfer controls live inside the shared
  picker, including Notification colors. Per-role reset removes overrides
  rather than materializing an inherited color. Non-color geometry actions
  remain with their settings.
- Graph excursion tint follows the newest valid measured value immediately;
  no two-reading confirmation. Invalid values remain excluded, arrival order
  cannot supersede measured event order, and return to target clears tint.
  Resolver hysteresis, alarm debounce and Collector BLE logic are unchanged.
- Historical widget time labels use actual glyph ink bounds for rounded-corner
  clearance instead of unused font descender space, reducing the left inset
  without discarding the contour guard.
- Glucose 2x2/4x2 detail circles grow from 16dp to 22dp with black symbols.
  Glucose 2x1 hides TIR/icon-only controls; 2x1/2x2 hide graph-only controls.
  The separate compact Glucose+Graph widget retains its graph controls.
- Shared switches also have 2dp horizontal knob clearance in both checked
  states; real measure/layout/draw assertions cover all sides.

Follow-up verification: 137 core-model, 9 ui-shared, 302 SugarWear and 460
Mobile tests passed (908 total, no failures/errors). Mobile debug/release,
Wear and SugarWear debug builds and relevant lint/static/format checks passed.
The last Mobile-only layout refinement passed all 460 tests and Mobile gates
again. No checks were weakened. Existing notification lint warnings remain.

Physical inspection on SM_S948B exposed a too-narrow three-column complication
catalog; it now reserves readable cell widths, uses two columns on this phone,
and separates title/gear in a row. Final APK was installed and inspected:
Watch menu/cards, larger glucose detail icons, widget preview/switches and
picker-contained HEX-copy/reset. Settings were exited without saving changes.
Only the phone is ADB-discoverable; Watch installation and first-crossing
hardware validation remain open. Light/large-font matrix remains open.
