# SugarWear collector foundation

Sugarlicious contains an independently installable Wear OS collector (`:g7watch`) that can obtain Dexcom G7 glucose readings without routing the sensor through the phone. It remains a read-only display integration: it does not dose insulin, change therapy, control a pump, or upload sensor credentials.

## Implemented data path

```text
Mobile setup (manual 4-digit code or applicator Data Matrix)
  -> Wear Data Layer setup command
  -> signature-protected explicit receiver in :g7watch
  -> Android Keystore protected code, G-Key and reusable session key
  -> G7 BLE scan (DXCM / DX01 / DX02)
  -> GATT discovery and notification setup
  -> libkeks certificate, challenge and key exchange
  -> Android BLE bond
  -> 0x4e glucose request and validated packet parser
  -> local Watch SQLite database
  -> Watch overview, graph, tiles and complications
  -> Wear Data Layer glucose update to the Mobile app
```

The Mobile settings expose `Dexcom G7 Watch` as a data source and open a dedicated setup screen. Google Code Scanner is used for camera scanning without a camera permission. Manual entry remains available. The standalone Watch collector also supports manual code entry if the phone is unavailable.

## BLE and authentication

The collector uses the G7 service and characteristic family rooted at `f8083532-849e-531c-c594-30f1f86a4ea5`. Initial authentication performs the public G-Key certificate exchange, persists only the resulting 16-byte session key, creates the Android bond, and requests the current reading. Reconnect authentication restores the encrypted session key.

The authentication implementation is derived from NightscoutFoundation/xDrip `libkeks`; its attribution and GPL terms are recorded in `LICENSES/xDrip-libkeks.md`. The Sugarlicious project is AGPL-3.0 licensed. No Juggluco implementation code is included.

Only one direct collector should be active for a sensor. Juggluco, xDrip direct collection, or another G7 collector must be stopped before Sugarlicious is connected. If the device contains an incompatible old G7 bond, the UI reports `G7-AUTH-211` and asks the user to remove that Bluetooth bond before setting up again.

## Persistence and recovery

- Pairing code, public G-Key material and shared session key are encrypted using a non-exportable Android Keystore AES/GCM key.
- Authentication secrets and raw authentication packets are never written to application logs, diagnostics or Health Connect.
- Readings are inserted idempotently before UI or phone delivery.
- The collector documents the encrypted applicator code, GTIN/serial (when present), BLE identity, sensor/session identifiers, activation time inferred from the sensor clock, regular ten-day end, twelve-hour grace-period end and every decoded field of the 0x4e glucose record in its local Sensor documentation card. The applicator code remains encrypted at rest and is not copied into exportable event logs.
- Decoded reading metadata (sensor clock, reading age, sequence, trend rate, predicted glucose, display-only flag, protocol status, calibration state and reserved field) is retained with the local reading database. Raw authentication frames and key material remain excluded.
- The next collection is scheduled shortly before the expected five-minute reading window.
- Recoverable BLE/GATT failures use bounded backoff. Stable safe error codes distinguish permissions, scanning, GATT, authentication and packet validation.
- Boot recovery resumes only if the user left the collector enabled.

## Source selection

`Automatic` prefers a fresh local G7 reading and otherwise retains the AndroidAPS phone-fed state. `Dexcom G7 Watch` never substitutes a phone glucose value when the local value is absent or stale; non-glucose AndroidAPS therapy context may still be retained for read-only display. AndroidAPS is the supported phone source.

## Validation boundary

Unit and Android tests cover G7 packet and timing metadata parsing, applicator-code parsing, protocol serialization, scanner name matching, state persistence, data-source propagation and existing Mobile/Wear regressions. Builds verify the complete module graph.

Physical sensor authentication and the first live glucose packet must still be confirmed with the
user's active sensor after entering its four-digit applicator code. The Collector implements the
verified G7 `0x59` history request for at most 24 hours. Historical rows are collector-owned,
deduplicated, session-bound and excluded from current-reading alarms/freshness; Mobile and other
surfaces can only consume rows already stored by the Collector.
