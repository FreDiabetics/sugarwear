# Independent forecasts and reduced watchface settings

## Data streams

AndroidAPS now exposes independent Glucose, Forecasts and Therapy switches. Existing forecast enablement migrates from the previous therapy choice. Changing Therapy subsequently preserves Forecasts. The master remembers/restores all three stream choices, including forecast-only configurations. Dexcom remains the canonical CGM source when selected; enabling forecasts never implicitly enables AndroidAPS CGM dots. Original forecast values and timestamps are retained without vertical translation to another source's current glucose.

The persisted forecast cache previously merged independent kinds across calculation runs. An omitted kind could therefore survive from an older seed/time and appear displaced beside fresh lanes. The cache now keeps only the newest calculation generation, retains partial updates within that generation, ignores older delayed generations, and still retains an empty transient update. Composition also normalizes legacy persisted mixed snapshots immediately on load.

## Watchfaces

A shared catalog contains seventeen tonal palettes: orange reference, eleven additional basic colors/grays/brown, and five new combinations. Retained option identifiers preserve existing orange selections. Generated native flavors reference only available options.

Orbit fixes the measured Arabic indices and minute style 2; fixed Samsung weather artwork replaces its icon-pack selector. The deliberate seconds-based weather caption alternation was removed. Moon weekdays use three uppercase German letters without dots. Standalone complication icons rotate ninety degrees right (corrected direction after device review); text-embedded inline icons retain native text flow. Upper-right numeric captions anchor their trailing edge beside the final tick. Eight progress combinations remain because segmented, gradient and indicator variants have distinct behavior.

ApeX removes night/dial, minute, AOD, AM/PM and unread controls. Hours retain the first two choices, minutes retain the selected dots. Background patterns are stronger/thicker. The lower-left exterior icon sits nearer its progress arc. Date and extra date icon were cancelled by the user and are absent.

Both faces provide white, theme and red smooth seconds plus Off. Existing Orbit red ID 2 remains red; new Off is ID 3. ApeX keeps its existing Off ID 2. In ambient mode only graph, lower glucose complication, hour/minute hands and white/black center hub remain; no seconds hand.

## Evidence

- Fifty focused Mobile tests and five prediction-cache tests passed.
- Nine ApeX and thirty-two Orbit structural/geometry tests passed.
- Mobile/Wear Debug builds and official packaged WFF validation passed.
- Initial current versions installed to main Samsung phone and Galaxy Watch; finished Mobile/Wear/ApeX/Orbit updates installed subsequently. SugarWear/Vigil initial installation preserved data. Pixel devices were not changed.
- On-device Mobile inspection confirmed AndroidAPS Glucose off, Forecasts on, Therapy on with BYODA CGM active. Orbit native runtime confirms the seventeen-option palettes and removal of marker, AOD and background choices. Live Orbit rendering inspected; user subsequently corrected icon rotation to ninety degrees right, followed by a new build/install.
- No collector implementation/analysis changes.
