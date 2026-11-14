# Native watchface presets and menu follow-up

Goal: freeze the user's selected ApeX/Orbit complication layouts as defaults and native preview assets, then add six native user flavors to each face. Preserve the established fixed preview-hand posture.

Captured on 2026-10-24 through the watch's diagnostic favorite repository, without changing selections:

- ApeX favorite 246 (explicitly selected by user): upper graph; outer TIR, watch battery, COB ranged, IOB ranged; middle delta, date, glucose/trend ranged. Favorite 250 was not used.
- Orbit favorite 240: IOB ranged, Samsung Health steps ranged, COB ranged, glucose/trend ranged, next-calendar-event short text, glucose delta short text; fixed graph and daily-TIR providers. Arc currently Graph. Native preview is a fixed-time illustrative rendering, not a capture of live glucose or current clock hands.

Tasks:

- [x] Read and validate both selected instances; retrieve original native preview bitmaps.
- [x] Regression-test captured provider defaults, including regeneration of Orbit; change defaults without replacing existing user selections.
- [x] Reuse imported native assets on Watch and Mobile; validate both active WFF files.
- [x] Split menu into Sugarlicious (ApeX/Orbit) and SugarWear (Vigil) sections; compact send button, remove three information rows, separate preview cards.
- [x] Update light/dark dashboard illustration: no brand text, progress gauges inside glucose card, IOB/COB graph below.
- [x] Append six native flavors per face, retaining prior presets, with Graph/TIR, varied colors, markers and gradients. Verify each selected option/provider type exists and official WFF validation passes.
- [x] Build relevant apps, tests/Lint/static checks and install on explicitly identified phone/watch.

Full arbitrary-provider slot reassignment from a third-party companion remains a separate platform constraint: the existing four-entry Data Layer preset is favorites storage, not a native slot-assignment API. Do not claim it changes Samsung/Google slot providers. A live all-slot editor remains pending that constraint; the native snapshots/defaults work is independently valid.

Presets: Graph Orange, TIR Aqua, Graph Rot, TIR Violett, Graph Limette, TIR Eis. Existing six Orbit presets remain unchanged. Native flavor selection includes provider policies as well as appearance; it does not rewrite existing favorites on installation.

Validation: 34 Python watchface tests pass; official WFF validator accepts ApeX v2 and Orbit v4. Reviewer confirmed eight real Scene slots per face (flavor policies are not new editable slots). Retired the old hand-drawn PNG preview helper in favor of importing native fixed-time WebP previews.

Final follow-up: fixed constrained Ranged Value previews (the four-entry strip could squeeze width independently of height). Preview layout now preserves a square, and arc geometry explicitly uses centered square bounds. Regression covers narrow, short and square constraints. Verified the corrected strip and larger catalog previews directly on Galaxy S26 Ultra after installation; both show circular rings.

Evidence: full tests/static/build/Lint succeeded in native-presets-gradle.log (767 tasks); final focused geometry/native-layout/snap tests plus static/build/Lint succeeded in native-presets-circular-final.log (441 tasks), both under the system temporary directory. Mobile and Wear plus both WFF APKs installed successfully on SM-S948B / SM-L705F. Installed APK hashes match the verified build outputs.
