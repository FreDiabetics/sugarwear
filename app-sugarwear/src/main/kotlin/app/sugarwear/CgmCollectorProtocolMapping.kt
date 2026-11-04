package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmConnectionState
import app.sugarlicious.sensor.protocol.CgmProtocolState
import app.sugarlicious.sensor.protocol.CgmSessionState

internal fun CgmProtocolState.toConnectionState(): CgmConnectionState =
    when (this) {
        CgmProtocolState.SCANNING -> CgmConnectionState.SCANNING
        CgmProtocolState.CONNECTING -> CgmConnectionState.CONNECTING
        CgmProtocolState.DISCOVERING, CgmProtocolState.DISCOVERING_SERVICES, CgmProtocolState.ENABLING_NOTIFICATIONS -> CgmConnectionState.DISCOVERING
        CgmProtocolState.AUTHENTICATION_START, CgmProtocolState.AUTHENTICATING, CgmProtocolState.AUTHENTICATED,
        CgmProtocolState.BONDING, CgmProtocolState.REQUESTING_GLUCOSE, CgmProtocolState.RECEIVING_GLUCOSE,
        -> CgmConnectionState.CONNECTED
        else -> CgmConnectionState.DISCONNECTED
    }

internal fun CgmProtocolState.toSessionState(): CgmSessionState =
    when (this) {
        CgmProtocolState.AUTHENTICATED, CgmProtocolState.REQUESTING_GLUCOSE, CgmProtocolState.RECEIVING_GLUCOSE -> CgmSessionState.ACTIVE
        CgmProtocolState.AUTHENTICATION_START, CgmProtocolState.AUTHENTICATING, CgmProtocolState.BONDING -> CgmSessionState.AUTHENTICATING
        CgmProtocolState.WAITING_FOR_NEXT_READING -> CgmSessionState.WAITING_FOR_NEXT_READING
        CgmProtocolState.RECOVERING, CgmProtocolState.ERROR -> CgmSessionState.RECOVERING
        else -> CgmSessionState.INITIAL_SETUP
    }

internal fun CgmProtocolState.label(): String =
    when (this) {
        CgmProtocolState.SCANNING -> "Sensor wird gesucht"
        CgmProtocolState.CONNECTING -> "Sensor wird verbunden"
        CgmProtocolState.DISCOVERING_SERVICES -> "G7-Dienste werden geprüft"
        CgmProtocolState.ENABLING_NOTIFICATIONS -> "G7-Datenkanäle werden geöffnet"
        CgmProtocolState.AUTHENTICATION_START, CgmProtocolState.AUTHENTICATING -> "Sensor wird authentifiziert"
        CgmProtocolState.BONDING -> "Sensor wird verbunden"
        CgmProtocolState.AUTHENTICATED -> "Sensor ist authentifiziert"
        CgmProtocolState.REQUESTING_GLUCOSE -> "Glukosewert wird angefordert"
        CgmProtocolState.RECEIVING_GLUCOSE -> "Glukosewert wird geprüft"
        CgmProtocolState.RECOVERING -> "Nächstes Sensorfenster wird abgewartet"
        else -> name.replace('_', ' ')
    }

internal fun CgmProtocolState.diagnosticCode(): String =
    when (this) {
        CgmProtocolState.SCANNING, CgmProtocolState.CONNECTING, CgmProtocolState.DISCOVERING,
        CgmProtocolState.DISCOVERING_SERVICES, CgmProtocolState.ENABLING_NOTIFICATIONS,
        -> "G7-BLE-110"
        CgmProtocolState.RECOVERING -> "G7-BLE-133"
        CgmProtocolState.AUTHENTICATION_START, CgmProtocolState.AUTHENTICATING, CgmProtocolState.BONDING,
        CgmProtocolState.AUTHENTICATED,
        -> "G7-AUTH-110"
        CgmProtocolState.REQUESTING_GLUCOSE, CgmProtocolState.RECEIVING_GLUCOSE,
        CgmProtocolState.WAITING_FOR_NEXT_READING,
        -> "G7-DATA-110"
        else -> "G7-STATE-100"
    }
