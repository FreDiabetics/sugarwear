# Dexcom G7 Share upload identity investigation

Date: 2026-11-02. Scope: identify the G7 upload identity and assess coexistence without changing the current sensor connection, monitored-device assignment, or followers.

## Findings

The installed main-phone G7 version is **1.6.1.4537**. The matching user-provided broadcast APK was inspected offline. Its SHA-256 is `F7F675868DC703653544FABAC3A0A6310D0A036775C351AEC71145232BD23B47`. The Dexcom application was not modified, installed, stopped, or instrumented during this investigation.

The G7 app does not require the user to supply a separate receiver identifier on this code path. The Share monitor obtains the current transmitter/sensor `txId` from its nominal transmitter-session state and passes that same string in `DeviceMonitoringRequest.deviceSerialNumber`.

The app's transmitter-ID validator requires a twelve-digit string; the pairing-code validator separately requires four digits. The pairing code, a Bluetooth MAC address, and SugarWear's locally generated `G7-…` session IDs are not interchangeable with this transmitter ID.

G7 uses an event-based Share upload path. Its glucose record includes `txId`, transmitter session start, transmitter time, measurement/recorded timestamps, glucose, trend, and backfilled status. This differs from assuming the legacy xDrip `PostReceiverEgvRecords` payload is the G7 app's own protocol.

Registration, checking monitoring state, starting monitoring, posting events, and follower management are separate methods. The monitor treats the server's `SERIAL_NUMBER_ASSIGNED_TO_YOU` response as an already-registered identity, rather than requiring a new receiver identity for every upload. This supports investigating reuse of an existing registration; it does **not** establish that two independent uploaders are accepted concurrently.

## Evidence map

Decompiled Java was cross-checked against Smali and recovered Kotlin metadata. Obfuscation causes JADX warnings, so method aliases alone are not treated as proof.

| Step | Matching APK code evidence |
| --- | --- |
| Nominal session identity | `kz.C4176`, recovered class `TxSessionState.Nominal`; constructor parameter `txId` is stored in `f41029`; `mo27114()` returns that field (dispatch key 93889, remainder 6067). The separate pairing-code field is `f41036`. |
| Share monitor receives identity | `kz.C5988` collects nominal transmitter-session state, reads `mo27114()`, then invokes `ShareMonitor.m32756(identity, …)`. |
| Registration | `kz.C4630`, recovered `ShareMonitor`, dispatch case 8; identity is passed to `ShareService.m23733`, successful registration and `SERIAL_NUMBER_ASSIGNED_TO_YOU` both cache that same identity. |
| Request model | `DeviceMonitoringRequest.deviceSerialNumber`, constructed by the Share service from the supplied identity. |
| Endpoint roles | Recovered metadata identifies `Endpoint.RegisterMonitoring`, `Endpoint.IsMonitoringActive`, and `Endpoint.StartMonitoring` separately. |
| Glucose upload | `ShareMonitor` builds `PostEvents` from real in-session sensor readings. Recovered `GlucoseRealtimeRecord` includes transmitter identity and original transmitter/session timestamps. |
| Identifier format | `kz.C4201.m31544`, dispatch case 2, checks digit characters and length 12. Pairing-code validation is separate, length 4. |

Temporary working evidence is under `%TEMP%/sugarwear-share-audit-20261102/`; third-party decompiled source is not included in the repository.

## Actual SugarWear state and integration implications

A read-only metadata check on the Galaxy Watch found **no stored sensor serial** (`g7_credentials.sensor_serial` absent/empty). No credential value was printed or written to the report. Current SugarWear setup permits connecting with just the four-digit code and generates a local sensor/session ID when no serial is supplied. Upload integration must explicitly distinguish that local ID from the twelve-digit identity used by G7.

### Follow-up: actual sensor metadata read

The original-app HCI capture contains an authenticated control request `32` and a successful 20-byte response. Its unsigned little-endian eight-byte field at offset 12 is a twelve-digit identity candidate. There was no standard Serial Number String characteristic (`0x2A25`) in the discovered characteristic inventory. The historic value was not reused for the current sensor.

SugarWear now performs that optional read only after a normal glucose/history collection has completed, outside its primary session timeout and immediately before normal GATT cleanup. The read is bounded to 1.2 seconds, preserves parent cancellation, and cannot enqueue another data write afterward. Failures do not discard the completed glucose/history result. Absent metadata is retried at most daily, without new scans, bonds, or connections. Storage is scoped to sensor ID plus address, guarded by collector generation, and cleared together with credentials on full unlink. It never replaces canonical sensor/session IDs or pairing credentials.

**Observed on the Galaxy after installation:** a normal scheduled collection received a fresh glucose sample and a matching twelve-digit metadata candidate. The Collector returned to `WAITING_FOR_NEXT_READING` with no current error. The actual identity is deliberately not included in this document or logs. Until checked against the Share account, the UI labels the fallback `Seriennummer · unbestätigt`.

Sensor start, regular end, and grace end are displayed with date/time in Systemstatus → Sensor and explicitly labeled `berechnet`: these existing values derive from sensor clock and the configured sensor lifetime; they are not newly claimed as server-confirmed expiry dates.

Validation: 437 g7watch unit tests, build, lint, Detekt and formatting passed; 22 product/artwork checks passed. A previous local run suffered a JBR C2 native compiler crash; the complete unchanged test suite passed on rerun. No tests were disabled. No authenticated publisher or upload calls were made.

The existing canonical Dexcom module implements Share **download**, not this G7 event upload protocol. Merely adding a serial to that client would not prove upload compatibility.

## What is established versus unproven

Established: which identifier the matching G7 app uses, its source/format, the separate registration and event-upload operations, and the missing serial in current Watch configuration.

Not established: server acceptance of a second uploader using that identity; whether an independently authenticated client changes the G7 publisher session; duplicate handling across native G7 events and legacy receiver uploads; delivery to existing Follow users. No authenticated publisher or upload requests were made in this investigation.

Consequently there is no verified parallel-upload feature yet. Do not infer server-side idempotence from the presence of timestamps, or infer upload success from login success.

## Safe next implementation boundary

1. Obtain the real twelve-digit transmitter identity through existing sensor details/QR information; never substitute a generated ID or pairing code.
2. Establish the compatible G7 authentication and event-upload contract, separate from the legacy receiver upload assumptions.
3. First perform only a monitoring/assignment check with a separate session. Never call device registration/replacement, start/stop monitoring, or follower modification as an automatic fallback.
4. Only after those checks pass, test event delivery with unchanged identity and original real-reading timestamps; verify the native app remains connected and existing Follow delivery continues. Until that succeeds, report compatibility as unproven.
5. Keep upload delivery outside BLE/alarm lifecycle locks, with a durable account/session-scoped outbox, bounded retries, and explicit failure status.

External comparison: xDrip's legacy `ShareRest` uses a configured `share_key`, starts remote monitoring, and may replace a monitored receiver assignment. That replacement behavior must not be copied. Public Dexcom V3 endpoints document data retrieval, not glucose upload, and cannot alone establish support for the private G7 event path.

References:

- https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/java/com/eveningoutpost/dexdrip/sharemodels/ShareRest.java
- https://developer.dexcom.com/docs/dexcomv3/endpoint-overview
- https://xdrip4ios.readthedocs.io/en/latest/services/dexcomShare/
