# SugarWear Playground File Mapping

This document lists all newly created files and modified existing files for the SugarWear Universal UI Playground.

## Newly Created Files

### Production UI Visual Style Contracts
- `app-sugarwear/src/main/kotlin/app/sugarwear/ui/visual/GraphVisualStyle.kt`: Visual parameters for graph rendering.
- `app-sugarwear/src/main/kotlin/app/sugarwear/ui/visual/HomeVisualStyle.kt`: Visual parameters for main overview screen.
- `app-sugarwear/src/main/kotlin/app/sugarwear/ui/visual/AlarmVisualStyle.kt`: Visual parameters for alarm screens.
- `app-sugarwear/src/main/kotlin/app/sugarwear/ui/visual/CommonVisualStyle.kt`: Common design tokens.
- `app-sugarwear/src/main/kotlin/app/sugarwear/ui/visual/TileVisualStyle.kt`: Visual parameters for ProtoLayout tiles.
- `app-sugarwear/src/main/kotlin/app/sugarwear/ui/visual/VisualStyleDefaults.kt`: Production default style instances.
- `app-sugarwear/src/main/kotlin/app/sugarwear/ui/visual/VisualStyleValidation.kt`: Range & dimension validation logic.

### Debug Playground Infrastructure
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/PlaygroundActivity.kt`: Debug launcher Activity.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/PlaygroundScreen.kt`: Main Wear OS UI container.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/PlaygroundNavigation.kt`: Navigation routes and category selector.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/PlaygroundCatalog.kt`: Screen inventory registry.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/PlaygroundEntry.kt`: Type-safe catalog entry model.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/PlaygroundViewModel.kt`: State flow & action handler.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/PlaygroundUiState.kt`: Immutable UI state.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/PlaygroundActions.kt`: Sealed action hierarchy.

### Editor Subsystem
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/editor/PlaygroundEditor.kt`: Main editor layout.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/editor/PlaygroundControls.kt`: Reusable Wear controls (Slider, Switch, Color swatch).
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/editor/PlaygroundColorEditor.kt`: Color swatch editor.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/editor/PlaygroundTypographyEditor.kt`: Scale & typography editor.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/editor/PlaygroundLayoutEditor.kt`: Margins, paddings, corner radius editor.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/editor/PlaygroundAnimationEditor.kt`: Animation toggles.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/editor/PlaygroundStyleOverrides.kt`: Style overrides container.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/editor/PlaygroundStyleValidator.kt`: Validation wrapper.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/editor/PlaygroundStyleExporter.kt`: JSON and Kotlin snippet exporter.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/editor/PlaygroundStylePersistence.kt`: Debug SharedPreferences persistence.

### Demo & Data Subsystems
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/demo/DemoScenario.kt`: Scenario enum.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/demo/DemoGlucoseFactory.kt`: Deterministic glucose generator.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/demo/DemoScenarioCatalog.kt`: Scenario catalog helper.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/demo/DemoAlarmFactory.kt`: Isolated test alarm state creator.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/demo/DemoHomeStateFactory.kt`: Isolated overview state creator.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/demo/DemoNotificationFactory.kt`: Isolated test error notification creator.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/demo/DemoTileFactory.kt`: Isolated test tile readings creator.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/demo/DemoPlaybackController.kt`: Coroutine playback/stepping engine.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/data/PlaygroundDataSource.kt`: Data source interface.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/data/DemoPlaygroundDataSource.kt`: Demo data source implementation.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/data/LivePlaygroundDataSource.kt`: Passive live data source implementation.
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/data/PlaygroundDataMapper.kt`: Graph input mapper.

### Screens, Previews & Testing
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/screens/HomePlaygroundScreen.kt`
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/screens/GraphPlaygroundScreen.kt`
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/screens/AlarmPlaygroundScreen.kt`
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/screens/NotificationPlaygroundScreen.kt`
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/screens/TilePlaygroundScreen.kt`
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/screens/ComponentPlaygroundScreen.kt`
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/previews/HomePreviews.kt`
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/previews/GraphPreviews.kt`
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/previews/AlarmPreviews.kt`
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/previews/ComponentPreviews.kt`
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/previews/NotificationContentPreviews.kt`
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/previews/TilePreviews.kt`
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/testing/DebugNotificationPublisher.kt`
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/testing/DebugTileController.kt`
- `app-sugarwear/src/debug/kotlin/app/sugarwear/debug/playground/testing/PlaygroundSafetyGuard.kt`

## Modified Existing Files

- `app-sugarwear/src/main/kotlin/app/sugarwear/CgmAlarmActivity.kt`: Extracted `CgmAlarmScreenContent` composable function for preview and playground reuse.
- `app-sugarwear/src/debug/AndroidManifest.xml`: Registered `PlaygroundActivity` with debug launcher category and taskAffinity.
- `app-sugarwear/build.gradle.kts`: Added `ui-tooling-preview` and `debugImplementation("ui-tooling")`.
