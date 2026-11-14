package app.sugarwear.debug.playground.demo

import app.sugarlicious.sensor.protocol.CgmCollectorError

/**
 * Creates test errors for notification previews and debug publisher testing.
 */
object DemoNotificationFactory {
    fun createTestError(
        code: String = "G7-SIGNAL-LOSS",
        message: String = "Kein Signal vom Sensor empfangen. Bitte Distanz prüfen.",
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CgmCollectorError {
        return CgmCollectorError(
            code = code,
            recoverable = true,
            occurredAtEpochMs = nowEpochMs,
            safeMessage = message,
        )
    }

    val sampleErrors: List<CgmCollectorError> = listOf(
        createTestError("G7-SIGNAL-LOSS", "Signalverlust seit 15 Minuten. Sensor in Reichweite bringen."),
        createTestError("G7-AUTH-FAIL", "Authentifizierung fehlgeschlagen. Sensorcode erneut prüfen."),
        createTestError("G7-BLE-DISCONNECT", "Bluetooth-Verbindung getrennt. Automatischer Reconnect aktiv."),
    )
}
