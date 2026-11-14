# Kotlin maintenance migration

## Scope and ownership

Replaced 25 Python files in Sugarlicious and 8 owned files in SugarWear, including
their tests. The baseline contained 74 main asset/watchface tests, 10 product
migration tests and 22 standalone tests: 106 original tests. Original test
assertions were retained in Kotlin; some methods aggregate source assertions,
while new CLI, complete-tree and pixel-hash checks add independent coverage.

One `tools:maintenance` Kotlin/JVM module owns all 18 commands. SugarWear includes
that canonical module from its shared submodule. Product source contracts are
enabled only for a real `g7watch/build.gradle.kts`, not leftover build directories.
The optional `sharedSourceRoot` property supports reviewed development checkouts.

No Android UI, sensor protocol, pairing, alarms, device settings or deployed
application data were modified by the Python-tool migration. Earlier G7 Kotlin
changes remain a separate preceding migration and retain their reference tests.

## Preservation evidence

- All 53 generated hand PNGs match original Pillow 11.3.0 decoded RGBA SHA-256
  values. PNG compression bytes are deliberately not a visual contract.
- Brand/contact sheet and Vigil raster fixtures match exact decoded pixels using
  Cairo 1.18.4. Existing live store-art tolerance remains the original 0.75 mean
  channel bound, separate from exact generated-fixture comparisons.
- Gray+alpha PNGs required raw luminance decoding: ImageIO's default grayscale
  color conversion changed the supplied art. This was caught by the old hashes
  and corrected without modifying the source artwork.
- Complete Orbit XML and labels match an independent original-generator oracle.
  Numeric literals use twelve significant digits for cross-runtime trig ULPs;
  expressions, field references, order and nonnumeric content remain compared.
- All 37 Orbit behavioral tests, six native-rendering contracts, palette/default/
  preset/capture/geometry contracts, live brand source contracts and 22 standalone
  contracts are ported. Fake-ADB tests retain device roles, preflight order,
  quoting, timeouts, permissions, acknowledgment and rollback behavior.
- All 18 command schemas support original arguments/defaults, choices, help,
  unique prefixes, repeated options and equals spelling; invalid inputs are
  rejected before file/device access.

## Toolchain and boundaries

No owned tool/test invokes Python. The SVG renderer uses native Cairo through
JNA; it explicitly rejects unsupported SVG artwork rather than silently changing
its rendering. CI verifies the official Cairo 1.18.4 archive SHA-256 before
building it with external Meson/Ninja. Meson's implementation itself uses Python;
this external build prerequisite is not claimed to have been migrated to Kotlin.
The prior local golden capture used CairoSVG 2.8.2 and Pillow 11.3.0.

Fixed geometry remains WFF/XML; CLI/platform wrappers remain PowerShell and CI
YAML. These are intentional platform/build formats, not Android runtime code.
The obsolete PowerShell brand wrapper had unsupported two-source flags; it now
uses the actual source/product interface and requires an explicit second product
repository before writing either product.

## Verification record

The focused shared-module gate passed 87 main tests, then 109 tests including
standalone contracts, together with Detekt and formatting.

The complete Sugarlicious command `verifyArchitecture detekt ktlintCheck test
assembleDebug lint` passed again after removing the two unused Batik dependencies:
870 tasks (37 executed, 833 up-to-date), 1,444 tests
represented by the module reports, zero failures/errors. These include all 111
G7 reference/unit tests. Existing unchanged module results were reused normally.

The real PowerShell wrapper generated all 53 PNGs into a path containing spaces
and Unicode. Command help passed in both PowerShell 7 and Windows PowerShell 5.1.
Independent review caught the Windows-only `$IsWindows` compatibility issue;
OS detection now uses the API available in both editions. The local 5.1 script
test used a process-only execution-policy override, without changing OS policy.

The normal pinned-submodule SugarWear build passed `detekt ktlintCheck test
assembleDebug :watchfaces:vigil:assembleRelease lint`: 397 tasks (43 executed,
3 from cache, 351 up-to-date), 1,025 represented tests and zero failures/errors.
This includes 426 SugarWear tests, all 111 G7 reference tests and all 109
maintenance/standalone contracts. The canonical PowerShell launcher then passed
the Kotlin code-free verifier on the newly built Vigil release APK.

The official pinned WFF validator passed Digital, ApeX, Orbit, the validator
fixture and Vigil. The product matrix and code-free checks also passed for the
existing unchanged Sugarlicious release watchfaces. No device installation or
real ADB migration was performed.

The pinned Gitleaks 8.30.1 history scan passed. Two exact false positives were
reviewed: the public deterministic ECDH vector and a literal English sentence
about packet order. `.gitleaks.toml` retains all default rules and constrains its
exceptions by rule, exact path and literal match. A separate synthetic Git
negative control proved that another key on the next line of the same fixture
file is still rejected while the public known-answer vector is allowed.

Independent review found no remaining Important/Critical issues after the
PowerShell compatibility correction. Remote GitHub Actions/Linux execution is
separate from the local verification reported here; commits remain local.
