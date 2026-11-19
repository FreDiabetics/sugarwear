# Mobile submenu header correction

This supersedes the earlier screen-fixed back-button interpretation. The user clarified that the **complete compact header row**, including back and submenu title, follows the first card while pulling. It remains directly above that card. The small title fades from this moving row; the large title appears above it. The settings overview has no back button; submenus retain it.

## Causes and correction

- Only the spacer expanded, leaving the compact row at the screen top. Native and Compose now use one shared card-relative row offset.
- Native category wrappers added a leading card margin on top of header clearance. The first card no longer gets that duplicate margin. Header clearance is one 8dp gap; including the button's 4dp inset, measured button-to-card distance is 12dp.
- The expanded title shared the spacer's changing height, revealing text by clipping. Its fully measured text box is now independent of the spacer. Alpha controls appearance; the complete text must fit above the compact row before becoming visible.
- Existing reverse crossfade and exact compact/expanded settle endpoints remain. Scroll gestures still cancel controls and content still ends at the final card.

## Evidence

The regression tests reproduced the old failures: row displacement was 0 instead of 96dp; title-box height grew from 110 to 160dp; overview back was visible. Targeted tests cover the corrected geometry, fixed text height, opacity-fit condition and overview/submenu navigation.

On SM-S948B, the native notification submenu's back bounds moved from `[56,163,224,331]` to `[56,723,224,891]` with the card. Both compact and expanded states had exactly 42px (12dp) between button and actual card. The widget configuration page likewise moved its back row by 560px during expansion and returned to its exact opening bounds.

The final build was installed successfully. Device hierarchy checks confirmed the settings overview has no back node, while the compact and expanded notification submenu retains the moving back row. The complete large title was visually checked in its expanded position. The old growing text-box failure was reproduced, then its fixed-height regression passed with the other targeted tests: 45 tests, zero failures. Build, formatting, Detekt and Lint completed successfully. Wear redesign was explicitly deferred by the user in favour of the collector reliability/efficiency investigation.
