# Wear OS alarm delivery and user-supplied sound migration (2026-11-20)

## Verified constraints and current implementation

- The historical `Notification.Builder.setFullScreenIntent(...)` call and
  `USE_FULL_SCREEN_INTENT` permission are **not** part of the current Wear OS
  alarm implementation. The Wear OS documentation states that full-screen intents
  are unsupported, so an old experimental reintroduction was intentionally
  superseded by SugarWear's later, supported notification implementation.
- In the background, alarm episodes are delivered through a high-importance
  `CATEGORY_ALARM` notification and a Wear OS `OngoingActivity` entry,
  with a tap target that opens the existing black full-bleed `CgmAlarmActivity`.
  When a SugarWear screen is already in the foreground, the alarm presenter can
  bring up that activity for a newly triggered alarm. No foreground transition
  is guaranteed when the app is not visible.
- Delivery is conditional on `POST_NOTIFICATIONS`, the user enabling
  notifications, channel importance, current CGM validity, alarm configuration
  and snooze/ack state. The implementation reports blocked or downgraded
  channels without overwriting the user's system choices.
- The CGM resolver, direct G7 BLE collector and alarm ownership remain independent
  of the optional Dexcom Share publisher.
- Current background notification rules are documented in
  `docs/wear-alarm-surface-policy-2026-11-20.md`. The later delivery recovery
  changes are in PR #22 (`docs/wear-background-alarm-delivery-2026-11-21.md`).

## Supplied original medium sounds

All uploads were inspected using ffprobe; each is an audio-only, stereo 48 kHz AAC
MP4. The files remain **byte-for-byte original** in the separately provided
`SugarWear_Alarmtoene_10_MP4.zip`, with this repo-relative destination:

`app-sugarwear/src/main/assets/alarms/`

| SugarWear alarm | Preferred audio filename |
| --- | --- |
| URGENT_LOW | `alerts_sounds_medium_urgent_low.mp4` |
| URGENT_LOW_SOON | `alerts_sounds_medium_urgent_low_soon.mp4` |
| LOW | `alerts_sounds_medium_low.mp4` |
| HIGH | `alerts_sounds_medium_high.mp4` |
| RAPID_RISE | `alerts_sounds_medium_rising_rate.mp4` |
| RAPID_FALL | `alerts_sounds_medium_falling_rate.mp4` |
| SIGNAL_LOSS | `alerts_sounds_medium_signal_loss.mp4` |
| TEMPORARY_SENSOR_PROBLEM | `alerts_sounds_medium_bsi.mp4` |
| TECHNICAL_WARNING | `alerts_sounds_medium_technical_alerts.mp4` |

The tenth `alerts_sounds_medium_system_alert.mp4` is preserved in the package
but **not automatically triggered**: the canonical alarm model has exactly nine
types, and inventing a tenth source/notification would violate alarm ownership
and duplicate-protection. The supplied system-alert file and signal-loss file
currently have identical SHA-256 hashes. A separate validated system-event
owner is needed before using this tenth sound.

The repository's text-only API connection could not upload binary sound files
to GitHub. Consequently, the feature branch provides the **preferred mapping and
a verified legacy-asset fallback**, not the new MP4 data. To install every new
sound, unzip the package at the root of the exact `FreDiabetics/sugarwear`
checkout **before building**. Verify the ten assets are inside the APK and
that each audio test plays the expected tone. Never claim that merely pulling
the branch installs those binary attachments.

## Quality gates

- Unit: nine distinct preferred mappings, nine working fallback descriptors,
  new-vs-restored notification behavior without unsupported full-screen intent,
  unchanged source alarm priority and protocol behavior.
- Build: `test`, `assembleDebug`, static analysis/format, Android Lint,
  WFF validation per standard CI.
- Required on real Wear OS hardware: foreground/background alarm posted;
  `POST_NOTIFICATIONS` approved; notification enabled; tapping notification
  opens the matching existing full-bleed screen; correct tone for each alarm;
  snooze/ack, AOD/Doze, DND constraints, silence preferences, and repeat behavior.
  Device testing was not available for this change.
