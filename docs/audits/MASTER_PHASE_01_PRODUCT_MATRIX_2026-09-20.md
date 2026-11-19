# Master phase 1 — active product matrix

## Root cause

The repository correctly exposed only Digital and Vigil to users, but retained
27 retired WFF modules in Gradle settings, CI release tasks, the release build,
the official validator, the DEX verifier, runtime deployment metadata, and dead
Mobile preview records. A later cleanup commit also re-added four retired
Sugarlicious variants to the catalog. The result was a nominal 30-WFF gate that
measured historical source rather than the current product.

## Change

- The authoritative catalog now contains only Digital and Vigil.
- Gradle settings, CI, Watch Face Push, release packaging, validation, and the
  DEX verifier consume only these products plus `test-wff` as a validator
  fixture.
- All 27 unreachable retired modules, dead preview resources, obsolete import
  and golden-capture scripts, and retained runtime deployment metadata were
  removed.
- The still-active Mobile ApeX preview keeps its required dial bitmap in the
  Mobile module instead of depending on a retired WFF module.
- The preview resource generator is now a `Sync` task, so removed inputs cannot
  survive as stale duplicate generated resources.

## Evidence

- Product-matrix regression script: passed.
- Mobile/Wear unit tests and debug builds: passed.
- Digital, Vigil, and test fixture release builds: passed.
- Official WFF validator: 3/3 passed.
- Code-free APK verification: 3/3 passed.

Historical reports and license records remain as historical evidence; their
old WFF counts are not current product requirements.
