# Phase 12 - Terminology, branding, and compatibility

Date: 2026-09-14

## Inventory snapshot

Case-insensitive repository matches outside generated build output include: Dexcom (177), AndroidAPS (139 exact long-name matches), xDrip (123), SugarWear (94), Sugarlicious (1866), Vigil (83), and Collector (1038). Counts include code identifiers, tests, documentation, protocol diagnostics, and generated-source inputs; they are not all user-facing labels.

## Classification and decision

| Example | Classification | Decision |
|---|---|---|
| `DataSourceId.ANDROID_APS`, source labels | source attribution / persisted enum | retain |
| AAPS broadcast actions and package queries | external contract | retain exactly |
| xDrip broadcast action, extras, package name | external contract | retain exactly |
| `DEXCOM_G7_WATCH`, GATT profile, setup certificate/key comments | technical protocol / persisted contract / legal attribution | retain |
| BLE-discovered device name `Dexcom G7` | technical device identity | retain |
| "AndroidAPS" and "xDrip+" in source selectors/status | truthful source attribution | retain |
| SugarWear, Sugarlicious, Vigil in current menus/products | user-facing product text | already current |
| `ic_complication_xdrip` | internal legacy resource identifier | retain until a dedicated resource migration; not user visible |
| AndroidAPS comparisons in graph comments/tests | implementation provenance / compatibility documentation | retain |

## Result

No blind replacement was performed. Removing or disguising source/protocol names would make diagnostics misleading and could break installed-version compatibility, broadcasts, persisted enum decoding, BLE behavior, package discovery, or attribution. The reviewed user-facing product labels already use the current SugarWear/Sugarlicious/Vigil naming where they describe this product rather than its upstream data source.

## Compatibility rule

Package IDs, intent actions, BLE UUIDs, serialized enum names, persisted keys, and Data Layer paths require an explicit versioned migration before renaming. Internal legacy identifiers may be renamed only in a separate mechanical change after all XML, manifest, generated-WFF, reflection, and resource-table references are proven.
