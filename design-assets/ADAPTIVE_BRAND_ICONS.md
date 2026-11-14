# Adaptive brand layers

The original supplied 108 x 108 SVG layers are preserved byte for byte in
`app-icons/adaptive108` (Sugarlicious) and `sugarwear/adaptive108` (SugarWear).
`combined.svg` composes the full background and centered 66-unit foreground motif for store artwork and review.

Reimport using `./gradlew :tools:maintenance:run --args="import-adaptive-brand-icons --source-dir PATH"`.
The separate Kotlin command `import-app-brand-icons` and 66 x 66 masters remain
available for the old input format; do not use that importer for layered108 input.

Launcher foreground and adaptive monochrome vectors use the exact supplied
path inside a 108 x 108 viewport, with scale 78/108 and translation (15,15) for Sugarlicious, and scale 66/108 and translation (21,21) for SugarWear.
Their optical sizes are identical. The current Sugarlicious art fills about85 units vertically; this
brand-specific import transform keeps the visible motif centered without editing
any supplied path coordinates. Sugarlicious background retains the source108 rounded rectangle geometry
and all gradient coordinates/stops. Both backgrounds are now genuine vector
rectangles and gradients without embedded or linked raster images.

Notification and in-app assets remain white symbols with their original optical
wrappers. A named inner group maps the new108 symbol bounds back to the former
66-coordinate symbol bounds (Sugarlicious 3..63; SugarWear 5..61).

The contact sheet shows unmodified source background and foreground separately,
and the final adaptive composition with the safe66-unit motif.

The unchanged SugarWear source has a 4,936-character symbol path. Its static, cached VectorDrawable paths carry a local VectorPath lint exception to preserve the exact supplied geometry; other lint checks remain enabled. The importer reproduces this exception only for paths over the detector's 3,000-character threshold.

Sugarlicious icon policy (2026-10-28): the normal adaptive icon retains the supplied gradient background. A separate monochrome layer provides the same symbol for system-tinted presentation; Samsung/Android selects presentation per surface. Sugarlicious uses a larger 78/108 import transform because the current source artwork occupies about 85 of its 108 units. Notification small icons remain white alpha-only symbols. Samsung Now Bar receives the explicit Sugarlicious green chip background (#22EB60), independently of app icon rendering.

2026-10-29 correction: both launcher foregrounds and adaptive monochrome layers use scale 66/108 and translation (21,21). The previous brand-specific 78/108 Sugarlicious enlargement was incorrect and is removed. Both supplied motifs have the same nominal height; their source paths are preserved. Sugarlicious background is reimported from the updated ic_background2.svg, including its original gradient direction. Notification-only alpha symbols keep their existing sizing.
