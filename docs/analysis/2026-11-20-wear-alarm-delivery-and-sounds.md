# Wear OS alarm delivery and user-supplied sound migration (2026-11-20)

## Verified constraints

- On 2026-11-18, commit `5615f6f23f` removed the previously present
  `Notification.Builder.setFullScreenIntent(...)` and `USE_FULL_SCREEN_INTENT`
  manifest permission. This is the precise code regression in the user's
  previously installed full-screen alert delivery. The original request is now
  **restored only for a newly triggered alarm**; refreshes/restoration keep the
  notification tappable without re-launching the activity.
- [Android's Wear OS notification documentation](https://developer.android.com/training/wearables/notifications)
  states full-screen intents are **not supported on Wear OS**. Restoring the
  historical API is therefore a **best-effort compatibility attempt**, not a
  guaranteed or officially supported background start on every watch. Never
  bypass OS launch restrictions or claim a reliable full-screen alarm on Wear.
- SugarWear's existing `CgmAlarmActivity` remains the full-bleed screen opened by
  the alarm's `PendingIntent`. The supported fallback is an independent
  high-importance `CATEGORY_ALARM` Wear notification with user action.
  **Background automatic presentation of the full-screen activity is not guaranteed.**
- Actual notifications additionally depend on Android `POST_NOTIFICATIONS` permission,
  app-wide notification enablement, channel importance, active G7 collector, enabled
  SugarWear alarms, per-alarm configuration, and suppression/snooze state.
  Settings show an actionable warning when permission/app-wide notification access is blocked.
- CGM resolver, sensor collection, alarm thresholds, acknowledgements and ownership
  remain independent from the Dexcom Share upload adapter.

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
  new-vs-restored full-screen intent behavior, unchanged source alarm priority and protocol behavior.
- Build: `test`, `assembleDebug`, static analysis/format, Android Lint,
  WFF validation per standard CI.
- Required on real Wear OS hardware: foreground/background alarm posted;
  `POST_NOTIFICATIONS` approved; notification enabled; tapping notification
  opens the matching existing full-bleed screen; correct tone for each alarm;
  snooze/ack, AOD/Doze, DND constraints, silence preferences, and repeat behavior.
  Device testing was not available for this change.
