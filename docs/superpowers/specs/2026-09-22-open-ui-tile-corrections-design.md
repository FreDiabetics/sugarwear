# Open UI and tile corrections design

## Scope

Correct the four visibly incomplete surfaces named in the approved 2026-09-22
prompt without changing collector, resolver, freshness, backfill or alarm logic.

## Decisions

- Mobile target labels choose their side solely from prediction series that
  contain samples in the active viewport. Other overlays do not influence the
  side.
- SugarWear app typography remains authoritative. A shared semantic spec owns
  family, size and renderer-specific weight; ProtoLayout uses the closest
  supported system-family configuration and matching weight rather than a
  nominal token with different metrics.
- SugarWear in-app graph and graph tile continue through the same
  `g7SharedGraphInput` and `SharedWearCgmGraphRenderer`. Tests verify non-empty
  history reaches non-background pixels and that refresh planning includes the
  graph tile when readings change.
- Therapy indicators use one renderer-neutral specification for progress,
  basal icon state, colours, ring geometry and icon placement. Compose converts
  its zero-degree convention to ProtoLayout's convention explicitly. The fixed
  gap is centred at six o'clock and the icon occupies that gap.
- Existing one/two/three metric placement remains unchanged.

## Verification

Add geometry and rendering regression tests for both scale sides, shared graph
pixels, typography specs, ring gap centre, icon placement, BR/TBR icon state and
equal metric bounds. Run affected unit tests, debug builds, Lint, Detekt and
Ktlint before any hardware installation.
