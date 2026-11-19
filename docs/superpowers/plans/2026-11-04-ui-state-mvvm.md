# UI State MVVM Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox syntax for tracking.

**Goal:** Integrate the first real lifecycle-safe ViewModel/Compose overview path in Mobile, Sugarlicious Wear and SugarWear without changing visible behavior or sensor ownership.

**Architecture:** Per-app ViewModels consume existing canonical repositories. Persisted provider notifications bridge the standalone collector; no process-local database flow is misrepresented as shared. Platform renderers remain interoperable while presentation state is immutable and lifecycle collected.

**Tech Stack:** Kotlin, Android lifecycle ViewModel, StateFlow/coroutines, existing Compose BOM, AndroidView for existing Wear views, existing DataStore/SQLite/provider and ProtoLayout/Complication services.

**Spec:** docs/audits/2026-11-04-ui-state-design.md

## Global constraints

- Preserve source policy, timestamps, stale/session rules, alarms and BLE protocol.
- No observer or UI timer while stopped; no UI-triggered background fan-out.
- Preserve approved layout, fonts, scaling and IDs. No visual redesign.
- No duplicate repository/resolver implementation. No service-lifecycle dependency on UI.
- Central source changes must propagate to Mobile/Wear consumers without manufacturing current data.

## Tasks

- [x] Mobile: characterization/failing ViewModel tests, repository/immutable state,
  DashboardViewModel and lifecycle Compose overview/watch wiring; remove old copied
  mutable render state and duplicate Activity observation on migrated paths.
- [x] Sugarlicious Wear: failing lifecycle/resolver/action tests, IO repository,
  WearOverviewViewModel, state-only renderer/Compose interop; stop offscreen work.
- [x] SugarWear: failing provider/state/lifecycle tests, application-context G7 UI
  repository and G7ViewModel, immutable overview state and Compose collection;
  remove direct overview SQL/state subscriptions and duplicate timers.
- [x] Verify widget/tile/complication planner contracts and add no-fan-out proof.
- [x] Independent review, targeted and full product tests/build/lint/static gates.
- [x] Record coverage, remaining legacy controllers and actual verification scope.

Result: `docs/audits/2026-11-04-ui-state-result.md`.

## Review focus

Configuration/recreation, stop/resume and missed invalidations; backfill and
out-of-order delivery; stale transition without new samples; collector paused/
unconfigured states; failed IO without replacing valid readings; no observer
leaks; UI clock never changes measuredAt or sends background updates.
