package app.sugarwear

import app.sugarlicious.model.CgmPresentationPolicy
import app.sugarlicious.model.CgmPresentationStatus
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmProtocolState
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.sensor.protocol.CgmSensorAvailability
import app.sugarlicious.sensor.protocol.CgmSensorState
import app.sugarlicious.sensor.protocol.CgmSessionState

internal enum class CgmStatusPillState(
    val title: String,
) {
    CONNECTED("Verbunden"),
    PAUSED("Sensorempfang pausiert"),
    SIGNAL_LOSS("Signalverlust"),
    SENSOR_ERROR("Sensorfehler"),
    NO_ACTIVE_SENSOR("Kein aktiver Sensor verbunden"),
}

/** Shared wording for the overview and Wear OS tiles; no protocol/progress text here. */
internal fun cgmSensorWarning(status: CgmStatusPillState, hasValidReading: Boolean): String? =
    when {
        status == CgmStatusPillState.NO_ACTIVE_SENSOR -> "Kein Sensor verbunden"
        status == CgmStatusPillState.PAUSED -> "Datenempfang pausiert"
        status == CgmStatusPillState.SENSOR_ERROR -> "Sensorfehler"
        !hasValidReading -> "Keine Daten"
        status == CgmStatusPillState.SIGNAL_LOSS -> "Signalverlust"
        else -> null
    }

internal fun deriveCgmStatusPillState(
    state: CgmPersistedState,
    credentialsPresent: Boolean,
    nowEpochMs: Long = System.currentTimeMillis(),
): CgmStatusPillState {
    val sensor = state.sensor
    if (sensor == null || !credentialsPresent || sensor.state == CgmSensorState.ENDED) {
        return CgmStatusPillState.NO_ACTIVE_SENSOR
    }
    if (!state.collectorEnabled) return CgmStatusPillState.PAUSED
    if (sensor.state == CgmSensorState.ERROR || state.lastReading?.status == CgmReadingStatus.SENSOR_ERROR) {
        return CgmStatusPillState.SENSOR_ERROR
    }
    val reading = state.lastReading
    return when (
        CgmPresentationPolicy.classify(
            reading?.timestampEpochMs,
            if (reading?.status == CgmReadingStatus.SENSOR_ERROR) CgmQuality.SENSOR_ERROR else CgmQuality.VALID,
            nowEpochMs = nowEpochMs,
        )
    ) {
        CgmPresentationStatus.SENSOR_ERROR -> CgmStatusPillState.SENSOR_ERROR
        CgmPresentationStatus.SIGNAL_LOSS, CgmPresentationStatus.NO_SOURCE -> CgmStatusPillState.SIGNAL_LOSS
        else -> CgmStatusPillState.CONNECTED
    }
}

internal enum class CgmUserStatusLevel { OK, WORKING, ATTENTION, ERROR, OFF }

internal data class CgmUserStatus(
    val level: CgmUserStatusLevel,
    val title: String,
    val phase: String,
    val status: String,
    val description: String,
    val action: String,
)

internal fun deriveCgmUserStatus(
    state: CgmPersistedState,
    credentialsPresent: Boolean,
    nowEpochMs: Long = System.currentTimeMillis(),
): CgmUserStatus {
    if (!state.collectorEnabled) {
        return CgmUserStatus(
            CgmUserStatusLevel.OFF,
            "Sensorempfang pausiert",
            "Pause",
            "Pausiert",
            "Keine Sensorverbindungen oder Sensoralarme. Verbindung, Sensorcode, Sitzung und Messhistorie bleiben erhalten.",
            "Zum direkten Empfang „Sensorempfang fortsetzen“ wählen.",
        )
    }
    if (state.sensor == null) {
        return problem(
            title = "Einrichtung erforderlich",
            phase = "Sensor einrichten",
            description = "Es ist kein Sensor eingerichtet.",
            action = "Vierstelligen Sensorcode speichern und danach den Collector starten.",
        )
    }
    if (!credentialsPresent) {
        return problem(
            title = "Sensorcode fehlt",
            phase = "Einrichtung prüfen",
            description = "Die lokalen Sensor-Zugangsdaten sind nicht verfügbar.",
            action = "Sensorcode erneut eingeben. Bond oder Shared Key nicht manuell löschen.",
        )
    }

    val ageMs = state.lastReading?.timestampEpochMs?.let { (nowEpochMs - it).coerceAtLeast(0L) }
    val hardSessionProblem =
        state.sessionState in
            setOf(
                CgmSessionState.REQUIRES_REBOND,
                CgmSessionState.REQUIRES_FULL_HANDSHAKE,
                CgmSessionState.USER_INTERVENTION_REQUIRED,
            )
    if (hardSessionProblem) {
        return problemFromError(
            state = state,
            title = "Eingriff erforderlich",
            phase = cgmProtocolPhaseName(state.protocolState),
        )
    }

    if (state.sensor?.state == CgmSensorState.ERROR) {
        return CgmUserStatus(
            CgmUserStatusLevel.ATTENTION,
            "Sensorfehler",
            "Sensorstatus",
            "Kein gültiger Sensorwert",
            "Der Sensor hat einen Fehlerstatus gemeldet. Der letzte gültige Wert bleibt als veraltet erkennbar erhalten.",
            "Sensor und Reichweite prüfen und die automatische Wiederverbindung abwarten. Bond, Shared Key und Sensorcode nicht löschen.",
        )
    }

    if (ageMs != null && ageMs >= CGM_SIGNAL_LOSS_AFTER_MS) {
        val ownershipSuspected = state.health.sensorAvailability == CgmSensorAvailability.POSSIBLY_OWNED_BY_OTHER_COLLECTOR
        return CgmUserStatus(
            CgmUserStatusLevel.ATTENTION,
            "Signalverlust",
            cgmProtocolPhaseName(state.protocolState),
            "Kein aktueller Sensorwert",
            if (ownershipSuspected) {
                "Der Sensor ist gespeichert, aber aktuell nicht erreichbar. Möglicherweise ist noch ein anderer Collector aktiv."
            } else {
                "Seit mindestens 16 Minuten wurde kein valider direkter Sensorwert empfangen."
            },
            if (ownershipSuspected) {
                "Den Sensor auf der bisherigen Uhr ausdrücklich freigeben und diese Uhr anschließend in Sensornähe lassen."
            } else {
                actionForError(state.lastError?.code)
                    ?: "Bluetooth und Sensorreichweite prüfen und die Uhr am Sensor lassen. Bond, Shared Key und Sensorcode nicht löschen."
            },
        )
    }

    return when (state.protocolState) {
        CgmProtocolState.SCANNING,
        CgmProtocolState.SENSOR_FOUND,
        CgmProtocolState.CONNECTING,
        CgmProtocolState.DISCOVERING,
        CgmProtocolState.DISCOVERING_SERVICES,
        CgmProtocolState.ENABLING_NOTIFICATIONS,
        CgmProtocolState.AUTHENTICATION_START,
        CgmProtocolState.AUTHENTICATION_ROUND_1,
        CgmProtocolState.AUTHENTICATION_ROUND_2,
        CgmProtocolState.AUTHENTICATION_ROUND_3,
        CgmProtocolState.CHALLENGE,
        CgmProtocolState.CERTIFICATE_EXCHANGE,
        CgmProtocolState.KEY_EXCHANGE,
        CgmProtocolState.BONDING,
        CgmProtocolState.AUTHENTICATING,
        CgmProtocolState.AUTHENTICATED,
        CgmProtocolState.REQUESTING_GLUCOSE,
        CgmProtocolState.RECEIVING_GLUCOSE,
        CgmProtocolState.BACKFILL,
        ->
            CgmUserStatus(
                CgmUserStatusLevel.WORKING,
                if (ageMs == null) "Verbindung wird aufgebaut" else "Sensorkontakt",
                cgmProtocolPhaseName(state.protocolState),
                "Aktiv · Messzyklus läuft",
                "Der Collector verarbeitet das aktuelle Sensorfenster.",
                "Kein Eingriff erforderlich.",
            )

        CgmProtocolState.WAITING_FOR_NEXT_READING,
        CgmProtocolState.IDLE,
        CgmProtocolState.DISCONNECTED,
        ->
            CgmUserStatus(
                CgmUserStatusLevel.OK,
                if (ageMs == null) "Bereit" else "Sensor aktiv",
                "Bereit für nächsten Wert",
                "Aktiv · Datenfluss in Ordnung",
                ageMs?.let {
                    "Der letzte valide Sensorwert ist ${formatAge(
                        it,
                    )} alt. Der nächste Messzyklus wird automatisch zum Sensorfenster gestartet."
                }
                    ?: "Der Collector ist aktiv und wartet auf den ersten validen Sensorwert.",
                "Kein Eingriff erforderlich.",
            )

        CgmProtocolState.RECOVERING ->
            CgmUserStatus(
                CgmUserStatusLevel.WORKING,
                "Autom. Wiederverb.",
                "Nächstes Sensorfenster",
                if (ageMs == null) "Aktiv · Verbindung wird aufgebaut" else "Aktiv · letzter Wert ${formatAge(ageMs)} alt",
                "Ein Sensorfenster wurde verpasst oder Android BLE hat die Verbindung kurz unterbrochen. Sugarlicious versucht automatisch das nächste erwartete Fenster.",
                "Nichts zurücksetzen. Uhr in Sensornähe lassen; erst ab 16 Minuten ohne Wert ist dies ein Signalverlust.",
            )

        CgmProtocolState.ERROR -> {
            val error = state.lastError
            if (error?.recoverable == true && (ageMs == null || ageMs < CGM_SIGNAL_LOSS_AFTER_MS)) {
                CgmUserStatus(
                    CgmUserStatusLevel.WORKING,
                    "Autom. Wiederverb.",
                    "Recovery",
                    "Aktiv · kein Benutzereingriff nötig",
                    error.safeMessage,
                    "Sugarlicious automatisch weiterarbeiten lassen. Bond, Shared Key und Sensorcode nicht löschen.",
                )
            } else {
                problemFromError(state, "Problem erkannt", "Fehler")
            }
        }

        CgmProtocolState.UNINITIALIZED ->
            problem(
                title = "Noch nicht initialisiert",
                phase = "Initialisierung",
                description = "Der Collector wurde noch nicht vollständig initialisiert.",
                action = "Sensor-Einrichtung prüfen und den Collector starten.",
            )
    }
}

private fun problemFromError(
    state: CgmPersistedState,
    title: String,
    phase: String,
): CgmUserStatus {
    val error = state.lastError
    return CgmUserStatus(
        CgmUserStatusLevel.ERROR,
        title,
        phase,
        error?.code ?: state.sessionState.name,
        error?.safeMessage ?: "Der Collector benötigt einen Benutzereingriff.",
        actionForError(error?.code)
            ?: "Bluetooth, Berechtigungen und Sensorstatus prüfen. Bond, Shared Key und Sensorcode nur bei ausdrücklicher Anweisung zurücksetzen.",
    )
}

private fun problem(
    title: String,
    phase: String,
    description: String,
    action: String,
): CgmUserStatus = CgmUserStatus(CgmUserStatusLevel.ERROR, title, phase, "Eingriff erforderlich", description, action)

private fun actionForError(code: String?): String? =
    when {
        code == null -> null
        code == "G7-BLE-103" -> "Bluetooth auf der Uhr einschalten und die Uhr in Sensornähe lassen."
        code.startsWith("G7-PERM-") -> "Bluetooth- und Benachrichtigungsberechtigungen in der Collector-App freigeben."
        code.startsWith("G7-SETUP-") -> "Sensorcode und Sensoreinrichtung in der Collector-App prüfen."
        code.startsWith(
            "G7-AUTH-",
        ) -> "Uhr in Sensornähe lassen und automatische Wiederverbindung abwarten. Bond, Shared Key und Sensorcode nicht löschen."
        code == "G7-GATT-133" ||
            code == CGM_DIRECT_CONNECT_TIMEOUT_ERROR_CODE ||
            code == "G7-BLE-107" ||
            code == "G7-BLE-111" ||
            code == "G7-BLE-FALLBACK-107" ->
            "Nichts zurücksetzen. Sugarlicious versucht automatisch das nächste Sensorfenster."
        code == "G7-SIGNAL-LOSS" -> "Bluetooth, Sensorreichweite und Sensorstatus prüfen. Bond, Shared Key und Sensorcode nicht löschen."
        else -> null
    }

internal fun cgmProtocolPhaseName(state: CgmProtocolState): String =
    when (state) {
        CgmProtocolState.UNINITIALIZED -> "Nicht initialisiert"
        CgmProtocolState.IDLE -> "Bereit"
        CgmProtocolState.SCANNING -> "Sensor suchen"
        CgmProtocolState.SENSOR_FOUND -> "Sensor gefunden"
        CgmProtocolState.CONNECTING -> "Verbinden"
        CgmProtocolState.DISCOVERING,
        CgmProtocolState.DISCOVERING_SERVICES,
        -> "Sensordienste prüfen"
        CgmProtocolState.ENABLING_NOTIFICATIONS -> "Datenkanäle öffnen"
        CgmProtocolState.AUTHENTICATION_START,
        CgmProtocolState.AUTHENTICATION_ROUND_1,
        CgmProtocolState.AUTHENTICATION_ROUND_2,
        CgmProtocolState.AUTHENTICATION_ROUND_3,
        CgmProtocolState.CHALLENGE,
        CgmProtocolState.CERTIFICATE_EXCHANGE,
        CgmProtocolState.KEY_EXCHANGE,
        CgmProtocolState.AUTHENTICATING,
        -> "Authentifizieren"
        CgmProtocolState.BONDING -> "Verbinden"
        CgmProtocolState.AUTHENTICATED -> "Authentifiziert"
        CgmProtocolState.REQUESTING_GLUCOSE -> "Wert anfordern"
        CgmProtocolState.RECEIVING_GLUCOSE -> "Wert empfangen"
        CgmProtocolState.BACKFILL -> "Historie nachladen"
        CgmProtocolState.WAITING_FOR_NEXT_READING -> "Bereit für nächsten Wert"
        CgmProtocolState.DISCONNECTED -> "Zwischen Sensorfenstern getrennt"
        CgmProtocolState.RECOVERING -> "Autom. Wiederverb."
        CgmProtocolState.ERROR -> "Fehler"
    }

private fun formatAge(ageMs: Long): String {
    val seconds = ageMs / 1_000L
    return if (seconds < 90L) "$seconds s" else "${seconds / 60L} min"
}
