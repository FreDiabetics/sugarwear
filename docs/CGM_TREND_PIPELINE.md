# CGM trend pipeline

## External reference

Sugarlicious uses AndroidAPS' public `TrendArrow` vocabulary for Mobile input. The upstream implementation preserves a supplied trend unless smoothing requires recalculation. Its calculated rate bands use `-3.5`, `-2`, `-1`, `+1`, `+2`, and `+3.5 mg/dL/min` with the inclusivity encoded in `CanonicalTrendPolicy`.

- AndroidAPS enum: <https://github.com/nightscout/AndroidAPS/blob/master/core/data/src/main/kotlin/app/aaps/core/data/model/TrendArrow.kt>
- AndroidAPS calculator: <https://github.com/nightscout/AndroidAPS/blob/master/implementation/src/main/kotlin/app/aaps/implementation/utils/TrendCalculatorImpl.kt>
- Dexcom G7 trend bands: <https://ie.provider.dexcom.com/sites/g/files/rrchkb156/files/2022-10/LBL021580_G7%20Getting%20Started%20on%20Your%20Dexcom%20App%20mmol%20OUS%20%28IRE%20Weblinks%29.pdf>
- Dexcom missing-arrow behavior: <https://www.dexcom.com/en-us/faqs/why-is-my-trend-arrow-missing>

## Canonical path

`source direction/rate → CanonicalTrendPolicy → GlucoseState → resolver/transport → TrendVisuals → TrendDrawableResources`

`deltaMgDl` is the difference between readings. `trendRateMgDlPerMinute` is the time-normalized rate. `trend` is the semantic direction. They are intentionally separate.

A valid source direction wins. A missing AndroidAPS direction may be derived only from valid, finite samples from the same source and compatible sensor/session identity, ordered by `measuredAtEpochMs`. G7 supplies a signed rate in tenths of `mg/dL/min`; it is decoded before classification. Missing or unsuitable evidence remains `UNKNOWN`.

## Root causes corrected in 2026-09

1. Mobile and G7 contained separate, conflicting rate classifiers. Boundary behavior could therefore differ by runtime path.
2. The SugarWear content provider exposed `trend_rate`, but the Direct-to-Watch reader discarded it, separating the selected reading from its canonical rate after transport.
3. Source-string normalization existed in more than one module, increasing the risk of mapping drift.

The shared policy and explicit semantic mappings remove those divergences. No trend ordinal is used across storage or transport boundaries. Debuggable builds log glucose, delta, elapsed period, rate, source trend, canonical trend, source, and chosen visual asset under `CgmTrendPipeline`.
