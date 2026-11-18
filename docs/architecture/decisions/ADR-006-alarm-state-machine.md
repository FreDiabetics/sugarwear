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

## Day and night profiles

Existing alarm rules become the day profile. On first use the night profile copies those rules and can then be edited independently. Automatic switching is opt-in and uses the watch's local time. A manual selection pauses the schedule while keeping its times. Global quiet modes apply to both profiles.

The alarm coordinator persists the selected profile and the last scheduled transition. Switching discards any pending high-glucose delay. The next measurement at or after the transition is assessed using the selected profile. System checks continue through the existing signal-loss monitor, which also schedules profile boundaries. Triggered notices keep their original measurement and stay available for acknowledgement.

## Explicit non-goals
UI visibility does not own or drive alarm state.
