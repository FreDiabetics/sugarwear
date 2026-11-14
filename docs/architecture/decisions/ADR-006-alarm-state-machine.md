# ADR-006: Alarm state machine

## Status
Accepted

## Problem
Candidate, confirmation, acknowledgement, repeat and recovery must remain distinguishable.

## Decision
Alarms use explicit transitions and the central AppClock. Candidate, confirmed, triggered, acknowledged, repeated and recovered transitions are recorded as structured diagnostic events.

## Rationale
Reproducible transitions prevent wall-clock sleeps in tests and make real incidents reconstructable.

## Consequences
Invalid, stale, duplicate and out-of-order readings cannot confirm a glucose alarm.

## Explicit non-goals
UI visibility does not own or drive alarm state.
