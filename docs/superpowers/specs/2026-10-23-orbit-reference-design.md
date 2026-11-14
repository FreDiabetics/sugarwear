# Orbit — reference watchface

## Intent and authority

User requests a separate, visually faithful reconstruction of the active Ultra 2 face,
with all visible settings, and existing Sugarlicious enhancements. The additional
request includes reference-style hands as well as the three ApeX hand sets.
Prior instruction explicitly authorizes autonomous implementation without approval loops.

Reference observed live on Galaxy Watch Ultra SM-L705F, 480×480, and Galaxy S26 Ultra
SM-S948B companion editor, 1440×3120, on 2026-10-23. Package reported by the runtime:
`com.amoledwatchfaces.ultra2`, version `1.0.6-wff4`. No reference executable or private
assets are imported. Geometry is reconstructed from the visible reference. Do not
claim pixel identity for variants not yet visually compared.

## Structure

New resource-only WFF module `watchfaces/sugarlicious-orbit`, package
`app.aapswear.watchfacepush.orbit`, display name Orbit. Existing ApeX and Vigil remain
untouched. Reuse first-party ApeX hand assets and graph provider; no collector,
resolver, glucose calculation or storage changes. Reference configuration is not saved.

## Measured layout (512-unit dial)

- Center 256,256. Outer minute markers near radius 249; four cardinal index areas.
- Three outer ranged/text slots: upper left, upper right, lower right. Upper-right
  optionally segmented. No extra icon-only slots.
- Lower-left circular ranged/text complication centered near 149,365, radius 80.
- Upper inner arc from left to right, centered on dial: date/weather/moon, or graph.
- Two lower-right concentric curved text rows use separate provider slots.
- Day-of-month capsule at 3 o'clock. Analog hands above all content, seconds sweeping.
- Theme-driven tracks, values, icons and ring contours. Unit typography remains same
  size/color as numeric value, per latest ApeX correction. Only glucose gets optional
  semantic color; no silent recoloring of other content.

## Observed editor categories

1. Presets (six visible)
2. Primary Theme: Juniper Haze, Neon Green, Neon Lime, Neon Orange, Oak Brown,
   Walnut Brown, Grey Brown, Grey Mud
3. Secondary Theme: same eight palettes
4. Tertiary Theme: separate extended palettes, inventory in implementation ledger
5. Night Mode: Off, Red, Green, Orange
6. Watch Hands: Default, Style 1–6, Premium Default, Premium 1–4 visible initially
7. Seconds: Sweep Tint, Sweep Red, Sweep Tint Alt, Tick Tint, Tick Red, Tick Tint Alt, None
8. Index: Numeric, Roman, Arabic-Persian, Kanji
9. Index Color: White, Tertiary 1–3
10. Arc: Weather, Moon, Full Date
11. Weather Icons: Default, Google Weather, Material Symbols
12. Weather Action: Samsung, Pixel, Google, OnePlus
13. AOD: Dimmed, Index & Hands, Hands Only
14. Gradients; 15. Minute Markers; 16. Round Markers; 17. Battery Indicator;
18. Hand Tint; 19. Background Glow; 20–25. Six provider complication slots.

## Additional requested controls

- Arc/Graph switch, default reference Arc. Graph uses existing ApeX compact portable
  CGM image, fixed 90 minutes, larger theme-tinted dots, black background, high/low
  lines, no axes/captions. Contour follows theme.
- Progress indicators separately switchable: hollow black ring, inner diameter equal
  to track thickness, no dark trail, full visible bar when marker mode is on.
- Gradients switch all ranged bars, not only upper-right. Segmentation independently
  switchable; all settings persist via native WFF configuration.
- Native rendered preview must represent Orbit, not old ApeX geometry.

## Acceptance and verification

Inventory is evidence, not implementation. Validate generated WFF, resource references,
slot count/types/bounds, configuration references, invalid/zero-range behavior,
marker geometry, ambient visibility and graph mask. Build release APK; install only
on verified SM-L705F. Verify actual layout and editor without changing reference face
settings. Final report distinguishes implemented, built, and device-verified items;
unverified reference variants remain explicitly open.

## Orbit-specific graph follow-up

Orbit uses its own SMALL_IMAGE service and catalog variant (57), sharing the existing
canonical GRAPH data path and WatchfaceCgmGraphRenderer. Its bitmap is 476 x 276,
displayed at 238 x 138 within a 240 x 140 rounded frame at (136,106). ApeX retains
400 x 140 and its existing service. Both use the existing fixed 90-minute window.
Graph mode hides both upper annular background panels; Arc mode restores them.
The new fixed slot 8 replaces old slot 6 to avoid retaining the ApeX provider in
already-installed favorites. No collector, resolver or medical calculations change.

### Full-bleed refinement (0.1.4, supersedes rectangular frame above)

The Orbit image now fills the entire upper inner semicircle (center 256,256,
radius 165); bitmap 660x330 is displayed at 330x165 at (91,91), with no contour.
Black background and circular mask meet the existing lower segments with no
rounded-rectangle corner gaps. Graph tint reads primary.0 directly from the
native theme configuration, so changing the primary theme updates it immediately.
Existing relative light/medium/dark tones retain target/high/low differentiation.
To prevent real samples being clipped by the circular mask, Orbit alone uses an
inscribed linear plot rectangle; guide lines extend full bleed to the curved edge.
Both axes stay linear. ApeX and the fixed 90-minute data window remain unchanged.
