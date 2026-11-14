# Fixed native slot panel

User decision: retain Samsung/Google providers; native editor owns provider assignments. All editable native slots, not four favorite IDs, belong in the permanently visible top Watch card. Selecting a slot highlights its true native bounds in blue on the preview. Lower catalog/settings scroll independently. Never label frozen preset data as live third-party provider data.

Implementation uses compact slot metadata generated from the existing WFF scene at build time (IDs, bounds, supported types and default provider policy). Exclude flavor policy declarations and fixed internal slots. No duplicated slot geometry constants. Each slot preview reflects its native arc/circle/graph shape. Current configured third-party bindings cannot be read through the current public transport; do not fabricate them or replace them.

- [x] Generate/read compact native slot metadata; test against both current WFF scenes.
- [x] Place slot selection in fixed top card; blue blinking geometry follows selection, not extra editable icon slots.
- [x] Keep provider assignment in native Samsung UI; retain catalog as separate provider previews, not fake native assignment.
- [x] Build/test/check and install when devices become reachable; inspect fixed-panel visibility and preserve current configurations.

Verification: 568 Mobile unit tests pass; assemble, lint, Detekt and ktlint pass (fixed-native-slots-verified.log). Mobile, Wear and ApeX installed successfully. On SM_S948B, selecting Mitte links displays the blue native oval on the large Watch preview. After scrolling the lower catalog, the slot title remains at [84,1140][427,1213] and Mitte links at [492,1576][625,1609]. Screenshot: local Temp/slot-after.png. The panel explicitly labels frozen data as Vorlagenansicht; it does not claim live third-party bindings. The native-app action opens the companion launcher, not a specific slot editor.
