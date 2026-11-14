# Wear Root-Cause Final Implementation Plan

1. Establish current `main`, baseline tests and prior-fix ancestry.
2. Validate typography runtime/resource path and its glyph/weight tests.
3. Validate insulin-activity ingestion, independent scaling and four graph modes.
4. Validate shared graph model/style/geometry and supported responsive Tile delivery.
5. Reproduce duplicate Wear state work with a 90 KB payload test.
6. Move fingerprint rejection and decode into the serialized IO path; persist the fingerprint only after commit.
7. Run focused Mobile/Wear/G7 tests, rendering tests, assemblies, lint, detekt and ktlint.
8. Install only after the source/build gate is green; report hardware-only checks separately.
