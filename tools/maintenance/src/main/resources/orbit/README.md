# Orbit geometry model

These WFF XML files hold the measured, first-party geometry previously expressed
as fixed Python builder calls in generate_orbit.py. They are native declarative
inputs, not executable code or a cache of the final output. The model was
captured before the final gradient, tick, hand and flavor transformations.

Kotlin supplies current palette CSV data, captured defaults/provider bindings,
weather manifest canvas/asset mapping, flavors and the common transformations at
generation time. Palette options are deliberately absent from the model to
retain one canonical palette inventory. Outputs are compared against an
independent pre-migration Python reference and the original behavioral tests.

Do not load the generated watchface as its own input. Geometry changes belong in
this model, while reusable calculations/transformations belong in Kotlin.
