# Therapy proportions and Wear overviews

Scope: same proportional ring/value/icon geometry for the IOB/COB/Basal core across Mobile Compose, Android widgets, Wear overview and therapy Tile. Keep existing surface sizes, selected metric sets, additional information, palette and persisted size overrides. Do not change collector/alarm/source logic or watchface complication geometry.

1. Add renderer-neutral reference constants to core-model (66 dp reference, 14 sp main value, centered value, icon center at85% height, calibrated existing IOB19/COB17/Basal16 sizes, shared stroke/marker proportions). Test linear scaling and icon containment.
2. Apply to Mobile Compose and widget Canvas, native Wear rings and Tile content. Keep supplementary information outside the core; maintain fitting for long values and device font scaling.
3. Independently align Wear overview spacing/arrangement with circular responsive One UI Watch principles; remove branding and use matching Settings controls. No redesign of existing data cards.
4. Run targeted unit/layout tests, compile affected modules, static checks/Lint. Install onto explicitly identified reachable devices, inspect actual screens. Record any unavailable visual proof honestly.
