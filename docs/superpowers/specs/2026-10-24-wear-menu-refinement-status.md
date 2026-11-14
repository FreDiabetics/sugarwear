# Wear menu refinement — 24 October 2026

## Implemented

- Both Wear applications share round-display settings styling: grouped rounded cards, system typography, switches, sliders, back/title header and a full-screen palette/HSV colour picker.
- Copy, hexadecimal entry and colour reset are inside the picker. Colour reset uses each field's actual default, not its value when the picker opened. Semantic reset callbacks remain authoritative.
- ScrollSafeSeekBar waits for deliberate horizontal intent; vertical and ambiguous scrolling does not start a slider edit. Rotary input scrolls the settings container instead of editing a focused slider.
- The colour dialog has no platform action/title bar, keeping the first palette row inside the round viewport. Hex input commits on Done and Copy without requiring keyboard submission.
- Mobile Watch category headings scroll with their content and have no opaque header strip. The fixed overview/header remains unchanged.
- Vigil preview is generated from its current WFF geometry and real complication preview renderers. Miniature CGM previews preserve canonical sample quality and use the existing watchface graph renderer for ApeX/Orbit.
- ApeX's selected graph remains visible in AOD. Orbit marker contour is proportional to bar thickness (40%), with a bar-sized inner opening; the lower round gauge retains no marker.

## Verification and delivery

- All 34 ui-shared unit tests (including four picker and four gesture tests), Mobile/Wear/SugarWear debug builds, Detekt and final Lint for all three apps pass. Final Lint run: BUILD SUCCESSFUL, 4m 9s (temporary menu-lint-final log).
- Current Mobile debug APK installed successfully on SM-S948B.
- ApeX AOD and Orbit proportional-marker releases were installed on SM-L705F before its debugging connection dropped.
- New Wear/SugarWear settings APKs are built but installation and physical menu validation remain OPEN: the watch is absent from ADB and mDNS. No PIN bypass, preference reset or provider changes were performed.
- Latest proportional Orbit marker still needs a native preview recapture when the watch reconnects; do not describe that thumbnail as newly captured.

Existing unrelated worktree changes are preserved. No blanket commit or cleanup was performed.

## Vigil graph follow-up

- Removed the fixed 4dp plot-to-scale gap only for Vigil, retaining the dot/outline safety inset and truthful wall-clock timestamp placement (including the natural five-minute sample movement).
- Increased both axis label sizes from 8.5sp to 11sp. Vigil's scale lane is 35dp so all enlarged labels remain inside the circular watch mask; other graph adapters retain their previous dimensions.
- Regenerated the Vigil preview at the Ultra's 340dpi density and inspected it: 400/high/low labels and time labels are visible and uncut.
- Focused renderer/DirectToWatch/provider-preview tests, SugarWear build and shared Detekt pass. Watch installation remains pending its debugging connection.
