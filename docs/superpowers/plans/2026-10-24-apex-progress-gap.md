# ApeX progress follow-up

- [x] Suppress upper-right marker and marker-induced full fill when segmentation is enabled.
- [x] Add optional Trennlücke setting, default off, with separate reversed remaining arc and ROUND caps, following Orbit geometry. Preserve existing continuous bars and indicator modes. Gap mode suppresses the marker; upper-right segmentation takes precedence over the gap.
- [x] Test provider range boundaries (empty/full), official WFF schema, build/static checks and install ApeX plus the updated Wear-embedded package.
- [x] Continue native full-slot editor feasibility without replacing existing providers or falsely treating four favorites as native slot bindings.

Official WFF v2 validation, targeted geometry tests, Python progress tests and final build/static checks pass. Native stored preview confirms segmented upper-right has no marker. Gap-mode endpoint expressions are covered by source tests; a live toggled gap-mode visual comparison remains unperformed. Native provider assignment remains in the companion editor per user decision; see fixed-watch-slot-panel plan for installed panel and device evidence.

Reuse existing WFF assets; keep all slots, selected provider bindings and existing options. Add an idempotent asset modifier rather than a second watchface renderer. Independent current instances must not be reset.
