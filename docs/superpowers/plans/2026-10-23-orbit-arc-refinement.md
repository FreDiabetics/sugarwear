# Orbit Arc Refinement Implementation Plan

Use executing-plans inline. Spec: ../specs/2026-10-23-orbit-arc-refinement-design.md
Goal: correct Orbit's Arc artwork, graph coexistence and focused editor choices.
Architecture: existing Python WFF generator, resource imports, existing previews.
Constraints: dirty main retained; no collector/resolver changes or publication.

- [ ] Add generator tests for reduced choices, original weather resources, normalized
  gradient angles, tight selector bounds, graph-mode center/battery clipping.
  Run unittest discover tools/tests/test_orbit_face.py; observe failures first.
- [ ] Import allowlisted weather artwork with source/hash manifest; replace synthetic
  symbols and retain per-hour condition availability/day/night checks.
- [ ] Implement reduced choices/labels, geometry, background and gradients.
- [ ] Run focused tests, WFF 5 schema, Orbit build/lint and preview generation.
- [ ] Install Orbit and refreshed Mobile preview; inspect actual native editor and
  render, record evidence/limits. Do not claim absent Google app launch tested.

Review focus: old choice IDs; day/night/unknown conditions; 0/100% wraparound bars;
graph versus Arc mode layering; native bounds and whether color changes are visible.

Ledger: weather API confirmed condition data only. Google pack is explicitly named
Google Weather in user-owned Ultra 2 reference resources; Samsung named artwork is
available in installed WeatherWatch.apk. No guessed vendor artwork is labelled original.

2026-10-23 follow-up: weather geometry now measured from original 450px reference:
35-degree spokes, image center 227/141, 36px canvas, shared outer caption radius
125.5px with alternating hour/temperature and device font. Version 0.1.9 installed.

Editor consolidation 0.1.10: AOD and Indizes/Indexfarbe labels; Arc contains
date/weather/moon/graph; Progress Bars combines solid/gradient/segments and dot
variants using one gradient direction (no inverted option). Base settings precede
palette and content settings. Theme index ink uses vivid tertiary instead of the
almost-white light tone. Full-dial background tint reduced from 48 to 20 alpha.
New contract failed before implementation; all 20 focused tests, WFF5 schema,
assembleDebug and lintDebug pass. Old independent configuration references absent.
New Arc/Progress Bars keys intentionally replace independent choices; prior theme
and hand keys retained. The native editor cannot migrate the retired selections.
Final live comparison is pending: user actively operating phone; no UI takeover.
Google Weather launch not tested because app absent. Background/gradient and new
index color visual fidelity not claimed from schema/build checks alone.

0.1.12 follow-up: user confirmed the remaining outward placement persists after
reopening the editor. Weather captions and image centers moved radially inward
by 8 design pixels; vendor artwork size unchanged. Latest user feedback approves
moon positioning, so moon center/caption radius retained, with moon diameter
reduced from 34 to 28 reference pixels only. Moon captions remain TextCircular;
native curved-text appearance still requires direct confirmation. New geometry
contract observed failing before implementation, then 22 focused tests, WFF5
schema, build and lint passed. Watch disconnected before installation attempt;
do not conflate validated APK with installed or visually accepted result.
Automatic mDNS reconnect succeeded at 192.168.178.68:36357. Installation succeeded;
device package reports versionCode 13 / versionName 0.1.12. Visual acceptance remains
open, particularly curved moon captions; no user settings changed for this test.

0.1.13: user requests Index/Indexfarbe, Sekundenzeiger (white/tinted/red, all
smooth), removes hand tint switch. Hour/minute art remains untinted; middle second
choice uses medium tertiary accent, not near-white light tone. Background alpha
96 instead of ineffective 20. Weather symbols retain 36-reference-pixel box but
now share moon centers exactly (225/140); moon diameter remains 28. Weather text
uses formatted numeric Templates (%s:00, %s%s, %s°), not mixed numeric/string
addition. Captions rotate around the common center with positive wrapped angle
intervals. Battery graph track/fill now 94..266 degrees with ROUND caps: four-degree
inset exceeds asin(3.41/55.75), so whole caps remain below graph edge. Existing
graph center disk remains lower semicircle. New contracts RED then GREEN; all 24
Orbit tests, WFF5 schema, build and lint pass. Installed package verified code14 /
0.1.13. Final weather text/device view pending user opening weather; no editor
settings saved or user gestures overridden. No Google Weather launch claim.

0.1.14: explicit user request makes weather hour/temperature/unavailable captions
share the moon caption radius exactly (125.5 reference pixels). Both branches now
consume ARC_CAPTION_RADIUS; vendor icon dimensions unchanged. Regression observed
RED against previous differing radii, then all 24 tests, WFF5 schema, build and lint
passed. Installed and verified code15 / 0.1.14. Read-only device screenshot
Temp/orbit-weather-align.png, taken immediately before this install, proves the
0.1.13 formatted weather hour labels now render curved (10:00 through 14:00).
Final 0.1.14 text-track visual comparison not independently confirmed yet.
