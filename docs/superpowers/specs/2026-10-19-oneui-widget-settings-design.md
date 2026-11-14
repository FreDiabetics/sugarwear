# One UI 9 widget settings

Reference: the user's open Samsung Health `DaHomeWidgetSettingActivityOneUI7` on Android 17 / One UI 9, captured read-only on 2026-10-19. The user confirms the preview backdrop is the actual home wallpaper.

All Mobile widget providers keep their shared configuration activity, independent instance configuration, supported-size controls, immediate explicit save, and cancel/back discard semantics. No G7, Wear, resolver or therapy-data changes.

The shared settings shell follows the measured reference: 60dp header beneath the status bar, `Widget-Einstellungen` at 22sp with 28dp side inset, 242dp wallpaper preview with 10dp page inset and 20dp corner radius, 20dp gap to 30dp-radius cards, 18dp card padding, large readable rows, muted system accent, and a 220x52dp bottom action capsule containing Abbrechen, a vertical divider, and Speichern. Use the device system font. UI follows system dark/light mode independently of the palette being edited.

The wallpaper is shown behind a transparent preview aperture using public `FLAG_SHOW_WALLPAPER`, not read, copied, embedded, or exported. All remaining page regions are painted opaquely. Keep a theme fallback if the device does not show wallpaper. Preview content uses existing production bitmap renderers, preserves aspect ratio, accounts for the launcher's resize ratio, and is uniformly fitted into the reference preview space. The scroll position must move the wallpaper aperture with the preview and never reveal wallpaper behind settings or actions.

Keep every existing setting. Restyle shared cards, row typography, color rows, toggles, number-entry dialogs and sliders. Use a public native SeekBar with a thick rounded track and outlined thumb; retain keyboard/accessibility and exact integer settings values. Colors remain fully configurable with existing picker capabilities.

The user explicitly selected original widget names and direct legacy Developer provider selection over a unified chooser or numbered names. Native Samsung grouping/order is accepted and no longer an open implementation requirement. Compact glucose keeps its own card.

Validate preview placement and resize ratios, wallpaper window flags, controls and existing per-instance/save/cancel tests. Run relevant Mobile tests and compile/static gates before installation. Inspect the actual settings page against the captured reference; hardware observations remain separate from unit/build proof.
