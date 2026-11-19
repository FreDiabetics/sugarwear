# ADR-007: Central observable runtime state

## Status
Accepted

## Problem
Independent mutable state in Activities, Services, Widgets and Tiles creates contradictory UI.

## Decision
`TherapyStateStore` is the observable persisted runtime state boundary. Source adapters and the canonical resolver publish state; renderers collect or snapshot it. Surface-local preferences remain local and never become therapy truth.

## Rationale
A single observable boundary preserves process-restart behavior while supporting Flow-based UI updates.

## Consequences
New therapy fields enter the shared model and store contract before a renderer consumes them.

## Explicit non-goals
Appearance settings are not globally coupled across applications or surfaces.
