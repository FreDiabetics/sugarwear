# Original native slot previews

User: retain exact watchface complication rendering, without hands, indices or neighboring decorations. Increase slot thumbnails to 72dp; enlarge fixed Watch hero; omit standalone fixed graph slot; shorten position labels. Stronger slow-scroll heading capture, retaining fast-fling bypass.

- Native-only WFF fixtures: prepare_slot_capture.py copies original ComplicationSlot subtrees unchanged. Retains only center-contours outside slots, removes non-editable graph/TIR image layers and irrelevant flavors. Uses separate temporary packages, never rewrites real watchface packages.
- Native captures on SM_L705F: ApeX favorite 251; Orbit favorite 252. Original preview WebPs imported as apex_slots_native.webp / orbit_slots_native.webp. Sample values are native preview values, not live readings.
- Mobile thumbnails crop these clean native images by existing WFF slot geometry. No independent text/icon/progress renderer remains.
- 72dp thumbnails replace former 36dp; compact hero grows from 104/154dp face/frame to 135/200dp. Heading snap capture grows from 48dp to 112dp; speeds above 600dp/s still bypass snapping.
- Tests: native fixture subtrees equal original WFF subtrees; slot filter/labels; geometry; slow/fast snap distinction. Native images visually checked before import.

Completed verification: targeted NativeWatchSlots, CircularComplicationPreview and SettingsSnap tests; Mobile assemble/lint/Detekt/ktlint; Wear assemble. Final build successful (native-slots-final-verified.log); Mobile lint reports no issues. Both APKs installed successfully. Temporary capture packages and the one-off debug receiver removed; original Orbit restored through the native favorites picker.

Device evidence: native-slot-ui.png and native-slot-selected.png in local Temp show 72dp original previews, correct two-line date/age-delta, provider icons and blue native slot highlight. After a slow 750px drag, SugarWear heading settles exactly at [49,2216][1391,2289], replacing Sugarlicious heading at the same anchor; fixed slot labels retain their original bounds. No settings or real provider bindings changed. Captured native images use sample preview values, not live values.

## Outer previews and transfer diagnostics follow-up

- Outer thumbnail masks gain radial breathing room and rounded ends. A separate ownership mask prevents neighboring slots from leaking into the rounded ends; native watchface hit bounds remain unchanged.
- Names and ordering are generated from the original WFF slot declarations and resolved string resources, rather than duplicated Mobile labels.
- Button content contrast follows accent luminance. Connected status remains on Overview and is omitted from the Watch menu.
- Data management exposes a bounded local Mobile-to-Wear transfer log. API acceptance is explicitly distinguished from Wear receipt. A bounded asynchronous writer avoids delaying time-critical sends; queue losses are reported.
- The exact connected-status watch path was exported as Desktop/Sugarlicious/App Icon/ic_watch_status.svg.
- Targeted tests: 14 passing (native geometry/metadata, contrast, transfer semantics/queue/cancellation, diagnostic route). Final build and device evidence recorded after verification below.
- Final verification: transfer-pill-final.log reports BUILD SUCCESSFUL; Mobile assemble, lint, Detekt and ktlint pass. Mobile installed on SM_S948B. pill-installed.png confirms complete outer pills, black button text on the light system accent and no Watch-menu connection pill. Datenverwaltung opens the log; pill-log.png shows 26 real events including DataClient acceptance and MessageClient attempts with timestamps, sizes and safe summaries. Temporary capture packages are absent from the watch.

## Final preview refinements

- Fit outer black pill radii and rounded ends to the visible native pixels with uniform padding; preserve the separate ownership mask. Circular previews receive matching breathing room.
- Glucose color preview reuses the Overview glucose/trend composable, configured sizes and selected trend style. Remove the unrelated placeholder rectangle.
- Widget palette editing keeps Hell/Dunkel labels and radio choices without menu illustrations; the actual theme selector and device-dependent option are unchanged.
- fitted-preview-build.log and fitted-preview-check.log both report BUILD SUCCESSFUL; assemble, lint, Detekt and ktlint pass. Installed successfully on SM_S948B. Final visual check paused because the user is entering a WhatsApp message; no further screen interaction until available.
- Resumed device verification: final-color-preview.png confirms the canonical glucose/trend preview; final-widget-palette.png confirms image-free palette radio selection. Widget settings exited with Cancel. Outer thumbnail verification caught a 480px capture versus 512-unit geometry mismatch; ink measurement now converts image pixels to WFF coordinates before fitting the backdrop.
- A second on-device check identified the upper-left fit admitting lower-left pixels because Kotlin's negative remainder differs from positive modulo. Normalize the angle before sector membership checks; a focused quadrant regression test now passes with the six existing slot tests. Temporary geometry logging was removed.
- Final pass: fitted-preview-angle-final.log reports BUILD SUCCESSFUL (7 slot tests, assemble, lint, Detekt, ktlint). Reinstalled successfully. final-fitted-slots-verified.png confirms all four complete outer complications with fitted curved black backdrops and no empty lower-left tail; circular thumbnails retain equal breathing room. Selected slot highlighting remains aligned to the native watchface target. Color and widget palette checks above are complete; no setting values or provider assignments changed.
