# Sugarlicious Appearance and settings architecture

## Contract

Sugarlicious shares one appearance schema and the same rendering semantics, while every application owns its persisted values. Mobile, Wear and SugarWear preferences are never copied or synchronized implicitly.

Resolution inside an owner follows:

`APP_DEFAULT -> SURFACE -> COMPONENT -> INSTANCE`

A missing override inherits from its parent. A reset removes only the override at the selected scope. `AppearanceResolution` exposes the effective value, default, scope and source layer to developer diagnostics.

## Shared model and rendering

`AppearanceSchema` defines validated setting types and ranges. `TrendArrowStyle` is the complete value and `TrendArrowStyleOverride` is the sparse component/instance representation. Runtime and preview code resolve the same model into `TrendArrowRenderSpec`; previews have no separate appearance values.

`TrendArrowStyle` contains fill color, outline enabled, outline color, outline thickness, size and alpha. Light and Dark profiles are persisted separately. Watch Face Format handles AOD in its own declarative watch-face configuration; app surfaces do not manufacture an AOD profile where Android does not expose one.

The classic Wear/Collector color editor is shared through `ui-shared`. Compose surfaces use the same ARGB parser/formatter, value ranges, presets, alpha semantics and live-update contract from the shared model while retaining a responsive Compose layout.

## Persistence and migration

- Mobile owns `dashboard_ui` and widget/complication instance stores.
- Wear owns `watch_display` and Wear tile stores.
- SugarWear owns its collector appearance stores.
- Complication settings are transferred explicitly to Wear and remain provider/component overrides.

Persistence schema versions advance only alongside an implemented migration. Legacy trend size and fill keys are read as fallbacks, preserving existing values. New outline and alpha fields default safely when absent. No medical state, source selection, freshness, alarms or range classification is changed by appearance resolution.

## Reset and transfer

Color editors expose current/default values, alpha, hexadecimal input, RGB feedback, HSV controls, presets/recent colors, live preview and reset. Component and instance screens remove only their sparse override. App-level reset is isolated to the active owner and Light/Dark profile. This separation permits a future explicit "copy appearance to Wear" or preset import/export without creating permanent synchronization.

## Platform matrix

| Feature | Mobile | Wear | Collector | Watchface | Complication | Widget | Tile |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Shared schema and validation | yes | yes | yes | via providers | yes | yes | yes |
| Independent persistence | yes | yes | yes | WFF config | yes | per instance | per surface |
| Light / Dark | yes | yes | yes | WFF themes | host controlled | yes | yes |
| AOD | n/a | n/a | n/a | native WFF | host controlled | n/a | n/a |
| Fill / alpha / size | yes | yes | yes | host/WFF | host dependent | yes | yes |
| Outline / color / thickness | yes | yes | yes | host/WFF | monochrome host may tint | yes | ProtoLayout tint only |
| Hex / RGB / HSV / presets | yes | yes | yes | editor dependent | Mobile settings | yes | Wear settings |
| Reset | value/section/app | profile/app | value/profile/app | WFF editor | component | instance | surface |
| Runtime/preview parity | yes | yes | yes | WFF preview | yes | yes | yes |

Wear OS monochromatic complication hosts and ProtoLayout tiles retain final control over supported color/outline rendering. Sugarlicious still resolves one render specification and supplies every field that the host type can represent; unsupported geometry is not faked with a parallel renderer.

## Adding a setting

1. Add the definition and validation to the shared schema.
2. Add an explicit migration before increasing the owning persistence version.
3. Persist it only in the owning app/surface store.
4. Resolve through the scope hierarchy and pass a render specification to runtime and preview.
5. Add independence, migration, reset and runtime/preview parity tests.
