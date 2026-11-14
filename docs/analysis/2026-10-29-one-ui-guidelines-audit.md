# One UI review — 29 October 2026

Scope: Sugarlicious Mobile, Sugarlicious Wear, SugarWear settings and shared controls.
This is a source-backed implementation review, not a Samsung certification or a
claim that every screen has already passed physical accessibility testing.

## Sources and applicability

- [Overview](https://developer.samsung.com/one-ui/index.html): task-focused grouping,
  predictable interactions, readable content and responsive layouts.
- [App bar](https://developer.samsung.com/one-ui/comp/app-bar.html): compact/expanded
  page titles, reachable actions, stable navigation and restrained action counts.
- [Lists](https://developer.samsung.com/one-ui/comp/list.html): meaningful groups,
  concise labels and right-aligned switches.
- [Buttons](https://developer.samsung.com/one-ui/comp/button.html): consistent action
  emphasis and immediate feedback.
- [Motion](https://developer.samsung.com/one-ui/motion/basic.html): continuous,
  interruptible movement and restrained settling rather than abrupt jumps.
- [Layout and typography](https://developer.samsung.com/one-ui/accessibility/layout-and-typo.html):
  scalable text up to 200%, adequate space and discoverable icon-only actions.
- [Interaction](https://developer.samsung.com/one-ui/accessibility/interaction-and-control.html):
  controls must remain available, labeled and usable through alternative inputs.
- [Contrast](https://developer.samsung.com/one-ui/accessibility/color-contrast.html):
  text/icon contrast and additional information beyond color alone.
- [Screen readers](https://developer.samsung.com/one-ui/accessibility/screen-reader.html):
  meaningful control descriptions and navigable content structure.
- [Haptics](https://developer.samsung.com/one-ui/sound-and-haptic/haptic.html): feedback
  that supports interaction without unnecessary interruption.

The linked One UI Watch documentation currently leads to Asset Creator. Search
results for `one-ui-watch-tizen` describe the discontinued Tizen platform; its
Breeze Sans requirement and Tizen APIs are not applied to these Wear OS apps.
System-owned Android fonts and public Wear OS controls remain authoritative.
These design pages do not constitute the complete Galaxy Store publication,
privacy or medical-device policy set.

## Reviewed implementation and changes

| Area | Result |
| --- | --- |
| Scroll physics | Header gestures now preserve platform-style inertia and hand remaining movement/velocity to content. A regression test verifies movement after release. |
| Settling | Only the last 8 dp settle automatically; the former 35%/velocity jump is removed. Settling remains interruptible by a new touch. |
| Header title | `Datenquellen` changes color at the word boundary: `Daten` / `quellen`, consistently in View and Compose titles. |
| Settings organization | Source controls remain grouped by provider. Changes update the existing panel; they do not destroy its composition or close it. Multiple CGM inputs have their own warning surface with text plus an alert symbol. |
| Fonts | Shared menu text uses native sp/Compose sp and the device typeface. No density/font-scale override was found in the reviewed Wear settings path. |
| Large text | Shared Wear headers wrap rather than fixing the title to a single line. A 200%-font, small-width test checks this layout. This does not substitute for testing every full page at that scale. |
| Back action | Shared Wear headers use a semantic ImageButton with an accessible name and tooltip. Its 48 dp hit area surrounds the existing 44 dp visual background. |
| Headings | Shared Wear page and object headings expose native accessibility-heading semantics. |
| Sliders | Sugarlicious Wear, SugarWear appearance/Vigil controls and Wear color pickers pass the setting name to the native SeekBar. Sliders are keyboard-focusable; crown scrolling remains intercepted by the existing parent scroll container. |
| Source disclosure | Provider-name controls expose button role, current expanded state and an expand/collapse action label. The small visual press feedback remains restricted to the text. |
| Icon-only actions | Classic Mobile icon views expose their existing description as a platform tooltip. Decorative icons without descriptions remain excluded. |
| Color and states | Neutral settings surfaces and contrasting primary/secondary text are retained. Source, signal-loss and paused states already include explicit text/icons rather than color alone. User-selected clinical colors and approved brand treatment are preserved. |
| Rotary feedback | Existing `WearRotaryFeedback` delegates to platform ScrollFeedbackProvider or standard CLOCK_TICK without overriding global haptic settings. |
| Notifications | Native ongoing/promoted notification identity is retained. The guidelines do not add a supported font-weight setter to the system status capsule. |
| Round layouts | Existing round-screen insets, scroll-safe controls and shared typography remain in use; no desktop/Tizen font or layout is substituted. |

## Validation boundaries

Run the focused Mobile scroll/header tests and shared Wear control/color-picker
tests, compile all three apps, and run their affected format/static checks.
Physical checks should cover scrolling after release, disclosure controls, large
font/display settings, TalkBack traversal, crown behavior, and small round screens.
Record installation/device evidence separately; compilation alone does not prove
physical readability or exact visual equivalence with Samsung's own apps.

Collector/BLE behavior, pairing, alarms, source resolution and clinical thresholds
are outside this UI review and are not changed by it.

### Evidence for this change

- Focused Mobile gesture, snapping and header tests: passed, including continued
  movement after release and header-to-content handoff.
- Shared Wear settings-control and color-picker tests: passed, including the
  200%-font header, accessible back action and labeled/focusable sliders.
- Mobile, Sugarlicious Wear and SugarWear debug assemblies: passed.
- Affected module format and Detekt checks: passed.
- Mobile APK installed successfully on SM-S948B.
- Wear installation and physical Watch review await renewed WLAN-debugging
  connections. The previous Galaxy and Pixel addresses do not respond.
