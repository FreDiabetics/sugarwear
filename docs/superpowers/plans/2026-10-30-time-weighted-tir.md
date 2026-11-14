# Time-weighted daily TIR/TITR

Replace only the Mobile daily range projection, preserve local-day boundaries, fixed TIR 70-180 / TITR 70-140 and existing UI. Use canonical valid history without the chart helper's 300-point cap, retaining a preceding midnight neighbor for clipping.

Integrate linear segments between consecutive compatible measured samples, splitting time at exact range crossings. Denominator is the sum of covered intervals only, never elapsed day length. Do not extend before the first or after the latest measurement. Unknown/zero coverage is a dash.

Gap rule: infer nominal cadence per source/sensor/session from the median observed intervals up to 5.5 minutes (timestamp jitter allowance) (5-minute fallback for sparse history); accept at most 1.5 nominal intervals, with an absolute 7.5-minute ceiling. This supports minute-based feeds without bridging their missed cycles and the primary five-minute G7 feed with timestamp jitter. Any explicit invalid measurement inside a segment or source/session change breaks continuity. No interpolation over such gaps. No new synthetic CGM records are stored.

Tasks: replace sample-voting tests with unequal-spacing, crossing, gaps/tail, midnight/DST, invalid/session and >300-sample tests; observe red then implement. Run focused tests, affected build/Lint/static gates, final independent review, install Mobile directly. Then audit all Sugarlicious WearOS tile fonts against the already-corrected SugarWear tile path and implement required follow-on changes.

## Evidence
Seven focused Mobile tests passed: unequal spacing and exact threshold crossings, long gaps and no trailing extension, local-midnight clipping and inclusive fixed bounds, invalid/session breaks, minute-rate feed with >300 samples, DST, and unrelated-source invalid barrier isolation. The additional cross-stream regression was observed failing before stream-keyed barrier maps were added. Independent review accepted the correction.

Mobile assemble, ktlint, Detekt and Lint passed (weighted-tir-stream-final.log). The time-weighted Mobile build was subsequently installed on SM-S948B after reconnecting. No sensor/collector changes.

The 5-minute sparse-history fallback is based on the primary G7 cadence documented by Dexcom: https://www.dexcom.com/en-us/faqs/why-am-i-getting-signal-loss . The 1.5-cadence/7.5-minute cutoff is an explicit conservative project continuity policy, not a claim that missing time is measured.
