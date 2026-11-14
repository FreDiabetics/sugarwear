# Current Settings System Defaults Implementation Plan

1. Capture and classify all configurable preference stores on phone and watch; exclude secrets and runtime state.
2. Inventory every settings screen and map controls to owning typed stores, defaults, reset behavior, preview, and renderer.
3. Add failing contract tests for captured global defaults and provider-specific widget templates.
4. Centralize defaults in the existing store classes and replace divergent literals in read/reset/preview paths.
5. Add a non-destructive migration that seeds only absent settings; never overwrite existing installations.
6. Audit dead code and warnings using compiler/static-analysis evidence; remove only proven unused or superseded paths.
7. Run targeted tests after each store, then the complete repository gate and relevant release builds.
8. Commit all changes on `main`, create and push a review branch, open a PR against `main`, and verify remote checks.

