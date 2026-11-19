# SugarWear Compose & Tile Preview Guide

## Viewing Previews in Android Studio

Navigate to the `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/previews/` package:

1. **`HomePreviews.kt`**: Renders `@Preview` for the main overview card across small and large round Wear devices.
2. **`GraphPreviews.kt`**: Renders `@Preview` for the glucose graph canvas under stable, fluctuating, and missing value scenarios.
3. **`AlarmPreviews.kt`**: Renders `@Preview` for LOW, HIGH, and TECHNICAL_WARNING full-screen alarm overlays.
4. **`ComponentPreviews.kt`**: Renders `@Preview` for individual UI components like `CgmConnectionDotsView`.
5. **`NotificationContentPreviews.kt`**: Renders `@Preview` for notification content screens.
6. **`TilePreviews.kt`**: Renders `@Preview` for Wear OS ProtoLayout tiles.

## Interactive Mode & Animation Previews

In Android Studio's Preview pane, click the **Interactive Mode** icon on any preview card to interact with buttons, scroll containers, or test state transitions directly inside the IDE layout editor.
