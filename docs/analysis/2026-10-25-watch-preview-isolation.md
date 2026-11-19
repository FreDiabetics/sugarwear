# Watch preview geometry and isolation

Orbit's lower-left circular slot defines its remaining track counterclockwise from 500 to 220 degrees, and its fill clockwise from 220 degrees. The Mobile preview had discarded direction and normalized the track into an 80-degree clockwise arc. The parser now retains track direction, fill origin/direction and the native 19.5-degree separation. Rendering uses the full 280-degree extent and the same shrinking remainder geometry. Native-slot tests cover both ranged and goal-progress templates. The corrected installed Mobile preview was inspected on the phone.

Complication appearance controls in the Mobile Watch menu are preview-only. Saving them persists locally without a Wear Data Layer request. Wear ignores legacy appearance-path payloads, and monochromatic complication providers do not consume legacy preview or Wear-app size/offset preferences. The host watchface owns its icon placement, dimensions and tint. The controls now describe their local scope.

Previously each direction's scaled bitmap was separately letterboxed into a square. Wide double arrows therefore received a square twice as large as single arrows, halving their displayed height in the same native icon frame. All monochromatic directions now use the same square host canvas and original vector ink height; aspect ratios are preserved. The obsolete per-direction runtime letterboxing path was removed. Rendering tests verify equal ink height and identical bitmaps despite legacy preview/global size and offset settings.

Validation: 18 Mobile native-slot tests and nine complication-icon rendering tests passed. Mobile, Wear and SugarWear debug APKs assembled; affected Mobile/Wear/Complications Lint, Detekt and ktlint checks passed with no Lint issues. Updated Mobile, Wear and SugarWear APKs were installed successfully. Independent review reported no important findings. No live glucose or trend was fabricated to exercise a double-arrow state.

Vigil marker artwork remains unchanged pending the user's replacement SVG.
