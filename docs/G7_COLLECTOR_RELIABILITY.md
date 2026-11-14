# G7 Watch Collector reliability architecture

The Wear OS collector is the only owner of direct Dexcom G7 BLE communication. Mobile, source
resolution and canonical reading identity remain separate and unchanged.

## Root-cause map

| Failure | Detection | Recovery | Durable result |
|---|---|---|---|
| missed alarm/window | runtime reconciliation | reconstruct canonical windows and arm next exact alarm | `SCHEDULING_MISSED` gap |
| process/package/boot interruption | persisted attempt without live coroutine | terminalize attempt, close runtime resources, rehydrate schedule | `PROCESS_INTERRUPTED` |
| no connect callback | bounded generation timeout | close once, fresh generation, then adaptive presence check | `DIRECT_NO_CALLBACK` |
| GATT 133 | callback status | close, stack cooldown, bounded fresh generation | `DIRECT_GATT_133` |
| sensor not advertising | foreign scan results but no known/G7 result | wait for following sensor windows; reduce scans after escalation | `SENSOR_NOT_ADVERTISING` |
| scan radio failure | scan cannot produce usable radio evidence | wait for next window | `SCAN_RADIO_FAILURE` |
| stale callback | callback generation is not active | trace and ignore | no state transition |
| open history gap | canonical expected window without reading | oldest-gap coalesced request after successful LIVE | persisted recovery state |
| incomplete history response | response does not close every window | retain gap; retry on next successful LIVE | `RESPONSE_DID_NOT_CONTAIN_GAP` |
| watch handover | explicit user release | cancel cycle/scan/alarm/backfill ownership; retain readings | `RELEASE_PENDING` |

## Orthogonal state

`G7CollectorHealth` persists five independent truths: sensor identity, sensor availability, BLE
link, collector runtime and data health. Bonding, stored identity and historic authentication never
mean current connectivity. `measuredAt` alone drives `FRESH`, `LATE`, `STALE` or `NO_DATA`.

Recovery progresses through `NORMAL`, `DIRECT_RETRY`, `STACK_COOLDOWN`, `SCAN_RECOVERY`,
`WAIT_NEXT_SENSOR_WINDOW` and `RECOVERY_ESCALATED`. Every recoverable stage returns through a
future expected sensor window. A successful LIVE atomically resets the failure streak and stage.

## GATT lifecycle

Every connection is owned by one attempt and monotonic generation. Timeouts invalidate and close
that generation before a retry. Callbacks from any other generation are recorded and ignored.
Cleanup is idempotent. At most one service cycle and one active generation are allowed.

## Scheduling and persistence

`ensureCollectorSchedule()` is the idempotent lifecycle entry point used after boot, package
replacement and failed service handoff. A future alarm is staged before BLE work. Rehydration
terminalizes interrupted work, reconstructs missing windows within the active sensor interval and
deduplicates windows by sensor, session and expected measurement time.

## Gap and backfill recovery

Each gap persists detection, recovery state, attempt count, first opportunity, latest result and
completion. After each successful LIVE, the oldest open same-session gap chooses one coalesced
history anchor. A response is not success: readings are validated, canonically deduplicated and
committed before matching windows become `RECOVERED`. Missing or partial responses stay open and
are retried at the next successful LIVE.

## Ownership and battery

`releaseG7CollectorOwnership()` stops the current BLE owner while preserving sensor history.
`POSSIBLY_OWNED_BY_OTHER_COLLECTOR` is explicitly heuristic, based on repeated missing callbacks
plus a working radio that cannot see the sensor. After a prolonged sensor-not-visible episode the
collector keeps five-minute expected windows and direct probes, but performs the expensive
presence scan only every third cycle. Any reachability evidence immediately resumes the normal
path.
