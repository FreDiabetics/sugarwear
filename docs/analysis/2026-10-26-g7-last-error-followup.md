# Follow-up of remaining G7 radio error

## Current scope and evidence boundary

Mobile value/dot color work was completed first (6e3a3e12), as requested. The user now reports that automatic reconnection after returning into sensor range works well and does not remain stuck in signal loss. This is current user-observed behavior, not a substitute for retrieving the new attempt history. No collector code was changed during this follow-up.

Both devices are currently unreachable from this workstation. The latest locally retained watch Bluetooth dump is from 21:39 on October 25; the saved watch bugreport was captured around 21:20. They cannot establish which later event was the last error of the night, including the later transient RECOVERING observation during UI deployment. The improved per-generation diagnostic retention is already installed and should permit that later event to be recovered from the watch when reachable.

## Rechecked low-level evidence

Re-decoded the existing watch btsnooz summary, matching only the configured sensor peer and normalizing its previously correlated +3600-second offset. Eighteen matched connection/termination pairs are retained: nine establishment/synchronization failures (0x3E), eight local-host terminations (0x16), and one response timeout (0x22). The 0x16 records must not be counted as nine additional failures: those occur on normal local close paths. A successful LE connection-complete record followed by 0x3E approximately 89-95 ms later is not proof of a usable GATT session or successful authentication.

Examples at 20:28, 20:33, 20:43 and 21:13 show failed establishment followed by a later locally closed session; the 21:03 full-window failure has four establishment failures. Earlier current-sensor readings and per-attempt classifications are needed to label every local close as a successful data cycle. This summary alone must not be used to claim perfect reception.

The controlled primary-Dexcom pause reproduced GATT 133 without that app registered, then recovered in the same cycle. Near-sensor testing at user-reported <=50 cm also reproduced an initial 133 and same-cycle fresh reception. Therefore active main-phone Dexcom and large separation are not necessary conditions. This does not prove that RF cannot contribute, that parallel access can never collide, or that premature application timing is the root cause. The secondary test phone's state during these later observations was not independently verified, so the experiment must not be generalized to the absence of every other possible BLE central.

## Next focused device read

1. Export the current completed and active attempt histories, slot history, expected-window ledger and startup/exit evidence with only the existing diagnostic allowlist; do not reset sensor/bonds or interrupt a working collector.
2. Identify the actual latest completed failing attempt after the evening tests and distinguish it from benign intermediate retries inside SUCCESS_FRESH cycles, package replacement, explicit unlink/setup or confirmed range loss.
3. Correlate its preserved generation timestamps, failure codes, advertisements/RSSI, expected sensor window and subsequent recovered sequence. If controller evidence remains available, align by a known callback rather than assuming wall-clock offsets.
4. Implement a regression-backed correction only if that sequence exposes a software cause. No speculative Bluetooth reset, altered authentication, endless scans or extra unbounded retries are justified by the retained summary.

The final late-night failure remains HARDWARE-READ OPEN; no claim of final root-cause resolution is made. Existing restart and range-recovery fixes remain installed.
