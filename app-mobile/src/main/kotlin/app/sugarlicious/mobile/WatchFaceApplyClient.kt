package app.sugarlicious.mobile

import android.content.Context
import app.sugarlicious.protocol.WearProtocol
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

internal suspend fun requestWatchFaceApply(
    context: Context,
    index: Int,
): Int {
    val nodes =
        Wearable
            .getNodeClient(context)
            .connectedNodes
            .await()

    val payload =
        index
            .coerceAtLeast(0)
            .toString()
            .encodeToByteArray()

    nodes.forEach { node ->
        context.sendLoggedWearMessage(node.id, WearProtocol.WATCH_FACE_APPLY_PATH, payload, "Watchface aktivieren", mapOf("index" to index.coerceAtLeast(0)))
    }

    return nodes.size
}
