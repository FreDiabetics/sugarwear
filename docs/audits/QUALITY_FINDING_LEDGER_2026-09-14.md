# Quality finding ledger — 2026-09-14

## Source and total

The authoritative input is 38 `lint-results-debug.sarif` reports produced by
the all-module Android Lint gate at commit `29eeb4b`. They contain 687 results:
682 warnings and 5 hints. There are no Lint errors.

| Rule | Count | Initial ownership and disposition |
|---|---:|---|
| `UnusedResources` | 311 | Mostly WFF resources referenced from raw Watch Face Format XML, which Android Lint cannot follow. Prove WFF reachability before any deletion; remove only ordinary app resources with no runtime/XML reference. |
| `UseKtx` | 102 | Repository-owned style migration. Low risk but non-functional; batch by module after correctness findings. |
| `GradleDependency` | 39 | Dependency/version-catalog maintenance. Upgrade only with release-note and regression review. |
| `DataExtractionRules` | 31 | One G7 and 30 code-free WFF manifests. Explicit backup policy must be declared per package. |
| `NotShrinkingResources` | 30 | Intentional for resource-only WFF APKs: shrinking can remove assets referenced only by raw WFF XML. Retain with narrow module rationale. |
| `HardcodedText` | 25 | Mobile layouts and preview fixtures. Move user-visible runtime strings; preview-only sample content remains scoped. |
| `UnusedAttribute` | 22 | Inspect by API/resource qualifier and remove obsolete declarations when packaging tests stay green. |
| `ObsoleteSdkInt` | 18 | Repository-owned cleanup after confirming each module minimum SDK. |
| `SetTextI18n` | 17 | Replace user-visible concatenation with resources; formatting-only editor/test values need scoped treatment. |
| `VectorRaster` | 14 | Packaging recommendation, primarily Wear resources. Do not alter launcher/notification rendering without image checks. |
| `WearRecents` | 12 | G7 Wear manifest and notification behavior. Review against current Wear task/recents requirements. |
| `NewerVersionAvailable` | 10 | Version-catalog maintenance; not an automatic upgrade authorization. |
| `IconDuplicates` | 7 | Consolidate only byte/semantic duplicates that do not cross launcher density contracts. |
| `IconLauncherShape` | 6 | Validate adaptive icon masks on phone and both round Watch targets before changing. |
| `UseCompoundDrawables` | 6 | UI simplification; preserve accessibility and touch targets. |
| `ClickableViewAccessibility` | 6 | Repository-owned correctness/accessibility issue in Mobile, Wear, G7 and `ui-shared`; high priority. |
| `AutoboxingStateCreation` | 5 | Compose performance cleanup with state-behavior tests. |
| `DrawAllocation` | 4 | G7 graph/loader hot-path allocation; high-priority energy and smoothness work. |
| `TrustAllX509TrustManager` | 4 | All four point to the same BouncyCastle 1.81 dependency JAR, not application trust-manager code. Track dependency ownership; do not suppress application source. |
| `BatteryLife` | 3 | Battery-optimization settings requests in Mobile/Wear/G7. Intentional user-initiated reliability feature; verify call timing and document locally. |
| `SmallSp` | 3 | Visual/accessibility decision requiring round-display validation. |
| `DefaultLocale` | 3 | Repository-owned deterministic formatting fix. |
| `ApplySharedPref` | 2 | Review synchronous durability requirement; keep `commit` only where immediate cross-process visibility is tested. |
| `RtlEnabled` | 1 | Manifest internationalization policy. |
| `Overdraw` | 1 | UI performance cleanup after screenshot comparison. |
| `OldTargetApi` | 1 | Dependency/manifest target review. |
| `DiscouragedApi` | 1 | Inspect exact platform behavior before migration. |
| `ModifierParameter` | 1 | Compose API style cleanup. |
| `UselessParent` | 1 | Safe layout cleanup after rendering test. |
| `VectorPath` | 1 | Visual asset correction with pixel comparison. |

## Module concentration

The primary application reports are Mobile 133, Wear 102 and G7 94. The five
Sugarlicious/Vigil WFF packages contribute 135 results. Remaining results are
spread across shared libraries and the imported code-free WFF collection.

## G7 XML parser output

G7 Lint prints 16 `Content ist nicht zulässig in Prolog` messages without a
source location while completing successfully and emitting 94 normal SARIF
results. All 15 source XML files in `g7watch/src` parse successfully as XML.
There is no repository XML failure to patch yet. The output is therefore owned
as a Lint/dependency scanner investigation; the next diagnostic step is to
isolate the detector using per-rule runs and identify whether binary dependency
inspection (notably the repeated BouncyCastle finding) produces the stderr.

## Priority order

1. accessibility, draw allocations, locale determinism and true obsolete API
   guards;
2. BLE, ProtoLayout, system chrome and notification API migrations;
3. manifest backup/recents policy and ordinary application resources;
4. dependency upgrades and mechanical KTX/style findings;
5. WFF warnings only with WFF-aware reachability and code-free validation.

## Kotlin static-analysis gate

Detekt 1.23.8 and ktlint 1.5.0 (via Gradle plugin 14.2.0) now run across every
Kotlin-bearing module on Java 21. No baseline was introduced. The initial Detekt
inventory contained 574 findings after excluding numeric-literal and line-length
style noise; the enforced, project-specific gate is now clean. Complexity metrics
that are dominated by Compose rendering, protocol parsing, or formatter-dependent
line counts remain inventory concerns rather than build failures. Dead private
members, empty branches, performance traps, malformed naming and correctness rules
remain enforced.

Ktlint initially failed 69 source-set checks and reformatted 336 Kotlin and Gradle
Kotlin files. The repository-wide `ktlintCheck` now passes. Compose naming, stable
subsystem filenames, PascalCase Compose tokens, wildcard-import policy, and maximum
line length are explicitly configured instead of hidden behind a baseline. The
format-only change was followed by a successful 1,505-task `test assembleDebug`
matrix.

During static cleanup, obsolete complication previews, graph helpers, unused graph
constants, stale pairing locals, and unused UI helpers were removed only after a
repository-wide reference check.

## Follow-up remediation — 2026-09-15

Sugarlicious Wear graph and trend images now use request-scoped ProtoLayout
resources. The obsolete `onTileResourcesRequest` pipeline and two unreachable
tile-content implementations were removed. A Robolectric regression test verifies
that the graph PNG is present in the request scope; the Wear unit test, debug APK,
Lint, ktlint and Detekt gates pass without the four former deprecated image-builder
compiler warnings.

The two remaining `ApplySharedPref` findings were classified as intentional
synchronous durability boundaries: settings must be committed before complication
providers are invalidated, and an appearance mode must be visible before the next
activity draw. Both commits now carry narrow, documented Lint suppressions rather
than being changed to asynchronous writes. Sugarlicious Wear explicitly declares
that its current German-only UI does not support RTL mirroring, resolving the
ambiguous manifest policy warning without changing layout behavior.

A fresh all-module Lint run on 2026-09-15 produced 650 warnings and no errors.
The Wear root no longer paints the same background as its window theme, and the
expanded Mobile notification no longer contains a redundant `FrameLayout`. The
notification spacing remains outside the measured value/meta block and is covered
by the existing Robolectric geometry test. These changes remove the `Overdraw` and
`UselessParent` findings, leaving 648 warnings before the next remediation block.

The three adaptive launcher icon `ObsoleteSdkInt` findings are confirmed Lint
false positives. Moving those XML files from their API-qualified resource folders
causes AAPT to omit the adaptive-icon resources and breaks all three application
builds, despite the matching module minimum SDK. The required qualifiers remain.

On this Windows host, Gradle 9.6.1 can finish every requested task and then fail
while replacing its optional `build/reports/problems-report.html`. Verification
runs therefore use Gradle's official `--no-problems-report` switch; this disables
only the incubating HTML Problems API report and does not skip tests, builds, Lint,
ktlint or Detekt.

All 17 `SetTextI18n` findings are now resolved. User-facing Mobile and Wear labels
and Health Connect status text use resources with typed placeholders. Shared color
editor channel labels use one resource format, while numeric editor seed values use
`Locale.ROOT` deliberately so their machine-parsed decimal/integer representation
does not change with the display locale. Unit tests, Lint, ktlint and Detekt pass
for all three affected modules. The running warning inventory is 631.

The sole `DiscouragedApi` finding is also classified and resolved. Android does
not expose `system_app_widget_background_radius` through a public `android.R`
symbol, so the optional framework lookup is retained behind its existing Samsung
fallback and a function-local documented suppression. Mobile tests, Lint, ktlint
and Detekt pass. The running warning inventory is 630.

All 25 `HardcodedText` findings are resolved. Runtime navigation and loading
labels now use resources, and launcher widget previews use explicitly named sample
resources while preserving their exact rendered text. The complete Mobile unit
test suite, debug APK and Lint pass. The running warning inventory is 605.

All 12 `WearRecents` findings are resolved. Every G7 activity now explicitly uses
the empty Wear task affinity, while the launcher activity remains `singleTask`.
Foreground, glucose-alarm and collector-error notifications share one flag-free
navigation intent instead of redundantly forcing `CLEAR_TOP` and `SINGLE_TOP`.
Manifest and navigation regression tests plus the G7 unit-test, Lint, ktlint and
Detekt gates pass. The running warning inventory is 593.

All 31 `DataExtractionRules` findings are resolved. The G7 collector and every
code-free WFF package now explicitly exclude all roots from cloud backup and
device-to-device transfer; legacy backup is disabled as well. The 30 WFF modules
consume one shared resource directory instead of maintaining duplicate policy
files. G7 plus all WFF Lint tasks pass, all 30 WFF release APKs build, and the
code-free verifier confirms 30/30 packages still contain no DEX. The running
warning inventory, freshly recounted across all 38 reports, is 561.

All 22 `UnusedAttribute` findings are resolved without removing modern widget
metadata. The five Mobile widget-provider definitions explicitly declare their
intentional Android 12 API context, while the platform continues to ignore newer
attributes safely on Android 8–11. Lint confirms the findings are gone, every
preview/cell-size/reconfiguration attribute remains packaged, and the Mobile
debug APK builds. The running warning inventory is 539.

All three `SmallSp` findings are resolved. The Widget loading label and the two
metabolic preview annotations now use the Android accessibility floor of 11 sp
instead of 10 sp. Mobile Lint and the debug APK build pass without introducing a
layout warning. The running warning inventory is 536.

Twenty behavior-neutral `UseKtx` findings are resolved across Mobile and G7:
URI parsing, bitmap allocation, view visibility, child presence and BLE scan
record size now use their type-safe AndroidX extensions. The retained
`Uri.fromParts` mail construction remains intentional because it preserves its
component encoding semantics. Both complete unit-test suites, debug APKs, Lint,
ktlint and Detekt pass. The running warning inventory is 516.

Another 27 Mobile `UseKtx` findings are resolved by moving asynchronous
`SharedPreferences.Editor` writes to `SharedPreferences.edit {}`. Each migrated
site retains the former `apply()` durability semantics; no synchronous commit
boundary was changed. Targeted graph, activity, watchface-preset and widget
configuration tests plus the Mobile debug APK, Lint, ktlint and Detekt gates
pass. The remaining `UseKtx` inventory is 55 and the running warning inventory
is 489.

The remaining 12 Mobile preference-editor `UseKtx` findings are resolved. Eleven
asynchronous writes now use the KTX editor while the settings-restore transaction
keeps its direct synchronous `commit()` because its Boolean durability result is
part of the restore contract; that single site carries a documented, local
suppression. The complete Mobile unit-test suite, debug APK, Lint, ktlint and
Detekt gates pass. Mobile now has only two canvas-scoping `UseKtx` findings, the
cross-module `UseKtx` inventory is 43, and the running warning inventory is 477.

All 41 G7 `UseKtx` findings are resolved. Preference writes for BLE wakeups,
collector diagnostics, encrypted credentials, alarms, appearance, graph colors,
error state, recovery windows and runtime state use the KTX editor while keeping
their former asynchronous behavior. The appearance-mode boundary still commits
synchronously through `edit(commit = true)`, and the reading acknowledgement
batch now uses the AndroidX SQLite transaction scope. The complete G7 unit-test
suite, debug APK, Lint, ktlint and Detekt gates pass; G7 reports zero `UseKtx`
findings. The remaining cross-module `UseKtx` inventory is two Mobile canvas
scopes and the running warning inventory is 436.

The final two `UseKtx` findings are resolved by replacing manual Canvas
save/clip/restore and save/translate/restore sequences with scoped AndroidX
helpers. The graph clip and widget trend-arrow transform now restore Canvas state
even if drawing exits exceptionally. The complete Mobile unit-test suite, debug
APK, Lint, ktlint and Detekt gates pass, and both Mobile and G7 now report zero
`UseKtx` findings. The running warning inventory is 434.

Seven application resources with no Kotlin, XML, manifest, preview or build-script
reference were removed: three obsolete Mobile widget/source drawables and four
superseded G7 icon assets. Mobile and G7 resource packaging, debug APK assembly
and Lint pass, and both application modules now report zero `UnusedResources`
findings. WFF resources are deliberately excluded from this deletion block because
Android Lint does not understand every Watch Face Format XML reference. The
running warning inventory is 427.

The Wear application resource inventory is also clean. A superseded unsuffixed
complication-preview series (27 PNGs and 28 XML descriptors), seven obsolete
background/logo/launcher assets, seven unused color tokens and three unused
strings were removed only after repository-wide reference checks. The currently
registered suffixed provider previews remain intact. The complete Wear unit-test
suite, debug APK, Lint, ktlint and Detekt gates pass, and `app-wear` now reports
zero `UnusedResources` findings. The running warning inventory is 355.

WFF resources were audited with a format-aware reachability graph rooted at the
platform-loaded `watch_face_info`, `watch_face_shapes` and `raw/watchface`
resources. Thirty-six resources outside that graph were removed: fourteen legacy
dial/hand/preview assets and twenty-two retired configuration labels. The four
affected release APKs build, all 30 Watchfaces pass the official Google WFF
validator, and the code-free verifier passes 30/30 APKs. The 195 reachable WFF
resources that Android Lint still calls unused are retained as detector false
positives rather than deleted. The running warning inventory is 319.

The Android `UnusedResources` detector is now disabled only for code-free WFF
application modules because it cannot model those platform entry points. Mobile,
Wear, G7 and all library modules retain the detector. All 30 WFF Lint tasks pass;
the official format validator and 30/30 code-free APK verification remain the
authoritative replacement gates. A fresh inventory across all current reports is
122 warnings and no errors.

The 30 WFF `NotShrinkingResources` findings are classified as another format-
specific detector mismatch. A verification experiment proved that removing R8
causes Android-plugin generated DEX to reappear in these `hasCode=false` APKs;
therefore minification remains required to enforce the code-free package contract,
while resource shrinking remains disabled because WFF resources are resolved by
the platform XML graph. Only this detector is disabled for WFF modules. All 30
release APKs rebuild, all 30 Lint tasks pass and the code-free verifier again
passes 30/30. The running warning inventory is 92.

Six `IconDuplicates` findings in the Wear complication picker are resolved
without weakening its provider-specific identity contract. The duplicated B
variant PNG payloads were replaced by distinct bitmap-drawable resources that
delegate to the matching A variant, so each of the 41 providers retains a
unique manifest icon resource ID while identical pixels are stored only once.
The provider-specific preview test, complete Wear unit-test suite, debug APK,
Lint, ktlint and Detekt gates pass; Wear Lint reports no `IconDuplicates`.
The running warning inventory is 86.

The final three `ObsoleteSdkInt` findings are classified as Adaptive Icon
detector false positives. A controlled move from the API-qualified mipmap
folders to `mipmap-anydpi` made all three launcher resources unavailable to
AAPT and failed both resource linking and Mobile compilation. The icons remain
in their platform-valid qualified folders, with path-scoped Lint exclusions in
the three owning application modules. Mobile, Wear and G7 debug APK assembly
and Lint pass, and the full inventory now contains 83 warnings.

All six `IconLauncherShape` findings were WFF false positives: the affected
packages intentionally expose their full-bleed watchface screenshot as both
picker preview and package icon, not as a maskable launcher foreground. The
detector is disabled only for code-free WFF modules alongside the existing
WFF-specific resource exclusions. All 30 WFF Lint tasks pass without changing
preview pixels or package metadata. The running warning inventory is 77.

The single `VectorPath` finding belongs to the approved sensor silhouette used
consistently by launcher, foreground notification, pairing and system-status UI.
Its high-detail outline is intentional design input; automatic point reduction
or rasterization would trade a theoretical parse cost for visible asset drift
and density-specific copies. The exclusion is scoped to that one drawable, G7
Lint passes, and the running warning inventory is 76.

All three `BatteryLife` findings now carry narrow, source-local rationale.
The exemption request remains explicitly user-initiated and protects the core
continuous-glucose/BLE data and safety-alert path from Doze suspension; no
background grant is fabricated and the existing fallback settings routes stay
intact. Wear and G7 background-access tests plus Mobile/Wear/G7 Lint and ktlint
pass. The running warning inventory is 73.

All 13 `VectorRaster` findings are now scoped to static widget, Tile,
complication-picker and WFF preview artwork. These full-surface vectors are not
hot-path icons; raster copies would add density variants and visual drift.
All affected application/library and 30 WFF Lint tasks pass. The running
warning inventory is 60.

The remaining six `UseCompoundDrawables` findings are classified at their
actual layouts: the Wear controls require independently addressable views for
runtime tint/state changes, while the widget-picker preview requires independent
icon sizing. The exclusions are limited to those two roots.

All 36 Android modules now compile against stable API 37 and Mobile targets API
37. Robolectric was upgraded to 4.17 with the JDK 25 test-only module opening it
requires; Compose BOM, Fragment, Glance, Guava and JUnit were updated to their
current stable versions. A stale preview test was aligned with the prior removal
of unreachable WFF layers. The unused `bcpkix` dependency was removed from the
G7 crypto module; its required elliptic-curve primitives remain supplied by
`bcprov`. A full `test assembleDebug lint` run completed 1,865 tasks, and the
follow-up affected-module gate passed. All 38 current Lint reports contain zero
issues.

Detekt now uses `2.0.0-alpha.6`, the release built and tested against this
project's exact Gradle 9.6.1, Kotlin 2.4.10, AGP 9.3.1 and JDK 25 toolchain.
Its configuration was migrated to the v2 schema with strict validation, and the
full analysis passes without the former Gradle-10 deprecation warning. CI now
installs API/build-tools 37, pins every third-party action to a commit and adds a
full-history Gitleaks secret scan.
