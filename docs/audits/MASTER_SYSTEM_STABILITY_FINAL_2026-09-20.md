# Sugarlicious master system stability — final candidate report

## Candidate

- Branch: `feature/master-system-stability`
- Base: `main` at `d588a266`
- Merge/push: intentionally not performed before user acceptance
- Product watchfaces: Sugarlicious Digital and Vigil; the third WFF module is the validator fixture

## Root causes and changes

### Core data and resolver

- Canonical identity now uses sensor, session and measurement time; transport sequence numbers remain metadata.
- Freshness is based on `measuredAt`, predictions remain separate, and source recovery requires distinct readings.
- Mobile persistence reconciles interrupted multi-store writes through one monotonic canonical revision.

### Transport and Wear

- Revision deduplication occurs before expensive resolver, alarm and surface side effects.
- Provider access has typed empty/unavailable/denied/malformed outcomes and bounded diagnostics.
- Tile and complication invalidation is targeted to the changed domains.
- The retired Watch-history acknowledgement protocol, pending provider, receiver and database index were removed.

### Collector and backfill

- One runtime owner, bounded callback queues, stale-GATT generation rejection and Bluetooth-restart recovery are enforced.
- Persistent session-aware gaps trigger coalesced recovery after suitable LIVE contacts without a blind timer.
- Sensor release clears active credentials/runtime/retry/scan/session state while preserving historical readings.

### Signal and alarms

- All surfaces consume the same current/aging/stale/signal-loss/sensor-error/no-source policy.
- A transport or BLE disconnect alone cannot create CGM signal loss.
- Alarm acknowledgement/repetition is bound to sensor and session.

### Graphs, Tiles and Watchfaces

- SugarWear app and Graph tile share the same canonical history, graph input and renderer.
- Bitmap tests prove visible graph content at compact and Galaxy-size tile dimensions.
- IOB, COB and basal can be selected in every non-empty combination with deterministic centered layouts and persistent migration.
- Retired watchfaces and their release/selection/packaging paths were removed; Digital and Vigil remain active.

### Background and energy

- The Mobile foreground notification no longer rebuilds on diagnostics changes or the minute clock; it updates for a new glucose measurement or changed display preferences.
- Only inherently time-sensitive widgets retain minute-aligned refresh.
- Active scans, GATT waits, WakeLocks, alarms and UI refresh jobs were checked for ownership, timeout/cancellation and backoff. No new polling was introduced.

## Quality comparison

| Gate | Baseline | Candidate |
|---|---:|---:|
| JUnit | 823/823 | 826/826 |
| Suites | 148 | 150 |
| Failures/errors | 0 | 0 |
| Skips | 0 | 0 |
| Lint findings | 0 | 0 across 11 active Android reports |
| Detekt | clean | clean |
| ktlint | clean | clean |
| WFF validator | 30/30 historical modules | 3/3 active modules |
| DEX-free WFF | 30/30 historical APKs | 3/3 active APKs |

The reduced WFF count is intentional: 27 retired modules were proven unreachable and removed instead of being revived from historical tests. The clean final Gradle matrix executed 1,177 tasks and completed `test`, all Debug and Release assemblies, `lint`, `detekt` and `ktlintCheck` successfully.

The unchanged MP4 alarm files can still make Android Lint's XML parser print 16 unclassified raw stderr lines on an uncached G7 lint analysis. They are not findings in any report and the master specification explicitly prohibits replacing, transcoding, suppressing or hiding those working resources solely for cosmetic console output.

## Hardware evidence

- Galaxy S26 Ultra (`SM-S948B`): candidate Mobile APK installed; version `0.6.4`/code 14 and installation time verified; launcher process remained alive and no new fatal exception or ANR was observed in the smoke window.
- Galaxy Watch Ultra (`SM-L705F`): candidate Sugarlicious Wear, SugarWear/G7 Collector, Digital and Vigil packages installed with data-preserving replacement. Both launch intents completed without a new fatal exception or ANR in the smoke window; no pairing or collector action was triggered.
- Pixel Watch: deliberately untouched.
- No sensor was disconnected, released or re-paired automatically.
- Long-running BLE, sensor handoff, recovery SLA, AOD and battery claims still require the user's real-device acceptance run.

## Acceptance boundary

The source candidate is fully green and suitable for controlled hardware testing. It remains unmerged. After the Galaxy Watch candidate is installed and the user accepts the hardware behavior, rebase/merge into current `main`, rerun the final gate on `main`, and only then delete the feature branch/worktree.
