# SugarWear Dexcom Share Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Do not delegate implementation.

**Goal:** Upload validated direct-watch glucose readings to the user's existing Dexcom Share followers without requiring the phone to be nearby.

**Architecture:** Reuse the canonical `data-source-dexcom` transport/session primitives through the shared submodule. Keep cloud delivery asynchronous and independent of BLE, alarms, and display updates. Never replace the G7 publisher's monitored-device assignment automatically.

**Tech Stack:** Kotlin, existing strict HTTPS transport, Android Keystore, durable local outbox, existing One UI Watch settings components.

## 1. Prove account compatibility before implementing the uploader

- [x] Inspect xDrip's publisher upload protocol and xDrip4iOS's current documented prerequisites.
- [x] Confirm user has a G7-app account and no known receiver/upload identifier.
- [x] Identify the matching G7 app's upload identity: the nominal transmitter session's twelve-digit `txId`, passed as `DeviceMonitoringRequest.deviceSerialNumber`. Current Watch configuration has no stored sensor serial; a local generated sensor ID must not substitute for this identity. See `docs/research/2026-11-02-dexcom-g7-share-upload.md`.
- [ ] Verify authenticated account compatibility without changing existing follower contacts or the phone publisher assignment. Credentials must be entered in app, never pasted into chat or logs.
- [ ] Verify a known real reading is accepted and reaches Follow with its original timestamp. Login success alone is insufficient proof.

Current evidence: G7's own code uses its transmitter identity and event-based uploads, rather than a user-supplied separate receiver identity. This corrects the earlier legacy-uploader assumption. Safe coexistence with the existing publisher and server acceptance of an independent client remain unproven. No upload feature is shipped or enabled.

## 2. Shared protocol implementation (only after compatibility is established)

### Prerequisite: read sensor identity on the existing connection

- [x] Add `G7SensorMetadata.kt` and parser tests using the original app's captured read-only request `32` and its successful 20-byte response. Treat the twelve-digit unsigned identity at offset 12 as device metadata pending server assignment verification, not a replacement canonical sensor/session ID.
- [x] In `AndroidG7Ble.kt`, perform this bounded optional metadata read after successful glucose/history collection, outside its primary session timeout, and only while no metadata exists for this sensor/address. No separate scan, bond, or connection.
- [x] Persist metadata in a separate address- and sensor-scoped store, guarded by current collector lifecycle generation. Never overwrite pairing credentials or canonical sensor/session identities.
- [x] Test strict packet shape/status/range, sensor/address scoping, and cancellation/failure isolation. Build/check before Galaxy-only installation, then wait for the normal measurement cycle and read only presence/format diagnostics. Fresh reading plus matching metadata were observed, followed by normal waiting with no current error.

- [ ] In the canonical Sugarlicious repository extend `data-source-dexcom` with a publisher client; share authentication and strict transport with the existing download client rather than duplicating them.
- [ ] Tests first: accepted assignment, rejected product/serial, expired session retry once, malformed response, bounded body, and no secret logging. Do not silently replace a monitored device.
- [ ] Serialize real mg/dL, original measurement timestamps, and validated trend codes. Never synthesize missing readings or report a queued upload as delivered.

## 3. Watch integration and settings

- [ ] Include shared data-source modules in SugarWear and add INTERNET/network-state permissions.
- [ ] Store Share credentials separately using Android Keystore-backed encryption; preserve existing collector credential formats.
- [ ] Add a bounded durable outbox keyed by source/sensor/session/reading identity; acknowledge only successful server delivery. Test restart, duplicate, out-of-order, account switch, and invalid/future samples.
- [ ] Enqueue outside collector lifecycle locks and alarm processing. Use event-driven delivery and bounded retry/backoff; no polling loop or additional permanent wake lock.
- [ ] Add system-scaled One UI Watch settings for account, region, compatible upload identity, enablement, last delivery, and actionable errors. Remain disabled until prerequisites are validated.

## 4. Verification and release

- [ ] Run shared-client/outbox unit tests, all affected module tests, build, lint, Detekt, and formatting gates.
- [ ] Install only on Galaxy Watch, leaving Pixel devices and the main phone's Dexcom installation untouched.
- [ ] Prove direct network delivery to the existing Follow account over multiple fresh measurement cycles, including network loss/recovery, with no duplicate or delayed-as-current readings.
- [ ] Commit, PR, review, verify, merge, and update the shared submodule without duplicated production code.

Sources: https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/java/com/eveningoutpost/dexdrip/sharemodels/ShareRest.java and https://xdrip4ios.readthedocs.io/en/latest/services/dexcomShare/ (checked 2026-11-02).
