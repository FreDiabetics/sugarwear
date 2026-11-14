# Range bar and brand presentations

**Goal:** Show the current glucose interval on the Mobile hero bar and correct brand proportions in README and Tile selectors.

**Design:** The full-width bar represents exactly one interval: low [40, low], target [low, high], high [high, 400] mg/dL. Exact thresholds follow the canonical classifier (low inclusive, high inclusive). Clamp presentation only at interval endpoints; invalid/missing values render underlay without a marker. Keep animated position and animate interval color changes. Four independent existing palette roles configure low, target, high and translucent underlay. The old measured-time distribution is no longer part of this display; TIR remains unchanged.

**Architecture:** Pure Kotlin interval mapping, existing Compose renderer and appearance repository. Brand presentation generation fits the exact supplied vector path uniformly to 80 units of height on a 108-unit canvas, preserving aspect ratio and centering. README compositions and Tile-selector icons use this presentation; adaptive launcher safe-zone assets and notifications remain separate.

**Tech Stack:** Kotlin, Compose, StateFlow palette, existing Cairo-based Kotlin asset tools, VectorDrawable/SVG.

- [x] Replace distribution tests with range/boundary/invalid-value tests; verify failure before implementation.
- [x] Implement range mapping and reactive color/position; remove unused header-only archive/statistics loader. Expose and test four palette roles including transparency.
- [x] Test and implement exact uniform brand fitting; regenerate both README compositions and standalone monochrome Tile-selector icons. Wire both products' Tile services.
- [x] Run relevant tests, builds, ktlint, Detekt and Lint; inspect generated artwork; document evidence and limitations.

No changes to sensor, collector, alarms, resolver, Tile content or TIR calculations.


## Evidence

Final Sugarlicious full local gates passed (3m18s; 884 tasks), SugarWear full gates passed (7m8s; 368 tasks). Pure interval, independent palette/alpha, vector fit, actual Android Tile-icon raster and manifest-binding tests passed. Both README renderings were visually inspected. Updates installed without clearing user data. The Mobile device was locked during the attempted final screenshot; live screen inspection remains separate from automated evidence.

The installation check exposed an uncaught widget-render failure in the package-replacement receiver. Its background restoration now reports a failure while retaining the previous widget surface, without terminating the bridge. Cancellation still propagates. Two regression tests and the complete local gates passed after this follow-up (3m45s; 878 tasks). Widget rendering errors are not presented as successful fresh frames.
