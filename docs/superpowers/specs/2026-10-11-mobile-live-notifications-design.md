# Sugarlicious Mobile Live Notifications and Samsung Now Bar

## Scope

This change applies to Sugarlicious Mobile and its existing `PersistentBridgeService` notification. It does not add a phone-side G7 collector, alter the alarm engine, or change SugarWear. The existing custom notification remains the fallback and no second persistent notification is introduced.

The implementation uses AndroidAPS `dev` commit `13306512325af0097eb2c213e2fe04412a0b3fce` and PR #5122 as a technical reference. Sugarlicious will adapt the mechanism to its canonical state and existing service rather than copy AndroidAPS data logic.

## Verified Baseline

The productive Sugarlicious path is:

`TherapyStateStore -> TherapyDisplayState -> PersistentBridgeService -> notification ID 4107`

The current implementation already has `POST_PROMOTED_NOTIFICATIONS`, `PREFERENCE_LIVE_NOTIFICATION`, a foreground service, one notification channel, custom collapsed and expanded `RemoteViews`, and notification graph rendering. Live mode currently retains the custom views, requests promotion through a raw extra, and refreshes primarily when `glucose.measuredAtEpochMs` changes.

AndroidAPS uses two complementary integrations:

- Android promoted ongoing notifications with `setRequestPromotedOngoing`, `setShortCriticalText`, and `MetricStyle`.
- Samsung Ongoing Activity metadata and `android.ongoingActivityNoti.*` extras, independently of Android promotion eligibility.

## Architecture

### Shared presentation

Introduce one immutable `NotificationPresentation` derived only from the current `TherapyDisplayState`, `FreshnessPolicy`, `TherapyDisplayFormatter`, canonical trend, configured unit, and existing display semantics. It contains the visible glucose, numeric metric value, unit, canonical trend symbol, delta, age/freshness, IOB, COB, basal, source/profile status, and explicit unavailable/error text.

No notification renderer may query raw source stores, choose Mobile versus Watch Direct, reclassify trend, or synthesize missing therapy values. `UNKNOWN` has no arrow. Missing IOB, COB, basal, delta, or glucose remains absent or uses the canonical explicit unavailable state; it never becomes zero or another placeholder that implies a measurement.

### Rendering modes

`NotificationModeSelector` selects exactly one mode:

- `STANDARD_CUSTOM`: live preference off, or non-Samsung below API 36.
- `ANDROID_LIVE`: live preference on, non-Samsung, API 36 or newer.
- `SAMSUNG_LIVE`: live preference on and manufacturer equals Samsung, independent of promoted-notification permission and Android API promotion support.

All modes reuse notification ID 4107 and channel `sugarlicious_background`.

`STANDARD_CUSTOM` preserves the current collapsed/expanded layouts, graph, appearance settings, actions, and `DecoratedCustomViewStyle` behavior.

`ANDROID_LIVE` and `SAMSUNG_LIVE` use standard notification content and do not attach custom content views, custom big content views, `DecoratedCustomViewStyle`, or graph `RemoteViews`.

### Android adapter

`AndroidLiveNotificationAdapter` accepts only a builder and `NotificationPresentation`.

On API 36 and newer it calls `setRequestPromotedOngoing(true)` and sets short critical text. The Android capsule contains glucose plus canonical trend when available, for example `118 ↘` or `6.6 ↘`. It intentionally omits delta. When trend is `UNKNOWN`, it contains only glucose. An unavailable or stale canonical state must not show an old glucose/trend capsule.

On API 37 and newer it attaches one `MetricStyle` metric and makes index 0 critical:

- mg/dL uses `Metric.FixedInt`.
- mmol/L uses `Metric.FixedFloat` with minimum and maximum fraction digits both set to 1.

Promotion diagnostics distinguish API support, `canPostPromotedNotifications`, and promotable characteristics when the platform exposes them. A resolvable `ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` intent may be offered from settings. None of these gates Samsung extras.

### Samsung adapter

`SamsungLiveNotificationAdapter` activates only when `Build.MANUFACTURER.equals("samsung", ignoreCase = true)`. It accepts `NotificationPresentation`, creates `SamsungLiveTexts`, and owns all proprietary keys:

- `android.ongoingActivityNoti.style = 1`
- `android.ongoingActivityNoti.primaryInfo`
- `android.ongoingActivityNoti.secondaryInfo`
- `android.ongoingActivityNoti.chipIcon`
- `android.ongoingActivityNoti.chipExpandedText`
- `android.ongoingActivityNoti.nowbarPrimaryInfo`
- `android.ongoingActivityNoti.nowbarSecondaryInfo`

The chip icon is the existing Sugarlicious notification icon. Optional fields are omitted when their value is unavailable.

For fresh mg/dL data the intended texts are:

- drawer primary: `118 ↘ −6 · Basal 0.45 U/h`
- drawer secondary: `IOB 1.3 U · COB 22 g`
- Now Bar primary: `118 ↘ −6`
- Now Bar secondary: `IOB 1.3 U · COB 22 g`
- expanded chip: `118 ↘ −6`

The mmol/L equivalent keeps exactly one decimal for glucose and delta. If only IOB or COB exists, secondary text contains only that value. Unknown trend contributes no glyph.

The Samsung adapter also receives the official Android adapter behavior when supported, so Samsung extras, promoted ongoing status, and API 37 metric style can coexist.

## Freshness and update scheduling

The presentation must never leave an old value looking current. Freshness transitions are derived from the existing Sugarlicious policy and canonical status. Current or aging states may show the permitted glucose representation; stale, signal-loss, sensor-error, and no-source states use explicit canonical status text and suppress misleading metric/capsule content.

Replace the glucose-timestamp-only notification revision with a stable revision of all visible presentation fields, including measured time, source, glucose, trend, delta, freshness/quality, unit, IOB, COB, basal, and status text. An identical presentation does not rebuild the notification.

The service maintains one cancellable freshness-boundary job. After a state or relevant preference change it computes the next boundary at which the rendered presentation changes, schedules one delay, rerenders at that boundary, and schedules the following boundary. A new state cancels and replaces the old job. This is not a periodic one-minute notification loop. The existing widget minute refresh remains separate.

Updates occur for new canonical CGM state, relevant therapy changes, source changes, quality/freshness boundaries, unit changes, and live-setting changes.

## Settings and diagnostics

Keep `PREFERENCE_LIVE_NOTIFICATION` as the only live toggle. Extend the existing Mobile settings surface with factual status only:

- detected manufacturer/device path;
- Samsung integration requested and extras applied/post attempted;
- Android live-update API support;
- promoted-notification permission state;
- promotable-characteristics result when available;
- hardware validation status described as not performed unless observed on the target device.

The UI must not claim that Samsung Now Bar is guaranteed merely because the manufacturer is Samsung.

## Manifest and service stability

Keep the existing promoted-notification permission exactly once. Add this metadata exactly once inside the Mobile application element:

```xml
<meta-data
    android:name="com.samsung.android.support.ongoing_activity"
    android:value="true" />
```

Preserve the foreground service type, `START_STICKY`, Wear Data Layer work, widget updates, bridge behavior, channel, notification ID, and existing action. No Android Auto integration is added unless an existing Sugarlicious path is discovered during implementation; if one exists, it is regression-tested but not redesigned.

## Testing

Implementation follows red-green-refactor. Tests cover:

- presentation formatting for mg/dL and mmol/L;
- canonical seven-state trend mapping and no glyph for `UNKNOWN`;
- Android capsule glucose plus trend, without delta;
- stale, signal-loss, sensor-error, and no-source suppression of old glucose;
- optional IOB, COB, basal, and delta without fabricated zeroes;
- API 35 fallback, API 36 promotion, and API 37 critical metric;
- fixed integer and fixed one-decimal float metrics;
- absence of custom views and graph in both live modes;
- preservation of custom views and graph in fallback mode;
- Samsung mode independent of promoted-notification permission;
- all seven Samsung extras on Samsung and none on non-Samsung;
- same notification ID with no duplicate persistent notification;
- visible-field revision changes and suppression of irrelevant rebuilds;
- cancellation/rescheduling at exact freshness boundaries;
- manifest permission and Samsung metadata exactly once;
- existing service, settings, notification graph, and Mobile tests.

The quality gate runs affected unit and Robolectric tests, the full relevant unit suite, Mobile debug and applicable release assembly, lint, detekt, and ktlint tasks that exist in this repository. Missing tasks are reported rather than silently substituted.

## Documentation and attribution

Add `docs/mobile/LIVE_NOTIFICATIONS.md` describing canonical input, the three modes, API boundaries, Samsung extras, freshness scheduling, diagnostics, testing, and hardware limitations. Credit AndroidAPS PR #5122 as the technical reference and preserve any attribution required if implementation text is materially copied. Prefer an independent Sugarlicious implementation based on the observed behavior and APIs.

## Deployment and acceptance

After code, tests, static checks, and builds complete, discover WLAN-debugging targets and verify model plus package before installation. If reachable, install Sugarlicious Mobile (`app.aapswear`) on the phone and the paired Sugarlicious Wear package on the watch. Do not install SugarWear/G7 Collector as part of this task. Verify installed package/version afterward.

Code/build evidence is reported separately from physical behavior. Samsung Now Bar, AOD, lockscreen, chip, and drawer behavior are marked `HARDWARE-VALIDIERT` only when observed on the intended Samsung One UI 9 phone. Reachability or successful APK installation alone does not prove those surfaces.

Acceptance requires the complete behavior requested in the replacement prompt, with the later clarification taking precedence: the Android capsule includes glucose and canonical trend, not glucose alone.
