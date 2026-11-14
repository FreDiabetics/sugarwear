# Jetpack Compose migration

## Current state

Jetpack Compose is enabled for `:app-mobile` as the first step of an incremental migration.
The existing XML/View dashboard remains active, so this change is intentionally UI-neutral.

### Added

- Compose Compiler Gradle plugin matching Kotlin `2.4.10`
- Compose build feature in `:app-mobile`
- Stable Compose BOM `2026.06.01`
- Activity Compose `1.13.0`
- Compose UI, Foundation, Material 3, preview/tooling dependencies
- centralized `SugarliciousColors`, `SugarliciousTypography`, `SugarliciousShapes` and `SugarliciousTheme`
- shared Compose spacing, component, radius and icon-size tokens
- matching XML color/dimension tokens while legacy Views remain active
- `ComposeView.setSugarliciousContent(...)` interoperability bridge
- A preview-only Compose foundation card to verify Android Studio previews

### Activity host

`MainActivity` now extends `ComponentActivity`. Its current XML layout and View-based behavior are unchanged.
This gives Compose screens and `ComposeView` sections a lifecycle-aware host during the gradual migration.

## Responsive overview baseline

While the overview is still View-based, `DashboardLayoutMetrics` selects a compact logical-dp height profile from the current window height. The large-phone profile is budgeted to fit the full overview between the fixed top and bottom bars without requiring vertical scrolling; changing Samsung FHD+/QHD+ render resolution does not depend on raw pixels.

## Next migration step

Replace UI sections one at a time with Compose while leaving the data layer, AndroidAPS bridge,
foreground service, notification handling, Wear protocol, and storage untouched.

Recommended order:

1. Header/top app bar
2. Bottom navigation
3. Overview cards
4. History/data/settings screens
5. Custom charts only after the surrounding UI is stable

## Local verification

```powershell
.\gradlew.bat :app-mobile:assembleDebug :app-mobile:testDebugUnitTest
```

The provided project copy was prepared in an environment without Gradle network access, so dependency
resolution/build execution must be verified locally after Gradle sync.
