# Phase 8 — Canonical signal and alarm presentation

## Result

- Introduced one transport-independent CGM presentation policy shared by Core, Mobile, Wear, SugarWear and Complications.
- The canonical age states are current through 6 minutes, aging through 12 minutes, stale below 16 minutes, and signal loss from 16 minutes onward.
- A transport disconnect, BLE reconnect or source transition cannot by itself create signal loss.
- Sensor error and no-source remain separate from signal loss.
- Vigil retains its separate no-active-sensor state and uses the canonical policy only for actual signal loss.
- Mobile notification, Wear tiles, glucose cards and complications now expose the same signal-loss boundary.
- Alarm records carry sensor and session identity. Acknowledgement, snooze and deduplication state from an old session cannot suppress a new session.
- The G7 signal-loss monitor and alarm engine consume the same central 16-minute definition.

## Verification

- Added regression coverage for current, aging, stale, signal loss, no source, transport-independent freshness and sensor errors.
- Added an alarm regression proving an acknowledged old-session alarm does not suppress a new sensor session.
- `test` passed across the complete repository after the model extension.
- Core, Dexcom G7, Mobile, Wear, G7 Watch and Complications unit tests passed.
- Mobile, Wear, G7 Watch and Complications Android Lint, Detekt and Ktlint passed (432 tasks).

## Remaining gate item

G7 Android Lint still prints 16 raw XML parser diagnostics while inspecting binary MP4 alarm resources. Lint reports no finding and exits successfully, but the diagnostics remain scheduled for removal in the final warning-free quality phase.
