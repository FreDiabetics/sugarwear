# Mobile Live Notifications

Sugarlicious exposes the canonical mobile therapy state through one persistent notification. The service keeps the existing notification channel (`sugarlicious_background`), notification ID (`4101`), foreground-service lifecycle, and app/disable actions.

## Rendering modes

- `STANDARD_CUSTOM` keeps the existing custom collapsed/expanded notification and optional CGM graph.
- `ANDROID_LIVE` uses the platform live-update surface on Android 16+ and requests promoted ongoing status. Android 17+ also receives a `NotificationCompat.MetricStyle` metric for the glucose value.
- `SAMSUNG_LIVE` adds Samsung ongoing-activity and Now Bar extras. This path is manufacturer-gated and remains available independently of the Android 16 promotion API.

Live modes intentionally use a standard notification layout. Custom `RemoteViews` are not mixed into promoted/live notifications because system surfaces need the platform title, text, short critical text, and metric data.

## Visible data contract

Both live modes derive their text from `NotificationPresentation`, which uses the same canonical `TherapyDisplayState`, unit selection, freshness policy, therapy formatter, source, and profile as the rest of Sugarlicious. Standard mode keeps its established custom-notification renderer unchanged.

- The Android capsule shows glucose plus the canonical trend arrow, for example `123 →`.
- The notification drawer and Samsung Now Bar show glucose, trend arrow, and delta, for example `123 → +5`.
- Unknown trends omit the arrow instead of inventing one.
- Missing, stale, signal-loss, error, and no-source states suppress old glucose values and show the explicit state.
- Therapy values, active basal, source, and profile are retained in the ordinary notification content where available.

The service compares the full visible presentation revision, not only the glucose timestamp. Therapy-only changes therefore refresh the same notification ID. While the process is awake, a coroutine updates every visible freshness or temporary-basal boundary. For AOD/deep sleep, one exact elapsed-realtime wakeup is reserved for the safety boundary where stale glucose must be suppressed, avoiding Android's exact-alarm rate limit between the earlier aging and stale transitions. Android 12+ therefore enables the Live/Now-Bar mode only after the system's exact-alarm special access is granted; without it, Sugarlicious keeps the safe ordinary notification instead of leaving stale glucose in a live capsule. Screen/time reconciliation corrects the presentation immediately when the device wakes. There is no polling loop, and duplicate ordinary service starts do not repost an identical presentation.

## Samsung integration

The manifest declares `com.samsung.android.support.ongoing_activity`. Samsung notifications receive these extras only on Samsung devices:

- `android.ongoingActivityNoti.style`
- `android.ongoingActivityNoti.primaryInfo`
- `android.ongoingActivityNoti.secondaryInfo`
- `android.ongoingActivityNoti.chipIcon`
- `android.ongoingActivityNoti.chipExpandedText`
- `android.ongoingActivityNoti.nowbarPrimaryInfo`
- `android.ongoingActivityNoti.nowbarSecondaryInfo`

## Diagnostics and validation

The Mobile settings screen reports the detected manufacturer, requested Samsung path, Android live-update capability, promoted-notification capability, and whether a physical hardware test has been performed. The service also records its last selected mode, Samsung-extra application state, and last post attempt in `live_notification_diagnostics`.

Unit and Robolectric tests cover all trend states, both glucose units, freshness transitions, Android API gates, Samsung manufacturer gates, the exact extra-key set, notification service behavior, settings status, and manifest uniqueness. A successful build or install proves the code path and package state, but not visible Now Bar, lock-screen, AOD, or OEM battery behavior; those remain physical-device checks.

## Reference

The behavior follows the AndroidAPS development implementation identified in the project specification while adapting it to Sugarlicious' canonical model and service architecture. AndroidAPS code is not copied into Sugarlicious.
