# SugarWear Live Edit & Visual Tuning Guide

## Enabling Live Edit in Android Studio

1. Open Android Studio (Ladybug / Iguana or later).
2. Ensure **Live Edit** is enabled in `Settings -> Tools -> Build, Execution, Deployment -> Live Edit`.
3. Select **Push changes automatically** or **Push changes on save**.
4. Run the **SugarWear Playground** debug build on an emulator or Wear OS watch (`installSugarWearDebug`).

## How Live Edit Operates

- **Compose Composable Edits**: When modifying function bodies in Compose files (e.g. `CgmAlarmActivity.kt`'s `CgmAlarmScreenContent` or `PlaygroundEditor.kt`), Android Studio automatically hot-swaps bytecode into the running debug process.
- **Visual Overrides via Playground Editor**: While the Playground is running on device/emulator, tap **Editor** in the top bar to adjust Sliders, Color Swatches, or Scale Factors in real time. Changes immediately update the Compose UI state.
- **Exporting Design Overrides**: Open the Editor, configure your desired visual parameters, and copy the exported JSON or Kotlin code snippet to permanently update `app-sugarwear/src/main/kotlin/app/sugarwear/ui/visual/*VisualStyle.kt`.
