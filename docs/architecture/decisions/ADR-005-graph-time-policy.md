# ADR-005: Graph time policy

## Status
Accepted

## Problem
Graph surfaces previously disagreed about now, prediction width and CGM spacing.

## Decision
Graph geometry is calculated from event timestamps and an injected AppClock. The selected duration describes CGM history only. Predictions extend beyond that viewport and do not change its label. The current CGM dot anchors the Now marker.

## Rationale
Five-minute CGM spacing remains stable while dots move continuously with real time.

## Consequences
Renderers receive a semantic GraphSpec and may redraw only as their platform permits.

## Explicit non-goals
No process-wide minute wake-up is required for Widgets, Tiles or Complications.
