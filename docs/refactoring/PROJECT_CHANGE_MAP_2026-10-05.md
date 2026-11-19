# Sugarlicious project change map

Date: 2026-10-05  
Baseline: `main` at `981b74d0`

## Module ownership

| Module | Responsibility | May depend on application modules |
|---|---|---|
| `core-model` | Canonical domain, freshness, source, graph and presentation policies | No |
| `data-source-api` | Common external-source adapter contract | No |
| `data-source-aaps` | AndroidAPS ingestion and validation | No |
| `dexcom-g7` | Dexcom protocol and cryptographic primitives | No |
| `wear-protocol` | Versioned cross-device messages | No |
| `wear-storage` | Durable canonical, therapy and diagnostic state | No |
| `ui-shared` | Shared render policy and vector assets | No |
| `complications` | Wear complication render/service layer | No |
| `app-mobile` | AndroidAPS bridge, Mobile UI, widgets and phone transport | Application shell |
| `app-wear` | Sugarlicious Wear UI, Tiles and watchface delivery | Application shell |
| `g7watch` | Standalone Wear-only direct G7 collector and SugarWear surfaces | Application shell |
| `watchfaces:*` | Code-free Watch Face Format packages | No application business logic |
| `tools:*` | Build-time parsing, generation and screenshot comparison | No runtime ownership |

## Runtime entry points

- Mobile: `MainActivity`, `PersistentBridgeService`, `MobileDataLayerService`, authenticated `AapsStatusReceiver`, boot receiver and five widget receivers.
- Sugarlicious Wear: `WearActivity`, `StateDataLayerService`, Tile services, complication providers and watchface push controllers.
- SugarWear: `G7WatchActivity`, `G7CollectorService`, boot/reconnect/watchdog/alarm receivers, two Tile services and the signature-protected reading provider.
- Watchfaces: platform-loaded WFF XML and resources; no runtime DEX is permitted.

## State owners

| State | Canonical owner |
|---|---|
| CGM domain identity/freshness/source decision | `core-model` |
| AndroidAPS ingestion | `data-source-aaps` |
| Mobile canonical orchestration | `MobileCanonicalCgm` |
| Cross-device payload | `wear-protocol` |
| Canonical and therapy persistence | `wear-storage` |
| Direct sensor credentials/session/runtime | `g7watch` |
| Direct readings and gap ledger | `g7watch` |
| UI appearance | Owning app's settings store |
| Rendering | Owning surface, consuming canonical policy |

## Data paths

```text
AndroidAPS broadcast -> authenticated adapter -> canonical Mobile state
 -> durable store -> Wear transport -> Wear store -> surfaces

G7 advertisement -> SugarWear BLE -> authentication/protocol
 -> reading database/gap ledger -> canonical resolver -> SugarWear surfaces
```

Mobile deliberately has no direct G7 BLE permission or collector dependency. Source selection belongs to the canonical resolver, never a renderer.

## Refactoring hotspots

1. `G7CollectorService` and `AndroidG7Ble`: separate pure policy/mapping from Android runtime ownership.
2. `DashboardCharts`: separate viewport, series transformation and geometry from View drawing.
3. Mobile appearance/widgets: separate preference migrations and bitmap rendering from Compose/Glance composition.
4. Complications/Tiles: separate pure render models from platform service subclasses.
5. Stores/providers/services: make revision, cancellation, timeout and completion contracts explicit.

## Verification

`verifyArchitecture` evaluates the actual Gradle project dependency objects and parses the Mobile/SugarWear manifests. It intentionally avoids source-name or line-count assertions: responsibility boundaries are enforced through dependency and packaged-platform contracts, while behavior remains protected by module tests.
