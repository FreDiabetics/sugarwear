# Adaptive Mobile overview and measured range periods

## Implemented

Landscape uses42/58 panes: glucose hero, canonical logo and Watch/Settings actions left; original cards independently scroll right. Orientation is unlocked. Right-pane scrolling was checked while left action coordinates remained unchanged. Therapy columns use integer-pixel flooring so fractional dp rounding cannot wrap the third indicator to another row. The IOB/COB chart is taller, headed Insulin und Kohlenhydrate; range card is headed Zeit im Zielbereich.

Portrait chrome starts translating/fading before the sticky capsule appears. The capsule contains logo, centered glucose/trend/delta/unit/age and green filled Watch/Settings actions. Watch artwork shifts8dp right inside its unchanged48dp target; the reading complex shifts to the midpoint between the logo and Watch artwork. A16dp soft shadow plus24dp fading underlay separates the capsule from scrolling cards. Light-mode metadata retains readable foreground colors. The smartwatch model is absent from the overview. Data-source cards use the canonical monochrome Sugarlicious foreground with adaptive insets compensated.

Range label cycles Today/3/7/14/30/90 calendar days. Selection persists independently of Compose view recreation. A single spring transition drives both TIR/TITR columns; no polling animation loop. The selected label and statistics publish atomically only after the matching archive loads. Existing gap exclusion, invalid-boundary blocking, sensor/session separation and time weighting remain active. The phone-only SQLite archive retains92days with a DST margin, imports existing history, deduplicates observations and records invalid boundaries separately. Watch state/history/transport do not gain90days of samples. Archive failures are logged and do not suppress current CGM delivery. Missing historical measurements are never synthesized or classified.

## Verification

- DailyGlucoseRanges tests, SQLite persistence/invalid-boundary tests, TherapyStateStore tests, period restoration/loop test and pixel-layout regression tests passed.
- Mobile Debug build passed. Format and lint checks run for affected Mobile/storage code.
- Main Samsung smartphone updated directly; no collector, watch or Pixel installation changes for this task.
- Device: portrait capsule logo/actions/centering/shadow inspected; landscape therapy indicators remain on one row; left pane remains stationary while right cards scroll.
- Device: all six period labels exercised,90-day selection survives portrait rotation, then loops back to Today.
- Temporary screen-awake and rotation test settings restored to original values.
- Read-only review found no critical/important issues before physical verification; the physical one-pixel wrap and lost selection were subsequently fixed and regression-tested.

The long-term archive starts with actual locally available history. Selecting90days does not imply ninety complete days of prior measurements exist.
