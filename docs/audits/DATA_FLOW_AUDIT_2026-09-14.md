# Sugarlicious/SugarWear data-flow audit (2026-09-14)

## Canonical phone CGM path

```text
AAPS broadcast or xDrip broadcast
→ source adapter validation
→ MobileCanonicalStateCoordinator
→ DisplayHistoryAccumulator
→ PhoneTherapyStateStore
→ TherapyStateStore
→ CanonicalDataChangeFanOut
→ widgets + Wear Data Layer
→ Wear StateDataLayerService
→ history/prediction merge
→ TherapyStateStore on Watch
→ activity/tile/complication invalidation
```

The path is centralized for normal AAPS/xDrip receipt. Widgets read `TherapyStateStore`; they do not directly parse source broadcasts. Wear also persists and renders the canonical transported state.

## Confirmed data-flow findings

### D-01: Duplicate DataClient/MessageClient delivery performs side effects before equality rejection

Status: confirmed reliability and energy defect candidate.

Mobile intentionally sends the same payload over:

- DataClient for durable eventual delivery, and
- MessageClient for low latency.

Wear serializes concurrent receipt with `stateSyncMutex` and rejects older state, but `shouldAcceptPhoneState` accepts an incoming state whose `receivedAtEpochMs` equals the stored timestamp. The envelope `eventId` is persisted for diagnostics but is not used as an idempotency key.

Before the later meaningful-state comparison returns, a duplicate delivery can still:

- rebuild and merge history,
- read the SugarWear content provider,
- resolve the canonical source,
- persist resolver hysteresis memory,
- publish G7 alert mode,
- write prediction diagnostics.

Required fix:

- establish a canonical transport event/revision identity,
- reject an already-applied identical event before resolver and alert side effects,
- continue accepting genuinely newer therapy-only state even when glucose measurement time is unchanged,
- retain the older-state protection for delayed DataItems,
- add order-permutation tests for message-first, data-first, duplicates, equal measurement/new therapy, and process restart.

### D-02: The envelope event identity includes receipt time but is not authoritative

`WearEnvelope.eventIdFor` includes source, sensor/session, measurement time, and `receivedAtEpochMs`. Re-encoding the same measurement after a therapy update or receipt-time change creates another event ID. A transport revision must distinguish full display-state revision from canonical glucose identity; one identifier cannot safely serve both purposes without explicit semantics.

### D-03: Dual Mobile stores can diverge across interrupted writes

The raw phone-input and canonical display stores are written sequentially. Nightscout enrichment repeats the same two-store pattern. No shared persisted revision currently proves that both writes represent one commit.

Required fix:

- introduce a monotonic state revision in the persisted contract or an equivalent reconciliation record,
- write characterization tests for interruption after either store write,
- define startup recovery without deleting valid glucose or therapy history,
- dispatch consumers only for a reconciled committed revision.

### D-04: Duplicate delivery is deliberately generated but only partially coalesced downstream

The durable/fast dual transport is justified, but coalescing must occur at every expensive downstream boundary. Current equality handling prevents a second `TherapyStateStore.save`, tile update, and complication update, but does not prevent all work listed in D-01. The transport architecture should remain dual-path unless measured evidence shows a better reliable alternative.

### D-05: Canonical fan-out can extend broadcast lifetime

AAPS and xDrip receivers use `goAsync`, save state, and then await `CanonicalDataChangeFanOut`. Widget invalidation and Wear publishing execute concurrently, but completion waits for both. Wear publishing in turn waits for the durable DataItem operation after a bounded immediate-message attempt.

Required validation:

- measure worst-case receiver lifetime with Play services unavailable and multiple Glance hosts,
- enforce a bounded completion budget,
- preserve durable eventual delivery,
- avoid cancelling already-committed state when one fan-out consumer fails,
- do not move source parsing or storage onto the main thread.

### D-06: xDrip support conflicts with AndroidAPS-only presentation filtering

xDrip broadcasts can be parsed, selected, persisted, exported, and sent to Wear. `MainActivity` then applies `mobileAndroidApsOnly`, which removes non-AAPS history. This can leave a current xDrip glucose with a filtered or empty graph history. The migration helper can also force every tested input to AndroidAPS because automatic and xDrip cases are not covered.

Required resolution:

- define the intended product policy,
- add tests for all source preferences,
- if xDrip remains supported, preserve its canonical current/history consistently,
- if Mobile is intentionally AndroidAPS-only, remove the unreachable xDrip product path through a compatibility-aware deprecation rather than keeping a half-active source.

### D-07: Provider failures are indistinguishable from an empty SugarWear source

Wear resolver provider calls return empty data on any exception. This protects UI rendering from crashes but erases the distinction between no readings, provider unavailable, permission denial, cursor/schema mismatch, and malformed data.

Required fix:

- return or record a categorized read outcome,
- keep rendering fallback non-throwing,
- rate-limit repeated diagnostics,
- never convert infrastructure failure into a fabricated sensor state.

## Settings paths

Appearance and graph settings use multiple named preference stores (`dashboard_ui`, `watch_display`, `complication_appearance`, and `direct_to_watch`) and are transferred over explicit Watch configuration/color paths. The architecture intentionally allows surface-local appearance, but keys are read directly at many render sites. Phase 9/10 must verify that every setting has one owner and that direct reads do not bypass migration or live invalidation.

## Collector-local path

SugarWear writes collector readings to `g7_readings.db`; SugarWear UI, tiles, provider, and Wear-side resolver read that database through direct repository access or the permission-protected provider. Detaching a sensor is expected to preserve reading history while removing active sensor/session credentials. This invariant must be tested at database, state-store, provider, graph, and handoff levels.

## Phase-4 outcome

Seven data-flow findings are established. D-01 is the first high-confidence self-interference issue: an intentionally duplicated transport causes repeated resolver and alert side effects before downstream equality suppression. D-03 and D-06 are the highest data-consistency questions for the next phase. No production behavior was changed in this phase.
