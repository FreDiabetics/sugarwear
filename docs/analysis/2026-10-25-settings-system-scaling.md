# Settings and system scaling follow-up

- SugarWear sliders keep a 10 dp centered track inside their larger touch target. The inactive track remains visible on black, and individual screens no longer overwrite the shared thumb/track styling.
- Both Wear apps use tap-only settings switches that cancel when a scrolling gesture begins. Color editors keep the existing scroll-safe sliders.
- Wear hour choices use two rows instead of six compressed buttons. Choice labels and alarm buttons use readable system text sizes.
- Mobile settings retain the expanded title only on the settings overview. Submenus open with the compact title beside Back. Synthetic bottom runways were removed; the widget editor keeps only the clearance needed for its floating action pill.
- Catalog short-text previews use the same 18/13 sp text roles as settings. Preview sizes and slot-grid columns respond to font scale. Native watchface slot IDs and provider data remain unchanged.
- Custom glucose/widget/graph renderers now receive the device font scale, including their settings previews. Graph axes reserve measured space for larger text instead of discarding scaled density. Fixed widget bounds still require fitting oversized value groups to their available space.
- Mobile refreshes widget surfaces on font-scale and display-density changes, as well as night-mode changes.
- Sugarlicious Wear graph-tile bitmap text follows font scale. Its overview grows its glucose card and stacks therapy cards for large system text; rings, trend glyphs and their surrounding layout grow together. The native TextViews/Compose/protolayout text continue to use their system sp units. Clinical complication geometry remains owned by its watchface; preview settings are not sent as watchface sizing overrides.

Validation covers vertical gestures across switches/sliders, shared track geometry, catalog typography, widget graph bounds, and the Wear overview at doubled system font size. Collector transport, authentication, readings and sensor pairing are unchanged.

## Samsung Health reference comparison

The connected Watch Ultra uses font scale 0.9 and density 340 dpi. The installed Samsung Health settings use 15 sp primary list text, 12 sp secondary/category text, 13 sp buttons, 14 dp inner horizontal list padding, and 16/8 dp category top/bottom spacing. The reference was checked in the visible app and its matching decoded `seslw` dimension resources. SugarWear now uses those typography roles, removes extra font padding, widens settings cards with approximately 7 dp outer insets, and retains the previous round-safe header inset. System font scaling remains enabled; no device setting was changed for this comparison.
