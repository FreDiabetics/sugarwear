# Watchface Presets and Previews Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the old native flavor inventory with six new coordinated presets for ApeX/Orbit, refresh all three native watchface previews, and integrate all authorized pending fixes through green CI onto main.

**Architecture:** Keep the resource-only WFF rendering and the existing preset generator. Six shared design profiles produce six native flavors per face; ApeX always keeps its graph, Orbit includes graph and TIR profiles. Native fixed-time favorite previews are the source of the canonical artwork. Vigil stays in the separate SugarWear repository; shared Kotlin is updated by submodule revision, not copied.

**Tech Stack:** WFF4, Python generators/tests, Android/Wear Gradle, ADB native previews, GitHub PR/Actions.

**Spec:** Current user request in this conversation; the indicator-position complaint was explicitly retracted.

## Global Constraints
- Do not change indicator geometry.
- Preserve current user provider bindings and never reset app/collector data.
- Keep exactly six new profiles; remove previous native flavors rather than appending more.
- ApeX has only a fixed upper glucose graph; no Arc/TIR/weather selector.
- Preview hand times remain consistent; Vigil digital demo remains 22:10 without default hands/markers.
- No collector analysis or behavior changes.
- Preserve all authorized pending working changes. Never discard dirty worktrees.

## Review Focus
- Slot position must not shift when defaults or presets change.
- Private graph providers remain hidden; only the center toggles hands.
- Preview images must use native demo rendering, not actual private CGM screenshots.
- Static images and in-app/gallery references must all point to the refreshed canonical asset.
- Presets retain valid IDs/options for every palette and consolidated progress choice.

## Tasks
- [x] Add failing coverage for exactly six new flavors and retired choices.
- [x] Replace the flavor catalog with Standard, Ultra, Military, Polar, Aurora, Solar; preserve existing source defaults and provider policies.
- [x] Capture current native bindings/styles read-only, update defaults, regenerate and validate both faces.
- [x] Import native fixed-time preview images for ApeX, Orbit and Vigil; remove obsolete canonical preview layering and update all consumers.
- [x] Run complete relevant repository gates and fix errors/warnings without suppressing checks.
- [x] Install and verify on Galaxy; keep Pixel untouched.
- [ ] Commit and PR Sugarlicious, wait for green checks, merge and verify main.
- [ ] Update SugarWear's shared revision and Vigil preview, verify, PR/merge, verify main.
- [ ] Remove only merged temporary branches/worktrees; final clean synchronized main in both repos.

## Execution rulings
- Six coordinated profiles means six native choices on each of ApeX and Orbit, not twelve different designs. Vigil receives a refreshed preview only.
- Standard preserves the captured current baseline. Other profiles alter visual configuration, not underlying source logic.
- Native capture needs a working Watch debug connection; code and local verification proceed while awaiting it.

## Verification checkpoint
- 70 Python tests pass. Official WFF validator passes digital/test WFF1 and ApeX/Orbit WFF4.
- Targeted Mobile preview geometry (51 tests) and Wear provider previews (3 tests) pass after replacing obsolete removed-option assumptions and excluding hidden internal providers from public picker contracts.
- Independent read-only review of the complete pending diff found no actionable code defects.
- Complete Gradle gates passed: verifyArchitecture, detekt, ktlintCheck, all 1,080 Unit tests, assembleDebug, lint and all four release Watchface builds. Code-free APK verification passed. Seven obsolete unused Mobile icon resources were removed; Mobile lint/assemble/targeted preview tests and ktlintCheck passed again, with zero Mobile lint findings. Native preview capture and installation remain pending a new Galaxy debugging port; the old port 36701 is unreachable. No claim of refreshed image or device verification yet.

## Final measured-geometry checkpoint

- ApeX outer font29.6, middle title32/value39; same fonts/colors/provider data retained. Generic outer icons22, padded glucose52; lower-left100% has36 degrees growth room with its end anchor fixed.
- Native Galaxy screenshots confirm the opposite tick pair only (ApeX1/3), moving long tick and arc-length gradient. Orbit uses the same exporter only for7; its round glucose gauge stays unchanged, per explicit user selection.
- 74 Python checks, all1,081 Unit tests, full architecture/static/build/lint gates, official WFF validation and four code-free release APK checks pass. Independent read-only review found no actionable defects.
- Native ApeX fixed-time preview now includes all middle values and100% without ellipsis. Orbit final native capture includes its visible dark tick track,12 measured ticks, full steps text and the corrected round field. All three canonical previews are refreshed; repository integration remains in progress.

Orbit current native style captured read-only after its measured rendering check: primary270, secondary/tertiary14, transparent hands1, dialInk1, progressBars7, graph3. Provider policies already match its active assignments and are retained.
