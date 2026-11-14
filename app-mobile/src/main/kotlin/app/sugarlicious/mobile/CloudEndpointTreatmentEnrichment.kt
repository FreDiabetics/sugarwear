package app.sugarlicious.mobile

import android.content.Context
import androidx.core.content.edit
import app.sugarlicious.model.CanonicalTreatments
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TherapyEvent
import app.sugarlicious.model.TherapyEventKind
import app.sugarlicious.model.TherapyEventSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class CloudEndpointAuthMode { API_SECRET, ACCESS_TOKEN }

internal data class CloudEndpointConfiguration(
    val enabled: Boolean,
    val baseUrl: String,
    val authMode: CloudEndpointAuthMode,
) {
    val normalizedBaseUrl: String get() = baseUrl.trim().trimEnd('/')

    fun isValid(): Boolean =
        runCatching {
            val uri = URI(normalizedBaseUrl)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null
        }.getOrDefault(false)
}

internal object CloudEndpointConfigurationStore {
    private const val PREFS = "nightscout_treatment_config"
    private const val ENABLED = "enabled"
    private const val URL = "url"
    private const val AUTH_MODE = "authMode"

    fun read(context: Context): CloudEndpointConfiguration {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return CloudEndpointConfiguration(
            enabled = preferences.getBoolean(ENABLED, false),
            baseUrl = preferences.getString(URL, "").orEmpty(),
            authMode =
                runCatching {
                    CloudEndpointAuthMode.valueOf(preferences.getString(AUTH_MODE, CloudEndpointAuthMode.API_SECRET.name)!!)
                }.getOrDefault(CloudEndpointAuthMode.API_SECRET),
        )
    }

    @Synchronized
    fun access(context: Context): Pair<CloudEndpointConfiguration, String?> = read(context) to CloudEndpointSecretStore.read(context)

    @Synchronized
    fun save(
        context: Context,
        configuration: CloudEndpointConfiguration,
        secret: String?,
    ) {
        require(!configuration.enabled || configuration.isValid()) { "Nightscout-URL muss eine gültige HTTPS-Adresse sein" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(ENABLED, configuration.enabled)
            putString(URL, configuration.normalizedBaseUrl)
            putString(AUTH_MODE, configuration.authMode.name)
        }
        if (secret != null) CloudEndpointSecretStore.save(context, secret)
        val sourcePrefs = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
        sourcePrefs.edit { putLong("source.nightscout.revision", sourcePrefs.getLong("source.nightscout.revision", 0) + 1) }
        PersistentBridgeService.start(context)
    }
}

/** Android-Keystore backed secret storage. Ciphertext is never included in SettingsBackup. */
internal object CloudEndpointSecretStore {
    private val store = AndroidSecretStore("nightscout_treatment_secret", "sugarlicious_nightscout_auth_v1")

    fun save(context: Context, value: String) = store.save(context, value)

    fun read(context: Context): String? = store.read(context)
}

internal object CloudEndpointTreatmentParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): List<TherapyEvent> {
        val array = runCatching { json.parseToJsonElement(payload).jsonArray }.getOrElse { return emptyList() }
        return array
            .flatMap { element -> parseObject(runCatching { element.jsonObject }.getOrNull() ?: return@flatMap emptyList()) }
            .distinctBy(TherapyEvent::id)
            .sortedBy(TherapyEvent::timestampEpochMs)
    }

    private fun parseObject(item: JsonObject): List<TherapyEvent> {
        val timestamp = timestamp(item) ?: return emptyList()
        val sourceId = text(item, "_id") ?: text(item, "identifier") ?: text(item, "id")
        val eventType = text(item, "eventType")?.trim().orEmpty()
        val enteredBy = text(item, "enteredBy")
        val notes = text(item, "notes").orEmpty()
        val insulin = number(item, "insulin")?.takeIf { it > 0.0 }
        val carbs = number(item, "carbs")?.takeIf { it > 0.0 }
        val duration = integer(item, "duration")?.takeIf { it > 0 }
        val explicitSmb = boolean(item, "isSMB") == true || eventType.contains("SMB", true) || notes.contains("SMB", true)
        val eCarb = duration != null || eventType.contains("eCarb", true) || notes.contains("eCarb", true)
        val baseId = sourceId ?: sha256("$timestamp|$eventType|$insulin|$carbs|$duration|$enteredBy")
        return buildList {
            insulin?.let {
                val kind =
                    when {
                        explicitSmb -> TherapyEventKind.SMB
                        eventType.contains("meal", true) || carbs != null -> TherapyEventKind.MEAL_BOLUS
                        eventType.contains("correction", true) -> TherapyEventKind.SMB
                        eventType.contains("bolus", true) -> TherapyEventKind.MANUAL_CORRECTION
                        else -> null
                    }
                if (kind != null) add(event("$baseId:insulin", baseId, kind, timestamp, it, insulin, carbs, duration, enteredBy, eventType))
            }
            carbs?.let {
                val kind = if (eCarb) TherapyEventKind.ECARBS else TherapyEventKind.MEAL_CARBS
                add(event("$baseId:carbs", baseId, kind, timestamp, it, insulin, carbs, duration, enteredBy, eventType))
            }
        }
    }

    private fun event(
        id: String,
        sourceId: String,
        kind: TherapyEventKind,
        timestamp: Long,
        amount: Double,
        insulin: Double?,
        carbs: Double?,
        duration: Int?,
        enteredBy: String?,
        eventType: String,
    ) = TherapyEvent(
        id,
        kind,
        timestamp,
        amount,
        TherapyEventSource.NIGHTSCOUT_ONLY,
        sourceId,
        insulin,
        carbs,
        duration,
        enteredBy,
        eventType,
        validated = true,
    )

    private fun timestamp(item: JsonObject): Long? {
        val direct = listOf("date", "timestamp").firstNotNullOfOrNull { key -> item[key]?.jsonPrimitive?.longOrNull }?.let(::normalizeEpoch)
        if (direct != null) return direct
        return listOf("created_at", "dateString").firstNotNullOfOrNull { key ->
            text(item, key)?.let {
                runCatching {
                    java.time.Instant
                        .parse(it)
                        .toEpochMilli()
                }.getOrNull()
            }
        }
    }

    private fun normalizeEpoch(value: Long) = if (value in 1..9_999_999_999L) value * 1_000L else value

    private fun text(
        item: JsonObject,
        key: String,
    ) = item[key]?.jsonPrimitive?.contentOrNull

    private fun number(
        item: JsonObject,
        key: String,
    ) = item[key]?.jsonPrimitive?.doubleOrNull

    private fun integer(
        item: JsonObject,
        key: String,
    ) = item[key]?.jsonPrimitive?.intOrNull

    private fun boolean(
        item: JsonObject,
        key: String,
    ) = item[key]?.jsonPrimitive?.booleanOrNull

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

@Serializable private data class StoredTreatments(
    val values: List<TherapyEvent> = emptyList(),
)

internal object CloudEndpointTreatmentStore {
    private const val PREFS = "nightscout_treatment_history"
    private const val DATA = "events"
    private const val RETENTION_MS = 7L * 24 * 60 * 60_000L
    private val json = Json { ignoreUnknownKeys = true }

    fun accountKey(configuration: CloudEndpointConfiguration, secret: String?): String {
        val identity = "${configuration.normalizedBaseUrl}:${configuration.authMode}:${secret.orEmpty()}"
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return "$DATA.$hash"
    }

    private fun accountKey(context: Context): String {
        val (configuration, secret) = CloudEndpointConfigurationStore.access(context)
        return accountKey(configuration, secret)
    }

    fun read(
        context: Context,
        now: Long = System.currentTimeMillis(),
        account: String = accountKey(context),
    ): List<TherapyEvent> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(account, null) ?: return emptyList()
        return runCatching { json.decodeFromString(StoredTreatments.serializer(), raw).values }
            .getOrDefault(emptyList())
            .filter { it.timestampEpochMs >= now - RETENTION_MS }
            .map(::normalizeCloudEndpointKind)
    }

    fun merge(
        context: Context,
        incoming: List<TherapyEvent>,
        now: Long,
        account: String = accountKey(context),
    ): List<TherapyEvent> {
        val values =
            (read(context, now, account) + incoming)
                .filter {
                    it.timestampEpochMs in (now - RETENTION_MS)..(now + 5 * 60_000L)
                }.associateBy(TherapyEvent::id)
                .values
                .sortedBy(TherapyEvent::timestampEpochMs)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(account, json.encodeToString(StoredTreatments.serializer(), StoredTreatments(values)))
        }
        return values
    }

    private fun normalizeCloudEndpointKind(event: TherapyEvent): TherapyEvent =
        if (event.kind == TherapyEventKind.MANUAL_CORRECTION && event.eventType?.contains("correction", true) == true) {
            event.copy(kind = TherapyEventKind.SMB)
        } else {
            event
        }
}

internal data class CloudEndpointSyncResult(
    val success: Boolean,
    val count: Int,
    val message: String,
)

internal object CloudEndpointTreatmentSync {
    private const val DIAGNOSTICS = "diagnostics"
    private const val WINDOW_MS = 24L * 60 * 60_000L
    private const val MIN_SYNC_INTERVAL_MS = 15L * 60_000L

    private val syncMutex = Mutex()

    suspend fun syncIfDue(context: Context, now: Long = System.currentTimeMillis()): CloudEndpointSyncResult? =
        syncMutex.withLock {
            val (configuration, secret) = CloudEndpointConfigurationStore.access(context)
            if (!configuration.enabled) return@withLock null
            val diagnostics = context.getSharedPreferences(DIAGNOSTICS, Context.MODE_PRIVATE)
            val attemptKey = "nightscoutTreatmentLastAttempt.${CloudEndpointTreatmentStore.accountKey(configuration, secret)}"
            if (now - diagnostics.getLong(attemptKey, 0L) < MIN_SYNC_INTERVAL_MS) return@withLock null
            diagnostics.edit {
                putLong(attemptKey, now)
                putLong("nightscoutTreatmentLastAttempt", now)
            }
            sync(context, now)
        }

    suspend fun sync(
        context: Context,
        now: Long = System.currentTimeMillis(),
    ): CloudEndpointSyncResult {
        val app = context.applicationContext
        val (configuration, configuredSecret) = CloudEndpointConfigurationStore.access(app)
        if (!configuration.enabled) return CloudEndpointSyncResult(true, 0, "Deaktiviert")
        if (!configuration.isValid()) return failure(app, "Ungültige HTTPS-URL")
        val secret = configuredSecret?.takeIf(String::isNotBlank) ?: return failure(app, "Authentifizierung fehlt")
        val account = CloudEndpointTreatmentStore.accountKey(configuration, secret)
        app.getSharedPreferences(DIAGNOSTICS, Context.MODE_PRIVATE).edit {
            putLong("nightscoutTreatmentLastAttempt", now)
            putLong("nightscoutTreatmentLastAttempt.$account", now)
        }
        return runCatching {
            val since =
                java.time.Instant
                    .ofEpochMilli(now - WINDOW_MS)
                    .toString()
            val endpoint = "/api/v1/treatments.json?find%5Bcreated_at%5D%5B%24gte%5D=${encode(since)}&count=1000"
            val payload = CloudEndpointHttp.get(configuration, secret, endpoint)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val fetched = CloudEndpointTreatmentParser.parse(payload)
            val persisted = CloudEndpointTreatmentStore.merge(app, fetched, now, account)
            enrichPersistedState(app)
            app.getSharedPreferences(DIAGNOSTICS, Context.MODE_PRIVATE).edit {
                putLong("nightscoutTreatmentLastSuccess", now)
                putString("nightscoutTreatmentStatus", "ok")
                putInt("nightscoutTreatmentCount", persisted.size)
                remove("nightscoutTreatmentError")
            }
            CloudEndpointSyncResult(true, fetched.size, "Verbunden · ${fetched.size} Treatments")
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            failure(app, error.message?.take(80) ?: error.javaClass.simpleName)
        }
    }

    suspend fun applyConfigurationState(context: Context) {
        MobileCanonicalStateCoordinator.recompose(context.applicationContext)
    }

    private suspend fun enrichPersistedState(context: Context) {
        MobileCanonicalStateCoordinator.recompose(context.applicationContext)
    }

    private fun failure(
        context: Context,
        error: String,
    ): CloudEndpointSyncResult {
        context.getSharedPreferences(DIAGNOSTICS, Context.MODE_PRIVATE).edit {
            putString("nightscoutTreatmentStatus", "unavailable")
            putString("nightscoutTreatmentError", error)
        }
        return CloudEndpointSyncResult(false, 0, error)
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

internal fun TherapyDisplayState.withCloudEndpointTreatments(context: Context): TherapyDisplayState {
    if (!CloudEndpointConfigurationStore.read(context).enabled) {
        return copy(
            therapyEvents =
                therapyEvents.mapNotNull { event ->
                    when (event.source) {
                        TherapyEventSource.NIGHTSCOUT_ONLY -> null
                        TherapyEventSource.AAPS_ENRICHED_BY_NIGHTSCOUT -> event.copy(source = TherapyEventSource.AAPS_ONLY)
                        TherapyEventSource.AAPS_ONLY -> event
                    }
                },
        )
    }
    val enrichment = CloudEndpointTreatmentStore.read(context, receivedAtEpochMs)
    if (enrichment.isEmpty()) return this
    val therapyBridge = therapyEvents.filter { it.source != TherapyEventSource.NIGHTSCOUT_ONLY }
    return copy(therapyEvents = CanonicalTreatments.merge(therapyBridge, enrichment))
}
