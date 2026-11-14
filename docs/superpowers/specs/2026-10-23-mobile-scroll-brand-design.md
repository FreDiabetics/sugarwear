# Mobile refresh scroll stability and supplied brand

Preserve settings scroll runway and compact header geometry on non-navigation
status/data refresh. Do not disable live updates or change intended scroll header
animation. Reproduction: refresh shrinks settings padding 1653px to 24px and clamps
ScrollView position. Regression exercises actual MainActivity/layout.

Import supplied 66x66 single SVG path unchanged. Existing contextual green/white
coloring retained. Replace active Mobile/Wear launcher, monochrome/themed launcher,
notification qualifier variants and in-app branding; regenerate editable brand
masters and store bitmaps. Adaptive launcher content stays inside 66px safe area
within 108px viewport; notification silhouettes use full 66px viewport at 24dp.
Wear notification uses monochrome drawable rather than adaptive launcher icon.
Do not replace separate SugarWear sensor identity or historical screenshots.
Targeted tests, Mobile/Wear build/lint/format/static gates and explicit installation.

Verification ledger: actual MainActivity regression failed with expected padding
1653 vs actual 24; after preserving settings runway and compact bar negative margin,
both background-refresh scroll and transform-only title tests pass. Importer copied
supplied path without tracing into 11 runtime vector variants, editable masters and
store PNGs; geometry parity test passes. New green glyph visually inspected via
generated store bitmap. Final combined Mobile/Wear builds, lint, Detekt, ktlint and
targeted MainActivity tests succeeded (Temp/mobile-scroll-brand-final.log).
Mobile + Wear installed on explicit SM-S948B / SM-L705F serials, package app.aapswear,
0.6.10, device update timestamps 2026-10-23 23:44:30 / 23:44:37. No commit/push or
unrelated worktree cleanup performed. Awaiting unlocked phone/settings for live
scroll refresh check; do not describe that hardware behavior as already verified.

Follow-ups 2026-10-24:
- Short menus stop after title collapse, with compact title above first card. Only
  long menus retain a runway for the last card to reach the header. Native sizing
  uses actual child bottoms, not fillViewport's artificially enlarged container;
  Compose measures natural content excluding footer. Five geometry tests and the
  two actual MainActivity regressions passed in mobile-short-menu-final.log.
- Scroll-safe slider interaction: native SeekBar previously changed 25 to 87 on
  initial DOWN before gesture direction was known. Store DOWN without native
  tracking; vertical movement/cancellation never forwards it, horizontal dragging
  starts normal tracking after platform touch slop. Explicit taps and keyboard/
  accessibility adjustment remain supported. Remove DashboardViews' premature
  disallow-intercept listener. One shared base covers native Mobile and AndroidView
  widget/appearance/complication sliders. Native Switch/menu rows rely on parent
  ScrollView CANCEL; regression dispatches real vertical gestures through both.
  Do not disable scrolling or save-and-revert accidentally changed settings.

Final gesture gate: OneUiSeekBarGestureTest 3/3 (vertical DOWN/MOVE, horizontal
adjustment, canceled gesture + deliberate tap); geometry 5/5; actual MainActivity
refresh/title tests 2/2; DashboardScrollViewGestureTest 4/4 including Switch/Button
scroll cancellation and preserved scrolling. Its first fixture omitted a second
MOVE after parent interception and therefore correctly failed the scrolling
assertion; corrected gesture sequence then passed. No production check suppressed.
Build/lint/Detekt/ktlint pass in Temp/mobile-scroll-gestures-verified.log. Native
runtime test could not be completed: phone disconnected during first gesture
attempt, then relocked before installation; do not claim physical gesture proof.
# Wear settings scroll safety follow-up (2026-10-24)

The tested direction-aware slider now lives in `ui-shared` and is used by Mobile,
Sugarlicious Wear settings, SugarWear appearance and Direct-to-Watch settings.
Vertical gestures do not enter native slider tracking or invoke value callbacks;
intentional taps and horizontal dragging retain their existing behavior. Standard
switches and menu rows continue to receive cancellation from their scroll parent.
No data-source, collector or watchface behavior is changed.
