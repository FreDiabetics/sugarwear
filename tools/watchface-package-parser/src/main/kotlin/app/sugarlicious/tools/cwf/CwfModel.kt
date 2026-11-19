package app.sugarlicious.tools.cwf

import kotlinx.serialization.json.JsonObject

data class CwfMetadata(
    val name: String,
    val author: String,
    val createdAt: String?,
    val authorVersion: String?,
    val cwfVersion: String?,
    val comment: String?,
)

data class CwfElement(
    val key: String,
    val width: Int,
    val height: Int,
    val top: Int,
    val left: Int,
    val visibility: String,
    val textSize: Int?,
    val gravity: String?,
    val font: String?,
    val fontStyle: String?,
    val fontColor: String?,
    val rotation: Double,
    val raw: JsonObject,
) {
    val visible: Boolean get() = visibility.equals("visible", ignoreCase = true)
}

data class CwfDocument(
    val metadata: CwfMetadata,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val previewEntry: String,
    val resourceEntries: List<String>,
    val elements: Map<String, CwfElement>,
    val featureFlags: Set<String>,
    val warnings: List<String>,
)

class CwfValidationException(
    message: String,
) : IllegalArgumentException(message)
