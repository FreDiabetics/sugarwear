# Therapy widgets and icon rollout

**Goal:** Complete the approved widget changes after installing ApeX 0.6.31.
**Architecture:** Reuse per-instance widget configuration, canonical TherapyDisplayState, existing ring geometry and Mobile metabolic/basal chart drawing. No collector or resolver changes.
**Execution:** Inline, sequential; preserve the existing dirty main checkout as requested. No bulk commit or cleanup.

## Task 1: ApeX first
- Transparent range areas over the existing black frame, medium/dark threshold guides, highest WFF1-supported smooth sweep.
- Renderer and WFF geometry tests, validator, builds, installation before widget edits.
- Complete: renderer/geometry tests and builds green; installed ApeX 39/0.6.31, Mobile and Wear. Watch screenshot apex-black.png verified black areas.
- Ruling: SYNC_TO_DEVICE fails the WFF1 validator. Use supported frequency 15, preserving tick/off options and AOD behavior.

## Task 2: Widget typography and rings
- Compact glucose+graph delta white/larger/bold, units gray/smaller/bold.
- Widgets only: remove secondary ring text and controls; vertically center primary text.
- Therapy 2x2: IOB/COB above Basal, equidistant triangular centers with safe edges.
- Relevant widget renderer/configuration tests, including existing light-mode and sizing coverage.

## Task 3: Therapy 4x2 and supplied icons
- Add native provider, Samsung family large preview, update routing/configuration/settings and immediate refresh paths.
- Left therapy indicators and configurable divider; right shared IOB/COB chart, with upside-down basal overlay reused from Mobile CGM.
- Import exact supplied SVG paths; circle-backed assets in widgets, plain assets elsewhere. Replace old carbs references with COB; retain dynamic basal icon selection.
- Test provider registration, per-size settings, previews, basal parity, rendering and no secondary widget text; compile Mobile/Wear, relevant lint/static checks.
- Install Mobile/Wear and inspect actual widgets. No hardware verification claim without device evidence.

## Interface checks
- The same METABOLIC kind must reach the new provider, frame coordinator and preview renderer.
- Shared basal extraction must preserve existing Mobile CGM rendering.
- New icon names require all provider previews and runtime icon mappings to migrate together.

## Progress
- Task 1 complete.
- Task 2 in progress.
- Task 2 renderer/configuration changes implemented; scoped presentation, therapy geometry, light-mode and instance-configuration tests green. Compact delta uses white/gray defaults while preserving explicit per-instance color overrides.
- Task 3 started: 4x2 provider registered, Samsung size mask expanded, shared Mobile basal overlay extracted without changing CGM geometry; offscreen IOB/COB renderer and picker generation tests green. Large preview asset still must be generated/copied before final install; supplied icons not imported yet. Do not ship the temporary large-preview reference to the medium artwork.
- Priority interruption requested by user: marker fix first. ApeX 40/0.6.32 installed but rejected visually; latest 41/0.6.33 replaces alpha-shaded marker interior with an explicit fully opaque dark palette color, retains original fill and track, black contour within exact track diameter. 46 geometry tests, WFF validator, lint and Wear build green (apex-opaque-build.log). Installation blocked: watch 192.168.178.68:36233 now refuses connection, mDNS finds phone only. Request current watch connection port, install ApeX/Wear and inspect before resuming widgets as explicitly requested.
- 2026-10-22: Reconnected watch at 192.168.178.68:45557, installed ApeX 41/0.6.33 and Wear; native editor point mode enabled. Live screenshot apex-opaque-live2.png proves filled bars and opaque dark marker interiors, black contours and track-sized circles.
- Task 2 complete in code, 183 scoped Mobile tests green. Removed unused widget secondary-alpha helper; Wear/Mobile-app secondary values retained.
- Task 3 complete in code: exact plain and circle-backed SVG geometry imported, old shared carbs and duplicate complication icons removed and all runtime/static-preview references migrated. Dynamic basal selection unchanged. Large therapy preview generated from production renderer; its now label fits inside chart edge.
- Fresh read-only review found a real left-pane reclassification defect at short/tablet widths. Regression test failed with missing Basal; explicit DETAIL_2X2 left-pane override fixes dropping Basal and nested graphs. Worker render and preference-isolation tests pass. Review considered axis writes, worker safety, removed secondary controls, shared basal, per-instance colors, provider routing and collector scope: no other concrete findings.
- Ruling: keep existing host-driven adaptive family classification; explicitly pin only the wide therapy left pane to the three-indicator layout. Shared basal Canvas extraction preserves Mobile CGM geometry and inverted BR/TBR, no collector changes.
- Complication suite found nondeterministic preview oldest-dot exclusion caused by separate clock reads. One captured preview instant now feeds fixture and renderer; GraphImageDensityTest red-to-green without relaxing tolerance. Pending final static/build gates and Mobile/Wear installation.
- Final widget gates complete: 183 Mobile tests and 63 complication tests passed; Mobile/Wear builds, lint (zero errors; 22 Mobile/1 Wear nonblocking warnings) and Detekt succeeded in therapy-install-gates.log. Installed both packages on phone/watch, confirmed update times. Existing Homescreen capture widgets-therapy.png shows compact white/bold delta and smaller gray/bold unit plus centered one-line therapy values and new circle-backed icons. User requested further visual testing later; do not block installation on it. 4x2 production rendering/picker artwork verified offline, new provider registered on physical phone; user has not yet placed it on Homescreen.
- Latest reference supersedes dark disk: ApeX 42/0.6.34 uses one hollow black stroked circle, no marker Fill or masking layer. Outer diameter including stroke equals track thickness. In point mode the valid range bar is fully colored; only the marker position encodes value. Classic mode and invalid-range safeguards preserved. UR segment toggle cannot hide the colored continuous bar in point mode. 46 geometry tests and WFF validation/lint/build green in apex-hollow-final.log; installation underway.
- Final minor deferred: Android Lint suggests Canvas.withTranslation for the offscreen chart; current save/translate/restore is balanced and correct. Other nonblocking lint hints predate this widget change. No collector/source/resolver changes; no bulk commit, merge, push or worktree cleanup performed.
- ApeX 42/0.6.34 and refreshed Wear APK installed successfully (07:24 local device time). Update reset point mode to off. Standard native-editor long press did not open the editor on the final two bounded attempts; no alternative settings or security changes made. User explicitly deferred further visual testing. Final on-device hollow-ring visual check remains open until point mode is enabled; do not claim it was observed active. Mobile and Wear widget/icon installation is complete.
- Follow-up: user requested a much stronger hollow marker contour. ApeX 43/0.6.35 increases the black stroke to 28% of each track diameter (outer ring ~4.78 instead of1.25 WFF px). Ellipse geometry shrinks inward to preserve the exact outer diameter and an open center 44% of the diameter. Full colored bar, marker positioning, classic mode and data guards unchanged. Added visible-stroke/open-center bounds to existing geometry regression test; observed expected RED before implementation. No devices found by ADB/mDNS; last watch connection port45557 refuses connection. Hardware installation remains pending.
- Icon-size follow-up: user clarified that placement is correct and must not change; only enlarge COB to match IOB when IOB already matches the glucose detail icons. Actual cause was the old COB-only 17dp override versus shared19dp IOB/glucose-icon basis. Removed that override; affects Mobile hero and therapy widgets consistently while preserving user scaling controls. Equality test observed RED then GREEN together with therapy-icon and clipping tests. Regenerated all therapy picker sizes and legacy therapy artwork; Mobile APK rebuilt. No text/layout changes. ADB/mDNS still find no devices, so this follow-up is not installed yet.
