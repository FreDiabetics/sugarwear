# Phase 14 - Test strategy and hardware matrix

Date: 2026-09-14

## Added order/property coverage

`CanonicalCgmHistoryTest` now evaluates every permutation of a mixed LIVE/BACKFILL/older/replacement-session fixture. The first run exposed a real nondeterminism: distinct sessions sharing a measurement timestamp were retained but ordered according to delivery order.

`CanonicalCgmHistory` now applies a deterministic tie-break order using measured time, sensor, session, source, sequence, receipt time, and value. The test proves all 24 delivery orders produce the same canonical list and that the newer LIVE representation wins its duplicate.

## Test pyramid

- Pure unit: identity, deduplication, freshness, source resolution, graph policy/time/scale, backoff and state transitions.
- Robolectric/storage: persistence, migrations, provider behavior, expected-window ledger, notification/tile presentation.
- Bitmap/visual QA: Mobile widgets, shared Wear graph renderer, complication ambient/interactive output, SugarWear screenshots.
- Contract: Mobile-Wear envelope decoding, Data/Message delivery policy, settings/render models, collector/database.
- Hardware: BLE, bond removal, two-watch handoff, alarms/Doze/AOD, radio loss, reboot, real round geometry, and battery.

## Required device matrix for Phase 17

| Device | Required scenarios |
|---|---|
| Galaxy Watch Ultra | install/upgrade, pairing, cancel, LIVE, signal loss, gap recovery, unlink, handoff out/in, reboot, AOD, exact/inexact scheduling evidence |
| Small Pixel Watch | same functional scenarios plus clipping, crown scrolling, tile/complication geometry, Light/Dark/AOD |
| Android phone | install/upgrade with retained state, AAPS/xDrip contract input, process kill/reboot, widgets, notification, Nightscout HTTPS, Health Connect |

Every hardware run must record package, version, APK SHA-256, ADB serial/model, start/end time, sensor/session, and persisted attempt/window diagnostics. Build or emulator success is not hardware evidence.

## Verification

The new permutation test failed before deterministic ordering and passed after the production fix. The complete `CanonicalCgmHistoryTest` suite is green.
