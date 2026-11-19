# ApeX reference parity implementation plan

> **For agentic workers:** Use superpowers:executing-plans. Continue inline without approval prompts as explicitly requested.

**Goal:** Reconstruct the visible Ultra reference design and useful customization options in ApeX without copying third-party code/assets or replacing its graph, providers or three hand packs.

**Architecture:** Keep the existing eight-slot WFF v1 implementation and Mobile default preview. Use native drawing, stable configuration IDs and public data sources. Clinical values remain provider-owned; decoration never creates clinical values.

**Tech Stack:** WFF v1, Android resources, Compose Mobile preview, JVM contracts, official WFF validator, Galaxy Watch Ultra.

**Spec:** User's 2026-10-21 reference screenshot and subsequent request to include overlooked details; existing WFS geometry snapshot.

## Global constraints

- Exactly eight slots; preserve provider bindings and existing palette/hand IDs.
- Keep Wear OS 4+ compatibility, existing Graph and hand packs.
- Do not extract third-party APK implementation/assets. Observe public editor and lossless screenshots only.
- Work in the existing dirty main checkout as previously requested; preserve unrelated changes. No bulk commit or cleanup.
- Scoped checks only, then real-device installation/visual comparison. Never claim exact parity from source tests alone.

## Review focus

- Empty, zero-range and long provider values must not create bogus progress or overlap neighboring arcs.
- Existing saved theme/hand selections must not be renumbered/reset.
- New decorative layers must not cover graph or create phantom editable slots.
- Gradient must remain opaque and actually interpolate hue, not fade toward black.
- Ambient/night modes must retain readability without second-hand animation or graph updates.

## Observed reference inventory

Public editor: Material SubTheme (Neon Orange), Night Mode (Off), Background Pattern (None), Watch Hands (Style 1), Sec Hand (Sweep Tint Alt), Index (Numeric), Index Color (Solid), Minute Markers (Dot), AOD (Dimmed + Index), Material Theme (Neon Orange), Index & Hands Theme (Almond), Gradient Progress Bars (On), Rating Bars Progress (On), Unread NC (Off), Am-Pm (Off), Complication (8 slots).

Lossless 480px reference sample: value #FFCCB1, title #FF935C, strong accent #FF5F0D, track/contours #372014, index #FFF7E4, markers #61646A. Existing 512px circle geometry matches the reference after 480/512 scaling. Weekday arc is decoration, not a ninth slot. Timer belongs to the selected provider; ApeX retains its graph in that location.

### Task 1: Reference color hierarchy, opaque gradient, provider images

- [x] Add regression coverage for dark themed contours, real color interpolation layers, usable SHORT_TEXT provider image path and stable eight slots.
- [x] Observe failures, then update WFF/palette bindings and Mobile preview together.
- [x] Replace failed InlineImage experiment with supported same-slot PartImage; no independent icon slots.
- [ ] Validate WFF, run scoped tests and check native rendering.

### Task 2: Additional design options

- [x] Add native settings for independent dial color, weekday decoration, night mode, background pattern, second-hand visibility, index/marker style, AOD style, AM/PM and unread indicator where the public v1 data sources support them.
- [ ] Keep defaults and data safety explicit; all layers must have visible option effects and retain circle/graph safe bounds.
- [ ] Native validate every new configuration and exercise selected states on the watch; do not claim uninspected option sets identical.

### Task 3: Delivery and evidence

- [ ] Review changed scope for clipping, double rings, broken saved selections and empty providers.
- [ ] Build ApeX and matching Mobile/embedded delivery artifacts, scoped static checks/tests.
- [ ] Install only the requested packages and compare actual ApeX against the reference. Record limitations and unverified combinations honestly.

## Progress / rulings

- Initial public-menu inventory complete. Main/SubTheme both Neon Orange; Index & Hands Almond. No reference settings deliberately changed during initial inventory.
- Native screenshot establishes four useful theme roles, not the existing three-role opacity-only approximation.
- Ruling: WFF v1 gradient color lists cannot bind two configuration colors directly. An opaque light-color arc plus a translucent vivid-color sweep produces actual color interpolation without black showing through; keep the official validator unchanged.
- Ruling: Main and SubTheme saved option IDs are retained. Measured reference palettes are appended, not substituted for old user choices.
- Reference additional choices observed: Night Mode Off / On (Red) / On (Green); Background Pattern None / Web / Target. Inspection restored Off and None on Ultra afterward.
- Final review caught missing night/AOD policies in three unindented legacy slot declarations. Fixed structurally for all seven clinical slots; individual-slot regression went RED to GREEN.
- Final review caught graph/cleanup-mask overlap and ambient gray-ring reappearance. Cleanup now stays opaque at Scene level before the graph. Optional weekday graph shifts to global (166,134,180,63), away from later contour strokes. Regression went RED to GREEN.
- Physical inspection caught a regular minute-dot grid crossing wide hour numerals. Replaced the grid with 26 measured original dial gaps; regression went RED to GREEN. Static source image remains unchanged.
- Scoped checks after those fixes: 36 geometry contracts plus 2 therapy typography cases, Mobile Debug, Wear Debug, Mobile ktlint/detekt, ApeX Release, official WFF/push checks and watchface lint passed. Further complete reference palette inventory is running through public UI, original Ultra theme is restored afterward.
- Native inspected 0.6.18 on SM-L705F, selected measured Neon Orange in both ApeX themes; hand-style and complication bindings retained. Native preview remains a default-binding illustration rather than a remote snapshot of user-selected sources/themes.
- Complete public picker inventory: 72 Main and 72 SubTheme choices, same names/order and matching swatch RGB (Almond's middle sample differs by 1 blue level across screenshots, consistent with sampled UI antialiasing). Ultra Main/SubTheme both restored to Neon Orange.
- Ruling: WFF permits at most 100 options per color configuration. Keep all option IDs; correct nine approximate legacy presets (9–17) to corresponding measured reference palettes, append the rest. This fits all 72 reference palettes plus 28 other presets into 100 choices without resetting the currently selected ID 37. Cost: those nine older presets intentionally change their colors/labels.
- Ruling: Native palette swatches must have three stripes like the reference. Runtime palettes now contain light/middle/vivid, with track/contours drawn from middle at alpha 55; do not insert a dark fourth stripe into the color picker.
- Reference gradients cover the full progress scale, rather than stretching the entire color range over whatever progress is currently filled. Fixed native sweep spans also avoid zero-length gradient spans at zero progress.
- Latest candidate 0.6.19 (27), Native/Push validation, 37 geometry cases + 2 typography cases, Mobile/Wear builds, Mobile lint-style/static checks and watchface lint passed; installed ApeX, matching Wear delivery package and Mobile on explicit Galaxy devices. Final live/weekday check underway.
- Final candidate 0.6.21 (29): 39 geometry cases and 2 typography cases passed, alongside Mobile/Wear builds, Mobile ktlint/detekt, ApeX Release, official WFF/push validation and watchface lint. Installed version 29 confirmed on SM-L705F; native screenshot captured.
- Native weekday inspection found English fallback and insufficient label spacing. German weekday labels now use the German settings language, selected pills sit at radius 160, and all seven selection bounds are checked against the optional compact graph (186,134,140,49). Weekdays remain optional, default off; enabled on the test watch for the captured comparison. Standard graph remains (156,116,200,70).
- Outer SHORT_TEXT and RANGED_VALUE labels now reuse the existing validated therapy formatting: insulin at one decimal, units same size/color, generic text preserved. Native negative IOB rendering inspected after installation.
- Delivery evidence is scoped, not a claim that every reference option or every provider combination is pixel-identical. Physical AOD inspection remains open. Mobile preview is a default illustration, not remote mirroring of the watch's selected theme/hands/providers. The full reference Index & Hands theme catalogue was not audited.
