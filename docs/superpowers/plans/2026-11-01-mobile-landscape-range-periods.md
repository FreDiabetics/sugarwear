# Mobile adaptive overview and measured range periods

- Keep existing portrait overview and use two independently scrollable42/58 panes in landscape: hero/logo/actions left, existing cards right. Unlock MainActivity orientation. Preserve card styling and scroll gestures.
- Move existing logo/actions into the sticky capsule through gesture-driven fade/translation preceding its appearance. Remove watch model label; use filled green Watch icon both placements. Retain capsule shadow and scaled, readable text.
- Give IOB/COB graph at least180dp and a heading; heading above TIR. Existing graph renderers and canonical data paths remain shared.
- Replace data-source vendor artwork with the existing monochrome Sugarlicious foreground, compensated for adaptive insets without introducing a second logo asset.
- Add measured TIR/TITR periods1,3,7,14,30,90 calendar days through a small local-ripple period label. All bars use one spring transition; no polling animation loop. Preserve gap exclusion and fixed70–180 /70–140 thresholds.
- Add phone-only incremental SQLite history behind TherapyStateStore, retaining92days for calendar/DST boundaries. Keep Watch archive/transport bounded. Import existing archive once, record canonical observations and invalid boundaries, query requested period off the UI thread. Never synthesize missing historical coverage.
- Verify focused period, archive and layout tests; compile Mobile/Wear storage dependents, format/lint affected modules; verify portrait, landscape and period change on main smartphone, then install. Do not change collector or previous watchface work.
