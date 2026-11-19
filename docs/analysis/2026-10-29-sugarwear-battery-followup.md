# SugarWear remaining battery work — 2026-10-29

## Hardware evidence

Galaxy Watch Ultra SM-L705F, `app.aapswear.g7watch`, same device as the preceding fan-out investigation. Existing battery statistics retained, no sensor pairing/reset, no alarm or BLE-policy changes.

- The automatic attempt around 21:32:54 completed successfully at 21:33:09.789. No collection wakelock remained afterward.
- A short idle trace showed Tile rendering and data-provider reads, with repeated Samsung One asset reads for individual text lines.
- The sampled active-cycle trace `g7-cycle-followup2.trace` in local Temp covered 50.21 seconds including the subsequent successful attempt, completed at 21:38:17.837. Diagnostic append accounted for roughly 6.665 seconds inclusive thread CPU; normalization/sanitization 3.119 seconds and JSON decode 2.627 seconds. These nested costs must not be added together. No leaked partial wakelock was present afterward.
- The unsuccessful 20ms profiling attempt produced an empty file and was excluded from conclusions. The valid active-cycle trace used 10ms sampling.

## Changes

### Diagnostic append

The existing local bounded event store now sanitizes retained legacy content once, recorded with a versioned format marker; every newly received event still passes the same complete sanitation and sensitive-key removal. Each append no longer sanitizes all 1,000 retained events again.

A bounded private append cache is keyed by the exact stored serialized payload. New store instances in the same process reuse it. A changed payload, clear, restore or failed previous write cannot make unrelated cached data authoritative. Public snapshots decode separately, so public callers cannot mutate the trusted append objects. Retention, duplicate-ID ordering and limits remain unchanged. Clearing removes the marker and retained cache.

This shared storage fix also applies to Sugarlicious Wear; both Wear APKs are updated so the same cost is not left behind there.

### Tile font assets

The SugarWear glucose tile retains only its regular/bold device-owned Samsung One typefaces per service instance. It reloads after an Android configuration change. Text size and density are still applied freshly for every layout; native fallback and visible rendering are unchanged. No font files are redistributed.

## Checks before installation

18 focused tests passed: diagnostic store (4), tile presentation (11), trend safety (2), typeface reuse/configuration invalidation (1). The typeface regression test first failed for the absent cache, then passed.

Both Wear APKs built. Wear-storage/G7 Detekt and ktlint passed. Wear-storage, Sugarlicious Wear and SugarWear lint reports all say `No issues found`.

Both updated APKs installed successfully around 21:46. A post-install natural sensor-window check is pending below; no extra manual scan is used.

## Post-install natural window

Attempt 10156 started at 1793306875474 and completed at 1793306889550 (about 14.1 seconds), classified `SUCCESS_FRESH`. Final stages CONNECT_REQUEST, VALIDATION, STORE and COMPLETE were successful. Persisted state returned to WAITING_FOR_NEXT_READING and a subsequent power dump showed zero partial wakelocks. No manual scan was triggered.

The first post-upgrade active-cycle method trace was empty and excluded. A subsequent valid 26.15-second idle trace showed Tile layout about 0.103 seconds inclusive, with no diagnostic append hotspot in that quiet interval. This does not quantify an active-cycle CPU or battery percentage saving. Functional full-cycle hardware proof is provided by the durable attempt ledger and state; the performance cause and code-path reduction are source/profile validated. All profiling was stopped after capture.
