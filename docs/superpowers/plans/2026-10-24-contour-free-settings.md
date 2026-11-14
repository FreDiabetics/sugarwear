# Contour-free settings and Orbit marker implementation plan

> **For agentic workers:** Use superpowers:executing-plans; execute inline without approval loops per user instruction.

**Goal:** Apply the user's fixed menu styling, gentle section snapping, Sugarlicious theme illustration and centered complication previews.

**Architecture:** Reuse native DashboardScrollView and Compose ScrollState/LazyListState; a common pure snap policy decides whether a nearby heading is eligible. Keep the data/resolver and watchface theme ownership unchanged.

**Tech Stack:** Kotlin, Android Views, Compose, native Canvas, Robolectric.

**Spec:** User request in current conversation, including opaque black Orbit current-dot fill with unchanged outline.

## Constraints and review focus

- Current Orbit dot: opaque black interior, existing outline; ApeX unchanged.
- Settings body text white, headings may use system accent. Neutral fixed cards shared with Watch menu; no selected tint or card/preview outlines.
- Slow drags settle at nearby headings; fast flings and control gestures are not captured. Respect scroll bounds and short pages.
- Ranged glucose text centered independently of its icon, matching therapy templates.
- Light/dark illustration depicts Sugarlicious dashboard rather than Samsung account/settings lists.
- White text requires neutral dark menu pages as well as cards, including in light appearance; the configured app/widget appearance and previews still show both modes.

## Tasks

- [x] 1. Add native-rendering regression for Orbit fill/outline, implement fill before outline and synchronize previews.
- [x] 2. Unify neutral menu tokens, remove Watch/catalog contours and selected fills, center ranged text independently of icon; update illustration.
- [x] 3. Add tested velocity/distance/bounds snap policy; integrate native settings, Compose widget headings and Watch section headers.
- [x] 4. Run relevant tests, builds/static checks and rendering review; install only on identified reachable targets if available. Record real-device limitations separately.

## Progress

Initial checkout: clean main at 0751615b. Existing full suite/CI green immediately before this task. User requests in-place main-only work and minimal necessary verification.

Orbit native rendering regression: failed for a transparent center, passed after opaque fill; unchanged ApeX hollow-marker tests pass.
Snap/color policy tests pass. Independent review identified remaining accent text and a control-retained gesture edge case. Explicit text overrides corrected. Native gesture regression reproduced an unintended 184-to-144 scroll; snapping now additionally requires actual parent scrolling.
Functional watchface rings/progress tracks and connection-status artwork are not preview-background contours and remain intact. No data/resolver changes.

Full tests/build/Detekt/ktlint/Lint successful (938 tasks); final Mobile system-bar follow-up verified separately (315 tasks). Native slider regression now passes. Wear and Mobile installed successfully on SM-L705F and SM-S948B respectively. Actual Orbit screenshot confirms the outlined current dot remains visible after installation. Device screenshot: temporary sugarlicious-orbit-dot-check.png. Mobile menus not manually navigated during the user's device use.

Next user-requested work, after this completed installation: complete per-watchface slot editor and slot-shaped previews; temporarily blinking blue editing outline on the dial; remove three preset information lines; smaller button labeled "An Watch senden".
