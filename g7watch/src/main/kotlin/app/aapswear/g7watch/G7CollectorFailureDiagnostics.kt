package app.aapswear.g7watch

internal fun unexpectedFailureMetadata(phase: String, error: Throwable): Map<String, Any?> {
    val type = error.javaClass.name
    val fingerprint =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest("$phase:$type".toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
    return mapOf("failurePhase" to phase, "exceptionType" to type, "failureFingerprint" to fingerprint)
}
