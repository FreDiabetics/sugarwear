package app.sugarlicious.mobile

import android.content.Context
import app.sugarlicious.model.DiagnosticEvent
import app.sugarlicious.model.DiagnosticSeverity
import app.sugarlicious.storage.DiagnosticEventStore
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

internal const val WEAR_TRANSFER_MODULE = "WEAR_TRANSFER"
internal const val WEAR_TRANSFER_ONLY = "wear_transfer_only"

internal data class WearTransferRecord(
    val outcome: String,
    val message: String,
    val metadata: Map<String, Any?>,
    val occurredAtEpochMs: Long = System.currentTimeMillis(),
)

/** A full queue drops new diagnostics, never backpressures the real-time transport. */
internal class WearTransferWriter(
    scope: CoroutineScope,
    capacity: Int = 300,
    persist: suspend (WearTransferRecord) -> Unit,
) {
    private val pending = Channel<WearTransferRecord>(capacity)
    private val queueLock = Any()
    private var dropped = 0L

    init {
        scope.launch {
            for (event in pending) {
                try {
                    persist(event)
                    val missing = synchronized(queueLock) { dropped.also { dropped = 0L } }
                    if (missing > 0) {
                        persist(
                            WearTransferRecord(
                                "DROPPED",
                                "Protokoll unvollständig: $missing Einträge wegen voller Warteschlange verworfen",
                                mapOf("category" to "Protokoll", "droppedCount" to missing),
                            ),
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A storage failure must not terminate the writer or affect the transport.
                }
            }
        }
    }

    fun enqueue(event: WearTransferRecord): Boolean =
        synchronized(queueLock) {
            pending.trySend(event).isSuccess.also { accepted -> if (!accepted) dropped++ }
        }
}

private object WearTransferPersistence {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var writer: WearTransferWriter? = null

    @Synchronized
    fun enqueue(context: Context, event: WearTransferRecord) {
        val active =
            writer ?: WearTransferWriter(scope) { record ->
                DiagnosticEventStore(context.applicationContext).record(
                    origin = "MOBILE",
                    module = WEAR_TRANSFER_MODULE,
                    code = "TRANSFER-${record.outcome}",
                    message = record.message,
                    severity =
                        if (record.outcome == "FAILED") {
                            DiagnosticSeverity.ERROR
                        } else if (record.outcome in setOf("CANCELLED", "DROPPED")) {
                            DiagnosticSeverity.WARNING
                        } else {
                            DiagnosticSeverity.INFO
                        },
                    metadata = record.metadata,
                    occurredAtEpochMs = record.occurredAtEpochMs,
                )
            }.also { writer = it }
        active.enqueue(event)
    }
}

internal fun wearTransferEvents(events: List<DiagnosticEvent>): List<DiagnosticEvent> =
    events
        .filter { it.origin == "MOBILE" && it.module == WEAR_TRANSFER_MODULE }
        .sortedByDescending { it.occurredAtEpochMs }
        .take(300)

/** Records API completion only: neither DataClient nor MessageClient provides application receipt. */
internal suspend fun <T> logWearTransfer(
    category: String,
    transport: String,
    path: String,
    target: String,
    bytes: Int,
    summary: Map<String, Any?> = emptyMap(),
    record: (WearTransferRecord) -> Unit,
    send: suspend () -> T,
): T {
    val metadata =
        mapOf(
            "transferId" to UUID.randomUUID().toString(),
            "category" to category,
            "transport" to transport,
            "path" to path,
            "target" to target,
            "bytes" to bytes,
        ) + summary

    fun append(
        outcome: String,
        message: String,
        extra: Map<String, Any?> = emptyMap(),
    ) {
        // Enqueue only; no disk operation or suspending logger is allowed on the send path.
        runCatching { record(WearTransferRecord(outcome, message, metadata + extra)) }
    }
    append("STARTED", "Sendeversuch gestartet")
    try {
        val result = send()
        append("API_ACCEPTED", "Von Play services angenommen; Empfang auf Wear nicht bestätigt")
        return result
    } catch (error: Exception) {
        append(
            if (error is CancellationException) "CANCELLED" else "FAILED",
            if (error is CancellationException) "Sendeversuch abgebrochen; Empfang unbekannt" else "Sendeversuch fehlgeschlagen; Empfang unbekannt",
            mapOf("errorType" to error.javaClass.simpleName),
        )
        throw error
    }
}

private fun Context.recordTransfer(record: WearTransferRecord) = WearTransferPersistence.enqueue(applicationContext, record)

internal suspend fun Context.putLoggedWearData(
    request: PutDataRequest,
    category: String,
    summary: Map<String, Any?> = emptyMap(),
) = logWearTransfer(
    category,
    "DataClient · persistent",
    request.uri.path.orEmpty(),
    "Wear-Netzwerk",
    request.data?.size ?: 0,
    summary,
    ::recordTransfer,
) { Wearable.getDataClient(this).putDataItem(request).await() }

internal suspend fun Context.sendLoggedWearMessage(
    nodeId: String,
    path: String,
    payload: ByteArray,
    category: String,
    summary: Map<String, Any?> = emptyMap(),
) = logWearTransfer(
    category,
    "MessageClient · direkt",
    path,
    nodeId,
    payload.size,
    summary,
    ::recordTransfer,
) { Wearable.getMessageClient(this).sendMessage(nodeId, path, payload).await() }
