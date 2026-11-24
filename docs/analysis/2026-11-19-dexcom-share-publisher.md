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

The required Share **receiver/upload serial is not the G7 four-digit pairing code nor the sensor's serial**. The explicit watch setup action can request assignment of the xDrip+ default after Dexcom reports it as unassigned. In particular, a valid account login does not prove an upload serial is assigned to that account.

## Components

- `DexcomShareUploadPolicy.kt`: only valid `DEXCOM_G7_WATCH`, `LIVE`, active sensor-session, non-display-only, time-valid measurements; no forecasts, no fabricated values. Old backlog is limited to eight hours, initial import to twenty minutes; server timestamps remain measurement timestamps. Historical sensor backfill is excluded pending server compatibility proof.
- `DexcomShareUploadStore.kt`: separate Android Keystore AES-GCM credentials, region, opt-in, monotonically revisioned, per-account/session hashed upload cursor and nonsecret diagnostic status.
- `DexcomShareUploadClient.kt`: HTTPS publisher authentication and uploaded EGV batches; monitoring/session recovery once each; duplicate-batch ambiguity triggers per-reading fallback. No read access to BLE or alarm components.
- `DexcomShareUploadWorker.kt`: unique, constrained WorkManager immediate uploads plus a bounded 15-minute recovery check. Network failures defer under exponential backoff. No time-critical alarm work is scheduled here.
- `CgmShareSettingsActivity.kt`: One UI Watch black-background Share & Follow, account setup through Wear OS Credential Manager, with masked keyboard entry available in the private debug build, region selection, consent-gated activation, explicit login test, status, disable and account removal.
- Existing `CgmReadingDatabase.kt` only signals WorkManager when new state is published, and only if the feature has been expressly enabled.

## Threats and safeguards

1. **Stale or wrong-session glucose:** exporter rejects invalid, different-session, synthetic, backfill and future measurements; does not mutate the canonical CGM state.
2. **Uncertain upload acknowledgement:** cursor advances only on accepted HTTP response and only for the same enabled account revision; failures do not alter it. A Share HTTP success is not shown as clinical Follow validation.
3. **Cancelled/changed account in flight:** store revision invalidates old acknowledgements, disabling cancels queued work. HTTPS requests time out.
4. **Authentication or serial mismatch:** typed errors persist safe error categories, never server payload, URL token or password.
5. **Watch battery:** no new BLE scan, long-running foreground service or network polling loop. Network-constrained WorkManager handles deferrable uploads. Real watch measurements still needed.

## Unresolved gates

- The private debug build accepts a typed password, disables view-state saving and keyboard learning for that field, and protects the account dialog against screen capture. The release build uses Credential Manager in line with Wear OS quality requirement WO-P6. Availability there depends on a matching saved credential.
- After saving credentials, SugarWear checks the account. If it lacks a saved receiver, it queries the assignment of the default used by [xDrip+ NewDataObserver](https://github.com/NightscoutFoundation/xDrip/blob/cb3a3a8be3a2ad8af9d8723227893bbc7f2a2b55/app/src/main/java/com/eveningoutpost/dexdrip/NewDataObserver.java). Only an explicit `AssignedToYou` reply permits saving that candidate. A user-supplied serial uses the same read-only assignment check. Tapping the watch setup action rechecks the candidate, requests replacement only for an explicit unassigned default, and rechecks ownership before enabling uploads.
- Product/legal: Dexcom German Terms restrict unauthorized third-party automated services and commercial usage; verify written authorization/terms and medical-device duties before distributing this feature. Do not describe an unsupported endpoint as official.
- Compatibility: With an expressly authorized account and legitimately assigned upload receiver, prove login, Share HTTP receipt, actual value/time in the **official Dexcom Follow app**, sensor changes, repeat/duplicate behavior and delayed watch uploads.
- Operational: Test on Samsung Galaxy Watch and Pixel Watch over watch Wi-Fi and LTE where available, with phone absent, background/doze/reboot/battery restrictions, lost network and expired sessions. Watch must continue providing local alarms regardless of Share state.
- Full GitHub CI, unit, lint, static analysis, release build, UI font/round-safe-area checks and a privacy review must pass before merge.

## User-facing labeling

An authentic Dexcom login is labeled `Konto bestätigt`, HTTP-upload acknowledgment `Upload bestätigt`. Neither is labeled `Follow erreichbar` until verified by an actual follower. Do not rely on unofficial Share upload for emergency monitoring.

## Existing followers on G7 (2026-11-24)

The Android G7 2.15.0 reference package contains separate choices for the reading alone or the reading with its graph, a named confirmation question for pausing an individual follower, a Continue Sharing action, and separate acknowledgement messages for pause and continuation. This is static UI evidence. It does not establish when the backend commits the change. The [G7 user guide](https://s3.us-west-2.amazonaws.com/dexcompdf/G7/AW-00046-06_REV005_UG_G7_OUS_en-SA_MGDL.pdf) also distinguishes a publisher pause from a follower who stopped following.

The older G7 1.6.1 reference package names an internal SDK operation `updateFollowerPermission` and distinguishes a subscription paused by its publisher from one paused by its subscriber. Neither a method name nor the invitation payload establishes the HTTP method, path, parameters or acknowledgement for editing an existing subscription. The [public Dexcom API](https://developer.dexcom.com/docs/dexcomv3/endpoint-overview) lists no follower-management endpoint. SugarWear can read the returned permissions from its existing publisher subscription list. An authenticated edit contract and per-subscription readback are still needed before offering the three write actions on the watch.

### Read-only check on the connected phone

The installed G7 app reported version 1.6.1.4537. Its follower details offered a choice between the current reading alone and the reading with the trend graph. Pause and remove were separate actions. The open pause confirmation offered **Abbrechen** and **Teilen pausieren**. No confirmation was submitted and no live sharing permission was changed.

Call sites in the matching local G7 reference version obtain `getContactInformation().getSubscriptionId()` before invoking the per-follower operations. The internal `IShareMonitor` interface exposes `updateFollowerPermission(UUID, Int)`, `pauseMonitoring(UUID)` and `resumeMonitoring(UUID)` as suspending methods. Global start/stop methods have no follower parameter. These are app-internal Kotlin signatures. Their UUID argument identifies the subscription, distinct from the contact used for removal and the subscriber account. SugarWear now retains the validated subscription ID from the existing list response, leaving it absent when the server omits it or returns an invalid value.

No HTTP contract for these three mutations was established. Neither a local confirmation dialog nor an SDK method name proves the request format, server response, or resulting state in Follow.

### Consented test with one accepted test follower

On the connected G7 1.6.1.4537 phone, switching from reading plus graph to reading alone and back produced the same “Teilen von Daten aktualisiert.” acknowledgement after each selection. Confirming the named pause prompt produced a pause acknowledgement and a paused status card. The card's “Teilen fortsetzen” action produced a continuation acknowledgement and removed the paused card. After closing and reopening the selected follower, the graph option was checked and the pause action was available again. The Follow dashboard was accessible, but receiver-side graph rendering was not independently established. Available app logs exposed no publisher request paths during this test. These observations establish the publisher UI state transitions, not an HTTP success response format.

### Network capture and debugger observation on the second phone

A PCAPdroid capture scoped to the G7 app recorded TCP traffic on port 443 to `shareous1.dexcom.com` around each selected test action. The installed G7 copy exposed Android debugging. Observing its OkHttp responses during a second round of tests showed these paths on the same host:

| Action | Method and path | Observed response |
| --- | --- | --- |
| Change trend permission | `POST /follow/v1/subscription/updatePermissions` | HTTP 200, Content-Length 0 |
| Pause one follower | `POST /follow/v1/subscription/pause` | HTTP 200, Content-Length 0 |
| Resume one follower | `POST /follow/v1/subscription/resume` | HTTP 200, Content-Length 0 |
| Reload follower details/list | `POST /follow/v1/patient/readExpandedSubscriptions` | HTTP 200 |

No query parameters were observed for these calls. SugarWear currently authenticates against the older ShareWebServices API. Compatibility of that session with the observed Follow v1 API has not been established. Only the designated test follower was changed. After the test the follower was active and the chart permission was enabled.

A focused repeat with header metadata confirmed `Authorization: Bearer …`, `X-Account-Id`, `X-Security-Schema`, `X-Software-Id` and `X-Trace-Id` on all four paths. Token and identifier values were excluded from the diagnostic output. Request lengths were 479 bytes for the expanded list, 607 bytes for each trend permission change and 564 bytes each for pause and resume. The debugger's object-field view did not reliably show the serialized request bodies. Those lengths establish that the requests carried data. The exact payload format and a way for SugarWear to obtain a valid Follow v1 authorization remain unverified.

### Follow receiver display after the test

On the first smartphone, the official Follow app 1.4.0.1484 initially displayed “Nicht unterstützt” for the sharer. Refreshing reproduced that state briefly. On a later check at 11:08 CET, the same app displayed one sharer, a current-reading field and a populated trend graph. The latest chart sample was timestamped 11:08 CET. No sharing permission or sensor configuration changed during this receiver-side check. The cause of the temporary status and the identity of the uploader were not established.
