# Pillow maintenance raster algorithms

Source: https://github.com/python-pillow/Pillow/tree/11.3.0/src/libImaging

Revision: Pillow release **11.3.0**. Relevant files: `Resample.c`, `Geometry.c`,
`Convert.c`, `Draw.c`, and `AlphaComposite.c`.

Changes: translated integer RGBA compositing/conversion, Lanczos coefficients,
bicubic sampling and capsule drawing into Kotlin/JVM. Native image-container
storage and input/output use BufferedImage/ImageIO. Grayscale sample handling
preserves the original Pillow RGBA conversion without Java color-space transfer.
All 53 hand-output decoded RGBA hashes are checked against original Pillow output.

License: MIT-CMU. The complete upstream copyright, permission and warranty notice
is retained at the top of `PillowRaster.kt` and must remain with derived copies.
These algorithms are desktop maintenance code and are not shipped in Android APKs.

The six fixed contact-sheet label alpha masks were rendered using Pillow's
default Aileron font. Their source/provenance and CC0 attribution are recorded in
`tools/maintenance/src/main/resources/assets/brand-labels/README.txt`.
