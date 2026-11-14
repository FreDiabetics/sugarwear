# Phase 11 - Dead-code and resource removal log

Date: 2026-09-14

## Removed block: retired Sugarlicious Wear G7 history forwarding

Removed:

- `app-wear/.../G7BackfillSync.kt`
- startup no-op dispatch
- reconnect no-op dispatch and misleading success diagnostics
- no-op legacy sync-request branch
- no-op legacy acknowledgement branch and misleading "forwarded" diagnostics

## Reachability proof

- Every production reference was inside `StateDataLayerService` and was removed in the same work package.
- `G7BackfillSync.sendPending` always returned `null`; `acknowledge` always returned `0`.
- The object did not access a database, Data Layer client, provider, service, manifest entry, XML resource, reflection target, serializer, or external API.
- SugarWear history remains private in the standalone collector database.
- Protocol constants and Mobile compatibility handling remain available for mixed-version upgrades; this cleanup does not rename or delete an external path.

## Validation

- Reference search after deletion: zero `G7BackfillSync` references.
- `:app-wear:testDebugUnitTest` passed.
- `git diff --check` passed before commits.

## Resource policy

Lint-reported unused Android resources are not bulk-deleted. Dynamic watchface, tile, complication, manifest, generated-WFF, Watch Face Push, preview, and resource-identifier entry points require per-resource proof. They remain classified for subsequent small deletion blocks rather than being hidden behind a baseline or removed by filename alone.
