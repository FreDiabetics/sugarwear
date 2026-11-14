# AndroidAPS Display Contract and CGM Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every Sugarlicious non-G7 surface reproduce AndroidAPS values, visible precision, and units while standardizing age text and repairing Mobile CGM notification, Tile, and double-arrow geometry.

**Architecture:** Preserve AndroidAPS display semantics at the adapter boundary, carry them through the existing serializable canonical state, and expose shared pure presentation formatters to every renderer. Keep layout and glyph sizing in their existing renderers, but make their geometry deterministic and tested rather than applying independent translations or square normalization.

**Tech Stack:** Kotlin, kotlinx.serialization, Android DataStore, Wear Data Layer, Jetpack Compose, RemoteViews XML, Wear ProtoLayout, Robolectric, bitmap assertions, Gradle, ktlint, detekt, Android lint.

**Spec:** `docs/superpowers/specs/2026-09-28-aaps-display-contract-and-cgm-layout-design.md`

## Global Constraints

- AndroidAPS is authoritative for every non-G7 data stream named in the spec.
- Visible value, sign, rounding, and unit must match AndroidAPS on every Sugarlicious surface.
- Valid zero and negative values are never converted to unavailable data.
- The direct G7 collector and every CGM value produced by it remain unchanged.
- Every compact age is exactly `<whole minutes>m`.
- Double arrows retain full shared-vector aspect ratio and glyph fill in notifications and complications.
- Existing quality gates may not be weakened or bypassed.

## Review Focus

- Payload values supplied as numeric strings, negative zero, and high precision must survive parsing and render with AndroidAPS precision.
- Missing fields in a later payload must not overwrite an explicit valid zero or negative value during state merging.
- A CGM source change must not replace AndroidAPS therapy, pump, profile, battery, target, prediction, or event fields.
- Long glucose/delta/age combinations at large font scale must remain inside collapsed and expanded notification bounds.
- Double arrows with custom size/offset settings must retain their native aspect ratio without clipping.

---

### Task 1: Canonical AndroidAPS display contract

**Files:**
- Modify: `core-model/src/main/kotlin/app/aapswear/model/TherapyDisplayState.kt`
- Create: `core-model/src/main/kotlin/app/aapswear/model/AapsDisplayFormatter.kt`
- Modify: `data-source-aaps/src/main/kotlin/app/aapswear/datasource/aaps/AapsPayloadAdapter.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DisplayHistoryAccumulator.kt`
- Test: `core-model/src/test/kotlin/app/aapswear/model/AapsDisplayFormatterTest.kt`
- Test: `data-source-aaps/src/test/kotlin/app/aapswear/datasource/aaps/AapsPayloadAdapterTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/DisplayHistoryAccumulatorTest.kt`

**Interfaces:**
- Consumes: current AndroidAPS payload fields and `TherapyDisplayState` domain values.
- Produces: `AapsDisplaySemantics`, `AapsDisplayFormatter.format(field, value, semantics)`, and canonical state fields that preserve AndroidAPS unit/precision metadata.

- [ ] **Step 1: Add failing adapter and formatter tests**

Assert negative, zero, positive, numeric-string, missing, non-finite, and high-precision values for IOB/bolus IOB/basal IOB, COB/future carbs, activity, basal/temp basal, targets, DIA, reservoir, and batteries. Assert the expected AndroidAPS-visible unit and precision for every field.

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `./gradlew :core-model:test :data-source-aaps:test --tests '*AapsDisplayFormatterTest' --tests '*AapsPayloadAdapterTest'`

Expected: failure because display semantics and shared formatter do not yet cover every field.

- [ ] **Step 3: Add serializable display semantics and central formatting**

Implement focused serializable metadata for source unit and precision without storing pre-rendered localized strings. `AapsDisplayFormatter` must reject only null/non-finite values and must never clamp sign or replace a valid zero.

- [ ] **Step 4: Preserve explicit values during history merging**

Add tests proving an explicit `0.0` or negative incoming value wins, while an absent field follows the existing retention/freshness rule. Ensure no G7 state or G7 model is modified.

- [ ] **Step 5: Run focused suites and commit**

Run: `./gradlew :core-model:test :data-source-aaps:test :app-mobile:testDebugUnitTest --tests '*DisplayHistoryAccumulatorTest'`

Commit: `fix: preserve AndroidAPS display semantics`

### Task 2: Transport, persistence, and resolver parity

**Files:**
- Modify: `wear-storage/src/main/kotlin/app/aapswear/storage/TherapyStateStore.kt` only if migration/default handling is required
- Modify: `wear-storage/src/main/kotlin/app/aapswear/storage/PhoneTherapyStateStore.kt` only if migration/default handling is required
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/MobileCanonicalCgm.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/AapsStatusReceiver.kt`
- Test: `wear-storage/src/test/kotlin/app/aapswear/storage/TherapyStateStoreTest.kt`
- Test: `wear-protocol/src/test/kotlin/app/aapswear/protocol/WearProtocolTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/MobileCanonicalCgmTest.kt`

**Interfaces:**
- Consumes: Task 1 canonical values and `AapsDisplaySemantics`.
- Produces: byte-for-byte-equivalent non-G7 clinical values and equal display semantics after serialization, persistence, transport, and CGM resolver changes.

- [ ] **Step 1: Add failing round-trip tests**

Round-trip a state containing every scoped stream, negative/zero values, predictions, and therapy events through serialization, stores, and Wear protocol. Assert complete equality.

- [ ] **Step 2: Add failing resolver isolation test**

Switch the CGM candidate/source while asserting AndroidAPS therapy, target, loop, pump, profile, device, prediction, and event fields remain identical. Include missing-later-field and stale-state cases from Review Focus.

- [ ] **Step 3: Run focused tests and verify RED**

Run: `./gradlew :wear-storage:test :wear-protocol:test :app-mobile:testDebugUnitTest --tests '*MobileCanonicalCgmTest'`

- [ ] **Step 4: Implement minimal compatibility/migration and resolver fixes**

Reuse `TherapyDisplayState` serialization and current stores. Add only schema defaults/migration needed for Task 1 metadata; do not add a parallel state store or change G7 collector persistence.

- [ ] **Step 5: Run focused suites and commit**

Commit: `fix: preserve AndroidAPS state across transport`

### Task 3: Shared presentation parity and compact age

**Files:**
- Modify: `core-model/src/main/kotlin/app/aapswear/model/TherapyDisplayFormatter.kt`
- Modify: `core-model/src/main/kotlin/app/aapswear/model/ComplicationPresentation.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousOverviewScreen.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWidgets.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/TherapyHeroIndicators.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/WearActivity.kt`
- Modify: `app-wear/src/main/kotlin/app/aapswear/wear/SugarliciousTiles.kt`
- Modify: relevant watchface presentation adapters that still format scoped values locally
- Modify: preview strings in `app-mobile/src/main/res/values/strings.xml`
- Test: `core-model/src/test/kotlin/app/aapswear/model/TherapyDisplayFormatterTest.kt`
- Test: `core-model/src/test/kotlin/app/aapswear/model/ComplicationPresentationTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/TherapyHeroIndicatorsTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/WidgetPresentationTest.kt`
- Test: `app-wear/src/test/kotlin/app/aapswear/wear/SugarliciousTilesTest.kt`

**Interfaces:**
- Consumes: Task 1 shared formatter and Task 2 round-tripped state.
- Produces: shared per-field presentation strings and `TherapyDisplayFormatter.ageMinutes(timestamp, now): String` returning only `Nm` or unavailable.

- [ ] **Step 1: Add failing cross-surface matrix tests**

For every scoped field, assert identical text across Mobile, widget, Wear activity presentation, Tile, watchface presentation, and complications. Include zero, negative, numeric-string-origin, stale, and unavailable cases.

- [ ] **Step 2: Add failing system-wide age tests**

Assert `0m`, `2m`, and `17m`; scan production resources/source outputs to reject `vor N`, `N min`, `Minuten`, `her`, and `N min alt` for compact data ages.

- [ ] **Step 3: Run tests and verify RED**

Run: `./gradlew :core-model:test :app-mobile:testDebugUnitTest :app-wear:testDebugUnitTest :complications:testDebugUnitTest`

- [ ] **Step 4: Route all renderers through shared presentation**

Remove local per-surface rounding, unit conversion, sign filtering, and age prose. Preserve diagnostic timestamps and non-age prose that do not display a data age.

- [ ] **Step 5: Run focused suites and commit**

Commit: `fix: unify AndroidAPS presentation across surfaces`

### Task 4: Mobile CGM Tile space and age visibility

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousOverviewScreen.kt`
- Modify: the existing CGM Tile/header layout helper identified by `OverviewInlineHeaderLayoutTest`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/OverviewInlineHeaderLayoutTest.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/OverviewGraphRegressionTest.kt`

**Interfaces:**
- Consumes: Task 3 compact age and existing trend renderer.
- Produces: CGM Tile header without delta icon and with a wider textual delta/age region.

- [ ] **Step 1: Add failing layout contract tests**

Assert the delta icon is absent, textual delta remains, `17m` is visible at the narrowest supported width and large font scale, and glucose/trend vertical alignment is unchanged.

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew :app-mobile:testDebugUnitTest --tests '*OverviewInlineHeaderLayoutTest' --tests '*OverviewGraphRegressionTest'`

- [ ] **Step 3: Remove the delta icon and reallocate its width**

Modify only the existing CGM Tile composition/layout specification. Do not change graph geometry, G7 collector Tile code, or trend semantics.

- [ ] **Step 4: Run tests and commit**

Commit: `fix: keep Mobile CGM Tile age visible`

### Task 5: Notification layout and full-size double arrows

**Files:**
- Modify: `app-mobile/src/main/res/layout/notification_sugarlicious_collapsed.xml`
- Modify: `app-mobile/src/main/res/layout/notification_sugarlicious_expanded.xml`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/PersistentBridgeService.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/NotificationTrendRenderer.kt`
- Modify: `complications/src/main/kotlin/app/aapswear/complications/TrendComplicationIcon.kt`
- Test: `app-mobile/src/test/kotlin/app/aapswear/mobile/PersistentBridgeServiceTest.kt`
- Create: `app-mobile/src/test/kotlin/app/aapswear/mobile/NotificationLayoutContractTest.kt`
- Modify: `complications/src/test/kotlin/app/aapswear/complications/TrendComplicationIconTest.kt`

**Interfaces:**
- Consumes: Task 3 age text and shared `TrendVisuals`/`TrendDrawableResources` assets.
- Produces: deterministic notification content bounds and aspect-ratio-aware double-arrow bitmap sizing for notifications and complications.

- [ ] **Step 1: Add failing notification bound tests**

Inflate collapsed and expanded layouts at narrow supported widths and large font scales. Bind longest glucose/delta/age strings and assert value, trend, and metadata bounds stay between the left system-icon reserve and graph without clipping or ellipsis.

- [ ] **Step 2: Add failing double-arrow bitmap tests**

For `DOUBLE_UP` and `DOUBLE_DOWN`, assert native `125:60` aspect ratio, intended arrow height, occupied-pixel bounds without extra transparent padding, and no clipping in collapsed notification, expanded notification, and complication sizes. Assert other five arrows remain unchanged.

- [ ] **Step 3: Run tests and verify RED**

Run: `./gradlew :app-mobile:testDebugUnitTest --tests '*Notification*Test' :complications:testDebugUnitTest --tests '*TrendComplicationIconTest'`

- [ ] **Step 4: Replace translation-based notification positioning with bounded layout geometry**

Move the complete glucose block left and center it in the actual free region. Expand the text container to available width, preserve graph bounds, and prevent clipping in both layouts.

- [ ] **Step 5: Preserve native double-arrow geometry**

Allocate width from the shared aspect ratio at the configured height. Bypass square normalization and transparent-padding shrinkage only for double arrows; keep single/diagonal/flat rendering unchanged.

- [ ] **Step 6: Run focused suites and commit**

Commit: `fix: restore notification space and double-arrow size`

### Task 6: Regression, G7 isolation, build, and deployment

**Files:**
- Modify tests only if a verified regression exposes a missing assertion.
- Do not modify direct G7 collector production files unless a build break is mechanically caused by the new shared API; no G7 behavior change is allowed.

**Interfaces:**
- Consumes: Tasks 1-5.
- Produces: release-ready Mobile and Sugarlicious Wear artifacts with documented hardware-test status.

- [ ] **Step 1: Run change-focused suites**

Run all adapter, model, persistence, resolver, transport, Mobile presentation, Wear Tile, notification, complication, and bitmap tests.

- [ ] **Step 2: Run G7 isolation regression suites**

Run `:dexcom-g7:test`, `:g7watch:testDebugUnitTest`, and direct-G7 complication tests. Compare behavior-sensitive fixtures to confirm no G7 collector or G7 CGM output changed.

- [ ] **Step 3: Run complete quality gate**

Run: `./gradlew test assembleDebug assembleRelease lint detekt ktlintCheck --no-daemon`

Expected: every task succeeds without weakening any gate.

- [ ] **Step 4: Verify repository state and commit any test-only follow-up**

Run `git diff --check`, inspect the complete branch diff, and confirm no unrelated or G7 behavioral changes.

- [ ] **Step 5: Install validated debug builds**

Install `app-mobile` on the identified phone and `app-wear` on the identified Wear OS device with data-preserving replacement. Verify package IDs/version codes after installation.

- [ ] **Step 6: Report validation honestly**

Separate `CODE-VALIDIERT`, `BUILD-VALIDIERT`, and `HARDWARE-TEST OFFEN`. Hardware validation must cover real AndroidAPS values, negative/zero therapy values, narrow Tile/notification rendering, both notification states, and both double-arrow directions.
