# Mobile sources and display completion plan

## Final authorized scope

- [x] Local Dexcom G7 protected broadcast contract, adapter, receiver, validation and source attribution.
- [x] Dexcom Share HTTPS login, encrypted credentials, both timestamp wrappers, bounded requests, duplicate transport row filtering, incremental gap retrieval and retry backoff.
- [x] Independent AndroidAPS / Broadcast / Share CGM switches; multiple-active warning lists sources; central choice is newest valid fresh measurement.
- [x] AndroidAPS therapy selection independent from CGM; source transitions preserve previously displayed CGM and therapy archives and only fill historical gaps.
- [x] Raw source streams remain separate. Share accounts are partitioned by a nonsecret identity hash, archived on change and protected against late replies and interrupted canonical cleanup.
- [x] Sources live directly in General as separate compact cards with icon, name and master switch. Only required settings expand. Account editing is compact with Cancel/Save.
- [x] Widgets inherit only Mobile Hell/Dunkel mode; individual detail settings remain independent.
- [x] Existing SugarWear/Vigil warning, pause/attempt, metadata, system typography, icons and defaults work retained.
- [x] Mobile progress rings enlarged responsively, including fitted primary/secondary text and proportionally sized icons.
- [x] Settings page titles neutral/green by halves; back icon neutral; submenu compact title fades without extra local movement or scale.
- [x] Vigil analog-mode lower delta enlarged and strengthened, units remain 23 design units, lower block moved down.
- [x] Focused code review and corrective account isolation / HIGH-boundary regression coverage.
- [ ] Finish project-wide checks and Git integration to main.

## Explicitly deferred by user on 2026-10-28

- Installation of the final completion build on devices.
- Any further visual/device inspection.
- Collector analysis and new Collector investigation, to resume in a few days.

Previously performed live evidence is recorded in docs/analysis/2026-10-27-share-timestamp-format.md and 2026-10-27-mobile-cgm-dot-visibility.md. Existing Collector changes in this checkout are preserved in the authorized commit; no new Collector analysis is included in this completion phase.
