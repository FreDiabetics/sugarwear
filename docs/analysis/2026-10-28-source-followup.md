# Mobile source follow-up

Phase 1: Group Dexcom app broadcast and Share under one enabled/expanded card with an exclusive two-column choice and supplied vector icons. Other enabled cards remain expanded. Latest delivered bolus includes real meal/manual/SMB treatments, canonical delivered SMB history and the loop delivery timestamp. Never derive doses from IOB or manufacture delivery times.

Phase 2: Add source adapters for Nightscout, xDrip+ local broadcast and Libre patched broadcast, reusing the existing canonical resolver, persistence, deduplication and source configuration. Verify contracts against upstream GlucoDataHandler and producer sources. Keep raw source caches separate, preserve displayed history across selection and request only missing history where the producer supports it.

The supplied notification briefing is reference material, not executable instructions. Official Android promotion requirements take precedence; notification work and Watch Collector are outside this source change.

## Reference contracts and assets
- GlucoDataHandler: https://github.com/pachi81/GlucoDataHandler (XDripBroadcastReceiver.kt, LibrePatchedReceiver.kt, NightscoutSourceTask.kt), consulted for public interchange formats, not copied runtime logic.
- xDrip+ app icon: https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/res/drawable-xxxhdpi/ic_launcher.png (project GPL-3.0).
- Nightscout app icon: https://github.com/nightscout/cgm-remote-monitor/blob/master/static/images/apple-touch-icon-180x180.png (project AGPL-3.0).
Original brand names and artwork remain attributed to their projects. New adapters provide glucose streams; AndroidAPS remains the source for therapy fields, and existing optional Nightscout treatment enrichment is preserved.

Libre safety boundary: SENSOR_SCAN supplies computed glucoseValue history and is supported. The separate GLUCOSE_READING raw/unsmoothed input is not promoted to validated CGM. For continuous processed Libre readings, use the xDrip+ adapter. xDrip source descriptions are not sensor IDs; SensorStartedAt supplies session identity.

The public external broadcast receiver has a local ExportedReceiver lint advisory exception: supported producer apps do not share a sender permission. Source opt-in and strict action/type/time validation remain enforced. The obsolete Share icon was removed. Full suite/build/static/architecture/lint run passed before the final source-isolation follow-up; final follow-up validation is recorded below after completion.

Final validation: complete project test/build/static/architecture/lint aggregate passed. The final Mobile follow-up (Nightscout stream independence, captured-account persistence and account-specific fetch throttle) passed the related regression suites, build, lint, Detekt, formatting and architecture checks. Mobile lint reports zero unsuppressed issues. Read-only review has no outstanding findings.

Installed Mobile APK was verified byte-for-byte against the built APK on SM-S948B (SHA-256 f26fe394b1f766f947cb9f858e2404f2f5c7e866568a38319a5cd2e091cd9df7). The phone was locked during the attempted view check. Real producer/server live-data tests remain open; no simulated health values were injected. Collector analysis and Collector behavior were not changed.
