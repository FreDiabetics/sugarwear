package app.sugarwear

/** Persist identifiers only: exception messages and protocol/authentication material are excluded. */
internal fun authenticationProcessingFailure(code: String, error: Throwable): CgmBleException {
    if (error is app.sugarlicious.sensor.auth.engine.AuthenticationMismatchException) {
        return CgmBleException(
            "G7-AUTH-MISMATCH",
            "G7-Kandidat passt nicht zum Authentifizierungsnachweis; Suche wird fortgesetzt",
            true,
            error,
        )
    }
    val stage =
        when (code) {
            "G7-AUTH-202" -> "EXTRA_DATA"
            "G7-AUTH-204" -> "AUTH_RESPONSE"
            else -> "AUTH_PROCESSING"
        }
    val identifier = Regex("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*")

    fun safeId(value: String): String = if (value.length <= 160 && identifier.matches(value)) value else "unknown"
    val frames =
        error.stackTrace
            .take(3)
            .joinToString(",") { frame ->
                "${safeId(frame.className)}#${safeId(frame.methodName)}:${frame.lineNumber.coerceAtLeast(0)}"
            }.ifBlank { "unavailable" }
    val summary = "Authentifizierungsdaten konnten nicht verarbeitet werden · stage=$stage · cause=${safeId(error.javaClass.name)} · frames=$frames"
    return CgmBleException(code, summary, false, error)
}
