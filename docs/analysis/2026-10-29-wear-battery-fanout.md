# Wear battery investigation — 2026-10-29

## Device evidence (before patch)

Galaxy Watch Ultra SM-L705F, Android package UIDs verified on-device:
Sugarlicious Wear `app.aapswear` (10251), SugarWear `app.aapswear.g7watch` (10267).
Raw snapshots retained in local Temp: `watch-battery-20261029.txt`, `watch-power-20261029.txt`, `watch-alarms-20261029.txt`.

The discharge statistics cover 2h48m58s, not the user's entire day. They include development installations, alarm tests, screen use and Wi-Fi debugging; they cannot establish a normal-day battery percentage or percentage improvement.

- Total discharge 90.6 mAh; screen on 27m30s; screen-off uptime 53m33s.
- Sugarlicious CPU user 41m00s + system 2m09s; own partial wakelock only 2ms. Foreground-service lifetime was almost the whole interval. Lifetime is not CPU or wakelock duration.
- SugarWear CPU user 51m25s + system 5m17s; collection partial wakelock blamed 20m28s, actual 24m05s; Bluetooth scan actual 14m17s over 13 scans.
- Many unselected Sugarlicious provider services had about 300 launches each: Glucose 309, Glucose/Trend 307, Orbit graph 299, large graph 310, multiple TIR/long-text variants about 307–309.

## Proven fan-out and fix

`StateDataLayerService` broadcast every minute and state invalidation to whole provider class lists using `requestUpdateAll()`. `DirectToWatchPreferences` did the same for the direct surfaces. Each request could bind an unneeded service. The per-provider launch pattern corroborates this fan-out on hardware. This does not prove that every such binding rendered a bitmap.

Shared `ActiveProviderUpdates` now tracks exact provider/instance pairs through native activation/deactivation callbacks, with live requests repairing tracking after upgrade. It dispatches `requestUpdate(instanceIds)` only to known active instances. An unknown provider receives a retryable discovery probe with a 60-second coalescing lease. Only an acknowledged deactivation may persist an authoritative empty instance set; a lost delivery cannot suppress updates permanently. Old unacknowledged empty v1 entries are migrated once, retaining known live IDs. The persisted registry survives process death. Previews do not register as active. Repeated activation of the same instance does not write preferences again.

Applied to Sugarlicious minute/state/startup/settings paths and SugarWear direct/clock/hands paths. Existing canonical resolver, clinical thresholds, source selection, BLE scan/retry timings, alarm scheduling, history and foreground-service policy are unchanged.

## Validation

- Regression first failed for the missing active-provider policy, then passed.
- 14 focused tests passed: active-provider targets (1), update planner (8), startup state (3), surface dispatch (2).
- Both Wear APKs built; complications/app-wear Detekt and ktlint checks passed.
- Both APKs installed on Galaxy at approximately 21:12; no statistics reset.
- After unlocking, real Orbit content was visible. Its own providers registered exactly five active assignments: IOB ranged, COB ranged, glucose/trend ranged, age/delta, Orbit graph. Other provider entries remained empty; inactive Vigil providers in SugarWear remained empty.
- Longer uncharged observation is required to quantify battery-life improvement. Do not claim that all battery causes are eliminated or infer a percent saving from foreground-service lifetime.

## Additional measured CPU causes

After the first installed fan-out change, two process CPU samples 56 seconds apart still accumulated 42.51 CPU seconds in Sugarlicious and 17.50 in SugarWear. This disproved the hypothesis that provider fan-out alone explained the drain.

A 12.34-second sampled ART trace from Sugarlicious (`sugar-battery.trace` in local Temp) showed approximately 3.02 seconds inclusive in `CanonicalCgmHistory.merge` and 2.90 seconds inclusive in diagnostic append, of which 2.53 seconds were normalization/sanitization. These are overlapping call-stack costs, not additive battery energy. Native regex compilation alone accounted for about 1.33 seconds exclusive, matcher input setup about 0.80 seconds. Method profiling itself adds overhead.

Follow-up fixes:

- Diagnostic string cleanup reuses one compiled regex and skips matching entirely for ordinary single-line strings. Retained event sanitation, private metadata filtering, retention, deduplication and limits remain in place.
- Canonical history uses a timestamp index to test only possible identity matches within the existing 90-second tolerance. The exact identity and preference functions remain unchanged. Original insertion indices preserve first-match semantics; a replaced timestamp updates the index. A regression test covers a phone correction shifting a timestamp followed by a later watch duplicate and a different sensor.
- Hand-overlay live requests also repair instance registration after upgrade, matching the other providers.

The first fan-out build is not sufficient completion evidence. The expanded build must be installed and measured again.

## Expanded deployment / short follow-up

Both expanded APKs installed successfully around 21:26. Focused history tests: 12 passed; diagnostic-store tests: 3 passed; previous 14 focused dispatch/startup tests also passed. Detekt and formatting checks passed. Sugarlicious lint reported no issues; SugarWear reported one indirect-reference resource warning, corrected by explicitly keeping the string consumed by Wear's static-preview XML.

Non-profiled /proc CPU counters (CLK_TCK=100), both processes unchanged during each interval:

- Before expanded fix, 56 seconds: Sugarlicious 42.51 CPU seconds; SugarWear 17.50.
- After expanded fix, 55 seconds: Sugarlicious 0.52 CPU seconds; SugarWear 0.40.

This is a short operational observation, not a controlled battery benchmark: arrival rate and sensor-cycle phase can differ. Do not translate it to a battery percentage or guaranteed day length. Runtime diagnostics still showed incoming phone traffic and minute complication refreshes. No AndroidRuntime fatal exception was present in the sampled log. Hardware screenshot of Orbit was captured after upgrade.

A follow-up non-profiled interval from epoch 1793305588 to 1793305766 (178 seconds) accumulated 9.67 CPU seconds in Sugarlicious and 14.47 in SugarWear. It includes an actual newly committed phone state (1793305721729) and minute refresh (1793305740515), so the reduced work is not explained by stopping phone reception. Orbit screenshot visibly changed glucose, delta, IOB and COB. Sensor-cycle phases still differ and no controlled battery saving percentage is asserted.

Final resource check: SugarWear lint now reports "No issues found"; final resource-only APK update installed successfully. Both Wear applications carry the CPU fixes. No Bluetooth pairing, sensor configuration, alarm thresholds, or battery-statistics reset was performed.

Pre-merge review corrected discovery acknowledgement: process death/failed IPC now leaves UNKNOWN retryable rather than persisting INACTIVE. Inactive-but-unacknowledged providers may receive a bounded once-per-minute discovery request; correctness takes precedence over eliminating that last small IPC cost. No new timers or wakeups are scheduled.
