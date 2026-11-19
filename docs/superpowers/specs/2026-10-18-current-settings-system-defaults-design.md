# Current Settings as System Defaults

## Objective

Make the currently configured Sugarlicious and SugarWear appearance and behavior settings the authoritative defaults for new installations, new surfaces, and reset actions without copying runtime state, credentials, sensor sessions, timestamps, or diagnostic data.

## Source of truth

- Mobile global settings: the connected phone's configurable preference stores.
- Sugarlicious Wear settings: the synchronized `watch_display` store plus the connected Wear app when available.
- SugarWear settings: the connected `app.aapswear.g7watch` stores.
- Widget templates: the newest active instance per provider kind (Glucose `74`, Graph `75`, Glucose+Graph `71`, Therapy `73`). Existing instances remain independent.
- Missing explicit preferences retain the current code default because that is the effective configured value.

## Architecture

Defaults live in typed default objects next to their owning stores. UI, persistence, reset, preview, and rendering consume the same objects. A settings-contract test inventories every user-facing control and proves it has one typed default and a persistence/reset path. Device-derived values are normalized to supported slider steps before becoming constants.

## Exclusions

Never promote secrets, URLs, tokens, certificates, package authorization chosen by the user, sensor codes, BLE identities, active sessions, alarms in progress, timestamps, histories, diagnostics, cached CGM/therapy data, widget IDs, or device-specific pairing state into defaults.

## Cleanup and integration

Remove only code proven unreachable or superseded by the canonical path. Preserve public provider identities and migrations. Commit on `main`, run the complete repository quality gate, push a branch, create a PR against `main`, verify the PR checks, then push the final verified state. Do not weaken checks.

## Acceptance

1. Fresh reads and reset actions return the captured current configuration.
2. Every visible settings control is covered by the contract inventory.
3. Existing user values are never overwritten.
4. New widget instances use the provider-specific captured template.
5. Full tests, debug/release builds, lint, detekt, and ktlint pass without new warnings.

