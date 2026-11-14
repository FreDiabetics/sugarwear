# SugarWear alarm access and first-run setup — 2026-10-29

## Verified path and causes

`G7CgmAlarmCoordinator` evaluates validated readings with the existing alarm engine. `G7CgmAlarmNotifier` uses a separate high-importance notification channel per alarm with the bundled SugarWear asset and `USAGE_ALARM`; repeat delivery uses the existing allow-while-idle scheduler. Thresholds, collector protocol and clinical evaluation are unchanged.

The connected SM-L705F was already on the device-idle exemption list, but lacked notification-policy access. Its policy-access settings action resolves to no Activity; battery exemption actions resolve to Google's `FakeSettingsActivity`. Opening generic notification or main settings cannot grant policy access. The targeted policy opener now reports failure instead of taking the user to unrelated settings. The Galaxy's policy access was granted through its authorized debugging connection. This is a device-specific grant, not something an app can self-grant on arbitrary watches.

The Galaxy audio dump aliases `STREAM_ALARM` to `STREAM_NOTIFICATION`; the app follows this device-owned mapping rather than using music volume. Zero system alarm volume remains silent by default.

## Changes

- Optional own alarm volume, off by default. Uses the alarm stream only, snapshots its previous level and restores after 30 seconds. Recovery is persisted and backed by a receiver/alarm plus an in-process callback. Overlap extends the window without losing the original level. Manual changes win. Turning the option off restores immediately. Recovery also runs after boot and on opening alarm settings. No global DND-mode modification.
- Display actual DND grant, active filter, system alarm volume and device-idle exemption.
- First-run guide for Bluetooth, notifications, device-idle exemption, exact alarms, policy access, each alarm channel, and volume review. Uses real OS grants; skipping/denial never claims permission was granted. State survives recreation. No sensor pairing is initiated. Existing sensor installations are not sent back through onboarding. Guide can be reopened under Permissions.
- Existing bundled per-type alarm tones, source/quality checks and acknowledge/repeat behavior remain in place.

## Platform limits

`NotificationChannel.setBypassDnd` applies to priority DND when the policy allows priority channels. It is not an override of total silence, blocked app notifications/channels, volume zero, force-stop or powered-off hardware. Battery exemptions are partial; ordinary deferred jobs do not become realtime. Unavailable system screens cannot be manufactured by the app.

Sources:
- https://developer.android.com/reference/android/app/NotificationChannel#setBypassDnd(boolean)
- https://developer.android.com/reference/android/media/AudioManager#setStreamVolume(int,%20int,%20int)
- https://developer.android.com/training/monitoring-device-state/doze-standby

## Verification

Focused existing alarm/system-access tests plus new volume overlap/restoration/manual-change tests and first-run denial test. Build, lint, Detekt and format checks are required before installation. Device grant/channel/test-notification checks recorded after installation; acoustic audibility requires the wearer to confirm and is not inferred from a successful notification post.

### Galaxy device result

Installed on SM-L705F. All eight current v5 `d1` channels report `mBypassDnd=true`, high importance, their matching `content://app.aapswear.g7watch.alarm-sounds/<type>` URI and alarm audio attributes. Device-idle whitelist still contains the package. The isolated very-high **test** notification (7300) was recorded as `not_intercepted ... new:priorityApp (allowed)` while priority DND was active; the previous OFF state was restored afterward. WearServices separately suppressed alerting because the device was off-body (`Not alerting: Device is off-body`). Thus audible output is explicitly NOT confirmed by this off-body test. A wearer-side test is pending; no clinical alarm state was synthesized or acknowledged.

28 focused tests passed (17 existing alarm tests, 5 policy access, 5 volume restoration, 1 first-run denial). No BLE/sensor session or threshold was changed by this task.

Final cleanup build passed: assembleDebug, Detekt, ktlintCheck and lintDebug (0 lint findings). The cleaned APK was reinstalled successfully on the Galaxy. Pixel installation and the wearer-side audible test remain open.

## Superseding sound-path result

The user confirmed that Wear's own notification UI still substituted a system chime even after an explicit sound URI was added. That attempted fix was removed. `G7AlarmAudioService` now plays the exact bundled asset directly using `USAGE_ALARM`, transient audio focus, a bounded media-playback FGS and MediaPlayer wake handling. Completion/error/timeout releases playback and restores optional volume. Higher-priority real alarms cannot be preempted by lower-priority or test sounds. Notification and direct vibration are separated.

Galaxy logs proved a second system chime from `WNotiFeedback` despite a silent HIGH-importance notice. Visual-only v7 alarm channels therefore use LOW importance and no sound; the direct player is the sole sound source. The user subsequently confirmed **only SugarWear tone**, no extra system tone. This supersedes prior high-importance/channel-only sound descriptions. No claim that total-silence policies can be overridden.

First-run setup now has one combined alarm-channel step, and Permissions has one combined alarm-channel card. Buttons are shared flat action surfaces without native shadows or insets. One UI Watch styling is a mandatory user requirement for all future Wear menus/dialogs.

Final button cleanup: flat contrast-separated rounded action surfaces, zero native elevation/state animation, no outline; 6dp vertical separation for stacked buttons. Build/static/format checks passed and APK installed on Galaxy at 192.168.178.68:35471.

Lock-visibility code audit: own complication sources do not request DO_NOT_SHOW_WHEN_DEVICE_LOCKED and own WFF definitions do not contain lock-state hide conditions. AndroidX defaults to ALWAYS_DISPLAY. Private-notification display is enabled on the Galaxy. This is code/config evidence, not a physical all-watchface locked-device render test; external Samsung/Google providers retain their own policies and system PIN UI cannot be replaced by the app.


## Pre-merge review: effective own-audio policy

A silent notification-channel bypass is not a grant for the independent MediaPlayer alarm stream. Alarm settings, setup and permissions now report the effective own-audio result from currentInterruptionFilter and consolidatedNotificationPolicy. ALL/ALARMS permit alarm usage; NONE blocks it; PRIORITY follows PRIORITY_CATEGORY_ALARMS. Unreadable/unknown policy is reported as unknown. Setup no longer treats a policy-access grant/channel exception as sufficient own-audio readiness. No hidden audio flags, global DND changes or unsupported bypass claims are introduced.

Primary references: [NotificationManager interruption filters and consolidated policy](https://developer.android.com/reference/android/app/NotificationManager), [AudioAttributes USAGE_ALARM](https://developer.android.com/reference/android/media/AudioAttributes), [Policy alarm category](https://developer.android.com/reference/android/app/NotificationManager.Policy). The earlier successful on-device own-tone test still establishes the no-extra-system-chime path; arbitrary DND configurations have not all been hardware-auditioned and are not claimed as such.
