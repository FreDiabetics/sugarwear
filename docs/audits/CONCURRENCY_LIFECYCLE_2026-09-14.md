# Phase 8 - Concurrency, lifecycle, and crash resistance

Date: 2026-09-14

## Fixed

### Blocking Wear Tile callbacks

The Sugarlicious Wear tile services used `runBlocking(Dispatchers.IO)` inside `onTileRequest` and `onTileResourcesRequest`. Moving the work to IO did not make the synchronous callback non-blocking: the binder callback still waited for state I/O, provider resolution, and graph bitmap rendering.

The services now return a `SettableFuture` immediately, complete it from a lifecycle-owned IO coroutine, propagate exceptions to the future, and cancel the scope in `onDestroy`. This matches the already working asynchronous SugarWear graph-tile implementation.

### Unbounded BLE callback queues

Per-GATT-generation callback channels were `Channel.UNLIMITED`. Control channels are now bounded to 16 events and the notification/history channel to 512 events, exceeding the protocol's 300-record backfill ceiling while preventing unbounded growth under a callback storm.

### Stale callback ownership

Phase 6 fixed the stale-characteristic failure race; generation invalidation occurs before disconnect/close and closed channels reject subsequent callbacks.

## Reviewed

- No production `GlobalScope` usage.
- No production `Thread.sleep` usage.
- Loops in collector/authentication and parsing paths are bounded by cancellation, protocol exit, size, or timeout rather than busy waiting.
- Activity/service-owned coroutine scopes are cancelled and preference listeners/receivers inspected have matching unregister paths.
- Broadcast receivers that perform asynchronous work use `goAsync` completion paths; their end-to-end duration remains part of the energy/budget audit.

## Verification

`:app-wear:testDebugUnitTest :g7watch:testDebugUnitTest` passed after both lifecycle changes.
