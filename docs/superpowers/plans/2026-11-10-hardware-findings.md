# Hardware findings implementation plan

Execute inline, sequentially, with regression tests before production changes. No commits, PRs, pushes, or hardware scenarios while the user is away. Preserve the running collector and all on-device data.

## Evidence
- Explicit Pause/Beenden cancels the foreground collector and pending collector alarms; attempt counter remains stable across a receive slot. Reopening stays paused; explicit resume retains identity/history and receives fresh LIVE samples.
- Backfill misses real cadence drift: anchor 390474, LIVE 391069 (595 seconds), guard requires 600. Earlier range 388969 repeatedly excluded a missing boundary sample. LIVE after resume initially has no valid adjacent predecessor; do not fabricate delta.
- Bundled VERY_HIGH tone audible with normal policy and priority DND; inaudible under INTERRUPTION_FILTER_NONE despite a playback-start log and alarm volume 15/15. Original zen mode 0 restored.
- User reports the policy-exemption settings action resolves to an unsupported Watch screen. Capability must be distinguished from a denied permission.

## Tasks
- [x] Add failing cadence-drift and boundary tests; request only missing history within the existing 24h bound, never LIVE, and retain packet encoding/crypto/BLE timing. Verify database/delta integration.
- [x] Add failing tests for unsupported permission/settings capability, truthful total-silence warnings, and alarm/test notification context. Use supported public settings only; never silently change global DND or reroute alarms to media.
- [x] Apply the same capability/status to permissions, onboarding, and alarm settings. Unavailable actions are not presented as grantable permissions. Preserve One UI Watch components.
- [x] Run targeted regressions, then full pinned-source tests/build/lint/Detekt/ktlint/coverage. Record hardware-retest requirements separately. Do not claim total-silence override or measured fixes without actual device proof.

## Verification and remaining scope
- Initial eight targeted regressions: six failed against the previous behavior. Corrected cases and added database integration now pass. Existing wire-opcode/little-endian packet vector remains unchanged.
- Full explicit pinned-source command passed: `gradlew -PsharedSourceRoot=shared/sugarlicious detekt ktlintCheck test assembleDebug lint koverHtmlReport koverXmlReport` (448 tasks, no compiler warnings). Independent read-only review: no important findings.
- No credentials, history schema, resolver identity, encryption, or BLE connection timing changed. No device update, global-DND mutation, commits, PRs or pushes performed in this implementation block.
- Public API does not promise audible custom playback under total silence. This remains a platform restriction, not an implemented override; the fix exposes the restriction in setup/settings and actual/test alarm text rather than claiming success from MediaPlayer.start().
- Hardware retest after installing the update: drifting gap backfill including the earliest missing sample, deduplication/history retention, immediate delta repair, real/unsupported settings routing, visible blocked-audio warnings. Full restart, controlled unlink/relink, additional alarm types and range-loss scenarios remain untested in this session.

Platform contract: https://developer.android.com/reference/android/app/NotificationManager and https://developer.android.com/reference/android/app/NotificationChannel#setBypassDnd(boolean).
