# Phase 9 - Energy and battery audit

Date: 2026-09-14

## Recurring work inventory

| Activity | Trigger/frequency | Thread/lifetime | Coalescing/backoff | Assessment |
|---|---|---|---|---|
| G7 LIVE collection | expected five-minute sensor window, advertisement wake, or explicit recovery | collector IO scope plus bounded GATT timeouts | single runtime owner, staged safety alarm, recovery policy | Required; event/window driven |
| G7 gap recovery | successful collector contact with persisted open gap | same GATT cycle | oldest-gap anchor, one coalesced range, no parallel cycle | Required; no blind timer |
| G7 signal-loss health check | alarm after loss/recovery boundary | receiver IO scope | unique pending intent and replacement | Required for alert correctness |
| Wear overview clock | 30 seconds while Activity is started | Activity scope | cancelled in `onStop` | Visible-only; acceptable, candidate for minute alignment |
| SugarWear pairing presentation | one second only while CONNECTING | main handler, Activity lifetime | semantic render gate; callbacks removed on destroy | Bounded interactive work |
| Wear/SugarWear tiles | 60-second system freshness plus data-triggered update | Tile service scope | system managed; scope cancelled | Required for displayed age/graph edge |
| Mobile external surfaces | aligned minute while foreground bridge is enabled | service IO scope | single job, cancelled on destroy | Updates freshness/age; broad widget fanout should be profiled |
| Health Connect sync | unique periodic work every hour | WorkManager | `ExistingPeriodicWorkPolicy.UPDATE` | User-feature work; system managed |
| Complications | canonical state and explicit settings/startup changes | event driven | affected-provider planning exists for phone state | Prefer targeted provider updates |
| Watchface push | explicit install/apply workflow | bounded coroutine delays | not periodic | No steady-state impact |

## Implemented energy improvements

- Duplicate Data Layer copies no longer repeat provider queries, resolver persistence, alert evaluation, or surface invalidation.
- Wear Tile callbacks no longer block their binder caller during storage/provider/bitmap work.
- BLE callback memory is bounded per GATT generation.
- Gap recovery remains attached to successful LIVE contacts rather than a periodic retry timer.

## Foreground collector notification

The SugarWear collector notification has the fixed title `Foreground Channel`, is silent/ongoing, is not updated for scan, GATT, recovery, or protocol-state transitions, and calls `notify` only after a newly committed reading. This matches its process-retention purpose.

Sugarlicious Mobile's configurable rich glucose notification is a separate user-facing feature and was not collapsed into the collector notification.

## Measurement boundary

Static review cannot claim battery improvement in percent. Phase 17 requires before/after Perfetto/Battery Historian evidence on the same device, sensor cadence, AOD state, and test duration. The broad once-per-minute Mobile widget fanout is the primary measurement candidate before changing its correctness-sensitive freshness behavior.
