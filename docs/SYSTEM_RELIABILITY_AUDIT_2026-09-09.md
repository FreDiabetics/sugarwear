# System reliability audit — 2026-09-09

## Scope and repository state

- Target branch: `feature/sugarwear-product-and-vigil-recovery`
- Pull request: draft PR #68
- Baseline HEAD: `8dba63116f1a22c46aebf14db4b10047fadb122b`
- Merge policy for this audit: no merge
- Hardware availability during the audit: no ADB device connected

The current branch already contains the relevant commits from the older collector diagnostics
branches. No collector branch was merged or cherry-picked for this patch.

## P0 findings and repairs

### Wear source changed to “Other”

The failure was not caused by AndroidAPS changing its configured source. Two legacy migration
paths could manufacture `OTHER`: a restored direct-to-watch snapshot was stripped of its reading
but retained an `OTHER` source label, and old Wear source preferences could still restore
`AUTOMATIC` or `WATCH` even though Sugarlicious Mobile is now AndroidAPS-only.

The repaired contract is:

- Mobile always publishes `WatchDataSource.PHONE`.
- Wear persists and restores `PHONE` for the Sugarlicious data-source setting.
- A removed legacy direct reading is represented as AndroidAPS-configured but without data; the
  resolver state remains independently `NO_SOURCE`.
- `OTHER` remains available only for a genuinely unknown automatic technical state, never as the
  result of sanitising a legacy Direct-to-Watch payload.
- Direct-to-Watch collector history is not added to the Sugarlicious Mobile graph.

### SugarWear graph scale did not change the rendered viewport

The scale label and tap path changed the stored duration, but the graph view read the setting a
second time during drawing. This created two competing sources of truth and allowed the label and
effective render window to diverge. The graph now renders the validated duration bound with the
same snapshot that produced the control label. The cycle is `1h → 2h → 3h → 6h → 12h → 24h → 1h`.

### Tile did not follow the watch font setting

The actual ProtoLayout text builder did not select the Android system sans-serif family, and the
resource version could leave an older tile tree cached. Both SugarWear and Sugarlicious Wear tiles
now select the platform `sans-serif` alias and have new resource versions. No bundled or custom
font is introduced; the platform alias is the hook through which the watch's configured system
font is resolved.

## Architecture assessment

The canonical source resolver already models `MOBILE_PRIMARY`, `MOBILE_DEGRADED`,
`WATCH_DIRECT`, `MOBILE_RECOVERY`, and `NO_SOURCE`. Configuration and runtime availability are
separate concepts. Mobile transport, Wear restoration, complications, and graph history were
audited at their boundaries so a missing reading does not rewrite the configured source.

Graph time projection uses measurement/event timestamps. Live viewports advance from wall-clock
time and do not rewrite `eventTimestamp` or `receivedAt`. Existing collector code retains the
expected-window ledger, bounded reconnect recovery, 24-hour backfill request support, session
separation, and delta recomputation from adjacent sensor measurements.

## Verification matrix

Classification: **A** = automated test passed, **S** = static/build validation passed,
**H-pending** = requires connected hardware and was not claimed as tested.

| # | Scenario | Result |
|---:|---|---|
| 1 | Fresh Mobile is primary in automatic resolver mode | A |
| 2 | Missing fresh Watch stream becomes NO_SOURCE | A |
| 3 | Mobile recovery requires two distinct fresh readings | A |
| 4 | Duplicate sensor timestamp is deduplicated | A |
| 5 | Different known sensor IDs are not deduplicated | A |
| 6 | Different known session IDs are not deduplicated | A |
| 7 | Phone disappearance during recovery does not flap source | A |
| 8 | Phone reachability is not used as source identity | A |
| 9 | End-to-end Mobile/Watch failover journey stays duplicate-free | A |
| 10 | Stale, session-change and out-of-order source journey stays safe | A |
| 11 | Legacy Mobile Direct preference publishes PHONE | A |
| 12 | Legacy Mobile snapshot sanitises to AndroidAPS, not Other | A |
| 13 | Legacy Wear Direct snapshot removes reading without creating Other | A |
| 14 | Legacy Wear source selection cannot override PHONE policy | A |
| 15 | PHONE-configured no-data complication stays AndroidAPS + NO_SOURCE | A |
| 16 | Mobile primary history excludes Watch Direct duplicate graph dots | A |
| 17 | Out-of-order history is sorted and invalid points excluded | A |
| 18 | Same-source correction replaces older received copy | A |
| 19 | Timestamp-tolerant phone duplicate wins while a real Watch gap remains | A |
| 20 | Incoming history closes an existing graph gap | A |
| 21 | Display history deduplicates and remains bounded | A |
| 22 | Sensor-error samples never enter canonical graph history | A |
| 23 | Reading batch round-trip only deduplicates identical IDs | A |
| 24 | Delta accepts adjacent sensor readings | A |
| 25 | Delta rejects sensor errors and implausible values | A |
| 26 | Session changes never create synthetic history gaps | A |
| 27 | Collector graph never mixes sensor sessions | A |
| 28 | Fresh-cycle classification rejects aged packets received now | A |
| 29 | Signal loss starts at 16 minutes, not before | A |
| 30 | Stale glucose triggers signal loss, not glucose/rate alarms | A |
| 31 | Signal loss does not clear an active high alarm | A |
| 32 | Glucose alarm priorities are mutually exclusive | A |
| 33 | Alarm acknowledge/repeat does not duplicate activation | A |
| 34 | Invalid packet does not resolve last valid alarm | A |
| 35 | Alarm notification restores after process restart | A |
| 36 | Session change cancels alarms belonging to old sensor | A |
| 37 | Every collector alarm maps to its dedicated bundled sound | A |
| 38 | Notification channels remain silent for app-played alarm sounds | A |
| 39 | Test alarm does not mutate live alarm state | A |
| 40 | Pairing deadline survives rehydration | A |
| 41 | Initial pairing scan permits the full 30-minute window | A |
| 42 | Enabled collector restores after boot | A |
| 43 | Enabled collector restores after package replacement | A |
| 44 | Unrelated broadcasts do not restore collector | A |
| 45 | Stale active collector attempt is closed as hung | A |
| 46 | Scheduled-cycle lateness survives receiver/service handoff | A |
| 47 | Two valid high readings activate high range | A |
| 48 | One valid high reading does not activate high range | A |
| 49 | In-range reading immediately clears high range | A |
| 50 | Two valid low readings activate low range | A |
| 51 | Duplicate/backfill/invalid events do not advance range debounce | A |
| 52 | Stale time does not erase semantic range state | A |
| 53 | Wear rehydration preserves identity and recalculates freshness | A |
| 54 | Stale complication keeps payload but renders stale | A |
| 55 | Invalid delta is not invented by complication presentation | A |
| 56 | Live Mobile graph advances on minute ticks without a reading | A |
| 57 | Irregular, missing, duplicate and out-of-order timestamps retain spacing | A |
| 58 | Stale latest collector dot leaves a real gap to now | A |
| 59 | SugarWear scale cycles through all six durations and wraps | A |
| 60 | Bound SugarWear duration is the effective graph viewport | A |
| 61 | Tile text selects the platform system-family alias | S |
| 62 | Tile resource versions invalidate the previous cached tree | S |
| 63 | Mobile, Wear, collector and complication debug builds compile | S |
| 64 | Mobile, Wear, collector and complication lint completes | S |
| 65 | Direct-to-Watch WFF release package builds | S |
| 66 | All WFF XML documents pass the official validator | S |
| 67 | Direct-to-Watch WFF APK contains no `classes*.dex` | S |
| 68 | Graph scaling visibly changes history on Galaxy Watch | H-pending |
| 69 | Tile visibly follows selected Galaxy Watch system font | H-pending |
| 70 | Tile visibly follows selected Pixel Watch system font | H-pending |

## Executed quality gate

- Unit tests: 689 passed across core model, G7 foundation, protocol, storage, shared UI,
  complications, Mobile, Wear, and SugarWear.
- Debug builds: Mobile, Wear, SugarWear/collector, and complications passed.
- Lint: Mobile, Wear, SugarWear/collector, and complications passed.
- WFF: release build passed; every repository WFF document passed the pinned official validator;
  Direct-to-Watch package is code-free.
- Whitespace/conflict check: passed.
- Hardware: not executed because `adb devices -l` returned no connected device.

## Remaining evidence boundary

The graph-scale and tile-font corrections are automated/build verified, but their final optical and
interaction behaviour is not labelled hardware-verified until a watch is connected. In particular,
the selected OEM/user font must be visually checked because the final mapping of Android's
`sans-serif` alias is implemented by the watch firmware.
