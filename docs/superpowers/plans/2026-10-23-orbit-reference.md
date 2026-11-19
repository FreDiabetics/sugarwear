# Orbit Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. Keep changes scoped;
> user explicitly requests autonomous execution and only necessary checks.

**Goal:** Separate reference-derived analog face with native settings, ApeX graph and indicators.
**Architecture:** Resource-only WFF; deterministic asset/layout generator; existing providers.
**Tech Stack:** WFF XML, Android Gradle, Python standard-library contract tests.
**Spec:** ../specs/2026-10-23-orbit-reference-design.md

## Global constraints

- Do not edit reference APK/assets or existing dirty unrelated files.
- No changes to clinical data, collectors, resolver or source freshness.
- Own package, reference remains installed. Reuse first-party assets by build-time sync.
- Do not invent provider values, weather or glucose history.
- No claim of exact reference parity until actual comparison.

## Review focus

- Ranged values: equal/inverted ranges cannot divide by zero or show a false fraction.
- Provider text and icons must remain inside the corresponding selection bounds.
- Graph/Arc modes must not produce overlapping or duplicate selectable slots.
- AOD hides seconds/graph and respects selected ambient detail mode.
- Changes to ApeX shared assets must propagate without duplicated maintained originals.

## Tasks

- [x] 1. Read-only reference/editor inventory and screenshots, device/package verification.
- [ ] 2. New module and deterministic generator. First add contract tests that fail
  because module output is absent; then implement six provider slots + graph slot,
  three independent palettes, dynamic bars, indicators, hand/second/index/AOD controls.
- [ ] 3. Reference arc/date/weather/moon and visible variant inventory; reconcile
  any option not implemented before asserting reference completeness.
- [ ] 4. Native preview, Mobile selection/deployment integration without reordering
  current index 0 ApeX / 1 Vigil. Verify configuration references and resource existence.
- [ ] 5. Run contract suite, WFF validator and scoped Android build/lint. Inspect rendered
  device output, configuration switching, AOD, indicators and reference alignment.
- [ ] 6. Install verified package on Ultra; report actual evidence and remaining gaps.

## Ledger

- 2026-10-23: main is heavily dirty from existing requested changes. Work in place
  per user project preference, isolate changes in new module/tools/docs; no bulk commit.
- Reference editor inspected read-only through 25 categories; source colors/settings
  not saved. Current face package and screenshot verified on physical Ultra.
- Native editor reference: Primary and Secondary each contain eight palettes. Primary
  triplets sampled directly from screenshots, stored in `generate_orbit.py`. Tertiary
  inventory extends from Graphite through the material/vivid palettes; shared ApeX
  palette reused initially, exact ordering/differences not yet reconciled.
- Task 2 baseline implemented: standalone module, first-party hand asset sync, seven
  slots (six reference positions plus CGM image), separate themes, arcs/date/weather/
  moon, marker and gradient controls, index/night/seconds/AOD switches. Three focused
  contract tests RED -> GREEN; official WFF4 schema valid; initial release build and
  lint succeeded. Build result is not reference parity or hardware proof.
- Follow-up fixes: corrected radians in marker transforms; proper CCW lower text;
  background-glow alpha cannot override itself to full opacity. Rebuild after these.
- OPEN before completion/installation: reference's full extra hand inventory, six
  presets, weather icon packs and launch actions, exact tertiary ordering/colors,
  typeface/cardinal geometry, richer forecast arc, headless/native preview, Mobile
  card/push integration, device rendering/ambient/editor tests. Do not label these
  done or install this baseline as a finished copy.
- 2026-10-23 device state: Ultra disappeared from ADB/mDNS during implementation.
  Phone remains reachable. Reference editor was exited using Cancel; original
  Ultra 2 remains active. No Orbit installation or activation performed.

### Later evidence and corrected inventory (supersedes initial observations)

- User reconnected Ultra, serial `adb-RFAY12MBZ8X-AVH2AE._adb-tls-connect._tcp`,
  SM-L705F, API 36. Read-only reference APK copied to TEMP/ultra2-reference.apk
  solely for measurements. No executable/layout XML or proprietary graphics imported.
- All THREE palettes have **74** options, not eight. The initial editor had scrolled
  to its last eight entries. Numeric triplets/order recorded in orbit_palettes.csv,
  used by all three independent configurations. This task is implemented.
- Added official Google Fonts Inter Tight, OFL text bundled in APK; provenance in
  watchfaces/sugarlicious-orbit/FONT-SOURCES.md. Font not extracted from reference APK.
- User explicitly requested canonical ApeX number file. Numeric 12/6/9 now use
  MASK regions from the unchanged canonical bitmap, scaled 1.7. The rotated source
  nine is rotated back 90 degrees around its own center. New regression RED -> GREEN.
- Native Orbit rendering exposed two problems that schema alone missed: curved
  bounds erroneously emitted as boxes; bounding arc diameter denotes outer diameter,
  not the track centerline. Fixed and covered by emitted-boundary contract.
- Samsung WFF4 did not resolve dynamic configuration color lists in gradient
  attributes/functions. Replaced with literal alpha-gradient over theme-colored
  vivid/light strokes, same compositing principle as reference. Fresh native log
  after reactivation showed no matching Wrong-parameter/Invalid/ParseException.
- Current 4 contract tests pass. WFF4 schema passes. Scoped release build/lint pass
  (TEMP/orbit-gradient-native.log). Latest Orbit 0.1.0 installed successfully as a
  separate package and activated temporarily for screenshots, then ORIGINAL Ultra 2
  restored (Favorite 185). Orbit development favorite 238 remains installed.
- No Mobile/Wear app updated by this task. No collector/resolver changes. No commits,
  cleanup, PR, merge or push. No claim of full reference parity.
- Still OPEN: all reference hand variants (15 total plus additional ApeX options),
  six presets, weather icon packs/launch actions/forecast layout, native preview
  fidelity, complete provider formats in inner slots, graph/marker/gradient editor
  interaction and AOD proof, Mobile card/push integration. First-party draft preview
  remains an approximation; do not present it as pixel-identical or finalize release.

### Continuation: native check and revised configuration budget

- Generated 15 first-party hand pairs plus three canonical ApeX choices, six
  presets, three weather symbol packs, four weather actions and forecast/moon rendering.
- User removed Night mode. Removed its configuration and all tint consumers;
  a separate Arc/Graph selector now controls visibility, while Arc retains its
  Date/Weather/Moon selection. WFF4 stays within 20 configuration groups.
- Fixed unnamed action Groups rejected by the WFF schema.
- PhoneBatteryComplication is SHORT_TEXT-only in the actual Wear manifest;
  corrected slot 1's previously invalid default RANGED_VALUE request.
- Five generator tests, WFF4 schema, release assembly and release lint pass.
  Installed Orbit successfully on SM-L705F and activated Favorite 238.
- Native screenshot: TEMP/orbit-current.png. Upper-right remains empty on this
  existing favorite; default policy correction alone is NOT hardware proof of
  resolution. Investigate persisted provider binding/provider response next.
- Remaining visual placement, preview/catalog integration and full fidelity
  checks remain open. No claim of completed reference parity or WFF5 migration.

### Superseding completion ledger (0.1.2)

- Mobile card, preview, Wear push packaging, catalog and CI integration completed.
- All 15 reconstructed and three ApeX hand options, themes, presets and weather
  variants implemented. Editor thumbnail size corrected to 300px (Samsung limit).
- Seven real slots only; upper-right default is platform watch battery RANGED_VALUE.
  Fresh favorite 240 resolves the old cached development binding. Installed and
  visually verified upper-right range/icon, segmented fill and inward longer marker.
- Lower-left round remainder has rounded caps and a real moving gap. Selection arcs
  no longer overlap. Graph is explicitly hidden in AOD after slot hoisting.
- 12 generator checks now pass, default WFF validator selects manifest format;
  preceding full gate passed Mobile 545 and Wear 104 tests, builds, lint, ktlint,
  detekt, WFF and push validation. Latest segment-only gate also passed.
- Version 0.1.2 installed on Watch; corresponding Mobile/Wear builds installed.
  This supersedes earlier 'no app updated' and favorite-238 notes above.
- Reference pixel identity across every theme/provider is NOT claimed. No commits,
  PR, unrelated cleanup or collector/resolver changes. WFF4 retained intentionally.

### Follow-up: dedicated Orbit graph (0.1.3)

- Separate service/catalog variant, shared canonical renderer; narrower/taller frame.
- Upper background annuli conditional on Arc mode. New slot 8 prevents stale
  favorite binding to ApeX. Existing ApeX graph stays unchanged.
- New regression covers provider bitmap, Apex dimensions, update registration,
  frame aspect and Arc-only panels. Final build/device evidence recorded below.

#### Follow-up verified

- 12 generator tests; WFF4 schema; 26 targeted unit tests (graph density/profile,
  update planner, catalog presentation and native static previews) pass.
- Mobile/Wear debug assembly and lint, Complications lint, relevant ktlint/detekt,
  Orbit release lint and packaged push validation pass. Final log:
  TEMP/orbit-graph-final-retry.log, BUILD SUCCESSFUL (469 tasks).
- Existing BasalDetailPresentation formatting corrected mechanically by ktlint;
  no calculation changed. No tests/checks disabled.
- Mobile, Wear and Orbit installed successfully on explicit SM-S948B/SM-L705F
  serials. Orbit package reports versionCode 4/versionName 0.1.3, favorite 240 active.
- Native screenshot TEMP/orbit-graph-013.png confirms taller dedicated graph,
  no upper background annuli, preserved upper-right segmented complication.
  Bounded AndroidRuntime error log empty. No user preference values changed for
  this check; the already-selected Graph mode remained selected.

### Follow-up 0.1.4: full-bleed upper semicircle and live primary theme

- Removed the rounded rectangular contour and replaced frame/mask with the full
  upper inner semicircle, radius 165, bounds (91,91,330,165). Background stays black.
- Orbit bitmap 660x330 matches display aspect. Its inscribed linear plot preserves
  every real dot inside the mask; no bending of equal glucose levels. Guides reach
  the curved mask. ApeX default rendering remains unchanged.
- Graph tint reads native primary.0 rather than the independent tertiary palette.
  Native editor screenshots orbit-theme-editor.png and orbit-theme-test.png show
  the graph changing from Neon Orange to Oak Brown with the primary selection.
- 12 generator checks, WFF4 schema, 10 shared graph renderer tests, 2 provider image
  tests, affected ktlint/detekt, Mobile/Wear assembly, Orbit release lint and push
  validation passed. TEMP/orbit-semicircle-final.log: BUILD SUCCESSFUL, 369 tasks.
- Mobile, Wear and Orbit installed. Watch reports 0.1.4/code5. Native screenshot
  TEMP/orbit-semicircle-014.png confirms no contour/corner gaps and theme-colored
  dots/guides. Bounded AndroidRuntime error output empty.
- During the native theme check the editor continued changing pages/options;
  stopped UI input and asked whether user was operating it to avoid overwriting
  their selection. Do not claim all native preferences were restored until verified.

- User confirmed they were operating the editor during this check; do not undo
  their subsequent selections. Continue with requested hand correction first.

### Queued after hand correction and installation

Hand correction completed first: 0.1.5/code6 installed successfully on Watch,
with updated Wear package and Mobile preview. All 15 Ultra 2 hand pairs now use
the user-requested original transparent strips with unmodified alpha/silhouette,
padded at native (200,0) into 450x450, centered on (225,225). Original tint inserts
are retained for style6 and premium7 (labelled Ultra 2); three ApeX sets unchanged.
Orbit-specific seconds use the original 3px line proportions and 225px pivot.
All 18 picker previews and Mobile face preview regenerated. Fourteen targeted
tests and WFF4 validation pass; release lint, push validation and affected builds
pass (TEMP/orbit-hands-final.log). Sources/provenance documented separately;
no redistribution license assumed, no commit/push performed.

- Refine all recently renewed Mobile/widget settings scroll behavior: large title
  collapses into normal title above first card, then scrolls out; back stays beside
  title until overlapping cards, then circular pinned background. Last card must be
  scrollable all the way to the header (sufficient bottom scroll extent).
- Theme choice: Samsung-style illustrated Light/Dark radio card plus lower
  "Geräteabhängig" switch for system mode. Keep explicit Light/Dark editing targets
  in Colors/Appearance; those select palettes, not display mode.
- Replace every changed-value textual reset action with consistently placed One UI
  reset/redo icon, including all settings types; retain accessible descriptions.
- Glucose units: screenshot-style horizontally separated choices/radios, no switch.
- Inspect English GB/US feasibility. User explicitly permits deferring/discarding
  language expansion if not straightforward; do not ship partial translated menus.
