# Dexcom Share publisher – isolated SugarWear Wear OS implementation

## Status – experimental, disabled by default

- Repository: FreDiabetics/sugarwear only. No Sugarlicious or other repository is modified.
- Current SugarWear collector exclusively owns G7 BLE. Dexcom Share publishing is an optional **output adapter**, never a glucose source, BLE collector, alarm or source resolver.
- Share's `Publisher/PostReceiverEgvRecords` endpoint is **undocumented and unsupported** by the official Dexcom public developer API. Successful login, HTTP acceptance and visible Follow data are three different outcomes.
- Production compatibility with a G7 account, real receiver association and actual Follow subscriber **has not been proved**. No health data was uploaded in development; no user passwords or receiver serials were supplied.

## Public comparison references (not copied as source files)

- xDrip+ `app/src/main/java/com/eveningoutpost/dexdrip/sharemodels/DexcomShare.java`
- xDrip+ `sharemodels/ShareRest.java`, `models/Egv.java`, `models/ShareUploadPayload.java`
- xDrip4iOS `xDrip/Managers/DexcomShare/DexcomShareUploadManager.swift`
- xDrip4iOS documentation: https://xdrip4ios.readthedocs.io/en/latest/services/dexcomShare/

The independently written Kotlin client uses the publisher login and upload sequence demonstrated there. In contrast to older xDrip+ code, default Android TLS and hostname verification are **never bypassed**, raw HTTP bodies and credentials are not logged, retry loops are bounded, and upload acceptance is persisted only after the server confirms the batch.

The required Share **receiver/upload serial is not the G7 four-digit pairing code nor the sensor's serial**. The app cannot generate, register, replace or spoof a Dexcom receiver. In particular, a valid account login does not prove an upload serial is assigned to that account.

## Components

- `DexcomShareUploadPolicy.kt`: only valid `DEXCOM_G7_WATCH`, `LIVE`, active sensor-session, non-display-only, time-valid measurements; no forecasts, no fabricated values. Old backlog is limited to eight hours, initial import to twenty minutes; server timestamps remain measurement timestamps. Historical sensor backfill is excluded pending server compatibility proof.
- `DexcomShareUploadStore.kt`: separate Android Keystore AES-GCM credentials, region, opt-in, monotonically revisioned, per-account/session hashed upload cursor and nonsecret diagnostic status.
- `DexcomShareUploadClient.kt`: HTTPS publisher authentication and uploaded EGV batches; monitoring/session recovery once each; duplicate-batch ambiguity triggers per-reading fallback. No read access to BLE or alarm components.
- `DexcomShareUploadWorker.kt`: unique, constrained WorkManager immediate uploads plus a bounded 15-minute recovery check. Network failures defer under exponential backoff. No time-critical alarm work is scheduled here.
- `CgmShareSettingsActivity.kt`: One UI Watch black-background Share & Follow, account setup, region selection, consent-gated activation, explicit login test, status, disable and account removal.
- Existing `CgmReadingDatabase.kt` only signals WorkManager when new state is published, and only if the feature has been expressly enabled.

## Threats and safeguards

1. **Stale or wrong-session glucose:** exporter rejects invalid, different-session, synthetic, backfill and future measurements; does not mutate the canonical CGM state.
2. **Uncertain upload acknowledgement:** cursor advances only on accepted HTTP response and only for the same enabled account revision; failures do not alter it. A Share HTTP success is not shown as clinical Follow validation.
3. **Cancelled/changed account in flight:** store revision invalidates old acknowledgements, disabling cancels queued work. HTTPS requests time out.
4. **Authentication or serial mismatch:** typed errors persist safe error categories, never server payload, URL token or password.
5. **Watch battery:** no new BLE scan, long-running foreground service or network polling loop. Network-constrained WorkManager handles deferrable uploads. Real watch measurements still needed.

## Unresolved gates

- Product/legal: Dexcom German Terms restrict unauthorized third-party automated services and commercial usage; verify written authorization/terms and medical-device duties before distributing this feature. Do not describe an unsupported endpoint as official.
- Compatibility: With an expressly authorized account and legitimately assigned upload receiver, prove login, Share HTTP receipt, actual value/time in the **official Dexcom Follow app**, sensor changes, repeat/duplicate behavior and delayed watch uploads.
- Operational: Test on Samsung Galaxy Watch and Pixel Watch over watch Wi-Fi and LTE where available, with phone absent, background/doze/reboot/battery restrictions, lost network and expired sessions. Watch must continue providing local alarms regardless of Share state.
- Full GitHub CI, unit, lint, static analysis, release build, UI font/round-safe-area checks and a privacy review must pass before merge.

## User-facing labeling

An authentic Dexcom login is labeled `Konto bestätigt`, HTTP-upload acknowledgment `Upload bestätigt`. Neither is labeled `Follow erreichbar` until verified by an actual follower. Do not rely on unofficial Share upload for emergency monitoring.
