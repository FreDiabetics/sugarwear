# Orbit Arc refinement

User requests autonomous implementation, no approval loop. Extend existing WFF 5
generator, canonical graph and ApeX hands; no new weather fetcher or data source.

- Two dial choices: numeric and larger Roman. White/theme dial color only.
- Three smooth seconds: white/theme/red. Retire old tick/off choices safely.
- Samsung/Google weather launch choices and original weather icon packs only.
  WFF supplies conditions, not vendor artwork. Import only requested named assets
  from installed Samsung Weather and the user's reference Google Weather pack;
  preserve provenance, never infer redistribution rights. Cover 0..15, day/night,
  and missing/error data. Keep artwork aspect ratio and symmetric arc placement.
- Graph mode: lower center semicircle and lower battery semicircle, no opaque
  upper disc over graph. Arc mode retains full center/battery circle.
- Tight native selector bounds match drawn outer/inner arcs, not broad padding.
- Background toggle visibly changes the uncovered dial. Gradients use normalized
  wraparound angles and reference-like light-to-vivid colors, consistent on all bars.
- Logical concise German headers; preserve unrelated themes/provider assignments.

Approach: resource-only generator changes rather than a second renderer or weather
service. Native editor/render verification is required; schema tests alone do not
prove visual fidelity. Google Weather may not be installed: never silently launch
Samsung for the Google choice or claim an unavailable app was tested.
