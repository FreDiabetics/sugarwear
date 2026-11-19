# Wear settings implementation plan

> For agentic workers: use executing-plans or subagent-driven-development task by task. The user has explicitly authorized completion without further approval.

**Goal:** Make both Wear settings surfaces coherent, compact, round-safe and system-scalable in the established One UI Watch visual vocabulary.

**Architecture:** Reuse WearSettingsUi, WearSettingsScrollView and existing persisted settings. Centralize typography/control geometry and round-safe value entry in ui-shared. App-specific pages keep their callbacks and validations and group controls by the element they affect. Subsequent approved Pixel scope also fixes sensor-control lifecycle and public Bluetooth bond management; canonical data/resolver rules remain intact.

**Tech stack:** Existing Android Views/Kotlin and Robolectric. Public APIs only; no private Samsung dependency or claim of a native Samsung SDK.

**Spec:** User's approved deferred scope and current request; Samsung Health settings on SM-L705F at current system font scale is the visual reference.

## Global constraints
- Preserve all stored values, ranges, callbacks, validation and scrolling/rotary gesture arbitration.
- System font family and SP-scaled text; no fontScale overrides, fixed-height text clipping or device-setting changes.
- Neutral dark menu cards; white body text, subdued secondary text and restrained accent selection.
- No BLE protocol or resolver changes. Pixel setup changes are limited to explicit start, safe bond removal and reactivation of an authenticated retired candidate; no sensor is paired by the agent.
- Do not run concurrent Gradle processes or edit Kotlin during lint. Root coordinates builds and device actions.

## Tasks
- [x] Shared controls: semantic typography, compact section/header/control geometry, multiline-safe switch and choice/value rows; full-screen round-safe numeric entry and color-picker consistency. Targeted gesture/scaling/validation checks.
- [x] Sugarlicious Wear: every WearSettingsActivity category, element-based groups, shared header and choices/actions, eliminate oversized/nested same-surface cards; preserve all options and save paths.
- [x] SugarWear: settings, appearance, alarms, Vigil, system status and pairing/unlink/threshold surfaces; shared controls, readable grouped status instead of giant diagnostic panel, coherent choices/actions.
- [x] Integrate and verify both app modules plus shared controls, static checks and independent review.
- [x] Install on Galaxy Watch and Pixel Watch, verify unchanged history and disabled collector after installation. Round-display/font-scale rendering is covered by targeted tests; final live sensor pairing and native consent remain user-driven. No display settings changed.
- [ ] Commit/PR/push and return clean main after green checks.

## Review focus
- Round-mask clipping at upper/lower corners and multiline enlarged system text.
- Vertical scroll starting on sliders/toggles must never change a setting; rotary remains scroll.
- Picker cancel/back/invalid number must preserve stored values; min/max ranges remain intact.
- No missing or renamed-to-unrelated settings; per-display controls remain with their display.
- Programmatic construction must not write settings or start/stop the collector.

## Added Pixel Watch 4 scope

User subsequently requested installation/debugging on Pixel Watch 4 (426x426,320dpi,SDK37), explicitly forbidding us to initiate pairing. Retained old sensor state targets another address; keep identity validation intact and do not delete history. Correct code-save auto-start versus explicit Connect activation, retaining the main wizard's explicit start. Fit Vigil graph artwork within the circular display with consistent WFF scaling and keep disconnected status out of the graph/scale area. Use fresh release WFF rather than stale debug artifact. Verify Pixel collector remains disabled before and after installation; leave final pairing to user.

## Pixel integration completion criteria

- Public API-36 companion bond removal with exact-address native consent and confirmed physical removal.
- Failed removal preserves state/history and commits no exclusions.
- Fresh explicit setup reactivates only an authenticated peer; no automatic wizard or sensor scans on launch.
- Exactly pair/unpair and pause/resume sensor controls.
- Real permission and DND-channel status with public settings fallbacks.
- Native consent and final live pairing remain user actions, distinct from automated build validation.
