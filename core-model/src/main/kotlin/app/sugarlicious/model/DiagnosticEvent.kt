package app.sugarlicious.model

import kotlinx.serialization.Serializable

@Serializable
enum class DiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
}

@Serializable
data class DiagnosticEvent(
    val id: String,
    val occurredAtEpochMs: Long,
    val origin: String,
    val module: String,
    val code: String,
    val severity: DiagnosticSeverity,
    val message: String,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class DiagnosticBatch(
    val events: List<DiagnosticEvent>,
    val createdAtEpochMs: Long,
)
