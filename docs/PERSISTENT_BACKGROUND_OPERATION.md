# Persistent Background Operation

This document describes the lifecycle contract for Sugarlicious Mobile, Sugarlicious Wear and SugarWear.

## Architecture

### Mobile

Sugarlicious Mobile remains the phone-side bridge. It does not contain a direct Dexcom G7 BLE collector.

```text
Dexcom G7 app -> AndroidAPS -> Sugarlicious Mobile -> Wear Data Layer -> Sugarlicious Wear
```

`PersistentBridgeService` is the existing foreground service for the user-visible background bridge/notification. It returns `START_STICKY` and is restored after `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`. A real Android Force Stop remains authoritative.

AAPS broadcasts are processed by the manifest `AapsStatusReceiver`. Valid state is persisted before Wear Data Layer I/O so a temporarily disconnected watch cannot invalidate phone-side data. The service notification keeps stale/no-data states explicit rather than making an old glucose value appear current.

### Wear

Phone-to-watch delivery remains event-driven through the existing `StateDataLayerService : WearableListenerService`. This same service is the permanent Sugarlicious Wear runtime foreground service; no parallel Data Layer or keep-alive service exists.

`StateDataLayerService`:

- calls `startForeground()` when created/started,
- returns `START_STICKY`,
- runs as `connectedDevice`,
- uses a silent low-importance ongoing non-auto-cancel notification,
- starts when the Wear app is opened,
- restores after `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`,
- continues to process Data Layer callbacks only when events arrive.

The permanent foreground lifetime does not add a polling loop. The Wear Activity's 30-second refresh job remains UI-only and is cancelled in `onStop()`.

### SugarWear

The direct G7 collector remains a separate Wear OS application/service and is independent from phone reachability and canonical source selection.

```text
explicit user start
    -> persisted collectorEnabled=true
    -> persistent G7CollectorService foreground runtime
    -> one BLE/auth/read cycle
    -> persisted reading/state
    -> BLE cycle ends + WakeLock released
    -> exact/inexact allow-while-idle reconnect alarm
    -> next alarm starts another cycle in the same service runtime
```

While `collectorEnabled=true`, `G7CollectorService` remains a foreground service between five-minute sensor windows. The foreground notification remains visible, but BLE scanning/connection work and the bounded collection WakeLock exist only during an actual collection cycle. There is no permanent BLE scan and no collector loop.

A successful or failed cycle no longer calls `stopForeground()` or `stopSelf()` while the collector remains enabled. Only an explicit user stop changes `collectorEnabled=false`, cancels the reconnect alarm and terminates the foreground service/notification.

`collectorEnabled=false` is not written by technical disconnects, process recreation, phone loss, source changes, boot or app replacement.

## Lifecycle restore

### Sugarlicious Wear runtime

- Activity closed: `StateDataLayerService` remains foreground and sticky.
- Process recreation: `START_STICKY` restores the foreground runtime.
- Boot: `WearRuntimeBootReceiver` starts the existing listener service.
- App update: `MY_PACKAGE_REPLACED` starts the same service.
- Data Layer handling remains callback/event driven.

### G7 enabled

- Activity closed: state and foreground service remain enabled.
- Between sensor windows: notification remains; BLE work and WakeLock are released.
- Process recreation: `START_STICKY` re-enters the persisted enabled state.
- Boot: `G7BootReceiver` restores only when `collectorEnabled=true`.
- App update: `MY_PACKAGE_REPLACED` follows the same restore policy.
- Reconnect alarm: triggers another bounded sensor cycle while the persisted state remains enabled.
- Phone unavailable: no collector disable side effect.
- Canonical source changes to phone: no collector disable side effect.

### G7 disabled

- Boot does not start the collector.
- App update does not start the collector.
- A reconnect receiver ignores the event.
- Source selection cannot enable the collector.
- No persistent G7 foreground notification remains.

## Source selection separation

The historical `SET_SOURCE` broadcast is retained for compatibility, but it no longer owns collector lifecycle. It may resume a collector only when both conditions are true:

1. the source signal selects direct G7, and
2. the persisted collector state is already enabled.

Leaving the direct-G7 display source never calls `G7CollectorService.stop()` and never writes `collectorEnabled=false`.

Canonical display/source resolution remains separate and unchanged.

## Battery optimization access

Battery optimization is handled separately for both Wear packages:

```text
app.aapswear
app.aapswear.g7watch
```

Both use the package-specific `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` request first. The only source of truth after returning from Android/Wear OS settings is:

```text
PowerManager.isIgnoringBatteryOptimizations(packageName)
```

No local preference or optimistic UI state represents a grant.

For Sugarlicious Wear, failure to open the settings surface or failure to grant the exemption creates visible feedback and a Watch diagnostic event. For SugarWear, `Dauerbetrieb freigeben` follows the same truth model and records visible/diagnostic failure when the exemption is not granted.

The G7 action button uses `WRAP_CONTENT` with a minimum touch height and vertical padding; it is not constrained to a fixed 46 dp height on round displays.

## Battery policy

A persistent foreground service lifetime is not implemented as a busy runtime.

- Mobile background state is event/flow driven.
- Wear Data Layer callbacks remain event driven.
- No new Wear polling loop is added.
- G7 reconnect remains scheduled around sensor windows.
- BLE scan/connection work ends after each G7 collection cycle.
- The bounded G7 collection WakeLock is released after each cycle.
- The G7 foreground notification remains resident without keeping BLE or CPU awake between cycles.
- Source changes no longer create collector stop/start churn.
- Existing BLE reconnect/backoff semantics remain unchanged.

## Force Stop

An explicit Android/Wear OS **Force Stop** is not bypassed. Android may suppress receivers, alarms and service restarts until the user launches the application again. Sugarlicious does not attempt to defeat this system behavior.

## Required hardware lifecycle test matrix

Automated tests and CI are necessary but are not sufficient for merge readiness. The following scenarios must be validated on the actual phone/watch combination before PR #47 may be declared merge-ready:

| Scenario | Required result |
| --- | --- |
| Phone app closed | Mobile foreground notification remains; AndroidAPS -> Mobile -> Wear updates continue |
| Watch app closed | Sugarlicious Wear foreground notification remains; Data Layer updates continue |
| Display off / Doze for 30+ minutes | Current values continue without manually opening either app |
| Phone out of range | Direct Watch collector remains enabled and continues independently |
| Phone returns | Normal transport/source recovery occurs without opening an app |
| Multiple G7 five-minute cycles | Distinct new Watch-direct readings arrive automatically while G7 FGS remains resident |
| Watch reboot | Wear runtime restores; enabled G7 collector restores; disabled collector stays disabled |
| App update | `MY_PACKAGE_REPLACED` restores Wear runtime and enabled G7 collector |
| Bluetooth off/on | Technical failure does not write `collectorEnabled=false`; automatic recovery continues |
| Battery exemption denied | Visible error and diagnostic; UI continues to report actual optimized state |
| Battery exemption granted | `PowerManager` reports unrestricted for the respective package |
| Explicit Collector stop | `collectorEnabled=false`, reconnect cancelled, G7 FGS/notification terminated |

Success criterion: **No app must be manually opened in order for current values to continue. Phone and Watch foreground notifications remain visible for their active runtimes.**

## Data-integrity invariants

Lifecycle restoration must never:

- rewrite `measuredAt`, `receivedAt` or sensor time to make data fresh,
- invent a glucose value,
- convert stale/no-source data into current data,
- delete bond/shared-key/session data for a recoverable lifecycle event,
- let a source-selection event overwrite the user's collector enable decision.

Resolver/source semantics are intentionally outside this lifecycle change.
