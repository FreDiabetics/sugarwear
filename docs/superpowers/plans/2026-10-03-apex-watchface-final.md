# ApeX Watchface Final Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore ApeX and ship corrected device-safe rings, a complete bottom glucose ranged-value renderer, an aspect-correct graph, and three selectable hand packages.

**Architecture:** Restore only the historical ApeX WFF product boundary, then make the declarative XML consume the existing Sugarlicious complication providers. Keep dynamic graph generation in `GlucoseGraphComplication`; test the WFF as a parsed XML artifact plus existing preview/package integration.

**Tech Stack:** Android Gradle Plugin, Wear OS Watch Face Format XML, Kotlin/JUnit, PowerShell WFF validation tools

**Spec:** `docs/superpowers/specs/2026-10-03-apex-watchface-final.md`

## Global Constraints

- Base all work on current `origin/main`.
- Do not restore the retired legacy watchface matrix.
- Preserve provider class/package identities and central validated data flow.
- Keep application ID `app.aapswear.watchfacepush.analog`.
- Keep the package code-free and Wear OS 5/6/7 compatible.
- Do not fabricate medical history or values.

## Review Focus

- A `RANGED_VALUE` payload that contains an icon must visibly render the icon in its own renderer.
- Zero-span ranged values must not introduce an invalid divide-by-zero expression.
- Every progress track must share exact geometry with its value arc.
- Graph images with the provider's native aspect ratio must not be stretched.
- All three hand styles must reference complete resources and remain selectable after process/watch restart through WFF persistence.

---

### Task 1: Restore the isolated ApeX product boundary

**Files:**
- Restore: `watchfaces/sugarlicious-analog/**`
- Modify: `settings.gradle.kts`
- Modify: `app-mobile/build.gradle.kts`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWatchScreen.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/WatchFacePushController.kt`
- Modify: `app-wear/src/test/kotlin/app/aapswear/wear/WatchFacePushControllerTest.kt`
- Modify: `tools/verify-codefree-watchfaces.ps1`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/SugarliciousAnalogPreviewGeometryTest.kt`

**Interfaces:**
- Consumes: historical module at parent of removal commit `52221a64`
- Produces: buildable `:watchfaces:sugarlicious-analog` and selectable `sugarlicious_analog.apk`

- [ ] Restore only ApeX files and add a failing packaging test showing that current main does not produce/select ApeX.
- [ ] Run the focused test and confirm it fails because ApeX is absent.
- [ ] Restore module registration, build dependency, catalog/push wiring and code-free validation entry.
- [ ] Run the focused test and module assemble to confirm both pass.
- [ ] Commit the restored product boundary.

### Task 2: Correct ring geometry and bottom ranged-value rendering

**Files:**
- Modify: `watchfaces/sugarlicious-analog/src/main/res/raw/watchface.xml`
- Modify: `app-mobile/src/test/kotlin/app/aapswear/mobile/SugarliciousAnalogPreviewGeometryTest.kt`

**Interfaces:**
- Consumes: restored ApeX WFF module from Task 1
- Produces: shared outer ring geometry and complete bottom renderer defined by the spec

- [ ] Add failing XML behavior tests for outer diameter `388`, stroke `22`, sweep `42`, matched tracks, bottom diameter `124`, stroke `16`, start `232`, sweep `256`, and bottom icon presence inside `RANGED_VALUE`.
- [ ] Run the focused tests and confirm each fails against the restored historical XML.
- [ ] Implement shared device-safe geometry and the bottom track/value/icon composition.
- [ ] Run focused tests and WFF validator; confirm both pass.
- [ ] Commit the geometry fix.

### Task 3: Restore graph contract and three hand packages

**Files:**
- Modify: `watchfaces/sugarlicious-analog/src/main/res/raw/watchface.xml`
- Restore: `watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi/*hand*.png`
- Modify: `watchfaces/sugarlicious-analog/src/main/res/values/strings.xml`
- Modify: `app-mobile/src/test/kotlin/app/aapswear/mobile/SugarliciousAnalogPreviewGeometryTest.kt`

**Interfaces:**
- Consumes: `GlucoseGraphComplication` SMALL_IMAGE output and Task 2 geometry
- Produces: aspect-correct graph renderer and WFF-persisted `handStyle` with three complete options

- [ ] Add failing tests for the graph provider/type/bounds and all three hand-style resource triplets.
- [ ] Run focused tests and confirm they fail because current authoritative ApeX lacks hand styles and the expected final graph contract.
- [ ] Restore the three hand options/assets and ensure the graph remains provider-driven and aspect-correct.
- [ ] Run focused tests and module assemble; confirm both pass.
- [ ] Commit graph and hand packages.

### Task 4: End-to-end package validation

**Files:**
- Modify if required by failures: only files in Tasks 1-3
- Test: relevant existing Wear/Mobile/complication suites and WFF validation scripts

**Interfaces:**
- Consumes: completed ApeX module and existing complication providers
- Produces: validated signed code-free Release APK and installation artifact

- [ ] Run focused unit tests, compilation, Release assemble, lint, detekt and ktlint checks for affected modules.
- [ ] Run `tools/verify-codefree-watchfaces.ps1`, XML validation, signing verification and `git diff --check`.
- [ ] Inspect the final APK for zero DEX files and verify the selectable bundled APK matches the newly built module artifact.
- [ ] Fix any contextual warnings/errors through test-first regression coverage.
- [ ] Commit final gate corrections and record CODE-VALIDIERT, BUILD-VALIDIERT and HARDWARE-TEST OFFEN separately.
