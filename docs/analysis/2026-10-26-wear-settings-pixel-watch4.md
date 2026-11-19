# Pixel Watch setup and Wear settings

## Verified causes

The Pixel Watch 4 runs API 37. Inspection of its installed Bluetooth module showed `BONDING_APIS_REQUIRE_PRIVILEGED_PERMISSION` (compatibility change 401333807) enabled for SugarWear. The old reflective `BluetoothDevice.removeBond` path requires a privileged system permission on this device. Adding ordinary runtime permissions cannot fix that failure.

SugarWear now uses the public API-36 `CompanionDeviceManager.removeBond(associationId)` path. An existing bonded sensor can first receive an exact-address, app-owned companion association through Android's consent screen. This does not create a sensor bond or start collection. Only actual `BOND_NONE`, followed by selective association cleanup, permits local sensor/auth cleanup. Failed removal preserves the association and history. Older platforms retain their existing compatible removal path.

Reference: https://developer.android.com/reference/android/companion/CompanionDeviceManager#removeBond(int)

The Galaxy's exclusion store also contained the known current sensor after earlier detachment. A fresh, explicitly initiated, addressless setup may now authenticate a previously retired candidate. Only successful authentication and persisted identity reactivate that exact address. Ordinary reconnects and wrong-code rejection do not bypass exclusions. Failed detachment no longer commits new exclusions.

## Sensor controls

- Overview and status expose one pair/unpair action and pause/resume when an association exists.
- First launch and background state refresh never open pairing automatically. Explicit user intent opens the code screen; only its Connect action starts collection.
- Unpairing pauses active work first, removes the real bond and app association, and retains reading history and the current screen.
- Native Android consent can be required once for a legacy bond. Cancellation is a failure to authorize removal, never a successful detach.
- No sensor pairing or scan is initiated by the installation or review workflow.

## Permissions and alarms

The manifest already includes Bluetooth scan/connect, notifications, connected-device foreground service, boot recovery, exact alarms, wake lock, battery exception request and notification-policy access. No location, privileged Bluetooth or unrelated health permission is added.

A dedicated permissions page reads actual grants and channel settings, requests missing runtime grants, and opens public system destinations with fallbacks. It does not infer success from launching a screen. Notification policy access and a channel's actual DND exception are reported separately. Current alarm channels are initialized without posting an alarm, changing alarm settings or pruning older channels. User-controlled system restrictions remain authoritative.

The Pixel build routes both public battery-exemption actions to a system `FakeSettingsActivity`. The launcher now skips only that positively identified system stub, falling back to the real app-details page. An unresolved query still gets a launch attempt, avoiding package-visibility false negatives. Narrow manifest intent queries make the known stub detectable without broad package visibility. The public DND access screen is absent on this build; notification/channel settings remain the supported route, and denied access is never shown as granted.

## Presentation

Both Wear applications reuse shared, system-scaled typography, round-safe headers, gesture-safe controls and numeric entry. Settings are grouped by the affected display element. Vigil renders its graph in canonical watchface units rather than multiplying by the target device's density again. The narrower viewport fits the circular Pixel display, and disconnected status does not cover the graph or scale.

## Validation boundary

Unit/build/static results and installation evidence are recorded after the final integrated checks. Real sensor handover and the Android companion consent flow require the user's explicit sensor action; no successful live pairing is claimed from unit tests. Device captures and sensor identifiers remain in ignored build reports, outside version control.

Integrated local checks: 45 shared-UI tests, 2 Wear presentation tests, 94 SugarWear lifecycle/access/settings tests and 36 Vigil/complication tests passed. Both Wear app debug builds and the Vigil release build passed. Lint, Detekt and formatting passed for the four affected modules. The Pixel and Galaxy accepted the SugarWear update. Post-install Pixel state remained disabled with no active attempt; the history database hash was unchanged. After reconnecting, the Galaxy also accepted Sugarlicious Wear. Final icon packages were installed on the main smartphone and both watches. Both watches remained disabled with no active attempt and unchanged history hashes afterward. Native consent, final user-initiated sensor pairing and the final on-screen comparison are not claimed as hardware-verified by these checks.

## Brand refresh

The supplied 66-unit SugarWear and Sugarlicious SVGs replace both full-colour masters and derived runtime assets. Adaptive launchers use the exact source gradient stops and white symbol; in-app, monochrome and notification variants use only the final central white path. Existing runtime insets and notification/Now Bar sizing are preserved. Obsolete Wear raster backgrounds are removed. Store assets and the README logo are refreshed. Four focused asset checks cover paths, gradients, symbol isolation and source rendering, and run in CI.
