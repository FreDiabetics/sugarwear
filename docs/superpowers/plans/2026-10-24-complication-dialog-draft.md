# Complication dialog follow-up

Goal: one type/appearance editor with draft changes, selected-only explanatory text, and a cancel/save pill shown only for real changes. Gray gear icons and centered numeric ranged previews. Existing provider/transport contracts remain unchanged.

Approach: reuse existing type previews and appearance controls; remove the separate immediate-saving appearance dialog. Keep a local immutable draft, save appearance and selected variant only from Save. Back/outside/cancel discard it. Selecting the original values hides the action pill again. Retain the current variant in the options, and allow gear access even for single-format entries.

- [x] Test draft dirty/revert behavior; integrate one dialog and the existing action pill.
- [x] Remove inherited line spacing/font padding from centered preview text; verify gray gears and numeric positioning on device.
- [x] Relevant Mobile tests, format/static checks, build/Lint and direct Mobile installation; verify cancel/save and selected-only info on the smartphone.

Scope: this does not pretend the existing four favorite IDs can assign arbitrary third-party WFF slots. Full native slot assignment/blinking remains separate; continue that work after this requested UI correction.

Hardware follow-up completed on unlocked Galaxy S26 Ultra: gray gears and centered glucose/trend number verified in screenshot; current variant remains in the menu; no Save button before changes; only selected format shows explanatory text. Compared both appearance and preset preferences before editing, during draft, after Cancel and after Back: unchanged. Saved a temporary Short Text format, confirmed immediate catalog/persistence update, then saved the original Ranged Value format again. Both appearance and preset preferences match the original baseline after restoration. No user setting was left changed. Screenshot evidence: `complication-dialog-verified.png` in system Temp.

Evidence: draft tests failed with a false dirty-state implementation, then passed after variant/appearance comparison. Full Mobile suite: 566 tests, zero failures/errors. Build, Detekt, ktlint and Lint succeeded (`complication-dialog-final.log` in system Temp). Mobile APK installed successfully. Read-only reviewer found no scoped blocker; hardware checks remain pending the currently locked phone.

Implementation: both gear and long press open the same dialog; current type remains available. Only the marked row includes explanatory text/appearance controls. Controls never persist directly. Unchanged appearance-only editing does not add an unselected family to the four-entry favorites list. Both text sizes use their own line height and disable inherited font padding, while the bottom trend icon stays separate from text centering.

Native editor follow-up, read-only investigation: the watch exposes Samsung and Google WFF editor activities, but their presence is not permission to programmatically assign another package's providers. Public WatchFacePushManager supports package lifecycle/activation, not slot-provider reassignment. AndroidX ComplicationHelperActivity requires caller and watchface package to match and an editing session on Android R+. Do not introduce private/system-permission APIs or fabricated successful assignment.
