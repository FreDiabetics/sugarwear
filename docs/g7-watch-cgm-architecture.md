# Sugarlicious CGM architecture and G7 Watch collector

This document records the implementation findings and the safe architecture used by SugarWear.

## 1. Architectural boundary

Sugarlicious Mobile remains an external CGM bridge. It does **not** own a direct Dexcom G7 BLE collector.

Current Mobile path:

```text
Dexcom G7 sensor
  -> official Dexcom G7 app
  -> AndroidAPS / external mobile CGM source
  -> Sugarlicious Mobile
  -> Wear data layer
  -> Sugarlicious Wear
```

The independent direct Watch path remains:

```text
Dexcom G7 sensor
  -> BLE
  -> SugarWear collector
  -> local read-only provider / local database
  -> Sugarlicious Wear canonical source resolver
```

The Wear-side source resolver selects **data**. It does not start, stop, scan, pair, bond or authenticate the BLE collector.

## 2. BLE implementation findings

### Advertising and sensor identification

`AndroidG7Scanner` performs an unfiltered low-latency BLE scan and accepts either the persisted device address or an advertised/device name matching:

```text
^DX(?:CM|01|02)[A-Z0-9]{0,8}$
```

A known sensor address therefore has precedence; the advertised name is the discovery fallback.

### GATT service and characteristics

The implementation uses the following G7 profile UUIDs:

```text
service        f8083532-849e-531c-c594-30f1f86a4ea5
control        f8083534-849e-531c-c594-30f1f86a4ea5
authentication f8083535-849e-531c-c594-30f1f86a4ea5
backfill       f8083536-849e-531c-c594-30f1f86a4ea5
extra data     f8083538-849e-531c-c594-30f1f86a4ea5
CCCD           00002902-0000-1000-8000-00805f9b34fb
```

The current live-reading path discovers the service, subscribes to Extra Data, Authentication and Control, and requests a glucose packet by writing opcode `0x4e` to the Control characteristic.

After the live `0x4e` packet is published, the Collector may subscribe to the Backfill characteristic
and issue the verified G7 `0x59` request. Start/end sensor clocks are little-endian and the request is
hard-limited to 24 hours. G7 history notifications are decoded as 9-byte records. This code exists
only in `:g7watch`; all other modules are consumers of the resulting validated rows.

### Pairing, bonding and authentication

Authentication is performed through `jamorham.keks.Plugin` using the four-digit pairing code plus G-Key material. The implementation persists the public G-Key material into the plugin, optionally restores the previously derived 16-byte shared key, processes Authentication/Extra Data packets, and asks the Android Bluetooth stack to create a bond when the protocol requests it.

The derived shared key is persisted for reconnect. Pairing code, G-Key material and shared key are encrypted at rest with AES/GCM using a non-exportable Android Keystore key. The diagnostic path does not intentionally log these secrets.

### GATT lifecycle

A collection cycle:

1. scans for the known G7,
2. opens `connectGatt(..., TRANSPORT_LE)`,
3. discovers services,
4. enables notifications/indications,
5. runs authentication,
6. creates/reuses the Android bond when required,
7. requests and publishes one glucose packet,
8. optionally fills a verified gap without blocking the current dashboard,
9. stores live and historical rows with distinct origin,
10. closes/disconnects the GATT connection,
11. schedules the next reconnect.

The collector therefore currently uses bounded connection cycles rather than holding one continuous GATT connection open.

### Reconnect and lifecycle

The collector persists sensor/session state and schedules reconnects through `AlarmManager`. Reconnect is only scheduled while `collectorEnabled == true`. Explicit stop cancels the pending reconnect alarm. Collection runs under a bounded partial wakelock and failures use bounded recovery rather than an endless retry loop.

First start is gated by a four-digit code screen. Saving a valid code prepares the new session and
starts the existing Collector service; the UI reports real protocol state and opens the dashboard
only after a valid live reading. Existing successful sessions survive process/device restarts.

### Local history

Direct readings are durably stored in `g7_readings.db`. The row identity already contains sensor ID, session ID, sequence number and sensor timestamp through `CgmReadingIdentity`; SQLite uses the reading ID as primary key with `CONFLICT_IGNORE`. This provides durable local history and direct-stream deduplication on the Watch.

## 3. Critical BLE role conclusion

**The current implementation does not prove that it is using the Dexcom "Smartwatch" device role/channel rather than the Smartphone role/channel.**

The code contains the GATT service/characteristics, authentication flow, bonding and shared-key persistence, but no explicit role selector, role identifier, Receiver/AID selector, or other implementation-level marker that establishes which Dexcom device slot is being consumed.

Therefore:

- Smartwatch-role coexistence with the official Dexcom app must **not** be claimed from code inspection alone.
- Receiver/AID must not be intentionally selected or repurposed.
- Automatic parallel collection while Mobile is primary must remain behind the existing explicit-activation/safety gate until hardware/protocol validation proves coexistence.
- Aggressive Phone -> Watch -> Phone BLE handover is not implemented as a fallback strategy.

This is the main unresolved hardware/protocol question.

## 4. Canonical Wear CGM source model

The canonical resolver has two conceptual inputs:

```text
MOBILE_AAPS
WATCH_G7_DIRECT
```

and the following deterministic source-health states:

```text
MOBILE_PRIMARY
MOBILE_DEGRADED
WATCH_DIRECT
MOBILE_RECOVERY
NO_SOURCE
```

These states are independent of BLE collector lifecycle.

### Default policy

The policy is centralized in `CgmSourcePolicy`:

```text
Mobile degraded after:       7 minutes
Mobile failover after:      15 minutes
Watch direct fresh window:  12 minutes
Mobile recovery requirement: 2 distinct fresh readings
Maximum preferred-source lag: 90 seconds
Future timestamp tolerance:   5 minutes
```

The important semantic rule is:

> 15 minutes without a valid Mobile CGM value means "make an already available Watch Direct stream canonical". It does not mean "start scanning/pairing/connect after 15 minutes".

### Automatic mode

`AUTOMATIC` prefers fresh Mobile data. Watch Direct is an independent fallback. A significantly newer Watch measurement cannot be overwritten by a delayed older Mobile measurement. Once Watch Direct is canonical, Mobile must deliver two distinct fresh recovery readings before normal Mobile-primary operation resumes.

### Explicit source modes

`PHONE` is Mobile-only. `DEXCOM_G7_WATCH` is Watch-only. These modes select the canonical data source; collector activation remains a separate concern.

## 5. Ordering, deduplication and session identity

Direct G7 rows expose:

- sensor ID,
- session ID,
- sequence number,
- sensor timestamp,
- received timestamp.

The canonical resolver uses sensor/session identity when both sides provide it and otherwise uses matching sensor timestamp plus glucose as a conservative cross-source deduplication fallback.

Current Mobile/AAPS `TherapyDisplayState` does not expose Dexcom sensor/session identity. Consequently, full cross-source session-aware deduplication is not possible until the upstream Mobile path can provide equivalent identity. Timestamp ordering still prevents a delayed older Mobile measurement from replacing a newer direct Watch measurement.

History is merged by sensor timestamp rather than `receivedAt`, preventing duplicate graph points for the same timestamp.

## 6. Separation of source stores

`TherapyStateStore` remains the **phone-fed state store**. A direct G7 receiver must not overwrite that store with a Watch reading, because doing so destroys the independent Mobile input required by the source resolver.

The canonical display state is derived at the Wear consumer boundary from:

```text
phone-fed TherapyStateStore
+
durable local G7 database/provider
-> CanonicalCgmSourceResolver
-> Watch UI / Tiles / Complications
```

This preserves the two independent data paths.

## 7. NO_SOURCE

When neither Mobile nor Watch Direct has a valid reading inside its allowed age window, the canonical result has no glucose value. Old glucose must not remain indefinitely current. UI, Tiles and Complications then render their existing no-data state instead of treating the previous value as fresh.

## 8. Alarms and complications

The existing `CgmAlarmEngine` accepts a single reading. It should only be fed the canonical resolved reading; collector-specific invocation would create duplicate alarms for the same G7 measurement.

Wear UI, Tiles and Complications resolve through the canonical Wear state path rather than binding directly to a specific collector. This keeps glucose, trend, delta, age and graph behavior source-independent.

## 9. Persistence and security

Persisted BLE/session information currently includes the configured sensor, collector enabled state, protocol/session state, reconnect timing and the encrypted authentication material required for reconnection. Secrets are encrypted with Android Keystore-backed AES/GCM and should never be added to diagnostics or plaintext preferences.

After process death or Watch reboot, automatic collector restart remains conditioned on the collector having been explicitly enabled previously.

## 10. Hardware validation required before enabling automatic parallel collection

The following must be verified with the official Dexcom G7 app actively connected to the same sensor:

1. Start the Sugarlicious Watch collector explicitly.
2. Capture the sensor's observed BLE advertisements and the Watch GATT connection sequence.
3. Confirm whether the official Dexcom app remains continuously functional while Watch collection succeeds.
4. Verify whether both devices receive the same sensor timestamps over multiple measurement cycles.
5. Reboot the Watch and repeat reconnect without re-pairing.
6. Toggle Watch Bluetooth off/on and confirm bounded recovery.
7. Move the Watch out of range and back and confirm bounded recovery.
8. Confirm that no Receiver/AID slot is consumed or altered.
9. Confirm that stopping Sugarlicious does not disturb the official Dexcom app.
10. Only after repeatable coexistence is proven should automatic background Watch collection during `MOBILE_PRIMARY` be enabled by default.

Until that validation is complete, the implementation intentionally keeps explicit collector activation as the safety boundary while allowing the canonical resolver to consume a direct stream whenever one already exists.

## 11. History ownership boundary

The Watch database is the durable source for direct-sensor LIVE and BACKFILL history. Mobile reads that canonical history through the read-only provider; there is no separate acknowledgement queue or second history-upload protocol. The retired unsynced/synced bookkeeping was removed because no production sender consumed it and its acknowledgement state could diverge from the canonical reading history. Cross-device recovery still requires hardware validation before claiming complete offline-history reconciliation.
