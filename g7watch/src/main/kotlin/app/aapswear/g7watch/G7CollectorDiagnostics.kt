package app.aapswear.g7watch

import android.content.Context
import androidx.core.content.edit
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.g7.CollectorCycleClassification
import app.aapswear.g7.CollectorCycleTiming
import app.aapswear.g7.CollectorDiagnosticAttempt
import app.aapswear.g7.CollectorDiagnosticEvent
import app.aapswear.g7.CollectorDiagnosticResult
import app.aapswear.g7.CollectorDiagnosticStage
import app.aapswear.g7.CollectorSlotStrategy
import app.aapswear.g7.CollectorSlotSummary
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Locale

internal class G7CollectorDiagnosticStore(
    context: Context,
) {
    private val controlPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val historyPreferences =
        context.applicationContext.getSharedPreferences(HISTORY_PREFERENCES, Context.MODE_PRIVATE)
    private val activePreferences =
        context.applicationContext.getSharedPreferences(ACTIVE_PREFERENCES, Context.MODE_PRIVATE)
    private val slotPreferences =
        context.applicationContext.getSharedPreferences(SLOT_PREFERENCES, Context.MODE_PRIVATE)
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    private val serializer = ListSerializer(CollectorDiagnosticAttempt.serializer())
    private val slotSerializer = ListSerializer(CollectorSlotSummary.serializer())

    init {
        synchronized(lock) {
            migrateLegacyHistory()
            compactCompletedActiveAttempts()
        }
    }

    fun begin(
        manual: Boolean,
        restart: Boolean,
        cycle: CollectorCycleTiming? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
        deadlineEpochMs: Long? = null,
    ): CollectorDiagnosticAttempt =
        synchronized(lock) {
            // A new canonical cycle is proof that older inactive rows have no live owner. Compact
            // expired/deadline-exceeded attempts before adding the new row so an unrelated healthy
            // current cycle cannot hide a persisted HUNG predecessor forever.
            expireStaleAttempts(nowEpochMs)
            val attemptId = controlPreferences.getLong(KEY_COUNTER, 0L) + 1L
            val initialEvents =
                buildList {
                    add(
                        CollectorDiagnosticEvent(
                            timestampEpochMs = nowEpochMs,
                            attemptId = attemptId,
                            stage = CollectorDiagnosticStage.IDLE,
                            result = CollectorDiagnosticResult.STARTED,
                            message =
                                when {
                                    restart -> "Collector-Neustart gestartet"
                                    manual -> "Manuelle Sensorsuche gestartet"
                                    else -> "Automatischer Collection-Versuch gestartet"
                                },
                        ),
                    )
                    cycle?.receiverReceivedAt?.let {
                        add(
                            CollectorDiagnosticEvent(
                                timestampEpochMs = it,
                                attemptId = attemptId,
                                stage = CollectorDiagnosticStage.ALARM_RECEIVED,
                                result = CollectorDiagnosticResult.INFO,
                                message = "Sensorfenster-Alarm empfangen",
                                durationMs = cycle.alarmLatenessMs,
                            ),
                        )
                    }
                    cycle?.serviceOnStartCommandAt?.let {
                        add(
                            CollectorDiagnosticEvent(
                                timestampEpochMs = it,
                                attemptId = attemptId,
                                stage = CollectorDiagnosticStage.SERVICE_START,
                                result = CollectorDiagnosticResult.INFO,
                                message = "Foreground-Service für Sensorfenster gestartet",
                                durationMs = cycle.serviceStartLatenessMs,
                            ),
                        )
                    }
                    cycle?.wakeLockAcquiredAt?.let {
                        add(
                            CollectorDiagnosticEvent(
                                timestampEpochMs = it,
                                attemptId = attemptId,
                                stage = CollectorDiagnosticStage.WAKE_LOCK,
                                result = CollectorDiagnosticResult.INFO,
                                message = "Begrenzter Cycle-WakeLock übernommen",
                            ),
                        )
                    }
                }.sortedBy(CollectorDiagnosticEvent::timestampEpochMs)

            val attempt =
                CollectorDiagnosticAttempt(
                    attemptId = attemptId,
                    processInstanceId = G7ProcessInstance.id,
                    startedAtEpochMs = nowEpochMs,
                    lastProgressAtEpochMs = nowEpochMs,
                    currentStage = CollectorDiagnosticStage.IDLE,
                    deadlineEpochMs = deadlineEpochMs,
                    manual = manual,
                    restart = restart,
                    events = initialEvents,
                    cycle = cycle,
                )
            val active = loadActive() + attempt
            val overflow = active.dropLast(MAX_ACTIVE_ATTEMPTS)
            if (overflow.isNotEmpty()) appendHistory(overflow)
            saveActive(active.takeLast(MAX_ACTIVE_ATTEMPTS))
            controlPreferences.edit { putLong(KEY_COUNTER, attemptId) }
            attempt
        }

    fun record(
        attemptId: Long,
        stage: CollectorDiagnosticStage,
        result: CollectorDiagnosticResult = CollectorDiagnosticResult.INFO,
        message: String,
        errorCode: String? = null,
        sensorId: String? = null,
        sequence: Long? = null,
        durationMs: Long? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) = synchronized(lock) {
        val attempts = loadActive().toMutableList()
        val index = attempts.indexOfFirst { it.attemptId == attemptId }
        if (index < 0) return@synchronized
        val current = attempts[index]
        val event =
            CollectorDiagnosticEvent(
                timestampEpochMs = nowEpochMs,
                attemptId = attemptId,
                stage = stage,
                result = result,
                message = sanitizeDiagnosticText(message),
                errorCode = errorCode?.take(40),
                sensorId = sensorId?.take(80),
                sequence = sequence,
                durationMs = durationMs,
            )
        val terminal = stage == CollectorDiagnosticStage.COMPLETE || stage == CollectorDiagnosticStage.ERROR
        val updated =
            current.copy(
                lastProgressAtEpochMs = nowEpochMs,
                currentStage = stage,
                completedAtEpochMs = if (terminal) nowEpochMs else current.completedAtEpochMs,
                result = if (terminal) result else current.result,
                summary = if (terminal) event.message else current.summary,
                events = (current.events + event).takeLast(MAX_EVENTS_PER_ATTEMPT),
                cycle = if (terminal) current.cycle?.copy(cycleEndedAt = nowEpochMs) else current.cycle,
            )
        attempts[index] = updated

        if (terminal) {
            // Persist the terminal active row first. If Android kills the process between writes,
            // construction on the next process compacts that completed row into history instead of
            // losing the attempt. Only this terminal transition rewrites the large history file.
            saveActive(attempts)
            appendHistory(listOf(updated))
            appendSlotSummary(updated)
            attempts.removeAt(index)
            saveActive(attempts)
        } else {
            // High-frequency scan/GATT events only rewrite this tiny active-attempt file. The
            // 16-hour completed history is deliberately not serialized on every BLE callback.
            saveActive(attempts)
        }
    }

    fun updateCycle(
        attemptId: Long,
        transform: (CollectorCycleTiming) -> CollectorCycleTiming,
    ) = synchronized(lock) {
        val attempts = loadActive().toMutableList()
        val index = attempts.indexOfFirst { it.attemptId == attemptId }
        if (index < 0) return@synchronized
        val current = attempts[index]
        attempts[index] = current.copy(cycle = transform(current.cycle ?: CollectorCycleTiming()))
        saveActive(attempts)
    }

    fun setClassification(
        attemptId: Long,
        classification: CollectorCycleClassification,
    ) = synchronized(lock) {
        val attempts = loadActive().toMutableList()
        val index = attempts.indexOfFirst { it.attemptId == attemptId }
        if (index < 0) return@synchronized
        attempts[index] = attempts[index].copy(classification = classification)
        saveActive(attempts)
    }

    /** Persisted after AlarmManager accepts the request as durable evidence of the armed slot. */
    fun stageScheduledCycle(cycle: CollectorCycleTiming) =
        synchronized(lock) {
            controlPreferences.edit {
                putString(KEY_PENDING_CYCLE, json.encodeToString(CollectorCycleTiming.serializer(), cycle))
            }
        }

    /** Stamps the real BroadcastReceiver delivery time before the FGS handoff starts. */
    fun markScheduledAlarmReceived(nowEpochMs: Long): CollectorCycleTiming? =
        synchronized(lock) {
            val pending = loadPendingCycle() ?: return@synchronized null
            val updated =
                pending.copy(
                    alarmTriggeredAt = nowEpochMs,
                    receiverReceivedAt = nowEpochMs,
                )
            controlPreferences.edit {
                putString(KEY_PENDING_CYCLE, json.encodeToString(CollectorCycleTiming.serializer(), updated))
            }
            updated
        }

    /** Transfers one scheduled slot into the service attempt and clears only the pending envelope. */
    fun consumeScheduledCycle(serviceStartEpochMs: Long): CollectorCycleTiming? =
        synchronized(lock) {
            val pending = loadPendingCycle() ?: return@synchronized null
            controlPreferences.edit { remove(KEY_PENDING_CYCLE) }
            pending.copy(serviceOnStartCommandAt = serviceStartEpochMs)
        }

    fun pendingScheduledCycle(): CollectorCycleTiming? = synchronized(lock) { loadPendingCycle() }

    fun discardScheduledCycle() = synchronized(lock) { controlPreferences.edit(commit = true) { remove(KEY_PENDING_CYCLE) } }

    fun snapshot(): List<CollectorDiagnosticAttempt> =
        synchronized(lock) {
            mergedAttempts().sortedByDescending(CollectorDiagnosticAttempt::attemptId)
        }

    fun slotSnapshot(): List<CollectorSlotSummary> = synchronized(lock) { loadSlotHistory() }

    fun hasActiveAttempt(): Boolean = synchronized(lock) { loadActive().any { it.completedAtEpochMs == null } }

    fun hasActiveAttempt(attemptId: Long?): Boolean =
        synchronized(lock) {
            attemptId != null && loadActive().any { it.attemptId == attemptId && it.completedAtEpochMs == null }
        }

    fun activeAttempt(attemptId: Long?): CollectorDiagnosticAttempt? =
        synchronized(lock) {
            attemptId?.let { id -> loadActive().firstOrNull { it.attemptId == id && it.completedAtEpochMs == null } }
        }

    fun expireStaleAttempts(
        nowEpochMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = STALE_ATTEMPT_AGE_MS,
        interruptedByProcessRestart: Boolean = false,
    ): Int =
        synchronized(lock) {
            val active = loadActive().toMutableList()
            val stale =
                active.filter { attempt ->
                    val lastProgressAt =
                        maxOf(
                            attempt.lastProgressAtEpochMs,
                            attempt.events.maxOfOrNull(CollectorDiagnosticEvent::timestampEpochMs) ?: attempt.startedAtEpochMs,
                        )
                    attempt.completedAtEpochMs == null &&
                        (attempt.deadlineEpochMs?.let { nowEpochMs >= it } == true || nowEpochMs - lastProgressAt >= maxAgeMs)
                }
            stale.forEach { attempt ->
                val terminal =
                    attempt.copy(
                        completedAtEpochMs = nowEpochMs,
                        result = CollectorDiagnosticResult.RECOVERABLE_ERROR,
                        summary =
                            if (interruptedByProcessRestart) {
                                "PROCESS_INTERRUPTED · Prozess endete vor dem Terminalzustand"
                            } else {
                                "HUNG · veralteter aktiver Collector-Zyklus automatisch bereinigt"
                            },
                        classification =
                            if (interruptedByProcessRestart) {
                                CollectorCycleClassification.PROCESS_INTERRUPTED
                            } else {
                                CollectorCycleClassification.HUNG
                            },
                        cycle = attempt.cycle?.copy(cycleEndedAt = nowEpochMs),
                        events =
                            (
                                attempt.events +
                                    CollectorDiagnosticEvent(
                                        timestampEpochMs = nowEpochMs,
                                        attemptId = attempt.attemptId,
                                        stage = CollectorDiagnosticStage.ERROR,
                                        result = CollectorDiagnosticResult.RECOVERABLE_ERROR,
                                        message = if (interruptedByProcessRestart) "PROCESS_INTERRUPTED · Prozess endete vor dem Terminalzustand" else "HUNG · veralteter aktiver Collector-Zyklus automatisch bereinigt",
                                        errorCode = if (interruptedByProcessRestart) "G7-PROCESS-INTERRUPTED" else "G7-CYCLE-HUNG",
                                        durationMs = nowEpochMs - attempt.startedAtEpochMs,
                                    )
                            ).takeLast(MAX_EVENTS_PER_ATTEMPT),
                    )
                appendHistory(listOf(terminal))
                appendSlotSummary(terminal)
            }
            if (stale.isNotEmpty()) {
                saveActive(active.filterNot { candidate -> stale.any { it.attemptId == candidate.attemptId } })
            }
            stale.size
        }

    fun attemptsBetween(
        fromEpochMs: Long,
        toEpochMs: Long,
    ): List<CollectorDiagnosticAttempt> =
        snapshot()
            .filter { attempt ->
                attempt.startedAtEpochMs <= toEpochMs &&
                    (attempt.completedAtEpochMs ?: attempt.startedAtEpochMs) >= fromEpochMs
            }.sortedBy(CollectorDiagnosticAttempt::startedAtEpochMs)

    private fun loadHistory(): List<CollectorDiagnosticAttempt> =
        historyPreferences
            .getString(KEY_HISTORY, null)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    private fun loadActive(): List<CollectorDiagnosticAttempt> =
        activePreferences
            .getString(KEY_ACTIVE, null)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    private fun saveHistory(attempts: List<CollectorDiagnosticAttempt>) {
        historyPreferences.edit {
            putString(
                KEY_HISTORY,
                json.encodeToString(serializer, attempts.takeLast(MAX_ATTEMPTS).map(::compactCompletedAttempt)),
            )
        }
    }

    private fun saveActive(attempts: List<CollectorDiagnosticAttempt>) {
        activePreferences.edit {
            if (attempts.isEmpty()) {
                remove(KEY_ACTIVE)
            } else {
                putString(KEY_ACTIVE, json.encodeToString(serializer, attempts.takeLast(MAX_ACTIVE_ATTEMPTS)))
            }
        }
    }

    private fun appendHistory(attempts: List<CollectorDiagnosticAttempt>) {
        if (attempts.isEmpty()) return
        val merged =
            (loadHistory() + attempts)
                .associateBy(CollectorDiagnosticAttempt::attemptId)
                .values
                .sortedBy(CollectorDiagnosticAttempt::attemptId)
                .takeLast(MAX_ATTEMPTS)
        saveHistory(merged)
    }

    private fun loadSlotHistory(): List<CollectorSlotSummary> =
        slotPreferences
            .getString(KEY_SLOTS, null)
            ?.let { runCatching { json.decodeFromString(slotSerializer, it) }.getOrNull() }
            .orEmpty()

    private fun appendSlotSummary(attempt: CollectorDiagnosticAttempt) {
        val cycle = attempt.cycle ?: return
        val expected = cycle.expectedReadingEpoch ?: return
        val classification = attempt.classification ?: return
        val strategy =
            cycle.slotStrategy ?: when {
                classification == CollectorCycleClassification.SUCCESS_FRESH && cycle.fallbackScanUsed -> CollectorSlotStrategy.FALLBACK_SCAN_SUCCESS
                classification == CollectorCycleClassification.SUCCESS_FRESH && cycle.directConnectAttempts > 1 -> CollectorSlotStrategy.DIRECT_RETRY_SUCCESS
                classification == CollectorCycleClassification.SUCCESS_FRESH -> CollectorSlotStrategy.DIRECT_ONLY_SUCCESS
                else -> CollectorSlotStrategy.FULL_SLOT_FAILED
            }
        val summary =
            CollectorSlotSummary(
                expectedReadingEpoch = expected,
                attemptId = attempt.attemptId,
                strategy = strategy,
                directResult = cycle.directConnectResult,
                directAttempts = cycle.directConnectAttempts,
                fallbackScanUsed = cycle.fallbackScanUsed,
                scanResultCount = cycle.scanTotalResults,
                finalClassification = classification,
                readingAgeSeconds = cycle.sensorAgeSeconds,
                durationMs = attempt.completedAtEpochMs?.minus(attempt.startedAtEpochMs),
                radioFailureStreak = cycle.radioFailureStreak,
            )
        val retained =
            (loadSlotHistory() + summary)
                .associateBy(CollectorSlotSummary::expectedReadingEpoch)
                .values
                .sortedBy(CollectorSlotSummary::expectedReadingEpoch)
                .takeLast(MAX_SLOT_SUMMARIES)
        slotPreferences.edit { putString(KEY_SLOTS, json.encodeToString(slotSerializer, retained)) }
    }

    private fun mergedAttempts(): List<CollectorDiagnosticAttempt> =
        (loadHistory() + loadActive())
            .associateBy(CollectorDiagnosticAttempt::attemptId)
            .values
            .sortedBy(CollectorDiagnosticAttempt::attemptId)
            .takeLast(MAX_ATTEMPTS)

    private fun migrateLegacyHistory() {
        val legacy = controlPreferences.getString(KEY_LEGACY_ATTEMPTS, null) ?: return
        val decoded = runCatching { json.decodeFromString(serializer, legacy) }.getOrNull()
        if (decoded != null) appendHistory(decoded)
        controlPreferences.edit { remove(KEY_LEGACY_ATTEMPTS) }
    }

    private fun compactCompletedActiveAttempts() {
        val active = loadActive()
        if (active.isEmpty()) return
        val (completed, running) = active.partition { it.completedAtEpochMs != null }
        if (completed.isNotEmpty()) appendHistory(completed)
        completed.forEach(::appendSlotSummary)
        if (completed.isNotEmpty() || running.size > MAX_ACTIVE_ATTEMPTS) {
            saveActive(running.takeLast(MAX_ACTIVE_ATTEMPTS))
        }
    }

    private fun loadPendingCycle(): CollectorCycleTiming? =
        controlPreferences
            .getString(KEY_PENDING_CYCLE, null)
            ?.let { runCatching { json.decodeFromString(CollectorCycleTiming.serializer(), it) }.getOrNull() }

    private companion object {
        const val PREFERENCES = "g7_collector_attempts"
        const val HISTORY_PREFERENCES = "g7_collector_attempt_history"
        const val ACTIVE_PREFERENCES = "g7_collector_attempt_active"
        const val SLOT_PREFERENCES = "g7_collector_slot_history"
        const val KEY_COUNTER = "attempt_counter"
        const val KEY_LEGACY_ATTEMPTS = "attempts_v1"
        const val KEY_HISTORY = "attempts_v2"
        const val KEY_ACTIVE = "active_attempts_v1"
        const val KEY_SLOTS = "slots_v1"
        const val KEY_PENDING_CYCLE = "pending_cycle_v2"

        // 192 five-minute attempts retain roughly 16 hours, enough to preserve a complete
        // overnight test plus the morning recovery while avoiding multi-megabyte preference
        // rewrites on every completed BLE cycle.
        const val MAX_ATTEMPTS = 192

        // Normally only one cycle is active. A small bound also preserves rare overlap/process-death
        // evidence without allowing interrupted attempts to grow unbounded.
        const val MAX_ACTIVE_ATTEMPTS = 8
        const val MAX_SLOT_SUMMARIES = 192
        const val MAX_EVENTS_PER_ATTEMPT = 40
        const val STALE_ATTEMPT_AGE_MS = 4L * 60_000L
        val lock = Any()
    }
}

private fun compactCompletedAttempt(attempt: CollectorDiagnosticAttempt): CollectorDiagnosticAttempt {
    if (attempt.completedAtEpochMs == null || attempt.events.size <= 4) return attempt
    // Cycle aggregates retain only the final GATT result. Keep a bounded generation timeline too:
    // otherwise compaction erases the evidence needed to distinguish pre-scan and post-scan 133s.
    val radioIndices =
        attempt.events.indices
            .filter { attempt.events[it].stage == CollectorDiagnosticStage.CONNECT_REQUEST }
            .takeLast(12)
    val retained =
        (
            listOf(0) + radioIndices +
                attempt.events.indices
                    .toList()
                    .takeLast(3)
        ).toSet()
    return attempt.copy(events = attempt.events.filterIndexed { index, _ -> index in retained })
}

internal const val G7_SLOT_RETENTION_COUNT = 2_304
internal const val G7_SLOT_INTERVAL_MS = 5L * 60_000L

internal fun g7SlotRetentionDurationMs(): Long = G7_SLOT_RETENTION_COUNT * G7_SLOT_INTERVAL_MS

internal fun classifyG7CycleFailure(
    errorCode: String?,
    cycle: CollectorCycleTiming?,
): CollectorCycleClassification =
    when {
        errorCode == "G7-BLE-FALLBACK-107" -> CollectorCycleClassification.FALLBACK_SCAN_FAILED
        errorCode == "G7-BLE-107" -> CollectorCycleClassification.NO_ADVERTISEMENT
        errorCode == G7_DIRECT_CONNECT_TIMEOUT_ERROR_CODE -> CollectorCycleClassification.GATT_NO_CALLBACK
        errorCode == G7_DISCOVERY_CALLBACK_TIMEOUT_ERROR_CODE -> CollectorCycleClassification.GATT_NO_CALLBACK
        errorCode == G7_DESCRIPTOR_CALLBACK_TIMEOUT_ERROR_CODE -> CollectorCycleClassification.GATT_NO_CALLBACK
        errorCode == G7_WRITE_CALLBACK_TIMEOUT_ERROR_CODE -> CollectorCycleClassification.GATT_NO_CALLBACK
        errorCode == "G7-GATT-133" || errorCode?.startsWith("G7-GATT-") == true -> CollectorCycleClassification.GATT_CONNECT_FAILED
        errorCode?.startsWith("G7-AUTH-") == true || errorCode?.startsWith("AUTH") == true -> CollectorCycleClassification.AUTH_FAILED
        errorCode == "G7-BLE-111" -> CollectorCycleClassification.GLUCOSE_TIMEOUT
        errorCode == "G7-DATA-301" -> CollectorCycleClassification.INVALID_PACKET
        cycle?.scanStartLatenessMs?.let { it > SCAN_LATE_THRESHOLD_MS } == true -> CollectorCycleClassification.SCAN_STARTED_LATE
        cycle?.alarmLatenessMs?.let { it > ALARM_LATE_THRESHOLD_MS } == true -> CollectorCycleClassification.ALARM_LATE
        else -> CollectorCycleClassification.GATT_CONNECT_FAILED
    }

internal fun isFreshG7CycleReading(
    reading: CgmReading,
    nowEpochMs: Long,
): Boolean {
    if (reading.status != CgmReadingStatus.VALID) return false
    val measurementAgeMs = (nowEpochMs - reading.timestampEpochMs).coerceAtLeast(0L)
    val reportedAgeMs = reading.sensorAgeSeconds?.coerceAtLeast(0L)?.times(1_000L)
    return measurementAgeMs <= FRESH_CYCLE_MAX_AGE_MS &&
        (reportedAgeMs == null || reportedAgeMs <= FRESH_CYCLE_MAX_AGE_MS)
}

internal data class G7ReadingHistorySummary(
    val count: Int,
    val todayCount: Int,
    val oldestEpochMs: Long?,
    val latestEpochMs: Long?,
    val missedExpectedWindows: Int,
)

internal fun summarizeG7Readings(
    readings: List<CgmReading>,
    startOfDayEpochMs: Long,
): G7ReadingHistorySummary {
    val ordered =
        readings
            .asSequence()
            .filter {
                it.status == CgmReadingStatus.VALID &&
                    it.glucoseMgDl.isFinite() &&
                    it.glucoseMgDl in 20.0..1_000.0
            }.sortedBy(CgmReading::timestampEpochMs)
            .toList()
    val missed =
        ordered.groupBy { it.sensorId to it.sessionId }.values.sumOf { stream ->
            stream.zipWithNext().sumOf { (before, after) ->
                val interval = after.timestampEpochMs - before.timestampEpochMs
                if (interval <= EXPECTED_INTERVAL_MS + WINDOW_TOLERANCE_MS) {
                    0
                } else {
                    (interval / EXPECTED_INTERVAL_MS - 1L).coerceAtLeast(1L).toInt()
                }
            }
        }
    return G7ReadingHistorySummary(
        count = ordered.size,
        todayCount = ordered.count { it.timestampEpochMs >= startOfDayEpochMs },
        oldestEpochMs = ordered.firstOrNull()?.timestampEpochMs,
        latestEpochMs = ordered.lastOrNull()?.timestampEpochMs,
        missedExpectedWindows = missed,
    )
}

internal fun maskBluetoothAddress(address: String?): String {
    if (address.isNullOrBlank()) return "—"
    val parts = address.uppercase(Locale.US).split(':')
    return if (parts.size == 6) "••:••:••:••:${parts[4]}:${parts[5]}" else "••••${address.takeLast(4)}"
}

internal fun sanitizeDiagnosticText(value: String): String =
    value
        .replace(
            Regex(
                "(?i)\\b(shared[_ -]?key|pairing[_ -]?secret|auth(?:entication)?[_ -]?(?:key|credential)|pairing[_ -]?code|sensor[_ -]?code)\\s*[:=]\\s*\\S+",
            ),
        ) { match ->
            "${match.groupValues[1]}=[REDACTED]"
        }.replace(Regex("(?i)(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")) { matchBluetooth ->
            maskBluetoothAddress(matchBluetooth.value)
        }.replace(Regex("[\\r\\n\\t]+"), " ")
        .trim()
        .take(240)

internal const val FRESH_CYCLE_MAX_AGE_MS = 7L * 60_000L
internal const val ALARM_LATE_THRESHOLD_MS = 15_000L
internal const val SCAN_LATE_THRESHOLD_MS = 20_000L
private const val EXPECTED_INTERVAL_MS = 5L * 60_000L
private const val WINDOW_TOLERANCE_MS = 90_000L
