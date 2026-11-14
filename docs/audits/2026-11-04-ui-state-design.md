# UI state architecture: first integrated MVVM slice

User authorizes implementation without additional approvals. Preserve appearance,
sensor protocol, alarms, source rules, persistence and service ownership.

## Verified current paths

- SugarWear alone owns G7CollectorService. It persists G7PersistedState through
  G7SensorStateStore, stores readings in G7ReadingDatabase, then notifies its
  ContentProvider and existing complication/tile fan-out. The database's instance
  StateFlow is not a cross-instance event bus: new database instances do not share it.
- G7WatchActivity currently observes ContentResolver plus preferences, reads SQL
  and persisted state while rendering, and owns visible clock/pairing timers.
- Mobile MainActivity already hosts Compose overview/watch surfaces through
  DashboardViewFactory; mutable ComposeRenderState copies and manual collectors
  mediate TherapyStateStore. Mobile does not bind to G7 BLE and excludes watch-only
  measurements according to existing product policy.
- Sugarlicious Wear Activity manually subscribes to TherapyStateStore and canonical
  invalidation events until destruction, resolves display state synchronously in
  render, and runs an additional visible 30-second clock. Preserve its explicit
  PHONE source policy, not the superseded pre-product-split assumptions.
- Widgets, Tiles and Complications already read durable canonical state and use
  semantic planners/deduplication. They are platform services, not ViewModel owners.

## Design

Each app owns a lifecycle ViewModel and a typed immutable StateFlow backed by its
existing canonical repository. No UI creates a second resolver or persistence layer.
Repositories adapt existing storage/invalidation APIs; database/provider work runs
on IO. Observation and UI-only clocks stop when the visible subscriber stops.
Latest state is loaded again on subscription, including changes made while stopped.

Mobile gets DashboardViewModel, Sugarlicious Wear WearOverviewViewModel, standalone
SugarWear G7ViewModel. Compose collects lifecycle-aware state; render functions take
state/actions and do not call Bluetooth services or the resolver. Wear may use
AndroidView for existing round-display renderers, preserving geometry and view IDs.
The ViewModels never own the collector service lifecycle or trigger Tile/widget/
complication fan-out. Platform permissions/navigation stay in Activities.

First slice is the actual overview data/display path in all three apps, not a
wholesale settings/pairing rewrite. Any remaining legacy settings/pairing controller
or feature-owned archive loader must be listed explicitly in the result. It must
not be described as full conversion of every screen. Graphs, gestures, fonts,
source policy, alarms and BLE timing remain unchanged.

Use StateFlow for recoverable screen state; SharedFlow only for invalidation or
one-shot events. Cancellation propagates. Storage failures are explicit and never
invent readings or make old data fresh. No new background polling.
