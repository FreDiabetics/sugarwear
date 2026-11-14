# ApeX complication measurements

Reference: the user-selected `com.amoledwatchfaces.ultra`, native favorite 279, Galaxy Watch Ultra, 450-unit viewport rendered at 480px. Read-only numeric measurements; no third-party layout code or assets are imported.

The reference uses Inter Tight SemiBold, whereas our existing font is SYNC_TO_DEVICE/BOLD. Equal nominal font sizes therefore do not guarantee equal ink width. This pass preserves existing fonts, colors, providers and accepted bar/indicator geometry; it adjusts geometry only.

| Element | Reference (450) | Normalized to our 512 viewport |
|---|---|---|
| Outer curved text | 26 | 29.58 |
| Middle title | 28 | 31.86 |
| Middle value | 34 | 38.68 |
| Outer text diameter | 343–344 | 390.26–391.40 |
| Reference middle text field height | 40 | 45.51 |
| Outer bar-text clearance at upper ends | 4 degrees | same angular clearance |
| Outer upper-right text direction | START, grows away from arc end | preserve optical clearance and growth direction |

Our outer tracks are offset clockwise by one degree compared with this reference; caption anchors follow those existing tracks so accepted indicator positions do not move. Existing custom foreground icons fill more of their images than the reference system icons. Their visible size must be reduced rather than copying the same nominal image-box size.

Canonical geometry source: configure_apex_progress.fit_outer_value_text, also reapplied by curated_palettes. Measured screenshot and APK/XML remain in temporary diagnostics, outside the repository.

## Applied geometry

Outer text is 29.6, middle title 32 and value 39 in the existing system bold font. Upper-right tracking is -0.03em; the Mobile slot renderer reads that same tracking. Generic outer icons use 22-unit boxes, padded glucose glyphs retain 52-unit boxes. All outer icon centers stay on the existing track circle.

Tick styles apply only to the opposite upper-right/lower-left pair on ApeX. Other bars remain continuous. Ten stationary ticks span the actual arc, with a longer current tick extending inward as the value changes. Non-tick styles retain the accepted circle-indicator geometry. Central glucose gauges stay gapped, with no circle indicator.

On-device active ApeX and the native editor show complete middle values and 100%. A refreshed native fixed-time preview now includes all middle values and the complete 100% text. The lower-left caption keeps its end anchor and has 36 degrees of growth space, retaining the measured font size. Final canonical import follows the last device check.
