# Daily overview implementation plan

Goal: local-calendar-day measured-only TIR 70-180 / TITR 70-140; independently enabled range and therapy cards; fixed graph minimum sizes and scroll-linked compact glucose pill; portrait Mobile.

Reuse canonicalWidgetSamples for validated/deduplicated measurements, existing preferences with legacy fallback and existing therapy rings. Overview owns a bounded Compose scroll. Scroll alpha is gesture-linked, without refresh timers.

- [x] Daily stats and midnight/DST/gaps/boundaries/invalid-data tests.
- [x] Independent visibility switches with legacy migration fallback.
- [x] TITR left / TIR right equal-width halves. Range card follows therapy, CGM and metabolic graphs.
- [x] Therapy details under each ring. Steps/pulse removed following revised user request, including new reader/settings/assets/tests.
- [x] Symmetric hero spacing, fixed graph sizes and compact single-line pinned pill with transparent surroundings. Age reads vor N Min., number larger/bold.
- [x] Portrait manifest and scoped intentional orientation Lint exceptions.
- [x] Independent code review.
- [x] Final Mobile build, focused tests, Detekt, ktlint and Lint green; installed on SM-S948B. Therapy-first layout and explicit age visually inspected.
- [x] Final pinned-pill scroll inspection on SM-S948B: one-line glucose group remains at top with the same fixed #1c1c1c capsule surface as the settings back button; surrounding header remains transparent.

TIR is 70-180; TITR is 70-140, confirmed by the user. Only valid measured samples in the current local day enter the denominator; missing periods are not assigned to any range.

COB detail revised: show Today recorded carbohydrate grams (MEAL_CARBS + ECARBS) from validated canonical events in the current local calendar day. Respect source IDs for deduplication; preserve distinct real entries at equal timestamps/amounts. Never derive intake from current COB. Missing event history renders a dash. Focused day/identity/invalid/future tests cover the calculation.

The original sample-percentage calculation is superseded by docs/superpowers/plans/2026-10-30-time-weighted-tir.md: denominator is now covered interval duration, with linear threshold-crossing integration and no extrapolation through gaps.

Final Basal design: Mobile Basal remains a 280-degree segment ring with its ordinary bottom opening. The whole arc is accent-colored with only the black-outlined position marker; there is no internal placeholder/remaining-track gap. Keep the stronger 2.5 scaled-dp marker contour and restore the icon to its original bottom opening. The Wear tile remains on its previously approved continuous open-arc design.

Final capsule refinement: add a soft10dp platform elevation shadow around the compact glucose pill, without blurring the CGM data or introducing a full-window blur. Basal arc stroke remains unchanged at shared geometry7dp times its existing layout scale; only the marker contour uses2.5dp.

Final scroll fade: mask the full hero/button header with a scroll-position-driven vertical destination-alpha gradient near the viewport top. Lower content stays opaque until it approaches that edge, replacing uniform hero alpha. The compact pinned capsule retains its own fade-in. The mask is isolated in an offscreen layer and is evaluated in the draw phase only; no timer or layout rebuild per pixel.

Final hardware check on SM-S948B: confirmed the top-down scroll gradient (upper glucose glyphs fade while metadata and lower header remain opaque), pinned one-line capsule with shadow, unchanged-width Basal segment arc with2.5dp marker contour, and12dp card spacing. Final Mobile APK installed after successful full-project checks and a fresh Mobile test/build/Lint/static pass.

Final visual polish before merge: compact-pill trend height20scaled-dp instead of24; Mobile Basal marker outline reduced to1.5scaled-dp while retaining the2.5dp geometry inset so the arc does not resize; TIR/TITR remainder gap reduced from60% to25% of bar thickness.

Nightscout CGM backfill now requests only missing windows of the current local calendar day, including midnight. Cached endpoints and previous-day history are excluded from requests; canonical history merging and therapy preservation remain unchanged. Gap detection follows observed cadence; checked empty historical holes are throttled for 30 minutes. Focused coverage includes empty cache, midnight/day rollover, cached intervals and minute-cadence gaps.
