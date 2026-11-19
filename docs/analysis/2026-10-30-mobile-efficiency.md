# Mobile efficiency audit

## Scope and evidence

Audited PersistentBridgeService, MainActivity, MobileDataLayerService, widget coordinator, Share and Nightscout source loops. Existing uncommitted hero UI preserved.

A 28.807-second sampled device trace contained 151 recorded method calls and predominantly parked threads/main message polling. It captured a quiet interval, not an active data/render cycle, and does not establish a battery percentage or day-length estimate. Profiling was stopped. Power snapshot contained no Sugarlicious partial wakelock. Background/foreground residency is not equivalent to CPU-active duration.

## Proven causes and correction

1. MainActivity collected TherapyStateStore until destruction, including while stopped. Each state emission wrote an observation timestamp and called refresh(), which reapplies colors and renders the dashboard/charts. Collection now starts in onStart and cancels in onStop. Reopening collects the latest persisted state. The independent service, ingestion, storage, resolver, widgets, alarms and notification schedules are unchanged.
2. Writing mobileObservedAt also triggered diagnosticsListener, calling refresh a second time for the same emission. The listener now ignores only that self-written key; the collector still explicitly refreshes immediately. Other diagnostic changes continue to refresh.

The regression test first failed on the old code: a stopped activity still wrote its observation timestamp. It also checks observation resumes after restart.

## Other paths

Share/Nightscout source loops already disable when unselected and retain failure backoff; Share schedules near the next expected reading. Widget render paths enumerate only installed instances, preserving the existing minute refresh for visible age/graph movement. No clinical path was slowed or source interval increased. No speculative changes to BLE, alarm freshness boundaries or foreground-service lifetime.

## Validation

33 MainActivity lifecycle/navigation tests passed, including the regression that failed before the change. Mobile debug build, Detekt, ktlint and Lint passed; Lint reports no issues. Updated APK installed on the main SM-S948B. Device lifecycle check explicitly verified STOPPED before taking its baseline, then confirmed no further UI observation while still STOPPED, followed by immediate latest-state observation on reopening and no sampled AndroidRuntime fatal error. An earlier immediate-HOME baseline included transition work and was discarded, not counted as a stopped-state result. Independent focused review found no important issues.
