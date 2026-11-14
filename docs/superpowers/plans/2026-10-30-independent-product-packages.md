# Independent product packages

Android identities: Sugarlicious Mobile/Wear `app.sugarlicious`; SugarWear `app.sugarwear`; Vigil `app.sugarwear.watchfacepush.vigil`; Sugarlicious faces `app.sugarlicious.watchfacepush.apex`, `.orbit`, `.digital`. Internal Kotlin/component class names retain their compatibility names; external AndroidAPS/Dexcom broadcast contracts remain their real upstream identifiers. These are not application identities.

Install paths are disjoint: Sugarlicious installs only Mobile+Wear; SugarWear installs only collector+Vigil. Optional Sugarlicious faces require explicit user selection/installation and never include Vigil.

Migration: first build/store/install same-signed legacy bridge APKs under existing IDs. Stream private app data only to exact signed new package; decrypt old secrets in old UID, reencrypt under new UID. Preserve sensor session/bond/history; do not unlink or clear. Target checks migration completion before app initialization. Quiesce source writers before snapshot; restore old component states if migration fails. Stop old apps before switching; transfer existing runtime/special grants only where already enabled. Do not run two collectors. Retain old data until new stores and current readings verified. Rebind watchface/widget providers as necessary because Android identities are new.

Validation: targeted migration policy/security/path tests, all affected app/face builds, architecture check, static/Lint checks, product installation/catalog regressions. Verify APK package manifests and no Vigil payload in Sugarlicious Wear. Hardware: marker acknowledgement, preferences/history counts and valid credentials without logging secret values; both products launched and canonical state/collector recovery checked.

## Verification on 2026-10-30

Full build, architecture checks and 1,428 unit tests passed. All three app Lint reports are clean after documenting the required Boolean synchronous-commit checks. Product installer tests, migration rollback/security preflight tests and 45 Watchface rendering/contract tests passed. Official Push validation passed for Sugarlicious faces. APK metadata confirms the new public IDs; Sugarlicious Wear has no Vigil APK/token assets.

Mobile (SM-S948B), Sugarlicious Wear and SugarWear (SM-L705F) each acknowledged their signed migration. Both former application packages are disabled; their data remains for rollback. SugarWear's migrated reading database contains the same 3,569 rows as the frozen source snapshot. New-package foreground collector and reconnect/watchdog alarms were verified. An imported scheduled cycle is reconciled as PACKAGE_REPLACED on first launch because OS alarms are UID-owned and cannot be copied as preferences. Vigil with the new package identity was installed independently. Native widget/watchface provider bindings still require reselection because Android does not migrate those identity-owned bindings.

The new SugarWear package received a fresh sensor reading after migration (under one minute old at verification), returned to WAITING_FOR_NEXT_READING with no collector error, and scheduled its next cycle. The legacy collector process remained absent and its application disabled.

Follow-up: the Mobile BYODA receiver additionally needs the dangerous vendor permission `com.dexcom.cgm.EXTERNAL_PERMISSION`, which the initial Android-only grant allowlist missed. The migration now mirrors that explicitly allowlisted permission only when granted to the source, including on acknowledged-target retries. Nine migration tests pass. The existing device grant was repaired; the next real BYODA cycle reached the canonical state and the visible overview showed current glucose, age, therapy values and the updated graph again. No source selection or stored history was reset.

## Final follow-ups before integration

Missing delta is completed from comparable, valid history immediately after Mobile composition and Wear history merge through the shared model policy. Source-provided delta and known trend remain authoritative; gaps and mismatched source/sensor/session do not create derived values. SugarWear's existing post-backfill recalculation is covered with a real inserted predecessor and immediate reading-observer assertion; its Dexcom trend semantics remain unchanged.

Orbit's upper-right numeric ranged caption uses a wider arc, slightly smaller text and no ellipsis. A rendered-font measurement verifies a full six-digit step count fits. Fixed Graph/TIR slots in the new Orbit package target the new Sugarlicious owner. Legacy native face instances must be replaced/reselected to retire old fixed-provider bindings. The already-approved watchface activation permission is included in migration alongside the vendor broadcast permission; neither is granted when absent in the source.
