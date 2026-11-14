# Mobile history continuity and Share duplicate transport rows

Verified runtime cause: raw AndroidAPS cache spans 23.92 h; Share cache and canonical display only 14.67 h despite about 290 rows. Interval histogram contains 101 zero-second gaps and additional one-second gaps. A 288-row Share request is not equivalent to 288 distinct five-minute measurements.

Implemented:
- Deduplicate same-value Share/Broadcast transport rows within five seconds, without merging differing values.
- Bounded Share response handling supports up to 2000 rows and requests a larger initial missing-day window; live server can still cap returned rows.
- After successful reading, request only the elapsed missing/recent interval; reuse cached timestamps across restarts when a complete day is already stored.
- Source composition and display archive use the existing Mobile 2000-point budget, not the generic 300-point default.
- Restore historical gaps from already persisted raw inputs only through the previously published timeline. Disabled sources cannot insert newer live points.
- Existing displayed records win over overlapping backfill; retain original raw source streams.
- Renderer already draws every valid visible sample; full-day overlapping-stream regression remains covered.

UI: Share account collapsed into a compact account/status row; edit fields appear only on Change, rounded controls, small Cancel/Save actions.

Device proof after recovery: canonical source DEXCOM_SHARE, 288 displayed points spanning 23.92 hours, 310 therapy history entries, insulin/carbs/basal still present. Fresh local Broadcast received alongside AndroidAPS and Share (all measurement age 3 minutes in the same check).

Source-card follow-up: each source has its own rounded surface, monochrome icon, name and right-side master switch. AndroidAPS expands glucose/therapy choices, Share expands compact account and Edit form, Broadcast has no unnecessary expanded body. Master toggle remembers AndroidAPS field choices and preserves other enabled sources; two regression tests passed.
