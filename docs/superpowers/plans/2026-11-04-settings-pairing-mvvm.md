# Settings and pairing MVVM migration

> **For agentic workers:** Use the executing-plans / subagent-driven-development workflow. Preserve existing uncommitted overview migration.

**Goal:** Move settings and pairing presentation/controller state out of Activities in Mobile, Sugarlicious Wear and SugarWear; collect immutable StateFlow through Compose without changing approved appearance, protocol or BLE scheduling.

**Architecture:** Feature ViewModels own navigation, drafts and operation state. Repositories/use cases adapt existing settings stores and command implementations. Activities own only platform permission/companion-consent launchers, window/navigation and existing rendering adapters. Use Compose AndroidView interop for established custom One UI controls and charts rather than redesigning them. No second settings store, resolver or BLE state machine.

**Constraints:** No credential/code persistence in saved state; no sensitive logs. Permission/consent effects exactly once while visible. Cancellation propagates. Timings and existing generation guards remain semantically identical. Do not alter sensor protocol, alarms or service ownership. User explicitly opts out of further approval questions.

## Verified starting paths

- Mobile MainActivity/DashboardViewFactory own settings category, edit mode, scroll and preference actions; settings helpers render approved View controls.
- WearSettingsActivity owns mutable WearDisplayPreferences, selected appearance/category, synchronous persistence and fan-out.
- SugarWear settings are G7SettingsActivity plus Appearance, Alarm, DirectToWatch, Permissions, Setup and SystemStatus Activities; each mixes view building with feature state/storage.
- G7WatchActivity still owns pairing drafts, access/in-flight/generation guards, executor, success delay and visible refresh handler alongside the migrated overview host. G7CompanionBondAccess owns Android consent plumbing. This migration must retain the existing coupling order and timings exactly.

## Tasks

- [x] Characterize and migrate Mobile settings ownership/actions to a settings ViewModel and lifecycle Compose host. Preserve existing header/scroll geometry and feature stores. Includes individual widget configuration and color/numeric editors.
- [x] Characterize and migrate Sugarlicious Wear settings navigation/preferences/actions into ViewModel/repository and Compose lifecycle host; remove duplicate Activity controller state/IO.
- [x] Characterize and migrate SugarWear settings feature state/actions and Compose hosts, retaining all original settings and system permission semantics.
- [x] Characterize and migrate SugarWear pairing presentation state, operation sequencing/generation and visible timers into a pairing ViewModel/use case; platform consent remains Activity-scoped. No code saved in Bundle/SavedStateHandle.
- [x] Review cancellation, recreation, repeated taps, permission denial/retry, stale callbacks, paired/unlinked transitions and pause/resume. Reuse existing tests; add regression tests before production changes.
- [x] Run targeted tests, full tests/builds, architecture check, ktlint, Detekt and Android Lint. Record exact remaining scope and hardware-vs-local proof honestly.

Result: `docs/audits/2026-11-04-settings-pairing-mvvm.md`.

## Execution boundaries

Mobile and Wear settings are separate feature directories and can be implemented independently. SugarWear settings do not edit G7WatchActivity or pairing classes; the root owns pairing. Root coordinates Gradle runs to avoid shared output races. Synced ChatGPT sources and the pinned shared submodule are read-only.
