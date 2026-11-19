# Kotlin maintenance tools

One Kotlin/JVM application owns product migration, watchface transformations,
artwork import, raster rendering and code-free Vigil verification. SugarWear
references this module through its pinned `shared/sugarlicious` submodule;
there is no second source copy. No project-owned Python runtime is used.

## Run

From either product root, use JDK 21 and the existing Gradle wrapper:

```powershell
.\gradlew.bat :tools:maintenance:run --args="--help"
.\tools\Run-MaintenanceTool.ps1 -ToolArguments @('generate-orbit', '--output', 'C:/Temp/Orbit output')
```

Each command accepts `--help`. The PowerShell launcher passes a temporary JSON
argument array, preserving spaces and Unicode without constructing shell code.
The canonical launcher also accepts `-RepositoryRoot` for an explicit product.
For SugarWear brand import pass `--product sugarwear --repository-root PATH`.

`refresh-brand-presentations` regenerates only `adaptive108/readme.svg` and the
standalone Tile-selector vector from the saved foreground/background masters.
Pass `--product sugarwear --repository-root PATH` for SugarWear. Both symbols
are uniformly fitted to a painted height of 80 on a 108-unit canvas. Adaptive
launcher safe zones, notification assets and store images remain independent.
The same presentation refresh runs after a subsequent adaptive-brand import.

| Former Python file stem | Kotlin command |
| --- | --- |
| configure_apex_progress | configure-apex-progress |
| configure_indicator_gradient | configure-indicator-gradient |
| configure_trend_icon_geometry | configure-trend-icon-geometry |
| curated_palettes | curated-palettes |
| generate_orbit | generate-orbit |
| prepare_slot_capture | prepare-slot-capture |
| restore_native_hands | restore-native-hands |
| segmented_progress | segmented-progress |
| unify_apex_progress_settings | unify-apex-progress-settings |
| user_flavors | user-flavors |
| import_app_brand_icons | import-app-brand-icons |
| import_adaptive_brand_icons | import-adaptive-brand-icons |
| import_orbit_weather | import-orbit-weather |
| render_orbit_hands | render-orbit-hands |
| configure_vigil_decoration | configure-vigil-decoration |
| import_vigil_markers | import-vigil-markers |
| migrate_products | migrate-products |
| verify_codefree_vigil | verify-codefree-vigil |

The original value-option names, defaults, choices, unique prefixes,
`--option=value` and last-value handling are retained. Help is available before
any file/device access. The obsolete brand PowerShell wrapper's unsupported
two-source flags were corrected to the actual source/product CLI; importing a
second product requires its explicit repository path.

## Raster backend

SVG rendering uses native **Cairo 1.18.4** through JNA. This is the same native
raster backend as the original CairoSVG tools, not a Python interpreter.
Set `SUGARLICIOUS_CAIRO_LIBRARY` to the absolute shared-library path when it is
not installed in the system search path. On Windows its dependency DLLs must
be installed beside it; Gradle adds that directory to the child process PATH.
An incompatible version fails explicitly. Android application APKs do not
contain this desktop tooling dependency.

CI builds the checksum-verified official Cairo source in the composite action
`.github/actions/prepare-cairo`. Meson/Ninja are external native build tools;
Meson's own implementation uses Python on the CI host. No application, owned
maintenance command or migrated test invokes Python. SVG support is explicitly
validated for the project's supplied source masters; unsupported artwork is
rejected rather than silently rendered incorrectly.

Native Kotlin ports of Pillow's integer compositing, Lanczos, bicubic sampling
and rounded drawing preserve all 53 hand asset RGBA hashes. Fixed label alpha
masks avoid installed-font differences. See their resource provenance files
and the Pillow license notice in `PillowRaster.kt`.

## Verification

```powershell
.\gradlew.bat :tools:maintenance:test :tools:maintenance:detekt :tools:maintenance:ktlintCheck
```

Tests cover independent pre-migration image/XML goldens, live source contracts,
CLI parsing, fake-ADB preflight/acknowledgment/rollback, palettes, presets,
geometry, weather mappings and native hands. SugarWear additionally enables
`src/standaloneTest/kotlin` for its own build, brand and Vigil source contracts.
No real sensor or device migration is performed by tests.

Orbit's fixed measured geometry is declarative WFF under `src/main/resources/orbit`;
Kotlin supplies current palettes/weather/defaults and transformations. XML
comparison preserves structure, order and expressions; numeric literals allow
12 significant digits for CPython/libm versus JVM trigonometric rounding.

For development against a separate reviewed canonical checkout, SugarWear
accepts `-PsharedSourceRoot=ABSOLUTE_PATH`. Normal builds use the pinned submodule.
