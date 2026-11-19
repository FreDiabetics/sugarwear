# Python tooling migration to Kotlin

Scope authorized: all 25 Python files in Sugarlicious and 8 owned Python files in
SugarWear, including their tests and callers. Retain results, validations,
operational sequences and meaningful gates. No Python runtime wrapper counts as
a completed migration. Do not touch synced sources/ or sensor/device state.

Architecture: one canonical tools:maintenance Kotlin/JVM module reused by the
independent SugarWear product, extending the existing JVM tooling layout.
Subdomains: watchface transforms, Orbit generation, SVG/raster assets, and safe
product migration. Shared product code stays single-source. CLI subcommands
preserve old file-stem names in kebab-case and their arguments.

Steps:
1. Inventory sources, CLI/import/call graph, baseline all 106 existing Python
   tests; capture outputs on temporary fixtures before replacing scripts.
2. Port pure XML/configuration and verification transformations with equivalent
   JUnit tests; compare generated XML semantically and preserve all geometry,
   expressions, settings, presets, names, ordering and resources.
3. Port importers/renderers. Keep original artwork and pin renderer/font behavior;
   measure raster parity explicitly, never silently accept approximation.
4. Port ADB product migration using injected runner/clock and fake-ADB tests only.
   Preserve preflight ordering, timeouts, quoting, permission allowlist,
   acknowledgment and rollback; no actual migration invocation in this task.
5. Replace wrappers, CI and documentation callers. Delete a Python implementation
   only after its replacement and tests pass. Integrate the canonical module in
   SugarWear without source duplication; align shared revision if necessary.
6. Run all affected tests, full static/build/architecture gates and parity checks;
   report actual coverage and any unresolved mismatch. Preserve prior G7 changes.

Parallel ownership: dedicated watchface, assets and migration packages; root owns
Orbit generation, module/CLI integration, CI/wrappers and final verification.
