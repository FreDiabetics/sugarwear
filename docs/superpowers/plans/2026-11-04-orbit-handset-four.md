# Orbit handset four

Add the five supplied SVG layers as the fourth `apexHands` choice (stable id 3), preserving all existing option IDs and defaults. Only Orbit changes. Native Orbit canvas and supplied art are both 512×512; retain them without cropping or shrinking. Center pivot stays (256,256).

Layer order is hours, minutes, fixed hub circles, sweeping seconds, fixed top dot. Existing seconds color/off controls and ambient hiding of the seconds hand remain supported. The new set owns its supplied center decoration in ambient/off modes. Generate a standard 10:08:32 option preview and retain source SVGs as read-only provenance copies. Use the existing Kotlin/Cairo tooling and add a reproducible import command.

- [x] Test source geometry, layer order, stable fourth option, preview, opacity and ambient behavior.
- [x] Implement import/render/integration; inspect preview.
- [x] Run Kotlin/static gates, Orbit builds and WFF validation; install if Galaxy remains reachable.

Middle strips and seconds are imported as neutral white alpha masks. Both strips bind to the vivid third tone of the Akzente palette (	ertiary.2); outlines and supplied hub colors remain independent. Source SVG copies retain the supplied original artwork. Existing selection IDs/defaults are unchanged.

Evidence: full local architecture/static/unit/build/Lint gates passed (3m46s, 870 tasks); WFF v4 validation passed. Final maintenance tests/ktlint passed (34s). Orbit and its Sugarlicious Wear bundle were installed on SM-L705F. Installed Orbit SHA-256 matches the built release APK: 733eaa0aa96f3b68852daa8a90a7f1e6bef8496577de66dbdf9aacfdee6f87d5. No commit/PR/push was performed for this handset.
