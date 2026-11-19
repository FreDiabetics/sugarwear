# Open UI and tile corrections — 2026-09-23

## Corrected productive paths

- Mobile CGM labels: `GlucoseDashboardChart` now decides the High/Low axis side
  exclusively from prediction samples that are actually visible in the current
  viewport. Target, basal and activity overlays no longer influence the side.
- SugarWear typography: the in-app TextView and both G7 ProtoLayout tiles now
  use the same explicit `sans-serif` family and regular/bold weights (400/700).
  Tile resource versions were advanced so Wear OS cannot retain the old type.
- SugarWear graph tile: app and tile continue to use the same canonical history,
  graph input and `SharedWearCgmGraphRenderer`. The actual stale-cache root cause
  was fixed: the Tile resource version now fingerprints the complete canonical
  graph, so an older BACKFILL value invalidates the bitmap even when the newest
  reading timestamp did not change.
- Therapy rings: Mobile and Wear now consume one renderer-neutral ring geometry.
  Compose's 130 degree start is converted to ProtoLayout's 220 degree start,
  keeping the 80 degree opening physically at bottom centre on both platforms.
- Therapy icons: the existing Mobile IOB, COB, basal, reduced-TBR and increased-
  TBR vectors were moved to `ui-shared` and are used by both Mobile and Wear.
  Wear places the icon in the bottom gap and uses the same effective basal and
  historical fallback logic as Mobile.
- Existing one-, two- and three-metric placement is unchanged; compact rings
  retain identical diameter, stroke, gap and reserved bounds.

## Why the earlier implementation was not visibly sufficient

1. The Mobile side decision used enabled overlay flags, not visible predictions.
2. G7 tiles requested weight 500 while the reference app used real bold (700).
3. The graph resource cache key covered only the newest timestamp; late history
   could therefore render correctly in memory but remain invisible on the Tile.
4. Compose Canvas and ProtoLayout use different angular zero points, and the
   Wear renderer copied 130 degrees without conversion. Wear also omitted the
   icons instead of consuming the existing Mobile assets.

## Regression coverage

- Visible-prediction/no-visible-prediction axis-side decision.
- Shared G7 family, size and regular/bold weight tokens.
- Real graph pixels at small and Galaxy round-display sizes.
- BACKFILL-driven graph resource invalidation with an unchanged latest reading.
- Bottom-centred ProtoLayout ring geometry and equal compact bounds.
- IOB, COB, normal basal, reduced TBR and increased TBR icon states.
- Resource registration for every one-, two- and three-metric selection.
- Existing graph period/range visual QA preview generation.

## Verification

- Focused Mobile graph tests: successful, 101 tasks.
- Focused G7 typography tests: successful, 84 tasks.
- Focused Mobile/Wear therapy tests: successful, 226 tasks.
- Focused G7 graph tests: successful, 84 tasks.
- Full affected matrix (`test`, `assembleDebug`, `lintDebug`, `detekt`,
  `ktlintCheck` for core-model, Mobile, Wear and G7): successful, 479 tasks.
- Android Lint reports for Mobile, Wear and G7: `No issues found.`
- `git diff --check`: no whitespace errors.
- Generated G7 graph visual QA PNGs were inspected locally and contain the real
  curve, points, thresholds and classified background.

Physical round-display acceptance remains a separate hardware step; no APK was
installed and no merge or push was performed by this correction run.
