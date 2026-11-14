# Mobile Live Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver one canonical Sugarlicious Mobile notification with custom fallback, Android promoted live mode, Samsung Now Bar mode, freshness-boundary updates, diagnostics, tests, documentation, and direct WLAN deployment.

**Architecture:** Extract platform-neutral presentation and mode selection from `PersistentBridgeService`, then apply small Android and Samsung adapters to a standard builder in live modes. Keep the current custom graph notification intact for fallback and drive all refreshes from visible presentation revisions plus one-shot freshness boundaries.

**Tech Stack:** Kotlin, Android API 26-37, AndroidX `NotificationCompat`, coroutines/Flow, Robolectric/JUnit4, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-11-mobile-live-notifications-design.md`

## Global Constraints

- Mobile only; do not change SugarWear/G7 Collector behavior or alarm architecture.
- Preserve canonical resolver ownership and never fabricate values.
- Keep notification ID 4107, channel `sugarlicious_background`, foreground-service behavior, and existing custom fallback.
- Android capsule is glucose plus canonical trend; unknown trend has no arrow; delta is omitted there.
- Live modes have no custom `RemoteViews` or graph.
- Samsung support is independent of promoted-notification permission.
- Preserve all pre-existing uncommitted widget/dashboard changes.
- Separate code/build/install evidence from physical Now Bar/AOD validation.

## Review Focus

- A therapy-only update with unchanged glucose must refresh visible IOB/COB/basal without notification churn for invisible fields.
- A freshness boundary crossed without a new reading must remove misleading live glucose and later recover on fresh data.
- Samsung below API 36 must still receive Samsung extras while avoiding unavailable Android promotion APIs.
- Unknown trend, missing delta, and partially missing IOB/COB must produce compact text without invented symbols or zeroes.
- Toggling live mode must replace the same notification ID and must not leave custom views attached in live mode.

---

### Task 1: Canonical presentation and mode selection

**Files:**
- Create: `app-mobile/src/main/kotlin/app/aapswear/mobile/NotificationPresentation.kt`
- Create: `app-mobile/src/test/kotlin/app/aapswear/mobile/NotificationPresentationTest.kt`

**Interfaces:**
- Consumes: `TherapyDisplayState`, `FreshnessPolicy`, `TherapyDisplayFormatter`, `Build` inputs supplied as values.
- Produces: `NotificationPresentation`, `NotificationMode`, `notificationPresentation(state, now)`, `notificationMode(liveRequested, sdkInt, manufacturer)`, `nextNotificationFreshnessBoundary(state, now)`.

- [ ] Write literal tests for fresh mg/dL/mmol/L, all trends including `UNKNOWN`, missing values, signal loss/no data, mode boundaries, visible revision changes, and next freshness boundary.
- [ ] Run `:app-mobile:testDebugUnitTest --tests app.aapswear.mobile.NotificationPresentationTest` and confirm failures because the interfaces do not exist.
- [ ] Implement the minimal platform-neutral presentation and selector.
- [ ] Run the focused test and confirm it passes.
- [ ] Commit only Task 1 files.

### Task 2: Android and Samsung live adapters

**Files:**
- Create: `app-mobile/src/main/kotlin/app/aapswear/mobile/AndroidLiveNotificationAdapter.kt`
- Create: `app-mobile/src/main/kotlin/app/aapswear/mobile/SamsungLiveNotificationAdapter.kt`
- Create: `app-mobile/src/test/kotlin/app/aapswear/mobile/LiveNotificationAdaptersTest.kt`

**Interfaces:**
- Consumes: `NotificationPresentation` from Task 1.
- Produces: Android builder mutation for API 36/37, `SamsungLiveTexts`, manufacturer-gated extras, and promotion diagnostics.

- [ ] Write Robolectric tests for API 35/36/37, `FixedInt`, one-decimal `FixedFloat`, capsule trend/no unknown arrow, Samsung texts/extras/icon, non-Samsung omission, and Samsung independence from promotion permission.
- [ ] Run the focused adapter test and confirm expected failures.
- [ ] Implement both adapters with all proprietary keys contained in the Samsung file.
- [ ] Run the focused test and confirm it passes.
- [ ] Commit only Task 2 files.

### Task 3: Integrate the service, settings, and manifest

**Files:**
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/PersistentBridgeService.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt`
- Modify: `app-mobile/src/main/kotlin/app/aapswear/mobile/MainActivity.kt`
- Modify: `app-mobile/src/main/AndroidManifest.xml`
- Modify: `app-mobile/src/test/kotlin/app/aapswear/mobile/PersistentBridgeServiceTest.kt`
- Modify: `app-mobile/src/test/kotlin/app/aapswear/mobile/MainActivityTest.kt`
- Create: `app-mobile/src/test/kotlin/app/aapswear/mobile/LiveNotificationManifestTest.kt`

**Interfaces:**
- Consumes: presentation, selector, adapters, and boundary function from Tasks 1-2.
- Produces: exactly one mode-specific notification, one-shot freshness scheduling, visible-field revision updates, factual settings diagnostics, and manifest metadata.

- [ ] Replace outdated live-notification tests with failing tests proving custom fallback, no live `RemoteViews`, same ID, therapy-only visible refresh, freshness rerender/recovery, settings diagnostics, and manifest uniqueness.
- [ ] Run the three focused test classes and confirm failures for missing integration.
- [ ] Integrate the existing service without changing graph renderer, bridge, widgets, foreground lifecycle, channel, or actions; merge edits around existing dirty files.
- [ ] Run the focused tests and confirm they pass.
- [ ] Commit only intended Task 3 hunks/files without staging unrelated pre-existing changes.

### Task 4: Documentation, quality gate, review, and WLAN deployment

**Files:**
- Create: `docs/mobile/LIVE_NOTIFICATIONS.md`
- Modify tests/code only if the final review finds Important/Critical defects, with RED-GREEN evidence.

**Interfaces:**
- Consumes: completed behavior from Tasks 1-3.
- Produces: technical documentation, build artifacts, validation evidence, and installed Mobile/Wear packages when targets are reachable.

- [ ] Add the requested architecture/API/Samsung/freshness/testing documentation and AndroidAPS attribution.
- [ ] Run focused tests, full relevant unit tests, `:app-mobile:assembleDebug`, applicable release assembly, `:app-mobile:lintDebug`, `detekt`, and `ktlintCheck`; report unavailable tasks explicitly.
- [ ] Generate and submit a whole-branch review package; fix only Critical/Important findings in one TDD pass.
- [ ] Commit documentation and any verified review fixes.
- [ ] Discover WLAN-debugging devices, verify phone/watch model and package targets, install Mobile on the phone and Sugarlicious Wear on the watch when reachable, then verify installed versions.
- [ ] Report `CODE-VALIDIERT`, `BUILD-VALIDIERT`, installation status, and `HARDWARE-TEST OFFEN` unless actual Now Bar/AOD surfaces were observed.
