# Collector reliability and efficiency audit — 26 October 2026

## Evidence and denominators

Private allowlisted device snapshot captured at 20:16 UTC. The watch clock agrees with the capture clock. Completed attempts, slot summaries and expected-window ledger each retain 192 rows, but their retention edges differ. Raw sensor identities, credentials and glucose values are not committed.

The attempt cohort spans 03:28–20:13 UTC on 26 October: **189/192 SUCCESS_FRESH (98.44%)**. Forty successful cycles preserve an intermediate 133; these are not forty missed readings. Three final failed cycles occurred at 05:48, 05:53 and 18:18 Europe/Berlin (CET). The latter two windows subsequently have SUCCESS_BACKFILL_ONLY; the first remains SCAN_FAILED without a recovered reading in this snapshot. The ledger also contains a reconstructed missed window during an interruption; it is not an additional executed attempt and must not be silently dropped from a continuous-availability claim.

The 16:23–17:18 UTC gap is **not established as deliberate pause**. Device uptime places the latest boot at approximately 16:57 UTC. Our independently saved pause/resume state belongs around 18:26 UTC, a different interval. The reason for the entire earlier gap remains unproven. Do not report the attempt success percentage as uninterrupted wall-clock availability.

## Previous retained cohort

Reopened the 25 October private watch-g7_collector_attempt_history.xml: 131/192 SUCCESS_FRESH (68.23%). It also contains six cancelled, five interrupted and six authentication-failed attempts, across earlier setup, deployment and range tests. The current success fraction is arithmetically 30.21 percentage points higher. These uncontrolled, differently conditioned cohorts **do not establish a 30.21-point causal gain from a particular fix**, and say nothing quantitative about battery savings. A second earlier live-history export has 130 successes, so exports must not be mixed. In the selected 131-success cohort, 77/192 cycles retain a scan start versus 14/192 currently (81.82% fewer scan-bearing cycles descriptively, not equivalent radio-time or battery reduction). Successful-cycle median duration increased from 15.841 s to 30.774 s across these exports; therefore the current data do not justify saying every cycle is faster or total energy is already lower. Current/older diagnostic overhead and timing boundaries must be controlled before assigning that increase to a specific component.

## Current measured timings

- Alarm delivery lateness: median 41 ms, p95 700 ms, n=191.
- Service handoff: median 850 ms, p95 1084 ms, n=191.
- Measurement timestamp to packet receipt: median 8.982 s, p95 14.940 s, n=189.
- Whole completed cycle: median 30.857 s, p95 40.479 s, n=192.

These are distinct intervals. GATT-start-to-cycle-end includes protocol/backfill/retry work and is not radio connected time. Wake acquisition-to-cycle-end does not measure actual release time. Sparse scan envelopes must not be substituted for OS radio counters.

## Proven software causes and corrections

### Redundant ledger decoding in callback path

Every existing-window update loaded and decoded the entire retained history to locate a row, then saveUpsert loaded and decoded it again. The update already holds the shared ledger lock, so the second read adds no consistency. The correction transforms the locked snapshot and saves it directly, skipping writes when unchanged. The regression observes real SharedPreferences reads and fails on the old two-read implementation; the same callback now reads history once. This is **50% fewer full-history decode operations per existing-window update**, not 50% less total CPU or battery. Persistence, process restart behavior, session association and recovery retention are preserved; no cache is introduced.

### Discovery incorrectly counted as fallback

The service cycle only marked a scan as fallback after a connection attempt, while the expected-window ledger unconditionally marked every scan as fallback. Initial discovery therefore inflated fallback and visibility metrics. Both now preserve the same distinction using the recorded preceding GATT attempt count; scan start/end/results and advertisements remain recorded for initial discovery. A regression covers both initial discovery and actual post-connect fallback. Existing persisted observations are not retrospectively rewritten or guessed.

## Remaining radio evidence boundary

The latest failed cycle in this snapshot is 9527, after the latest boot: a direct generation receives no callback within the bounded timeout, followed by an empty fallback scan. The next cycle receives fresh data; the missed reading is backfilled. Earlier 9387 has the same outcome; 9388 never reaches a GATT generation. These facts do not prove that authentication or a stale bond caused the failures. Current retained controller evidence cannot isolate RF, peripheral availability and controller scheduling for every such event.

Previous HCI correlation includes establishment/synchronization failures (0x3E), normal local closes (0x16) and one response timeout (0x22). Local closes are not radio failures. Main-phone Dexcom pause and near-sensor tests previously reproduced transient 133 followed by recovery; neither distance nor main-phone parallel access is a necessary condition. Other centrals were not fully controlled. No speculative reset, bond deletion, longer scans or extra unbounded retries is warranted.

## Energy conclusion

The current UID-wide on-battery report covers 12h35m45.740s and includes UI, watchface/complication work and collection. It reports actual Bluetooth scanning 4m58.246s and the G7Collection lock actual 7326.511s (146 acquisitions). It was captured while charging, with debugging and installations in the observation period. There is no matched previous energy baseline, so **battery savings in percent are not measurable from these data**. The decode reduction above is a directly verified reduction of unnecessary work; total energy improvement requires matched unplugged windows with the same build settings, sensor proximity, UI activity and debugging state.

## Validation

The old implementation failed the initial-discovery and single-history-read regressions. After correction, 42 targeted tests passed (21 backfill/ledger, 18 diagnostic-store, 3 recovery integration), with assembleDebug, lintDebug (no issues), Detekt and ktlintCheck successful. Independent read-only diff review found no critical or important issue. The debug APK was installed on SM-L705F between measurement windows; the first automatic post-install cycle (9567) completed SUCCESS_FRESH at 20:38:19 UTC without opening the app or manually starting collection. Total attempt duration was 17.574 s; first GATT registration occurred 0.996 s after attempt start. Using the same attempt-start boundary, the 148 prior successful single-generation direct/no-scan cycles had median start-to-first-GATT 0.7545 s; the last ten had 0.720 s and median whole-cycle duration 14.698 s. Thus the one post-install result demonstrates reception, not a speed improvement. The separate 16.143 s wake-to-first-GATT median includes time before attempt start and must not be compared with 0.996 s or called preparation latency. It is positive hardware reception evidence, not proof that all NO_CALLBACK/133 causes or total battery consumption are resolved.
