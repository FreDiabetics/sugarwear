# Sugarlicious/SugarWear quality baseline (2026-09-14)

## Verification result

The following baseline completed successfully on Gradle 9.6.1:

```text
test
assembleDebug
:app-mobile:lintDebug
:app-wear:lintDebug
:g7watch:lintDebug
```

Result: `BUILD SUCCESSFUL`, 1,565 actionable tasks.

The optional Gradle problems HTML report was disabled because a generated report from 2026-09-05 exists at `build/reports/problems/problems-report.html` with ACLs that prevent replacement and ACL inspection. The report collision caused the first otherwise-complete run to exit unsuccessfully. No source, test, lint, or assembly task failed. The successful rerun disabled only problems-report generation.

## Android Lint totals

| Module | Issues | Errors | Warnings | Hints |
| --- | ---: | ---: | ---: | ---: |
| `app-mobile` | 135 | 0 | 130 | 5 |
| `app-wear` | 102 | 0 | 102 | 0 |
| `g7watch` | 94 | 0 | 94 | 0 |
| Total | 331 | 0 | 326 | 5 |

## Finding classification

### Confirmed project findings

- Four draw-time allocations in `G7GlucoseChart` and `G7IndeterminateLoader` are performance candidates.
- Five custom touch/click paths across Mobile, Wear, and SugarWear require `performClick` accessibility verification.
- Twelve SugarWear Wear-recents findings identify activities or activity-starting intents without explicit task-affinity handling.
- `G7AppearanceStore` performs one synchronous preference commit. Its durability requirement must be checked before converting it to an asynchronous write.
- SugarWear lacks Android 12+ `dataExtractionRules` despite defining backup behavior.
- The Wear activity layout reports a potential double-painted background.
- Mobile uses resource-name lookup in widget code, reducing compile-time resource verification and optimization.
- Mobile contains locale-sensitive number formatting findings.
- Mobile contains 25 hardcoded user-visible strings and Wear contains four non-resource `setText` values.
- Three modules request battery-optimization exemption. This may be intentional for reliability but requires Play policy, user-consent, and measurable battery justification.
- Five Sugarlicious WFF module build configurations enable minification for debuggable builds. AGP disables those optimizations and reports the configuration as contradictory.
- The root build uses deprecated Kotlin DSL delegated container creation at `build.gradle.kts:182`, scheduled for removal in Gradle 10.

### Dependency/toolchain findings

- Robolectric and Conscrypt invoke restricted native-loading methods under the current Java runtime.
- Wear ProtoLayout protobuf invokes a terminally deprecated `sun.misc.Unsafe` method.
- BouncyCastle 1.81 contributes the three `TrustAllX509TrustManager` findings. These locations are dependency JARs, not project-defined trust managers.
- Guava and Robolectric have newer available versions. Availability alone is not a reason to upgrade; compatibility and regression checks are required.
- Build output reports newer SDK/target versions. Android 37 adoption requires a separate compatibility decision rather than a blind version bump.

### Reachability-required findings

- `app-wear` reports 73 unused resources, dominated by complication preview XML/PNG assets, icon variants, and theme resources.
- `app-mobile` reports four unused resources, including one generated Watchface preview asset.
- `g7watch` reports four unused sensor/icon resources.
- Preview, manifest, WFF, launcher, notification, dynamic identifier, and marketplace-asset paths must be checked before any deletion.
- Six duplicate-icon groups in Wear may be intentional provider previews and require visual/marketplace contract review.

### Low-risk mechanical findings

- 102 KTX usage suggestions across Mobile and SugarWear.
- obsolete SDK checks given the current minimum SDK.
- compound-drawable simplification suggestions.
- Compose primitive state hints.
- modifier ordering and small-text findings.

Mechanical findings will not be changed ahead of data-integrity, lifecycle, security, and runtime-performance findings. Broad automatic rewrites are prohibited.

## Baseline stderr observations

Several successful tests print XML parser messages equivalent to `Content is not allowed in prolog`. The tests do not fail, but the origin must be identified because noisy expected-failure parsing can conceal real malformed input. It must not be silenced until the parser/test boundary is understood.

## Phase-2 outcome

The build is green but not warning-clean. The warning population is now separated into project defects, dependency/toolchain warnings, reachability-sensitive resources, policy decisions, and low-risk mechanical cleanup. Phase 3 can now inspect ownership and coupling without treating all 326 warnings as equivalent or deleting runtime assets based solely on Lint reachability.
