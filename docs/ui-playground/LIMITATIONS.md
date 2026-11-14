# SugarWear Playground Technical Limitations

## Recognized Platform & Tooling Limitations

1. **ProtoLayout Tile Live Edit**: ProtoLayout tiles (`CgmCollectorTileService`, `CgmGraphTileService`) use system-level ProtoLayout layout definitions rather than Jetpack Compose runtime nodes. Live Edit hot-swapping does not apply to ProtoLayout tile services; use Android Studio's native Tile `@Preview` or trigger tile updates via `DebugTileController.requestTileUpdate()`.
2. **Android System Notification Rendering**: The native Android system notification shade is rendered by the Android OS System UI process. The Playground provides `DebugNotificationPublisher` to post test notifications on a dedicated debug channel and a preview screen for notification composable content.
3. **Hardware / Emulator Testing**: Physical Wear OS watches require active Bluetooth pairing or Wi-Fi ADB debugging. On emulators, full-screen alarm overlays require display lockscreen permissions (`setShowWhenLocked`).
4. **Permanent Style Application**: Style edits made using the Playground on-device Sliders are saved to debug SharedPreferences. To apply changes permanently to the production app, copy the generated Kotlin code snippet from the Exporter into `src/main/kotlin/app/sugarwear/ui/visual/*VisualStyle.kt`.
