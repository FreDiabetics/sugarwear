# SugarWear DND, persistent sensor search, and xDrip source removal

## Purpose

Make SugarWear alarms use the public Wear OS notification-policy mechanism, keep an explicitly started G7 sensor search alive independently of the visible UI, and remove xDrip as a Sugarlicious data source. The result must preserve the existing canonical CGM, alarm, collector, persistence, and recovery architecture rather than adding parallel implementations.

## Scope and boundaries

This change covers `:g7watch`, the shared source model and consumers, Mobile source configuration, persistence migrations, tests, build configuration, and documentation.

xDrip is removed as an ingestible or selectable data source. This includes its adapter module, broadcast receiver, package visibility entry, source enum member, UI choice, resolver/formatter branches, and source-specific tests. Existing persisted `XDRIP_PLUS` values must migrate safely to a non-xDrip supported choice without crashing deserialization.

The Dexcom G7 authentication implementation derived from the xDrip project is not an xDrip data-source integration. It is required by the direct G7 collector and remains in place with its legally required provenance and license. User-facing xDrip advice is removed where it describes xDrip as a supported Sugarlicious source; technically accurate conflict guidance may name external collectors only where necessary.

## Existing-path findings to verify with tests

### DND settings

The current button first constructs the undocumented action string `android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS` with a package URI. It only falls back when `resolveActivity` returns null. A Wear OS settings package can advertise that activity but reject or fail the launch, after which the click handler catches the exception and shows the generic toast. The public API documented by Android is `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` and the authoritative state is `NotificationManager.isNotificationPolicyAccessGranted`.

The current alarm channels are silent and the app separately plays assets with `USAGE_ALARM`. The notifications use `CATEGORY_ALARM`, while channel IDs encode sound, vibration, and policy state. The implementation must verify that this split path actually preserves notification visibility, deduplication, acknowledgement, repeat scheduling, and DND behavior. It must not claim that policy access overrides zero alarm volume, a muted device, a blocked channel, missing notification permission, or vendor restrictions.

### Sensor search

The UI removes only UI callbacks on pause, but initial pairing is currently represented by a persisted deadline and a long foreground-service scan. The investigation must trace every transition that turns an active pairing/search request into timeout or “Sensor nicht gefunden,” including service cancellation, coroutine cancellation, process reconciliation, boot recovery, scan callback termination, and the pairing deadline. UI lifecycle events must never be accepted as terminal search causes.

## Architecture

### DND permission navigation

Introduce one focused policy-access gateway used by the existing alarm settings screen:

1. Query the real policy-access state from `NotificationManager`.
2. Build an ordered list of public, resolvable settings intents:
   - `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`;
   - app notification settings as a diagnostic fallback;
   - general settings only as the final supported surface.
3. Attempt candidates in order and continue after either resolution failure or launch failure.
4. Return a typed outcome so the UI distinguishes opened, unsupported, and failed states.
5. Re-query policy access and channel state in `onResume`; never persist “button pressed” as authorization.

The normal UI states are “Erteilt,” “Nicht erteilt,” and “Auf diesem Gerät nicht verfügbar.” Supporting text states that authorization permits configured alarm channels to bypass DND where Wear OS allows it, without guaranteeing audible output under all system configurations.

### Alarm delivery

Keep the canonical chain:

`Canonical CGM -> G7 alarm engine -> alarm event -> G7CgmAlarmNotifier -> Wear OS`

All eight alarm types use the same notifier and channel policy. Channel creation is versioned and idempotent. A migration creates a new channel family only when immutable channel configuration must change; obsolete app-owned family members are deleted once during migration, not on every startup. The channel uses high importance, alarm category, public visibility, configured vibration, and alarm audio attributes where channel sound is used. If direct asset playback remains necessary for the product’s per-alarm sounds, it must be initiated only by the notifier’s common delivery path and use `USAGE_ALARM`; tests must prove real and test alarms share that path.

The settings health model reports policy access, notification permission, app notifications enabled, per-channel importance/blocking, DND bypass capability, alarm-volume state, ringer mode, sound setting, and vibration setting separately. Unsupported or unreadable states remain unknown rather than being reported as healthy.

The test-alarm action posts an explicitly labelled test event through the production notifier. It must not write active alarm state, acknowledgement state, repeat state, CGM history, or deduplication state.

### Persistent sensor-search ownership

The collector service owns a durable search command. Persistence records at minimum:

- request identity;
- sensor/session setup material;
- requested-at time;
- active/cancelled/completed status;
- current scan/backoff phase;
- next eligible scan time;
- last technical failure and retry count;
- explicit terminal reason, if any.

The UI can start a command, observe it, and explicitly cancel it. Reopening the app renders the existing command and never starts a second search implicitly. `onPause`, `onStop`, `onDestroy`, Compose disposal, navigation, display timeout, ambient mode, wrist-down, and watch lock do not mutate the command.

The foreground collector executes bounded filtered scan windows with bounded exponential backoff and jitter. End of one scan window transitions to backoff/retry, not “Sensor nicht gefunden.” Discovery continues through connect, authentication, handshake, and live CGM without requiring an Activity. Only explicit user cancellation, successful completion, invalid credentials/setup, unsupported Bluetooth state that requires user action, or another classified non-recoverable error may end the command.

For process death, the runtime reconciler treats persisted active search as work to resume, not as a live in-memory scan and not as failure. Boot/package replacement recovery restarts a still-valid command through the existing foreground-service and alarm/PendingIntent mechanisms. A filtered `PendingIntent` BLE scan may wake the process; periodic unfiltered polling is not introduced.

## Persistence and migrations

- Remove xDrip source preferences while accepting legacy stored names during migration.
- Map legacy Mobile `AUTOMATIC`/`XDRIP_PLUS` selections to `ANDROID_APS`; do not infer a new fallback.
- Map serialized `DataSourceId.XDRIP_PLUS` records to `OTHER` only during backward-compatible decoding or migration, preserving historical glucose samples rather than deleting measurements.
- Add a versioned durable search-command record. Migration from existing pairing fields preserves an active valid pairing request when enough setup material exists.
- Keep alarm-channel migration versioned and one-shot.

## Failure handling

- Settings navigation tries only public resolvable activities and records which candidate failed.
- Missing DND access never suppresses the alarm event; it changes only system delivery capability and UI health.
- Missing notification permission or blocked channels are shown as distinct actionable states.
- Recoverable BLE scan failures schedule retry/backoff and retain the search request.
- Non-recoverable search failures persist their exact reason; “Sensor nicht gefunden” is not produced by UI disappearance or by the end of a scan window.
- No aggressive restart loop, permanent wake lock, or unlimited high-power scan is introduced.

## Automated validation

Tests must cover:

- no xDrip module, receiver, package visibility, selectable source, resolver branch, or accidental fallback remains;
- legacy xDrip preferences and stored source values migrate without data loss or crashes;
- public DND settings intent selection, resolvability, launch fallback, unsupported result, actual access state, and `onResume` refresh;
- all alarm types use the same channel family and notifier path;
- channel importance, bypass request/capability, vibration, sound/audio attributes, category, permission/channel health, and one-shot migration;
- test alarm uses production delivery without changing real alarm or deduplication state;
- alarm delivery remains service/background driven after Activity closure and recovery;
- active search survives UI pause/stop/destroy, navigation, display/ambient-equivalent lifecycle loss, scan-window completion, process reconstruction, and boot recovery;
- reopening observes one existing command; background discovery continues to connection/authentication;
- explicit cancellation ends retries; classified terminal errors are distinct from recoverable misses;
- scan/backoff policy is bounded and resource-conscious.

Run focused tests first, then all relevant module tests and the repository gates: `test`, `assembleDebug`, relevant `assembleRelease`, `lint`, `detekt`, and `ktlintCheck`. New warnings or failures introduced by this work are fixed without weakening gates.

## Hardware validation

Automated and build results are labelled `CODE-VALIDIERT` and `BUILD-VALIDIERT`. DND delivery and locked/background BLE behavior remain `HARDWARE-TEST OFFEN` until tested on a physical watch.

The hardware matrix covers DND off, DND on without access, DND on with access, display off, AOD, app closed, watch restart, process recovery, alarm/channel/volume restrictions, search across display timeout, wrist-down, lock/PIN, background discovery, and reopening the existing search. Galaxy Watch Ultra and Pixel Watch are tested when available.

## Definition of done

- xDrip is not a Sugarlicious source and cannot be selected or used automatically.
- The existing DND button opens a supported system surface or reports a precise unsupported state.
- Real policy and channel status refresh on return.
- Every critical alarm type uses the verified common notification/audio path.
- Test alarms exercise that path without creating real alarm state.
- Alarm and collector recovery do not depend on a visible Activity.
- A user-started sensor search survives UI/display/lock lifecycle changes and recoverable scan windows.
- Process death and boot have deterministic search recovery.
- Only explicit cancellation or a classified terminal outcome ends the search.
- All repository gates are green with no new warnings.
- Hardware-only claims remain explicitly open until physical validation.
