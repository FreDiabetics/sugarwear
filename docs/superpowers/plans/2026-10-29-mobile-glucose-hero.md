# Mobile Glucose Hero Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline.

**Goal:** Implement the approved free-standing overview and install Mobile.
**Architecture:** Existing canonical state/presentation feeds a new Compose header; existing details and charts remain.
**Tech Stack:** Kotlin, Compose, existing Android Mobile module.
**Spec:** ../specs/2026-10-29-mobile-glucose-hero-design.md

## Constraints and review focus
System font/density, narrow width and large text; gray stale state and suppressed unknown trend; no duplicate glucose; both TIR/therapy; no background work added.

- [x] Add OverviewGlucoseHero.kt: measured glucose/trend group, wrapping metadata, decorative arc.
- [x] Replace wordmark slot in OverviewInlineHeader.kt, retain toolbar.
- [x] Split summary card to details only in SugarliciousOverviewScreen.kt and remove obsolete header-space test.
- [x] Run Mobile tests/build/Detekt/ktlint/Lint; inspect real-device rendering and install directly.

## Initial hero verification

Full Mobile tests/build/Detekt/ktlint/Lint passed; final color/channel follow-up passed targeted header, color and bridge-service suites plus build/static/Lint. Lint: no issues. Installed APK SHA-256: 41ff128aa6f6cb2012cf3fd4e2815a031c41dddb284bef5cf72adea85a2a7e56, matched installed APK on SM-S948B. Overview inspected at original fontScale 0.9 and temporary 1.5; original restored. Obsolete Mobile Wear-runtime channel confirmed deleted. That initial range heading/calculation has since been replaced by the local-calendar-day time-weighted implementation documented in 2026-10-30-time-weighted-tir.md.
