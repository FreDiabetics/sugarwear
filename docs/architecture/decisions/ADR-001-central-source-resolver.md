# ADR-001: Central source resolver

## Status
Accepted

## Problem
Multiple surfaces must not independently choose or reinterpret CGM sources.

## Decision
Mobile consumes only the canonical, validated CGM state produced by the central resolver. Widgets, notifications and Wear synchronization use that state and never read a source around the resolver.

## Rationale
One source decision makes freshness, duplicate handling and fallback behavior explainable and testable.

## Consequences
Source adapters publish candidates. The resolver records accepted, rejected and transition decisions in the diagnostics ledger.

## Explicit non-goals
Widgets, Tiles and Watchfaces do not contain their own source-selection fallback.
