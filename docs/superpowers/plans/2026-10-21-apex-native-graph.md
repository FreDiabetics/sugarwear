# ApeX native graph implementation plan

**Approved design:** Large transparent, restrained watchface graph without axes or app chrome; alternatively weekdays and a freely selected complication. Update the Mobile preview. Then inspect and repair progress gradients. No collector or app-chart redesign.

**Architecture:** Keep the existing eight slot identities. The upper slot accepts graph images and text/ranged providers: image selection displays the large graph, text/ranged selection displays weekdays with the chosen data. This avoids hidden duplicate slots and separate provider bindings. Reuse one focused watchface graph renderer in the existing SMALL_IMAGE provider and Mobile preview; leave app/tile renderer unchanged.

## Tasks

- [x] Add real rendering tests for transparent background, no axes, validated data, time-positioning, gaps and current-point emphasis; implement the focused renderer and wire the existing graph provider.
- [x] Update upper-slot geometry and supported formats, replacing the small graph/weekday toggle with automatic layout from the selected provider. Update geometry tests, native picker preview and Mobile preview together.
- [x] Correct progress gradient color composition after examining native output; keep solid option and clinical progress normalization unchanged.
- [x] Run relevant tests, WFF validation, builds and scoped static checks; install matching packages and inspect on the connected Galaxy devices.

## Review focus

- Missing/invalid history must not generate a plausible curve; stale data stays at its real timestamp.
- Gaps over ten minutes must not be joined; sensor/source/session transitions must break the line.
- Bitmap must not carry a background, frame, text or target fill; native graph tint belongs to the watchface.
- Weekday/text alternative must not create a ninth slot or duplicate image/icon slots.
- Enlarged graph and weekday alternative must clear the outer ring and the three central circles.

## Execution notes

User requests native execution without approval loops and proportional checks. Preserve the existing dirty main checkout; do not commit unrelated user work. Official WFF documentation limits faces to eight slots; reuse slot 7 for both layouts. Main/SubTheme preferences and other seven provider bindings remain intact.

- Native graph field is (142,112,228,80) on the 512px canvas, versus the preceding weekday miniature (186,134,140,49). Full transparent bitmap has no app background, frame, axes, labels or filled ranges. Native face supplies its theme tint.
- Standard GRAPH uses the focused renderer; GRAPH_LARGE, application/tile and collector renderers retain their existing paths. Source/session transitions and gaps over ten minutes are not connected; no synthetic history is inserted into live data.
- Native inspection on SM-L705F confirmed large graph rendering and retained seven other bindings. Three measured arc starts before the fix were #FF5F0D instead of the intended #FFCCB1. The wrap occurs where a round cap crosses the sweep-gradient seam. Padding the shader span and adding constant-color end stops restores all three measured starts to #FFCCB1 on installed 0.6.23 (31).
- Independent read-only review found one Important preview mismatch: fixed two-hour/default-threshold preview versus configured native graph. Fixed by sharing the existing complication-hours preference reader and Mobile CGM thresholds; two settings-consumption tests went RED to GREEN.
- Latest scoped verification: 17 ui-shared tests, 20 complication tests and 45 Mobile tests, zero failures. Mobile/Wear builds, WFF/push checks, watchface lint and affected ktlint/detekt passed. Mobile preview/settings fix rebuilt and installed after the last native bundle install.
- Review ruling: weekday arc is decoration; slot7's editable/tappable bounds cover the graph/value field, not all weekday letters. A single static WFF v1 bounding area cannot exactly outline both layouts. Keep eight stable slots rather than introducing hidden alternative slots; cost is the weekday decoration extending outside the selection outline.
- Review minor deferred: tests explicitly exercise sensor transitions, not every source/session/duplicate permutation; source/session guards remain in the production renderer. Additional broad scheduling/stale-delivery work is not part of this rendering change. Physical AOD and every third-party provider combination are not claimed tested.
- Delivery: ApeX 0.6.23 / versionCode31 and matching Wear bundle installed on 192.168.178.68:37231; final Mobile APK installed on 192.168.178.71:33781. Native graph and corrected gradient caps inspected in the awake face. Weekday/text/ranged alternative is schema/contract-validated; exhaustive third-party-provider visual testing remains outside this pass. Existing user changes are preserved and no Git integration was performed.

## Follow-up: dots, context and rotation (0.6.24 / 32)

- User rejected curve-only rendering. Historical readings now appear as distinct filled dots without invented connecting lines; the newest sample retains its larger hollow outline. Real timestamps, quality filtering, deduplication and transparent background remain unchanged.
- Added subordinate dashed high/low guides, configured boundary values in the source glucose unit, and a small duration label (e.g. 3h), not a full time axis or app panel. Boundaries use a shared pure overload of the existing glucose formatter; formatting of existing clinical readings is unchanged. Mobile preview uses the same renderer; static picker artwork updated.
- Lower-left outer typography is aligned with the opposite outer slot: SHORT_TEXT 35 bold and RANGED_VALUE 30 bold, including the Mobile illustration. Icon order and arc offsets preserved.
- Renderer/context and lower-left typography regressions observed RED then GREEN. Focused formatter, renderer, provider-density and Mobile geometry tests passed; Mobile/Wear/ApeX builds, affected ktlint/detekt and WFF/lint checks passed. A real Canvas rendering with illustrative data was exported and visually inspected; temporary export code removed from tests afterward.
- Rotation root cause: the prior external reset enabled free accelerometer rotation. On confirmed SM-L705F at 192.168.178.68:39037, observed free / accelerometer_rotation=1 / user_rotation=1. Applied wm user-rotation lock 0 and verified lock 0 / accelerometer_rotation=0 / user_rotation=0. No orientation-setting writer exists in the inspected app/tools source paths; no app-level orientation workaround added.
- Watch WLAN-debugging disappeared again after that verified reset (39037 refuses connections; mDNS only discovers Mobile). Watch installation and physical inspection of 0.6.24 remain pending reconnection; do not claim the new Watch renderer or enlarged lower-left text is already installed. Mobile can be updated independently.
- Reconnection resolved: confirmed SM-L705F at 192.168.178.68:39757, installed ApeX and matching Wear APK successfully, verified 0.6.24 / versionCode32. Rotation still lock 0 with accelerometer_rotation=0. Awake native screenshot inspected: measured dots, configured 160/80 threshold guides and 6h duration, larger lower-left IOB text visible; selected theme/background and other provider bindings retained. The earlier pending-installation note is now resolved.

## Ordered follow-up: pattern separation, widget controls, Wear progress cards

1. ApeX first: increase native pattern visibility (alpha 28 to 112); suppress pattern only behind the selected graph image. Strengthen semantic range lines and a subtle target band, remove threshold-number scale/lane, retain duration at the right. Reuse the transparent renderer and update Mobile/static previews. Tests cover line/band contrast, no left scale lane, graph-only masking and existing eight-slot geometry. Build/validate before installation.
2. Widget additions next: audit relevant per-instance color picker coverage, expose existing missing color roles, add switch/width/color for the 4x2 glucose family's graph contour without changing outer widget contour. Keep per-instance/dark/light isolation and immediate Save update. Only icon colors become new defaults now; do not capture other settings yet.
3. Wear overview last: replace plain IOB/COB/Basal displays with the existing smartphone therapy progress presentation, each metric in its own card. Reuse canonical data and shared rendering, not copied therapy logic; validate round-screen bounds and install only after affected checks.

Icon default snapshot (read-only phone capture, SM-S948B, 2026-10-21): active glucose-family instance 91, both dark/light: delta circle #FFCCCCCC, age circle #FF59FFFF, TIR circle #FF59FF59; symbol default remains #FF000000. Instance 103 is the separate compact glucose+graph widget, not the source of new family-icon defaults. Preserve existing saved overrides; new defaults apply to new/reset values.

### Follow-up implementation evidence

Full-bleed revision 0.6.28 (36): range fills/guides have no horizontal inset and use the complete image height; only sample markers retain edge clearance. A local WFF v1 MASK group clips the image precisely to the inside of the existing contour, leaving other complications and background patterns untouched. Target/high/low points have opaque neutral RGB levels 255/184/120 before the group's light theme tint; the current marker follows its sample's range. Mobile/static previews updated. Eight native rendering tests and 43 geometry tests passed; affected formatting, WFF preparation and Mobile/Wear builds passed (apex-fullbleed-tones.log, apex-fullbleed-install.log). ApeX, Wear and Mobile installed successfully. Native screenshot apex-fullbleed.png verifies rounded full bleed and light-theme target dots; no high/low samples were visible in that live 2h window, so those tones are covered by rendering tests rather than live claims.

Latest revision 0.6.27 (35): graph contour now exactly uses the middle-circle style (materialSubTheme.1, alpha 55, 2px stroke), separate from the opaque pattern knockout. Graph always displays the last 2h regardless of legacy complication duration; historical dots increase from 2.1 to 2.7 renderer units and current ring from 3.2 to 4. Mobile/static previews updated. Six renderer tests, 43 geometry tests, preview settings tests, affected formatting and Mobile/Wear builds passed (apex-contour-fixed2h-final.log). Mobile installed; Watch deployment awaits Wi-Fi debugging (old port 34823 refused).

- ApeX 0.6.25 (33): pattern knockout exists only in slot 7's image format; exactly eight selectable slots remain. Shared renderer has solid range lines, a subtle target band, right-aligned duration and no numeric threshold lane. Static/Mobile previews follow the same presentation.
- Widgets: per-instance graph contour toggle/width and dark/light color; icon symbol colors are no longer hardcoded. Therapy ring, track, icon, main/secondary text each have independent per-metric pickers. Compact delta/unit colors and very-high/very-low value colors are exposed. Existing stored instance overrides are preserved.
- Wear overview: three separate cards in IOB/COB/Basal order; shared TherapyRingGeometry and existing Wear therapy presentation/progress semantics. Changes in glucose validity, therapy events/history and theme trigger refresh. No collector changes.
- Green scoped tests: graph native rendering, ApeX DOM geometry, widget configuration/presentation, Wear overview and normalized/missing progress. Builds, affected ktlint/detekt, WFF preparation and watchface lint passed. Logs: apex-widget-wear-verified.log, apex-widget-wear-install-ready.log, wear-cards-final.log in the local temporary directory.
- Mobile, ApeX and Wear installed successfully. Watch reconnected at 192.168.178.68:33715; model SM-L705F verified. WearActivity resumed successfully; native screenshots confirm separate IOB/COB progress cards, values and icons. Rotation lock remains 0. Final Basal/ApeX screenshot inspection was interrupted by an incoming call; no call controls were touched.
