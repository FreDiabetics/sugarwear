# ApeX WFS-source repair — 2026-10-21

## Follow-up 0.6.11: native inline content and clear provider picker

- Twelve saturated Vivid palettes appended (IDs 25–36); both palettes now offer 37 choices without changing existing IDs. Outer ranged value fonts reduced to 30; side-circle bodies increased to 31, left titles to 33. Bottom 46 is retained.
- Outer Short Text uses a native centered TextCircular run with the provider's InlineImage and text. The icon participates in actual glyph/run layout rather than fixed coordinates or estimated character counts. Full quadrant text sectors replace the narrow ranged-value label sectors only for SHORT_TEXT.
- User clarified automatic provider-field formatting for circle Short Text. Body layout chooses inline provider icon versus plain text using the image field, retains the provider title when supplied and centers the body when title is absent. No additional ComplicationSlot or invented icon is introduced. Standalone side-circle Short/Ranged icon boxes and their manual visibility controls were removed; bottom Ranged retains its open-segment provider icon.
- Picker service labels now describe content with human-readable variants such as Fortschritt/Details instead of numeric codes and Android type jargon. Stable service classes, IDs, supported types and actual static/dynamic preview values are unchanged. Thirty-three old number-heavy PNG picker icons were replaced by native XML icons using semantic provider glyphs plus a small format badge. Removed assets are recoverable from Git. Other provider preview XMLs use the same native style.
- Mobile preview follows native inline layout and uses the canonical default GlucoseTrendDeltaAge presentation for its right circle, not the currently user-selected Watch binding. It is still a default-binding illustration, not a remote screenshot.
- Twenty ApeX layout contracts and three provider/static-preview tests pass; Mobile/Wear Debug, ApeX Release/pinned WFF/push checks, watchface lint, Mobile/Wear ktlintCheck and detekt pass. Real-device inline-image layout still requires physical verification after installation.
- All three APK installations succeeded. The Watch then showed its PIN screen; no lock bypass or PIN input was attempted. Native inline glyph layout, automatic no-icon formatting and picker appearance remain HARDWARE-TEST OFFEN until the user unlocks it.
- Provider consolidation into one service per content family supporting multiple types is not part of this patch. WFS type priority applies within a selected provider; it does not cross between today's separately registered variants. A user-defined per-provider field contract is the proposed input for that later change.

## Follow-up 0.6.10: larger bold typography and broader palette choice

- User explicitly requested fonts larger than the restored WFS baseline, all bold: outer 35 (lower-left 34), middle values 29, left title 31/right title 29, bottom value 46/title 29. Text boxes were enlarged to retain line-height headroom; icons retain original square dimensions. Mobile preview follows sizes and weights.
- Both theme configurations now offer 25 three-tone palettes, retaining original IDs 0–8 and appending 9–24. Values/titles retain distinct light/accent tones, active bars accent, tracks dark. Other hand styles, providers and G7 collection are unchanged.
- 78 targeted tests (ApeX geometry/palettes, native widget rendering, widget presentation and axis bounds), Mobile/Wear Debug, ApeX Release/pinned WFF/push checks, watchface lint, Mobile detekt and ktlintCheck pass. A review caught three title boxes lacking headroom; those were corrected before installation.
- All three APKs installed successfully. Watch screenshot confirms enlarged bold text and multi-tone orange palette; existing sources/hands remain. Long values can still ellipsize within fixed slots. Smartphone was displaying an actively used music screen, so no Homescreen navigation was forced for the final widget sight check.

## Mobile widget graph horizontal-edge correction

- Shared graph surface was full-width while compact graph bands retained a separate 5dp horizontal inset. Removed that obsolete inset parameter and use full surface width for range fills, threshold lines and plot surface. The intentional right value-scale lane and dot-radius safety inset are retained; colored areas do not leave background strips to either side.
- A native pixel regression first failed on the compact graph's left/right strips, then passed with the corrected shared geometry. Standalone/compact geometry and axis tests verify the same edge contract. Widget font preferences are unchanged; the larger/bold typography request applies only to ApeX as confirmed by the user.

## Follow-up 0.6.9: restore WFS font hierarchy and semantic palette tones

- Supersedes the rejected uniform-24 typography in 0.6.8. Original WFS was re-read directly and its SHA-256 still matches the pinned source. The source canvas is 450, runtime is 512: outer 27 maps to 31 (lower-left 26 maps to 30), middle body 22 maps to 25, middle-left title 24 maps to 27, middle-right title 22 maps to 25, bottom body 35 maps to 40. The additional bottom title remains 25.
- Every supported text variant uses this per-slot hierarchy. Middle title boxes gain height for the larger source title without moving the fixed icon anchors. Mobile preview matches the restored font sizes.
- Palette index 0 is light value text; titles now use index 1 (middle accent), active progress uses index 1, tracks use index 2 (dark). Main and SubTheme remain independently selectable, but choosing the same palette no longer makes title and body identical in tone. This fixes the actual semantic binding rather than merely adding differently named palettes.
- Source-derived font and title/value tone regressions failed before the fix. The 18 ApeX tests, Mobile/Wear Debug, ApeX Release, pinned WFF/push checks, watchface lint, Mobile detekt and ktlintCheck pass. The previously approved transparent hand assets are unchanged.
- Installed 0.6.9 and matching Wear/Mobile APKs successfully. Galaxy Watch Ultra screenshot confirms the enlarged bottom value/outer text and distinct medium-green titles versus pale-green values. This is a limited visual check; all palette/provider/AOD combinations are not claimed validated. Long combined provider strings still ellipsize inside the fixed circles.

## Follow-up 0.6.8: typography, palettes and revised transparent hands

- User-approved font size 24 applies to every text variant in outer slots 0–3 and center slots 4–6. Graph slot 7 is unchanged. Mobile preview uses the same sizes.
- The former materialAccent configuration was not consumed by any rendered element. It now supplies nine three-color palettes (light text/icons, active progress, dark track); original IDs 0–5 retain their color families. A separate materialSubTheme palette controls titles and the lower central slot. These are native WFF editor palettes, not automatic phone wallpaper extraction.
- Every monochromatic complication image, including images behind icon-visibility transforms, follows the corresponding palette. Source clinical graph colors remain unchanged. Main/SubTheme can be selected independently; saved hand/provider IDs remain intact.
- New hour/minute transparent SVGs from Desktop/Sugarlicious/Watchfaces/hands_2, modified 2026-10-21, are preserved under reference/hands and rasterized directly with CairoSVG 2.9.0 at 450 x 450. The SVG viewBox/pivot is unchanged; the new 70-percent black center is retained. Only the transparent hour/minute PNGs change; seconds and other styles remain untouched.
- Regression tests first failed for font sizes, unbound palettes, untinted center icons and old transparent centers; all 17 ApeX contract tests now pass. Mobile/Wear Debug, ApeX Release, pinned WFF/push validation, watchface lint, Mobile detekt and ktlintCheck pass. Physical appearance is verified separately after installation.
- Installed 0.6.8/versionCode 16 and matching Wear/Mobile APKs successfully. Galaxy Watch Ultra screenshot confirms green themed text/icons/active progress/dark tracks, uniform smaller text and the revised transparent hand pair; existing providers and hand selection remain. Every palette combination, all provider lengths and full AOD coverage are not claimed hardware-validated.

## Follow-up 0.6.7: complete complication variants

- All four outer slots now support provider icons and bounded curved text in both Short Text and Ranged Value. Lower-left gains the missing real progress arc. Upper ordering is icon / progress / text; lower ordering is mirrored. Long labels may ellipsize rather than overlap adjacent slots.
- Upper-left default now uses a new TIR Ranged Value adapter (stable ID 56), reusing canonical TIR statistics. Short Text cannot provide a numeric progress value. Existing saved Short Text selections are preserved and require explicit reselection to the ranged adapter; changing a default policy does not migrate saved bindings.
- Middle ranged slots use circular open-bottom progress arcs, titles at 24 and values at 26; bottom title is 24 and value 36. Provider icons occupy the open segment. Three default-on editor toggles control middle-left/right/bottom icons. Empty titles center the remaining value.
- Graph root causes: the old image rectangle overlapped the opaque template mask, and device DPI incorrectly enlarged content inside the fixed-size bitmap. The new 200 x 70 viewport at (156,116) preserves aspect ratio and lies wholly in the transparent cutout. Bitmap geometry uses a fixed logical width, retaining system font-scale support.
- Mobile default-binding preview follows the new geometry, icons, text bounds and default TIR ranged selection. It does not pretend to mirror arbitrary saved Watch provider choices.
- Targeted provider, static-preview, source-geometry and native bitmap-density regressions pass. Debug Mobile/Wear builds, ApeX Release, official pinned WFF/push validation and watchface lint pass. Full affected-suite/static-check results and physical installation are recorded separately after execution. Hardware validation of 0.6.7 remains open while the Watch is unreachable. G7 collection is unchanged.

### Follow-up execution evidence

- Full affected suites: Mobile 486, complications 63, Wear 93, core-model 137; 779 tests, zero failures/errors. Catalog cardinality updated from 38 to 39 with an explicit TIR ranged selection assertion.
- Mobile/Wear/complications lintDebug, all four modules' detekt and ktlintCheck passed. Mobile retains 20 existing lint warnings and zero errors; no warning-free whole-project claim.
- Read-only independent review found no actionable defects in the bounded change.
- ApeX 0.6.7, Wear Debug and Mobile Debug installed successfully on SM-L705F and SM-S948B. The Watch initially displayed a different face; ApeX was activated for the requested test.
- Physical screenshot confirms the graph fully visible in its cutout and upper-left TIR progress after selecting 19d TIR Ranged Value in the public editor. Existing Short Text selection had correctly survived the APK update. Other devices, long strings, every provider variant and full AOD matrix remain hardware-test open.
- Lower-left's saved combined Long Text was explicitly changed to 14a IOB Short Text for the requested compact icon/value presentation. The editor confirms both new bindings; remaining providers and the selected hand style were preserved.

## Scope and source

Repair the actual ApeX watchface first, then its Mobile previews. Preserve package identity, slot IDs, user-selected complication providers and all three hand packages (style IDs 0/1/2). The user explicitly chose to keep hands despite their absence from the original WFS scene.

Authoritative read-only source: `C:/Users/typ1d/WatchFaceStudio/Sugarlicious Analog.wfs`, SHA-256 `2B2E3E818DE8B74AEDD503C38975967FAC79AB042D16C4336DD010E72E63051F`, canvas 450 x 450. Extraction: `tools/watchface-assets/Read-ApexWfsGeometry.ps1`. Snapshot: `watchfaces/sugarlicious-analog/reference/wfs-geometry.json`. No Samsung application implementation was copied.

## Root causes and corrections

- The first installed 0.6.5 repair inferred arc-midpoint icon placement and oval center clipping. The user rejected it; those assumptions are superseded, not accepted design evidence.
- Original icons occupy cardinal anchors independently of progress bars. Runtime now uses the measured anchors. Provider images remain upright to preserve clinical trend direction; decorative source-image rotations must not rotate a clinical arrow.
- WFS progress dimensions are outside dimensions. WFF stroke-centerline diameter is `(outside - stroke) * 512/450`: outer 391.3956, stroke 17.0667; bottom 125.1556, stroke 11.3778. Source sweeps and centers are retained.
- Curved text is independent of the progress sweep: original direction/alignment and 180-degree text paths are restored. Baseline oval subtracts the source font height.
- Middle and bottom source clip areas are rectangles, not ovals. Original middle text positions, bold weight and gray titles are restored.
- Outer WFF hit/clipping arcs are adapted to contain the visible source icon/bar/label sectors. The source's central editor boxes cannot be copied literally: official WFF bounding areas crop their contents. Very long provider text remains subject to the slot's bounded space and ellipsis; no arbitrary unlimited overlay is introduced.
- Mobile used Canvas angles without the clock-to-Canvas -90-degree conversion, a nonexistent fourth progress arc and unrelated shared hand vectors. These are corrected; preview sync now takes the runtime hand PNGs.
- Mobile color, opacity, font weight, lower-left baseline and provider-supplied icons now follow runtime. Providers without an icon do not receive invented placeholder images.
- Device inspection exposed raw AAPS basal description text overflowing the Mobile preview. It now uses the same canonical BASAL formatter as the complication instead of the raw description.
- Bottom default uses the existing GlucoseTrendRangedValueComplication. Existing user-selected bottom providers are intentionally not overwritten: an existing generic glucose provider still supplies its generic icon.

## Evidence and limits

- WFS contract tests first reported six failures on the prior implementation; source-driven tests now pass. Protection includes all nine hand assets, stable IDs, AOD second-hand hiding, template transparency, graph provider and typography.
- Full Mobile unit run: 481 tests, zero failures/errors, before the subsequent independent urgent-dot color addition.
- Mobile Debug, Wear Debug, ApeX Release, official pinned WFF/push validation, Watchface lint, Mobile detekt and ktlintCheck passed.
- ApeX 0.6.6 / versionCode 14, matching Wear APK and Mobile preview APK were installed on Galaxy Watch Ultra SM-L705F and SM-S948B.
- Watch screenshots before/after show the changed layout and live complication values. Release APK and generated Wear delivery APK had matching SHA-256.
- Hardware coverage is limited: every provider/text length, all three selected hand styles, full AOD/Doze matrix and Pixel Watch remain OPEN. Mobile preview is a preview of default bindings, not a remote screenshot or a guarantee that every customized Watch binding is mirrored.

## Subsequent Mobile urgent-dot correction

The user added independent very-high/very-low dot colors after the watchface task. Previously Dashboard dots reused GLUCOSE_VERY_LOW/HIGH text roles. Separate CGM_DOT_VERY_LOW/HIGH picker roles now persist dark/light values independently. Existing effective colors remain the fallback until a dot override is selected; glucose value colors and G7 collector behavior are unchanged. A native bitmap regression asserts both actual dot fill colors and fails with the old two renderer mappings. Final verification and installation evidence is recorded in the task handoff.

## Final verification

### Later native inline-image experiment: NOT ACCEPTED

### 0.6.17 user-requested unit simplification and matched left spacing

Supersedes the smaller-unit and shared-visible-bottom request: recognized
therapy number and unit now share one Font and a two-parameter Template,
retaining only a U+2009 gap. Font family, weight, size and color are consequently
identical by construction. One-decimal IOB presentation remains ApeX-local.
Removed unused dim palette colors at indices 3/4; the original three colors,
palette option IDs and saved selections remain intact. Mobile preview follows.

Lower-left ranged text end angle changes from 244 to 248 with END alignment.
The gap from the progress end (243) is now five degrees, matching upper-left
text start 338 versus progress end 333. More text grows away from the bar.
The short-text layout and provider bindings remain unchanged.

31 scoped tests and Mobile Debug, Mobile ktlint/detekt, ApeX Release, watchface
lint and official push validation passed. Installed ApeX 0.6.17 (25) and Mobile
preview on the explicit Watch/Phone targets. Native `%TEMP%/apex-v17.png` shows
same-size/same-color U/g and complete lower-left IOB text with the new gap.
Theme selections were preserved; the current user-selected saturated theme
differs from earlier screenshots. Earlier unrelated outer inline-icon limits
remain unchanged; this is not a full arbitrary-provider rendering guarantee.

### 0.6.16 bottom alignment and consistent contours

Bottom ranged provider image grows from 30x30 to 32x32, centered at x=59 and
raised from y=118 to 114. Bottom body/title move up three design units, including
the no-title transform. All three contour strokes now use opaque SubTheme.1;
the side contours previously used half-opacity MainTheme.1 over static gray,
which made them look unrelated to the bottom contour. Mobile preview follows.

All recognized U/g runs add U+2009 thin space; joint Text centering is retained.
IMPORTANT: exact visible glyph-bottom alignment is NOT implemented. Native
WFF v1 Font has no separate per-run vertical position/baseline offset. The g
descender remains below the shared number baseline. Do not describe the shared
baseline as satisfying the requested shared visible bottom edge. A separate
unit layout/renderer is needed; no unsupported attribute or fixed replacement
font was introduced. This requirement remains OPEN.

29 geometry tests + 2 therapy tests passed, with Mobile Debug, ktlint/detekt,
ApeX Release, watchface lint and official push validation green. Installed ApeX
0.6.16 (24) and matching Mobile preview on the explicit Watch/Phone targets.
Native `%TEMP%/apex-v16.png` confirms the common orange outlines and thin-space
therapy formatting; overlapping hands limit inspection of the larger bottom
image in that particular screenshot. This is not a completion claim for the
unresolved unit glyph-bottom alignment or the earlier outer inline-icon issue.

### 0.6.15 visual follow-up

Unit Font runs increase from 22 to 24. Each existing palette appends two
opaque colors at indices 3/4: the original light/accent RGB multiplied by 0.78.
The first three palette colors, option IDs and saved selections stay intact.
Unit colors follow the actual adjacent number's palette/index, not the other
accent or reduced alpha. The default Mobile preview uses the same RGB dimming.

Upper-right regular marks increase from 3.5 to 5 stroke units; the 100% mark
uses stroke 6 and radial length 24 instead of 15. Regular marks use the main
accent, with the terminal mark highlighted. Marker geometry is still native
vector drawing within the existing ranged slot; no new slot/assets.

The gradient's previous 50%-opacity beginning and full-range sweep made short
values look muted. It now starts fully opaque and ends at 75%, with the shader
end angle driven by the same guarded range expression as the actual active arc.
The existing native gradient toggle is retained, as is the marker toggle.

Lower-left ranged text uses END alignment at 244 degrees adjacent to the bar's
243-degree end; added text grows away toward 270. Upper-left starts at 338,
five degrees after the bar, and extends to 360 at font 28. A first 340–358/font30
trial clipped the current percentage in native testing and was rejected.
TextCircular auto-size was also rejected by the pinned v1 validator; it was
removed rather than silently changing the compatibility floor.

Final verification: 27 geometry/configuration tests and 2 therapy-text tests
passed; Mobile Debug, Mobile ktlint/detekt, ApeX Release, watchface lint and
official push validation passed. Installed ApeX 0.6.15 (23) and matching Mobile
preview on the explicit Watch/Phone targets. Native screenshot
`%TEMP%/apex-v15-final.png` shows complete 82% text, thicker markers with a longer
terminal, lower-left text adjacent to its bar and the larger darker units.
This is current-live-value coverage, not proof for arbitrary provider lengths.
Saved Material Steel/Orange palette choices were preserved; their saturation
is intentionally not replaced by a different Vivid palette without a choice.

### 0.6.14 theme accents, optional gradient and upper-right markers

The smaller U/g Font runs now use the other light/accent tone of their existing
palette instead of fixed gray. Main/subtheme selection still controls them;
provider values, signed rounding and generic-string fallback remain unchanged.
Mobile default preview uses the corresponding second tone and draws the markers.

Added independent native options `progressGradient` (default FALSE) and
`upperRightMarkers` (default TRUE). Each of seven ranged slots shares one track
and chooses exactly one active stroke: solid or gradient. The native v1 validator
rejects a gradient color list containing separate indexed configuration tokens.
The accepted implementation uses literal alpha stops, #80FFFFFF to #FFFFFFFF,
and theme tint on the containing PartDraw. This is a theme-colored intensity
gradient, not an arbitrary two-hue gradient or a format-version upgrade.

Upper-right RANGED_VALUE has eleven radial line markers, calculated from the
existing 15–63 degree sweep, not a bitmap or additional complication slot.
They follow palette tones and do not alter the range value or its min/max.

26 geometry/configuration tests plus 2 therapy text tests passed. Mobile Debug,
Mobile ktlint/detekt, ApeX Release, watchface lint and official push validation
passed. Installed ApeX 0.6.14 (22) and matching Mobile preview on the explicit
Galaxy Watch Ultra/SM-S948B targets. Native markers screenshot:
`%TEMP%/apex-markers.png`. Enabled gradient through the native editor with a
simulated rotary scroll, then left the carousel and verified the actual live
face in `%TEMP%/apex-gradient-live.png`. Gradient is currently enabled on this
Watch for the user's inspection; its installation default remains disabled.
All other bindings and theme choices preserved. Coverage is this Watch/current
palette/live values, not every palette/device/AOD combination. The three other
outer SHORT_TEXT dynamic inline-image limitations remain open.

### 0.6.13 centered therapy typography and themed contours

Middle SHORT_TEXT no longer contains the failed inline-image branch or its
invisible leading space. Left/right text boxes are centered on x=61.5 and the
bottom text on x=75. Known standalone therapy strings (signed decimal number,
up to nine number characters, at most two decimal places, followed by U/g)
render as two Font runs inside one centered Text layout. U values use one
display decimal at most; units use smaller bold gray text. Original provider
payloads, canonical data and other clients remain unchanged. Unknown/compound
strings pass through unchanged. Recognition validates characters before any
numeric conversion, avoiding conversions of generic provider strings such as
`+9` or labels. Locale-sensitive round-trip comparison was rejected in native
testing: a dot-decimal provider string differs from German formatted output.
The final recognition expression is locale-independent; output remains local.

Native screenshot `%TEMP%/apex-therapy.png` demonstrated raw 0.73U rendered as
0,7 with a smaller gray U, plus a smaller gray g for COB. No recent numeric
conversion errors were found in the inspected log window. This is a limited
live sample, not hardware coverage of all negative/missing/provider cases.
Pure preview tests cover signed rounding, German/US locale, COB preservation
and missing/compound/malformed strings. The Mobile preview follows these new
text runs without misleading inline icon spacing.

All three center outlines now overlay the source contours with theme-bound
strokes (main accent left/right, subtheme bottom); no extra complication slots.
Lower-left RANGED_VALUE reverses the source endpoint roles: text starts at 270
degrees, the provider image sits near the bottom at 229/438, and the 48-degree
progress sweep moves to 195–243 to leave space for both. SHORT_TEXT retains its
left-side provider image, restored through the supported PartImage path. Native
screenshots confirm the range, text and icon remain visible. The other three
outer SHORT_TEXT inline-image variants are still unresolved, not accepted.

Final scoped checks: 25 geometry tests + 2 therapy typography tests, Mobile Debug,
Mobile ktlint/detekt, ApeX Release, watchface lint and official push validation
passed. During testing Ultra was active; switched to ApeX for the actual tests,
without changing Ultra's settings. Final artifact version is 0.6.13 (21).

### 0.6.12 editor-boundary correction

Removed the remaining `bottomIcon` configuration, its alpha transform and its
unused label. A provider-supplied bottom ranged icon is no longer hidden by an
old saved toggle. No provider bindings, hand selection or theme selection were
reset. Replaced three rectangular center bounds with circular bounds; tightened
the graph bounds to its actual 200x70 image without moving the graph. Outer
bounds use four non-overlapping 90-degree sectors rather than 125–145-degree
overlapping sectors. Radial bounds reduced from 464/76 to 440/64.

Two regression cases failed before the edit, then all 22 geometry cases passed.
Release build, watchface lint, Mobile ktlint and official push validation passed.
Installed 0.6.12 (20) directly on SM-L705F. Native editor screenshot
`%TEMP%/apex-editor.png` confirms eight areas, three circular center outlines,
the tight graph box and no bottom-icon tab between Hands and Complication.
`%TEMP%/apex-check.png` confirms live ranged bars and the bottom trend icon remain
visible after the boundary change. Arbitrary provider text lengths and the
failed SHORT_TEXT inline-image experiment remain separate unresolved issues;
this editor-boundary fix does not claim to solve them.

Picker follow-up: the actual WFF contains exactly eight unique slots (0–7),
not additional independently configurable icon slots. Large overlapping outer
BoundingArcs and the graph BoundingBox explain multiple overlapping blue editor
outlines; removing an image renderer does not remove a slot because it was
never a separate slot. Do not delete valid provider images on that assumption.
The graph now advertises only SMALL_IMAGE/EMPTY, and the seven value slots only
RANGED_VALUE/SHORT_TEXT/EMPTY. Removed 23 unreachable long-text/image-only
variants. A new failing-then-passing source contract checks the exact slot IDs
and advertised types. All 21 geometry tests, release build, watchface lint and
official push validation passed. This is type filtering, not provider-specific
semantic filtering or proof that arbitrary provider strings fit. The unfinished
SHORT_TEXT icon path below still prevents final acceptance or deployment of
this combined patch. Existing on-device bindings were not replaced by this
source-only picker change.

On Galaxy Watch Ultra SM-L705F, ApeX 0.6.11 (19), both a direct
`InlineImage` complication resource and the `source` expression variant failed
to display the provider icon, despite accepted XML and passing source tests.
Screenshots: `%TEMP%/apex-short-inline-live.png` and
`%TEMP%/apex-inline-source.png`. Those tests do not prove native rendering.
The current inline-image experiment is unfinished and must not be called a
completed repair. Public WFF documentation explicitly supports dynamic
complication images through `PartImage/Image`, not through `InlineImage`.
Exact shared centering with arbitrary proportional provider text remains open;
character-count estimation is not measured glyph width. Do not substitute a
static icon or rotate clinical trend arrows as a workaround.

The temporary lower-left SHORT_TEXT test binding was restored to
`IOB · Fortschritt` through the native editor. The other user bindings and
selected themes were preserved. New native picker symbols/labels were visible;
two subsequently corrected duplicate labels still need the latest Wear APK
installed. Historical verification below applies to its stated earlier build,
not to completion of this experiment.

- Full Mobile suite: 483 tests; core-model suite: 137 tests. Zero failures/errors.
- Mobile Debug, Mobile lintDebug, Mobile/core-model detekt and ktlintCheck succeeded. Android lint reports zero errors and 20 existing warnings in unchanged widget/dependency/notification areas; this is not a warning-free repository claim.
- Final Wear Debug rebuild, ApeX Release and official push validation plus watchface lint succeeded after synchronizing the native picker ring color.
- Read-only follow-up review found no remaining Important defects in the changed scope. The physical Watch screenshot is a limited visual check, not validation of all provider, device, hand-style and lifecycle combinations.
- Final Mobile APK including independent urgent-dot colors installed successfully; PersistentBridgeService was verified foreground afterward. The Watch became unreachable before transferring the final native-picker-only orange-ring correction and refreshed embedded delivery APK. The WFS runtime repair 0.6.6 and earlier matching Wear artifact were already installed and photographed successfully. The last picker/delivery refresh remains pending; no further unlock request was sent because the user went to bed.
