# SugarWear collector pause and energy audit

## Verified code paths

- Explicit pause reuses `G7CollectorService.stop` and `G7SessionManager.stop`; it retains the sensor identity/session, authentication state, credential store, Bluetooth bond and reading database. Full unlink remains a separate action.
- Explicit resume reuses `start`; automatic source and boot recovery use `resumeEnabled`, which cannot change a persisted pause back to enabled.
- An active cycle previously could write an enabled snapshot after stop saved disabled. Collector persistence now rejects disabled state, and cycle-local writes also reject invalidated cycle tokens. Pause requests cancel the registered collection coroutine immediately.
- Alarm creation and pause cancellation now share the state lifecycle lock. Late reconnect scheduling cancels its caller; late signal-loss scheduling does nothing. Alarm evaluation/restore/repeat is serialized with pause and policy checks require collection enabled.
- Cleanup telemetry deliberately stays callable during cancellation: throwing there could interrupt GATT disconnect/close.

## Energy findings and changes

- Normal radio reception already uses bounded cycles, a staged next-window alarm, and a cycle WakeLock with explicit release and timeout. Direct reconnect disarms advertisement wake scans between windows. These parameters remain unchanged because real-device radio failures and range recovery need their existing recovery paths.
- Manual pause now reliably removes collector reconnect, advertisement wake, watchdog, signal-loss and alarm-repeat work, stops the foreground service, releases the handoff/cycle WakeLocks, and unregisters the service-owned clock receiver through destruction. It does not wait for a future sensor window to request cancellation.
- Vigil clock updates use system time broadcasts, not a polling loop. Minute updates are required by the bitmap clock providers; they were preserved. Provider activation filtering needs separate evidence before changing it.
- Overview's minute refresh is Activity-visible only and stops in onPause. It now also refreshes glucose freshness, so a paused or aged retained reading cannot stay visually current indefinitely. Pairing's one-second UI refresh is similarly visible-only.
- No scan duration, preconnect lead, retry, radio-recycle threshold, WakeLock timeout, or successful reception cadence was tuned speculatively.

## Validation

Added `G7CollectorPauseTest` for retained sensor state, late snapshot rejection, automatic recovery respecting pause, late scheduling rejection and suppressed alarms. Updated user-status expectations and the alarm-test enabled-collector fixture. Root integration runs the Gradle gates; this worker intentionally ran no Gradle build.

`git diff --check` passed (line-ending warnings only). Battery consumption and pause/resume on a real watch still require hardware validation. No measured battery-saving percentage is claimed.

## Rapid pause/resume follow-up

- State-store lifecycle generations change on enable/disable. An old cycle retains its generation and cannot persist after pause then resume, even though collection is enabled again. Regression: `pre pause callback cannot write after an explicit resume`.
- Invalidated cycle completion releases its owned WakeLock but cannot schedule recovery. Prepare and runtime-repair side effects are serialized with pause. Success fanout and credential callbacks check cycle token/generation while holding the same lifecycle lock.
- The external provider now includes collector_enabled on reading rows as well as the state row. AndroidDirectCgmProvider consumes the state flag; G7LocalReadingResolver excludes paused readings/errors from active candidates and reports NO_SOURCE with PAUSED session state, while retaining graph history.
- Pause/resume reuses the existing database/provider/broadcast/tile notification fanout so consumers refresh immediately on lifecycle changes.
