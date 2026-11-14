# ADR-003: Canonical validated CGM model

## Status
Accepted

## Problem
Raw source payloads differ in units, timestamps, quality and duplication behavior.

## Decision
Every user-facing surface consumes the canonical validated CGM model. Only valid, ordered, non-duplicate readings participate in freshness and range confirmation.

## Rationale
The same reading must produce the same value, freshness and range presentation everywhere.

## Consequences
Contract tests use shared histories against the core presentation state.

## Explicit non-goals
Predictions and synthetic replacement values never count as real CGM readings.
