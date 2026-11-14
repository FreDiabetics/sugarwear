# Sugarlicious Wear tile typography

Cause: all Sugarlicious ProtoLayout text used a hardcoded sans-serif family. Samsung's tile host does not honor the installed Samsung One FlipFont in native text; SugarWear had already solved this with device-owned font rasterization.

Extract the proven font/cache/bitmap primitive into ui-shared DeviceTileTypography, reuse it in SugarWear, and route Sugarlicious glucose, therapy and graph tile text through SugarliciousTileTypography. On Samsung load regular/bold Samsung One from the device package (400/700), with manifest visibility. Other devices keep their system/native font fallback. Graph bitmaps receive the same optional regular/bold faces; other graph callers remain unchanged.

Text uses Android SP conversion and actual pixel density. Explicit card/ring width and height budgets prevent large system fonts/configured multipliers from overlapping or leaving round-display bounds. Reserve the trend arrow in the glucose row. Two metadata cells share available width. Bound the encoded-text cache to 48 entries, clear it with the typeface cache on configuration changes, and register fresh scope resources from cached PNGs. Resource versions fingerprint values, colors and configuration so old text images cannot persist across updates. No new timer or background task.

Native graphics tests cover font scaling and bounded text bitmaps; existing font-cache and Wear therapy tile tests passed. Independent re-review accepted card/ring fitting and cache correction. A direct side-by-side font comparison on the native therapy tile remains a hardware-validation limit.

Final affected assemble, native font rendering tests, Wear tile/cache tests, Detekt, ktlint and Lint passed (tiles-font-bounded.log). A final exact-source packaging/native-render test check also passed (tiles-font-package.log). Both Wear packages were subsequently installed on SM-L705F. No side-by-side hardware-font-fidelity claim is made.
