# Final UI follow-up before further collector work

## Scope and status

The next Bluetooth capture and further collector changes are deferred until the UI follow-up is complete. Existing sensor pairing and the daily Dexcom installation remain unchanged.

- Mobile isolated slot previews now center curved glyph ink on the native text radius rather than placing the baseline there. This corrects Orbit upper-left/lower-right bar gaps without changing actual watchface layout.
- Orbit's two isolated inner pills use the same radial geometry and backdrop dimensions. The full-face native geometry is retained.
- Steps artwork comes from Samsung Health's complication preview resource; battery geometry comes from the connected watch framework vector.
- Vigil uses the supplied 450×450 marker SVGs unchanged as sources; only the rendered minute color is dark gray. Its seconds hand defaults to red with a medium-gray alternative.
- Native full-frame hands share the opacity mask. The center-only action remains a separate 50×50 hit surface, avoiding the clipped seconds hand caused by its former small native bounds. No per-second app refresh is used.
- Native hand visibility hides both marker sets. Shadows use the same hand silhouettes, offset down and right. Preview time remains 22:10.

## Verification

Mobile build, Lint, Detekt and ktlint passed. All 23 targeted slot/model/native-rendering tests passed. The resulting Mobile APK was installed successfully on SM-S948B and both Orbit and ApeX slot panels were inspected on the device. The inspection includes complete outer text/icons, equal inner pill silhouettes and the Samsung resource replacements.

Vigil's full-length red seconds hand, shared opacity toggle and native hands-off marker visibility were verified on SM-L705F during the preceding device pass. The medium-gray alternate is configuration/build validated, not separately selected during the hardware pass. The updated Vigil preview is bundled with this Mobile build.

Combined repository verification passed: `verifyArchitecture test assembleDebug detekt ktlintCheck lint`, plus all five watchface release builds. The older provider-composite test was updated to render the reference hands rather than painting their new uniform opacity mask as artwork; the invisible center action is correctly excluded from its visible-slot list.

Remaining delivery work: commit, PR, CI, merge and main-only synchronization. These steps must not be described as complete before their results are available.

## Native selector warning placeholder

The white oval with an information symbol occupied the digital-clock slot. Preview data inherited the live clock's next-minute expiry; Samsung caches the selector image longer and then substitutes its system warning image. Preview-only small images now have unlimited validity while live requests retain their original freshness/time ranges. A regression test first failed on the former minute-limited preview and passed after the fix. Complications tests, Lint, Detekt, formatting and the SugarWear build passed. SugarWear was installed and the native selector was inspected again across the minute boundary: the cached clock remained visible with no white warning object.
