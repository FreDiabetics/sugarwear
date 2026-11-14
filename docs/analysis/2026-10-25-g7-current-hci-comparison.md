# G7: current original-app HCI comparison (2026-10-25)

## Evidence boundaries

The secondary Galaxy S24 runs the user-provided G7 1.6.1 test APK without Frida Gadget. The same signing certificate allowed replacing the Gadget build without clearing app data. Gadget was removed for the controlled comparison after startup ANRs and a hidden-API exception in obfuscated code. A specific anti-instrumentation trigger has not been proven.

The user confirmed that the Watch was outside sensor range during the failed collection windows around 18:43 and 18:48. Those failures cannot be used as evidence of an in-range collector regression.

Private HCI captures, bugreports and Watch diagnostic snapshots remain outside the repository under the user's reverse_engineering/captures/2026-10-25 directory. No credentials or raw medical packets are committed.

## HCI collection

- HCI logging: Enabled (full, not filtered); stack logging: Verbose.
- Android's always-available BLE scan kept the stack alive during an earlier simple toggle. Temporarily disabling that preference permitted confirmed OFF -> ON; its original value was restored immediately.
- Samsung's additional HCI socket switch is enabled, but TCP 8872 still closes immediately on this build. Live socket export is not claimed operational.
- A subsequent bugreport contains FS/data/log/bt/btsnoop_hci.log and its rotated .last file. These are actual full captures; unlike the embedded diagnostic summary, neither contains truncated records.
- Initial full files: current 96,814 bytes, rotated 344,559 bytes. The subsequent stable-cycle export contains a current file of 866,542 bytes. Its rotated file is the same older capture and must not be counted as independent evidence.
- Samsung vendor diagnostic packets and ACL continuation fragments must not be interpreted as standalone ATT PDUs. Attribution uses the observed sensor connection handle and actual ATT channel. Absolute HCI timestamps require correlation with Android wall-clock logs before comparison; relative protocol timings are used here.

## Observed protocol versus SugarWear

| Operation | Original application capture | Existing SugarWear implementation |
| --- | --- | --- |
| Current glucose | Control write 0x4e, matching indication | GLUCOSE_REQUEST = 0x4e; validate and publish live reading before history |
| Plain history | Control write 0x59, 9-byte request | G7CollectorBackfillProtocol.request builds the same opcode and little-endian clocks |
| Plain history delivery | 18-byte notification, then 9-byte notification: three records | AndroidG7Ble splits complete multiples of RECORD_BYTES = 9 |
| History range | 600-second inclusive range; three records, each 300 seconds apart; all within requested bounds | Bounded start/end and per-record sensor-clock validation |
| History completion | 0x59 control indication after the notifications, about 116 ms after request | Existing stream consumes records until 0x59 completion with bounded timeout guards |
| Other history transfer | 0x51 plus a separate longer transfer | Not interchangeable with the plain 0x59 path; encrypted payloads are not guessed or fed into the plain parser |

## Watch diagnostic snapshot

The saved 192-attempt history contained 131 successes, 49 recoverable errors, six cancellations and six fatal errors. These counters alone do not identify a current code defect. Earlier failures include missing advertisements, GATT 133, interrupted processes and authentication failures; the latter must be correlated with sensor/session changes rather than combined into one radio-failure category.

Six consecutive successful validated live cycles were recorded through 18:38 before the subsequently confirmed out-of-range period. Automatic recovery after returning to range was observed at 18:57:13: attempt 9261 completed SUCCESS_FRESH, with a validated live sample stored locally, without a manual reconnect or sensor reset.

## Completion status

- Full current HCI export: verified through actual untruncated files.
- Original-app current-read and plain-backfill wire format versus existing code: verified as described above.
- Frida decryption/application hooks: not active and not claimed complete.
- New collector behavior fix: none justified solely by these captures. Do not change authentication, scan timing or packet decoding based on the out-of-range failures.
- End-to-end recovery after the user-confirmed range loss: observed successfully in attempt 9261.
- Historical failure audit remains open: this capture does not justify claiming every older failure fixed.


## Follow-up: proved Vigil update and stale-history defects

The user observed a signal-loss label and only one old dot after an otherwise successful collection. Two actual code paths explain these symptoms:

1. `resolveWatchDirect` deliberately enters NO_SOURCE when the latest direct reading is stale. The history merge used only `canonicalSource == WATCH_G7_DIRECT` as its inclusion gate, so it threw away all persisted direct history precisely at signal loss. The header retained its last value, and the graph appended that single value as its only dot. WATCH_ONLY now retains direct history independently of freshness; NO_SOURCE and the original measurement timestamps remain unchanged. Regression coverage includes six graph windows and explicitly asserts NO_SOURCE.
2. Database publication notified Sugarlicious Wear (`app.aapswear`) and tiles, but the active Vigil WFF points to providers hosted by SugarWear (`app.aapswear.g7watch`). The compatibility receiver requested components in its own Sugarlicious package. Consequently, the actual providers could retain old images until their next system refresh. The existing post-commit/coalesced database publication now directly requests the Vigil providers in the collector package as well. No timer or extra polling is introduced, and historical batches retain their single fanout.

The Vigil signal-loss label is explicitly red in active and ambient rendering. This does not change sensor state, authentication, freshness, alarm thresholds or timestamps. Regression tests verify red pixels and immediate requests for the collector-hosted header, graph and ambient graph components.


### Verification and device proof

The initial update/history fix passed 49 targeted tests (provider outcome, canonical history, Vigil rendering and collector database), build, Detekt, ktlint and Lint (no issues). Installed on Galaxy Watch Ultra without sensor reset. At 19:13:20 attempt 9265 completed SUCCESS_FRESH; the immediately captured Vigil frame showed 0m and the retained multi-point history. Raw screenshots remain private.

Live provisional publication is additionally coalesced until the matching persisted sensor state has been saved. This prevents the first update racing an UNKNOWN/previous error state before optional backfill completes. A regression checks that a coalesced pair of inserts produces no early Vigil request and exactly one header request after the block.


## Follow-up: collector radio recovery fixes

Two additional defects were found in the real AndroidG7Collector loop (not UI code):

1. A cached earlier GATT-133 exception could replace a later terminal failure after a successful fallback scan/reconnect. A real Robolectric BluetoothGatt sequence reproduces two 133 responses, one successful discovery, then missing G7 service. Before the fix it incorrectly returned G7-GATT-133; after the fix it returns G7-GATT-204 and closes every platform client. Authentication errors at the same terminal boundary likewise retain their actual cause rather than being hidden behind stale radio state. Existing empty-fallback classification and bounded retries are preserved.
2. The bond-reconnect catch delayed and published RECOVERING before its finally block closed the old GATT client. It now closes immediately on entering that catch, before persistence, delay or another connection. A regression injects the real bond-reconnect exception at the registered-client boundary and checks that the platform client is closed when RECOVERING is delivered. Existing cleanup remains idempotent.

These are code-path/reproduction-proven defects; the retained historical logs do not permit assigning every old 133 or authentication error to them. Out-of-range failures, package replacement and low-memory process deaths remain separately classified. No sensor reset, bond deletion, authentication bypass, unbounded scan or new polling was introduced.


Radio-fix validation: the reproduced terminal-cause test failed on the previous implementation (expected G7-GATT-204, returned G7-GATT-133) and passed after removing the stale exception replacement. Both new radio regressions and the adjacent GATT critical-path/recovery/reliability tests passed. Collector build, Detekt, ktlint and Lint passed with no lint issues. The APK was installed with no active collection attempt and over two minutes remaining before the next window. The first scheduled cycle after installation (attempt 9269, started 19:33:02) completed SUCCESS_FRESH and returned to WAITING_FOR_NEXT_READING. Real-device GATT-133/bond-loss injection was not performed on the active sensor; those branches are covered by the controlled BluetoothGatt regression tests.


## Follow-up: scan lifetime and imminent alarm recovery

- A live, explicitly bounded scan could be cancelled by the generic two-minute progress watchdog while still inside its advertised 30-minute discovery window. A regression reproduced HEALTHY_ACTIVE being incorrectly returned as STALE_ATTEMPT. The inactivity exception now requires a real live cycle, matching active attempt, SCANNING connection/protocol/diagnostic stages and finite scan/attempt deadlines within the maximum discovery duration. Dead processes, expired/missing/oversized deadlines, mismatched attempts and non-scan stalls remain recoverable; an active scan still requires its future safety alarm.
- The same runtime assessment incorrectly rejected an already armed alarm during its final 30 seconds because it required pendingTime > now + overdueTolerance. A second regression reproduced HEALTHY_ARMED being returned as RECOVERY_REQUIRED. A still-future armed alarm now remains valid until its due time; overdue tolerance continues to apply to missed-delivery classification. Tests cover 1 ms, 10 s and 30 s before delivery, including an active collection cycle.

Validation: 43 focused runtime, ordered receiver, integration and lifecycle tests passed, together with collector build, Detekt and ktlint. Both regression cases failed before their respective fixes and pass afterward. Installed without an active collection attempt. A proposed physical process-death test was deferred: the next normal cycle encountered 133 with a confirmed sensor advertisement at -96 dBm, and no fresh idle boundary was available. No process termination, sensor reset or bond deletion was performed during that observation. Physical recovery proof for this final change remains pending a stable in-range baseline.


### Stale-data startup must retain the next real sensor window

A further live observation showed an unscheduled automatic package-restoration attempt at 19:47:01 after the last valid LIVE reading was already stale. A valid upcoming window existed before that restoration. The old startup guard only waited during the first five-minute interval after a fresh reading; after signal loss it attempted connection in the silent interval and subsequently advanced recovery to the later 19:52:36 trigger. This could unnecessarily skip an already armed opportunity.

Automatic service restoration now reuses the existing scheduler to reassert a future canonical pending window from the current sensor/session, even when the latest validated LIVE reading is stale. It validates sensor/session identity, expected-window identity and requested/expected ordering. Delivered reconnect envelopes, explicit manual/restart requests and initial discovery remain outside this shortcut. Fresh startup without a persisted envelope retains the original sensor phase. Tests reject old-session, due/expired, malformed, disabled, missing, BACKFILL and future-measurement cases.

A fresh in-range cycle 9273 succeeded before final installation (advertisement RSSI -81 dBm). Final physical process-death recovery verification follows that idle boundary; no sensor or bond state is deleted.


Final restart-path validation: 46 focused runtime, receiver, integration and lifecycle tests passed, as did collector build, Detekt, ktlint and Lint (no issues). Independent focused review found no blockers. After final installation, the collector process was terminated via its own UID without force-stop. Android recreated the process automatically (PID 32309 -> 32479); it returned to WAITING_FOR_NEXT_READING with no active attempt and retained the same expected next sensor window (the trigger adjusted from scan lead to healthy direct lead). WLAN debugging disconnected around that next window, and port 44961 closed, so the subsequent fresh reading has not yet been verified. This is a hardware-evidence limitation, not a claimed successful final recovery cycle. Sensor/session credentials and bonds were not reset.


Hardware follow-up after reconnecting WLAN debugging: persisted attempts 9274 and 9275 both completed SUCCESS_FRESH in the automatically recreated process, with consecutive measurement sequences 338 and 339. Attempt 9274 used DIRECT_ONLY_SUCCESS; attempt 9275 used DIRECT_RETRY_SUCCESS. The collector is back in WAITING_FOR_NEXT_READING. This completes the physical process-death/restart reception check without manually starting the app or resetting sensor/session/bond state. It does not constitute a full OS-reboot test or proof that every historical radio failure is eliminated.


## Full watch reboot: foreground-start timeout reproduced

The subsequent full OS reboot changed boot ID from 702e5a55-248c-4fe1-b539-49fa4c5d0088 to 59a992c6-82d4-40e1-96b8-31bb0303b803. At uptime 313 seconds no new attempt after 9275 existed; the retained reconnect timestamp was overdue and AlarmManager had only the signal-loss alarm. This hardware test failed, unlike the preceding process-death test.

The system broadcast history contains an ANR for G7CollectorService: startForegroundService did not call startForeground in time. Its recorded main stack is rendering DirectToWatchStatusComplication / SystemTypography while handling a complication update. The recreated process was serving tiles, not running the collector. Changes move live DirectToWatch complication resolution/rendering to Dispatchers.IO, promote the service immediately in onCreate before retained-store restoration, and restore the durable reconnect alarm in the boot receiver before requesting the foreground service. An asynchronous foreground timeout is not caught by the existing synchronous launch failure handler. No app UI was launched to obtain this evidence; no sensor/session/bond was reset. Repeat full-reboot proof is pending installation of this correction.

Correction validation: 43 focused runtime/lifecycle/receiver tests passed; collector build, Lint, Detekt and ktlint passed, including complications Detekt/ktlint. The corrected collector APK was installed successfully; it restored WAITING_FOR_NEXT_READING without opening the UI. A second full reboot was then initiated for the hardware acceptance test.


Repeat full-reboot hardware acceptance passed: boot ID 4910e3ab-96f6-454e-9bf4-58d44dd83992 differs from the failed boot. At uptime 683 seconds, persisted attempts 9276 and 9277 both completed SUCCESS_FRESH after this boot, with consecutive sensor sequences 344 and 345. Their strategies were DIRECT_RETRY_SUCCESS and FALLBACK_SCAN_SUCCESS, respectively; measured packet ages were 5 and 7 seconds. No SugarWear UI or manual collector-start command was used. ActivityManager confirms G7CollectorService isForeground=true, foregroundId=7001, and AlarmManager contains the next G7ReconnectReceiver alarm. State is WAITING_FOR_NEXT_READING. This verifies autonomous fresh reception following the tested complete watch restart; it is not a claim of exhaustive long-term radio reliability.


## Reception after returning Dexcom to the main phone

The user confirmed the main-phone Dexcom app was receiving again after watch sequence 350. The first subsequent collector attempt 9283 failed with GATT status 133; the matching sensor advertisement was observed at -87 dBm. This does not establish phone-app contention as the cause, and an earlier failure also preceded the switch. Without opening SugarWear, restarting the collector or changing pairing, attempts 9284 and 9285 then both completed SUCCESS_FRESH directly from the sensor, with consecutive sequences 352 and 353 and packet ages 15 and 5 seconds. Strategies were DIRECT_ONLY_SUCCESS and DIRECT_RETRY_SUCCESS. Final state is WAITING_FOR_NEXT_READING with the next reconnect timestamp staged. Thus concurrent main-phone Dexcom use and autonomous recovery are observed working in this sample, but uninterrupted reception is not proven: sequence 351 was missed live, and the initial radio failure remains explicitly recorded.


## Targeted investigation of attempt 9283 / Android GATT 133

A private watch bugreport was captured at `C:/Users/typ1d/AppData/Local/Temp/g7-watch-133-20261025.zip`. It contains an embedded btsnooz summary, not a full untruncated HCI log; decoded locally with the official AOSP decoder. Controller connection/disconnection events needed for this investigation are present. Peer addresses were matched privately against the configured sensor and were not exported to Git.

After correlating the watch trace's +3600-second timestamp offset against the final persisted GATT callback, the four sensor handles 35, 36, 37 and 38 each show LE connection complete status 0 followed by disconnection reason 0x3E after approximately 90-96 ms. The final controller disconnection at normalized epoch 1792958606.283 precedes the application's final GATT callback at 1792958606.299 by 16 ms. This identifies controller-level establishment/synchronization failure before a usable GATT/authentication session, rather than a G7 parser or stored-key rejection. Bluetooth SIG defines 0x3E as Connection Failed to be Established / Synchronization Timeout: https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Core_v6.3/out/en/architecture%2C-change-history%2C-and-conventions/controller-error-codes.html . This does not distinguish weak RF/interference from simultaneous connection timing; it does not prove Dexcom app contention.

A separate reproducible diagnostic defect destroyed the evidence of individual generations: completed-attempt compaction retained only the first and final three events, while cycle aggregates overwrite each connection result. A regression expected eight request/result events for four generations but lost them before the fix. Compaction now retains up to twelve connection events plus the entry and terminal context (at most sixteen events, original ordering), leaving generic event noise compacted. Retry limits, sensor identity, bonding and authentication remain unchanged. This is an observability fix, not a claimed software cure for physical 0x3E failures. Existing bounded retry and subsequent-window recovery have already been demonstrated receiving fresh values with main-phone Dexcom active.

Validation: the new retention regression failed on the old compaction and passes with the correction. All 22 targeted diagnostics/radio-recovery/critical-path tests, collector build, Detekt and Lint passed. Four consecutive successful cycles 9284-9287 were confirmed with main-phone Dexcom active. Installation was deferred while attempt 9288 was CONNECTING and performed only after it returned to WAITING_FOR_NEXT_READING with no active attempt and a future slot more than 45 seconds away. APK installation succeeded. The underlying RF/simultaneous-access distinction remains unproven; no speculative Bluetooth reset, re-bond or unbounded retries were added.


## Controlled main-phone Dexcom comparison

Both watch and main phone were read simultaneously; main phone identified as SM-S948B, running com.dexcom.g7. Local Bluetooth dumps are retained outside Git. At 21:03 the main phone's successful sensor connection ended at 21:03:16.820; it immediately rearmed background connection and then logged repeated 0x3E establishment failures to the same peer. The watch's four failed direct starts at 21:03:18.451, 20.875, 23.141 and 26.097 overlapped this retry activity. This is correlation, not proof of causation. Successful following windows also coexist with phone connection/reconnection activity.

For a controlled single-window comparison only com.dexcom.g7 was force-stopped on the main phone; Bluetooth, pairing, sensor session, watch collector and AndroidAPS were left unchanged. Bluetooth's app record confirms the Dexcom client died at 21:35:36.885 and no Dexcom client was registered again until 21:38:25.232 after automatic app restoration. During that absence, watch attempt 9290 still reproduced GATT 133: generation 2 registered at 21:38:01.255, controller disconnect reason 62/0x3E at 21:38:07.608, callback duration 6377 ms. Generation 3 started at 21:38:09.363 and succeeded in 205 ms, delivering valid LIVE sequence 358 with sensor age 5 seconds. The attempt finished SUCCESS_FRESH. Dexcom was automatically reopened at epoch 1792960701.626 and its process and subsequent sensor connection were confirmed.

This falsifies the main-phone Dexcom app's parallel access as a necessary cause of the observed class of 133/0x3E errors. It does not exclude a contribution to particular collisions and does not prove weak RSSI alone. The remaining distinction is RF/body attenuation/interference versus controller/sensor readiness timing; no speculative protocol/key/bond changes are justified by this comparison. A fixed near-sensor comparison has been requested to constrain the RF variable.


Near-sensor observation: user specifies sensor at rear of left upper arm, watch at most approximately 50 cm away (three-quarter arm length). With main-phone Dexcom confirmed running, attempt 9291 reproduced initial GATT 133 after 7167 ms (generation 4; actual connect registration epoch 1792960981.094), followed by generation 5 success in 635 ms at 1792960990.565. Valid LIVE sequence 359, sensor age 6 seconds, was stored and the collector returned to WAITING_FOR_NEXT_READING. Neither large physical separation nor active main-phone Dexcom is a necessary condition for the transient error. Short distance is not a measured RF-quality guarantee; this direct-only cycle has no advertisement RSSI sample. The repeat pattern (early first connection fails, subsequent connection succeeds near the sensor cadence) warrants readiness/timing investigation, but does not establish a software timing root cause by itself. No speculative reconnect-delay or authentication changes were made.
