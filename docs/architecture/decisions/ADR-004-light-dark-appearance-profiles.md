# ADR-004: Separate Light and Dark appearance profiles

## Status
Accepted

## Problem
Shared color values make switching modes destructive and migrations ambiguous.

## Decision
Light and Dark persist separate color profiles. System mode selects a profile; it does not merge their values. Schema migrations are explicit and versioned.

## Rationale
Users can configure both modes without one overwriting the other.

## Consequences
Exports identify profile and schema version. New roles require defaults in both profiles.

## Explicit non-goals
No implicit copying between profiles after migration.
