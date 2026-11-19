# Collector pause and One UI settings organization

Approved by explicit user request; existing authorization is to implement without repeated approvals. Preserve current sensor/recovery reliability and every saved setting.

## Independent domains

1. Collector pause: reuse persisted collectorEnabled=false stop path retaining sensor, credentials and history. Audit scheduled/boot/source callbacks and active-cycle cancellation races. Expose clearly labelled pause/resume in SugarWear overview/system status, communicate intentionally paused state, distinguish it from unlink. Minimal targeted lifecycle tests. Audit battery work without weakening receive windows or inventing battery savings; implement only justified reductions.
2. Settings grouping: organize Mobile controls by visual element rather than control type, including colors/sizes/visibility/outline together for glucose, trend, delta, age, graph and therapy. Reuse existing controls and persistence; retain explicit light/dark appearance editing and mode settings. Separate neutral cards with dividers, not one giant catch-all surface. Apply same principle to Sugarlicious Wear and SugarWear.
3. Shared Mobile submenu header: compact title beside fixed back at entry; upward scroll fades title into page depth before circular back surface appears just before first card; downward pull reveals a modest expanded title with a delayed crossfade. Keep scroll geometry stable, no re-anchoring and no synthetic footer scroll range. Main settings root keeps its larger header. Native accessibility font/display scaling is preserved.
4. Wear controls/menu audit: use shared One UI Watch-safe controls, consistent system text sizes, neutral cards, grouped element sections; keep scroll gestures from changing controls and preserve rotary handling. No unsupported Samsung internal API claim.

## Ownership / integration

Root owns Mobile scroll/header code and integration/build/device verification. Collector worker owns G7 lifecycle/system-status/overview and targeted tests; Wear settings worker owns WearSettingsUi, WearSettingsScrollView, WearSettingsActivity and G7 appearance/direct-to-watch/alarm menus; Mobile grouping worker owns DashboardViews, GraphAppearanceSettings, ComplicationAppearanceSettings, SugarliciousColorSettingsPanel and related settings grouping (coordinate any overlapping file first). No agent commits or runs parallel Gradle builds. Root runs one targeted combined gate after integration.

## Acceptance and evidence

Pause retains session/bond/key/history, leaves no sensor scan/GATT or reconnect/signal-loss alarms, remains paused after restart/source updates, resumes explicitly. Existing receipt reliability remains tested. Settings preserve values and preview updates, show meaningful object cards and fit system scaling. Scroll endpoints are actual content endpoints. Header transitions follow gestures and do not trigger controls. Build Mobile, both Wear apps, relevant tests/static checks; install already-authorized fixes when hardware is reachable. Report measured hardware proof separately from code/build proof and do not claim energy gains without measurements.
