# ADR-008: Visual regression testing

## Status
Accepted

## Problem
Typography, arrows, graph clipping and round-screen bounds regress despite logical tests passing.

## Decision
Deterministic renderer tests produce referenceable PNG artifacts. `tools:screenshot-comparator` is the canonical pixel comparator; real-device captures remain the acceptance gate for Wear and Watchfaces.

## Rationale
Generated images catch geometry drift, while hardware captures catch platform rendering differences.

## Consequences
Visual changes update references deliberately and include a diff artifact. CI retains generated G7 visual QA images.

## Explicit non-goals
A successful bitmap comparison does not prove physical-device acceptance.
