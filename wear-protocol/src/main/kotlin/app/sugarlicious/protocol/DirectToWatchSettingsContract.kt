package app.sugarlicious.protocol

/** Signature-protected, explicit app-to-app settings handoff from the G7 collector to Vigil. */
object DirectToWatchSettingsContract {
    const val ACTION_APPLY = "app.aapswear.APPLY_DIRECT_TO_WATCH_SETTINGS"
    const val TARGET_PACKAGE = "app.sugarwear"
    const val TARGET_RECEIVER = "app.sugarlicious.complications.DirectToWatchSettingsReceiver"
    const val PREFERENCES = "direct_to_watch"
    const val EXTRA_VALUES = "values"
}
