# Unified Orbit-style progress displays

User request: visible separation and rounded ends like Orbit lower-left, throughout Sugarlicious; Basal additionally shows the outlined position indicator used by watchfaces.

- Finish and install the in-flight overview/complication changes first.
- Reuse shared renderer-neutral ring geometry for the gap. Clamp valid progress; unknown stays unknown and has no marker. Account for round caps and keep marker contours inside bounds.
- Apply to Mobile overview, therapy widgets, Wear overview and therapy tile; horizontal measured-range bars use the same separated-end treatment. Catalog progress previews follow the visual convention without inventing data.
- Keep native third-party watchface rendering under the watchface's control. Orbit already supplies the reference style; ApeX's existing gap option remains available.
- Add focused geometry tests covering missing, zero/full, intermediate cap spacing and finite normalization. Compile/check affected modules and inspect/install on available devices.

## Result
Implemented shared cap-aware segments in Mobile overview, therapy widgets, Wear overview and therapy tile. Basal marker uses an accent-filled circle with a black contour, inner diameter equal to stroke thickness, fully inset within the allocated ring. Tile uses a 3x RGB565 ring image with unchanged native text/icon overlays; resources are regenerated only on ordinary tile builds, not per frame. Horizontal TIR bars also separate fill and remainder. Settings illustration follows the gap style. ApeX existing Trennlücke now defaults true; saved settings remain untouched and third-party watchfaces retain rendering control.

Geometry tests, Mobile compilation, Wear therapy/Tile tests and three ApeX progress XML tests passed. Final affected builds, ktlint, Detekt and Lint passed (progress-style-final.log; 5m32s). Mobile installed successfully. The Wear progress and typography updates were subsequently installed on SM-L705F.

Independent final code review found no important issues. Mobile progress/scroll rendering was inspected on SM-S948B after installation. Final Mobile Basal is a bottom-open segment arc, fully accent-colored with only a position marker and2.5scaled-dp black marker outline; the arc stroke stays unchanged. Wear therapy Tile Basal likewise keeps the previously requested continuous open arc, with its original marker outline.
