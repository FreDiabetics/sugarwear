# Sugarlicious/SugarWear project inventory (2026-09-14)

## Baseline

- Repository: `FreDiabetics/sugarlicious`
- Branch: `feature/sugarwear-product-and-vigil-recovery`
- HEAD: `5e30c411c423a0753977c4bac3f6d8de5c96f8b0`
- Upstream state at inventory start: 15 commits ahead
- Working tree at inventory start: clean
- Open pull requests: PR #68 only; it is a draft and was not merged
- Gradle wrapper: 9.6.1

The baseline command `test assembleDebug :app-mobile:lintDebug :app-wear:lintDebug :g7watch:lintDebug --continue --no-daemon --no-problems-report` completed successfully with 1,565 actionable tasks. The `--no-problems-report` switch is required locally because an old generated `build/reports/problems/problems-report.html` cannot be replaced or have its ACL read. This does not disable tests, compilation, Android Lint, or APK assembly.

## Repository scale

The build currently contains 45 included Gradle modules:

- 12 shared/application modules
- 30 resource-driven Watch Face Format modules
- 3 JVM build/validation tools

Source inventory:

| Area | Production code files | Test code files | Main resources |
| --- | ---: | ---: | ---: |
| All included modules | 214 | 146 | 508 |
| `app-mobile` | 62 | 50 | 93 |
| `g7watch` | 47 | 32 | 24 |
| `dexcom-g7` | 28 | 6 | 0 |
| `core-model` | 24 | 21 | 0 |
| `app-wear` | 17 | 14 | 160 |
| `wear-storage` | 7 | 6 | 0 |
| `complications` | 7 | 5 | 18 |
| `data-source-aaps` | 6 | 6 | 0 |
| `ui-shared` | 5 | 1 | 7 |
| JVM tools | 7 | 3 | 0 |

The WFF modules contain no Kotlin or Java production code. Their Android manifests and XML/resources are nevertheless runtime entry points and must be included in reachability and dead-resource analysis.

## Module responsibility map

| Module group | Current responsibility | Principal consumers |
| --- | --- | --- |
| `core-model` | Canonical CGM, graph, freshness, alarm, trend, and scale domain rules | all source, storage, UI, and protocol modules |
| `dexcom-g7` | Sensor protocol, authentication, message parsing, and G7 domain models | SugarWear collector and Wear protocol |
| `data-source-api` | Common phone-side source boundary | AAPS and xDrip adapters |
| `data-source-aaps` | AndroidAPS broadcast/provider adapter | Mobile runtime |
| `data-source-xdrip` | xDrip adapter | Mobile runtime |
| `wear-protocol` | Phone/watch serialized state and transport contract | Mobile, Wear, SugarWear |
| `wear-storage` | Persisted Wear state and phone therapy input state | Wear, complications, SugarWear |
| `ui-shared` | Shared graph renderer and presentation primitives | Mobile, Wear, complications, SugarWear |
| `complications` | Therapy and Direct-to-Watch complication providers and local reading resolution | Wear and SugarWear APKs |
| `app-mobile` | Source ingestion, dashboard, widgets, settings, Health Connect, and phone-to-watch fanout | Android phone |
| `app-wear` | Sugarlicious Wear UI, tiles, complication hosting, Watch Face Push, and Data Layer runtime | Wear OS companion |
| `g7watch` | Standalone SugarWear UI, G7 collector, BLE lifecycle, pairing, alarms, recovery, tiles, and diagnostics | Wear OS standalone collector |
| `watchfaces:*` | Code-free WFF packages and resources | Wear OS watchface runtime |
| `tools:*` | CWF parsing, WFF generation, and screenshot comparison | build and visual validation |

## Dynamic entry-point inventory

Dead-code analysis must account for the following non-source references:

- Android manifest activities, services, receivers, and providers
- boot and package-replaced receivers
- exported source integration components
- Wear Data Layer message and data listeners
- tile provider services
- complication provider services
- Watch Face Push assets and slot identifiers
- WFF manifests, XML expressions, resources, and preview assets
- permission-protected SugarWear broadcasts and provider access
- exact/inexact alarm `PendingIntent` targets
- WorkManager workers
- preference and DataStore serialization keys

The most entry-point-heavy runtime surfaces are `app-wear`, `g7watch`, `app-mobile`, and `complications`. These modules require manifest-to-class and external-contract reachability checks before deletion or renaming.

## Persistence and state surfaces

The project currently combines several persistence mechanisms:

- multiple named `SharedPreferences` stores for dashboard, display, diagnostics, pairing, collector state, alarms, widgets, and appearance
- Preferences DataStore in `wear-storage`
- the SugarWear reading database and persisted expected-window/attempt diagnostics
- serialized phone/watch protocol state
- file-backed settings backup and treatment enrichment caches
- durable Wear Data Layer items plus low-latency messages

This is not itself a defect, but it creates a high-priority ownership question: every persisted value must have one authoritative writer, a documented migration rule, and an explicit rehydration/reset policy.

## Background and energy-sensitive entry points

Confirmed mechanisms requiring later lifecycle and energy review include:

- SugarWear foreground collector service
- Sugarlicious Wear foreground Data Layer service
- Mobile persistent bridge foreground service
- bounded collector and alarm-handoff wake locks
- BLE direct connection and fallback scans
- advertisement-based wake path
- exact/inexact reconnect, watchdog, alert, and alarm scheduling
- Health Connect periodic WorkManager work
- minute-aligned Mobile and SugarWear UI refresh loops
- 30-second Wear UI refresh loop
- complication `requestUpdateAll` fanout
- Glance widget `updateAll` fanout
- simultaneous durable DataClient and low-latency MessageClient delivery

Every occurrence is an audit candidate, not a presumption that the mechanism should be removed. Medical-data freshness, Doze reliability, and battery cost must be evaluated together.

## Structural hotspots

The largest production files are:

| Lines | File |
| ---: | --- |
| 1,781 | `app-mobile/.../DashboardCharts.kt` |
| 1,452 | `app-mobile/.../SugarliciousColorSettingsPanel.kt` |
| 1,388 | `g7watch/.../G7CollectorService.kt` |
| 1,291 | `app-mobile/.../ComplicationCatalog.kt` |
| 1,188 | `complications/.../TherapyComplications.kt` |
| 1,074 | `app-mobile/.../SugarliciousWidgets.kt` |
| 1,065 | `app-mobile/.../DashboardViews.kt` |
| 1,026 | `g7watch/.../AndroidG7Ble.kt` |
| 824 | `app-mobile/.../PersistentBridgeService.kt` |
| 812 | `g7watch/.../G7WatchActivity.kt` |
| 772 | `app-wear/.../StateDataLayerService.kt` |
| 771 | `complications/.../DirectToWatchComplications.kt` |
| 746 | `app-mobile/.../SugarliciousOverviewScreen.kt` |
| 714 | `app-mobile/.../WidgetInstanceConfiguration.kt` |
| 701 | `app-wear/.../WearSettingsActivity.kt` |

File size alone does not prove a defect. These files are priority candidates for responsibility mapping, characterization tests, and carefully bounded extraction because they combine high line count with central runtime responsibilities.

## Initial dependency observations

- `core-model` is the intended shared domain base.
- `data-source-api` exposes `core-model`; both phone source adapters expose `data-source-api`.
- `wear-protocol` exposes both `core-model` and `dexcom-g7`, so protocol consumers inherit sensor-specific types. This coupling must be justified during architecture analysis.
- `wear-storage` exposes `core-model` and is itself exposed by `complications`.
- `app-wear` and `g7watch` both consume protocol, storage, shared UI, sensor/domain, and complication functionality.
- Mobile consumes both phone source adapters, protocol, storage, shared UI, and the G7 domain module.
- Dependency versions are declared independently in module build files rather than a version catalog. This is a maintainability and drift candidate, not an automatic requirement to migrate.

## Phase-1 outcome

The project inventory is sufficient to begin the reproducible quality classification. No production behavior was changed in this phase. The next phase must classify current lint, compiler, Gradle, and test-harness findings before adding suppression, deleting code, or restructuring any hotspot.
