# G7 collector: historical audit and radio critical path

## Evidence and limits

Captured from the Galaxy Watch Ultra on 2026-10-25, before the changes below. Raw exports stay outside the repository because they contain personal sensor data. SQLite integrity check passed.

- Measurement database: 2,000 rows, 2026-10-18 22:18 UTC through 2026-10-25 11:18 UTC. The declared 30-day retention was silently overridden by a 2,000-row cap (less than seven days).
- Detailed completed attempts: 192 rows, approximately 2026-10-24 11:43 UTC through 2026-10-25 11:18 UTC. There is no defensible ten-day connection-event history in this export. Deleted records cannot be reconstructed.
- Old session: 1,758 stored readings, 1,319 valid LIVE; 433 BACKFILL including invalid readings. There are 116 gaps over 7.5 minutes between stored valid LIVE readings. The largest is about 670 minutes; missing process/event history prevents attributing that entire interval to a radio failure.
- Current session: 242 stored readings at the capture boundary, 14 valid LIVE and 228 BACKFILL. Most of those historical measurements predate watch setup. They are not evidence that the watch missed 228 scheduled cycles.
- The 192 attempts include 146 SUCCESS_FRESH, 16 FALLBACK_SCAN_FAILED, 14 GATT_CONNECT_FAILED, six CANCELLED, six AUTH_FAILED, three PROCESS_INTERRUPTED and one NO_ADVERTISEMENT. Installation interruptions, explicit setup and rejected old-sensor candidates must not be counted as steady-state failures.
- The retained Android process-exit history contains 14 package-update stops, one Settings-origin force-stop, and one LOW_MEMORY exit (2026-10-23). It contains no crash/ANR record. This does not prove that no earlier crash ever happened; it separates the available exit evidence from radio failures. The existing scheduled-window recovery is required after low-memory process death.

The earlier search/unlink fixes are already installed and merged in PR #90. In particular, initial discovery now has real Android scan filters instead of an unfiltered screen-off scan; rejected authentication candidates cannot become the active sensor.

## Timing findings

For 173 recorded deliveries, alarm lateness was median 26 ms, P95 238 ms and maximum 668 ms. Service handoff was median 437 ms. The exported evidence does not show an AlarmManager sleep/wakeup failure.

Recorded connect-start minus alarm delivery was median approximately 7.3 seconds, minimum 5 seconds and P95 approximately 22 seconds. These old timestamps include diagnostic work before actual Android radio registration, so they are preparation-path measurements, not exact physical radio-start measurements.

During the live audit, attempts 9177–9179 reproduced NO_CALLBACK followed by an empty fallback scan. Fallback started approximately 17–18 seconds after the expected sensor timestamp. Each following cycle repeated direct-first because the final `G7-BLE-FALLBACK-107` replaced the original NO_CALLBACK classification with OTHER_GATT_ERROR. The bounded scan recovery policy was therefore not selected until the third complete failure. A previously armed safety alarm also retained the old strategy's shorter lead after health changed.

## Changes

1. Register `connectGatt` before diagnostic persistence and protocol-state persistence. The direct-start telemetry now timestamps the actual call boundary rather than preceding JSON work.
2. Bluetooth connection callbacks enqueue immutable status/state and original epoch/elapsed arrival times. Persistence and failure classification run on the collector coroutine, not Android's callback thread. Callback duration excludes subsequent diagnostic processing.
3. Invalidate the connection generation, disconnect and close before diagnostic persistence. A diagnostic delay cannot keep a failed connection registered while recovery is waiting.
4. Preserve NO_CALLBACK/GATT-133 as the failure cause when the subsequent bounded fallback is empty. Authentication failures retain their own classification.
5. After one NO_CALLBACK cycle, use the existing bounded presence-first strategy for the next sensor window. Healthy direct connections retain the short lead; presence-first recovery gets the existing 30-second lead. Do not start healthy direct attempts 30 seconds early: their 20-second callback bound could expire before the sensor advertises.
6. Replace the already armed safety alarm for the same canonical next window using the new health/strategy. Never cancel before replacement, create an extra window, or reset the sensor.
7. Skip serializing an unchanged reconciled ledger.
8. Raise the measurement row ceiling to 10,000 so the existing 30-day age retention can actually retain 8,640 five-minute readings with room around sensor transitions. Existing data and schema remain intact. Detailed JSON event history remains bounded; it has not been expanded into repeated multi-megabyte callback writes.

## External comparison and Samsung settings

- [xDrip source, pinned revision](https://github.com/NightscoutFoundation/xDrip/tree/c42cf55c6b972215654bc2a34ce563c87a7aa594): phase-aware reconnect, bounded scan fallback, wake handoff and actual alarm-delay measurements are applicable patterns. Its Samsung wakeup workaround uses AlarmClock for measured late wakeups; it is different from Special Pairing.
- [xDrip maintainer discussion about Android 16](https://github.com/NightscoutFoundation/xDrip/discussions/4425): Special Pairing should not be enabled indiscriminately on Android 16. Bond-loss behavior and extra pairing delays are not a universal Samsung G7 fix.
- [Juggluco G7 timing documentation](https://www.juggluco.nl/Jugglucohelp/sensorhelp.html): its alarm-clock option addresses sleeping watches failing to wake for reconnect; backfill can hide these failures in a filled graph. Its author reports that enabling it can worsen phone behavior. The source uses a roughly 27.5-second alarm lead for that mode. Libre-specific Bug/Turbo options are not G7 remedies.
- [Samsung application management](https://developer.samsung.com/mobile/app-management.html): sleeping/deep-sleep exceptions are relevant. On this watch, Nearby Devices, exact alarms, foreground operation and wake locks are allowed, and SugarWear is battery-whitelisted. These observations do not justify disabling global Doze or resetting Bluetooth.
- DiaBox's current Android collector implementation was not available as inspectable source, and direct G7 support could not be established from its developer documentation. No unverifiable "DiaBox trick" was transplanted.

The official G7 APK can provide additional app-side protocol/timing reference. It does not reveal sensor firmware or replace observation of the actual BLE exchange. Do not alter the working Dexcom phone app or current sensor credentials to obtain it.

## Acceptance evidence

Targeted regression coverage includes actual Robolectric GATT registration/callback/close ordering, policy selection after NO_CALLBACK, preservation of authentication classification, ledger/backfill reconciliation, lifecycle recovery and retaining more than 2,000 historical readings. Build, lint and static checks, installation boundary and subsequent live observations are recorded below once executed. Long-term radio reliability cannot be established by a short build or installation test.

- CODE/BUILD VALIDATED: 102 targeted tests, zero failures/errors; `:g7watch:assembleDebug`, lint, Detekt and ktlint passed. Independent review found no important issues.
- INSTALLED on the Galaxy Watch Ultra with `adb install -r`, preserving the active sensor. APK SHA-256: `A6C65A7326821562079641BF9135713B4EFD50CBD4BA4895C1D40AED9A0B4D02`.
- HARDWARE OBSERVED: first post-install cycle 9182 stored a fresh LIVE reading at 11:53 UTC after one successful presence scan; cycle 9183 stored the next fresh LIVE reading at 11:58 UTC via direct reconnect. No unpair, credential reset or manual sensor setup was performed. This is a short functional check, not proof of multi-day reliability.

## Scan callback follow-up

Final path inspection found the same persistence-before-handoff pattern in scan completion. A successful scan now captures its counters/timestamps in an atomic pending value, stops the platform scan and resumes its result without writing diagnostics. The collector flushes that value exactly once after actual GATT registration/physical cleanup. Failed scans flush on the collector coroutine before propagating their failure. `SENSOR_FOUND` persistence is deferred to the same safe boundary.

Late stale callbacks are explicitly forbidden from consuming this pending handoff or changing the pending state flag. The real scanner regression test verifies result delivery, stopped scan, refusal of a stale callback, then exact-once flushing at a genuine collector radio boundary. The 103-case targeted suite passed; both critical-path cases passed again after the stale-callback guard, as did build, lint, Detekt and ktlint. Independent final review found no remaining important issue. Installed final Collector APK SHA-256: `3D23684273D4E86F0CF4F95000AA390A5EAE668E2C26A32A4716FFC61F607507`.

## Watchface follow-up

The live Orbit screenshot also exposed why all trend arrows remained small: the neutral provider uses a common square canvas wide enough for two arrows, while the face still allocated the old 32-unit square image box. The face now compensates its glucose ranged-value image container by 125/60 around the same center. Visible arrow height is therefore the intended 32 units, including doubles without horizontal compression. Other providers keep their old image bounds. ApeX uses the same host-side compensation. No Mobile preview preference or provider style setting controls these dimensions.

Both face builds and the official WFF validator passed; 33 existing ApeX/Orbit layout tests passed. Both updated watchfaces were installed. An actual post-install Orbit screenshot shows the single arrow at the intended larger height; a live double-arrow comparison remains dependent on real trend data.

The ordinary glucose ranged-value provider uses the same neutral canvas, so the host's glucose-range discrimination does not accidentally enlarge a full-size ordinary glucose icon. Short/long ordinary glucose icons are unchanged. Ten complication icon tests, complication lint/static checks and Mobile/Wear/Collector builds passed. Mobile, Wear and Collector were reinstalled with this shared icon-contract follow-up. Final Collector APK SHA-256: `3228B41F20045B9DFB4A72A46FB3B59D7D60B052ACF3EF831D614A715B4C6A68`.

## Automatic startup between sensor windows

Attempts 9186 and 9189 were unscheduled automatic startup cycles after package replacement, not scheduled lost sensor windows. Their direct timeout and empty fallback occurred during the normal silent interval. The next scheduled presence-first cycles 9187 and 9190 each found one advertisement (RSSI -80/-84 dBm) and stored fresh LIVE data. This distinction prevents interpreting package-restoration work as a lost clinical measurement window.

Verified the actual boot/package path calls `G7CollectorService.start()`. Automatic restoration now waits for the next known window when the current sensor/session has a validated, non-future LIVE value and its next lead is still in the future. Backfill, invalid data, another session, explicit manual/restart commands and actual delivered window alarms do not use this shortcut. Transient scanning state is cleared and the service displays WAITING_FOR_NEXT_READING.

The known expected timestamp is rearmed explicitly through the existing scheduler, rather than passed through missed-window advancement. A regression at 269.5 seconds after the previous reading proves that the minimum alarm lead cannot skip the next window. Focused lifecycle/recovery tests, build, lint, Detekt and ktlint pass; independent review found no remaining important issue.

Installed final startup-fix APK SHA-256: `56665B62B7B591BC4563F17D7451EC151FFB9309D8A94FB3EF1E2D43F7F020E7`. At 12:35 UTC, after replacement in the middle of the interval: fresh sensor data retained, WAITING_FOR_NEXT_READING, zero active attempts and the correct next 12:38 UTC window armed. No intermediate scan or new pairing was started.
