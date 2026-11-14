# Orbit daily TIR implementation plan

Goal: five daily historical TIR percentages, exclusively selectable under Arc.
Architecture: core-model calculation, bounded wear-storage archive, hidden
Complication provider, WFF fixed slot tinted by existing theme. No live resolver changes.
Spec: ../specs/2026-10-23-orbit-daily-tir-design.md

- [ ] Core tests missing days/duplicate-invalid-future/gaps/midnight; implement calculator.
- [ ] Persist canonical archive without replacing current state; test recreation.
- [ ] Internal provider and theme-neutral curved rendering, manifest hidden/default preview.
- [ ] Arc TIR selection and fixed slot; retain graph/other slot configuration.
- [ ] Focused Kotlin/render/manifest tests, Orbit contracts/schema, Wear/Orbit build/lint.
- [ ] Install explicit Ultra target and report any visual/historical-data limits honestly.

Implementation ledger: core model + separate bounded archive, internal hidden
provider, exact generated static preview and Arc option 4 complete. RED observed
for missing calculator and Arc choice. 140 core-model tests pass; storage recreation
2/2, native bitmap rendering 1/1, Orbit generator 25/25; WFF5 schema, Wear/Orbit
build/lint, core/complications/storage Detekt and formatting checks pass. Preview
render inspected at complications/build/reports/orbit-daily-tir.png. One Gradle
problem-report collision after a test-only nullable-file warning was resolved by
fixing that warning; subsequent preview/check build succeeded.
Wear + Orbit installed successfully on SM-L705F via mDNS 192.168.178.68:46231.
Orbit package confirms code16 / 0.1.15. Real-device selection of TIR not yet
visually confirmed. Historical days unavailable in prior 24h cache remain dash;
cache accumulates across restarts without increasing transport payloads.
Queued next request: Mobile settings scroll jumps to top; new ic_Sugarlicious.svg
supplied 66x66 single path, replace launcher/notification/in-app branding after
this installed feature, preserve other app identities.

Review focus: timezone/DST, source duplicates, missing days versus zero, partial
today, configuration/threshold changes, restart and Watch Direct updates.
