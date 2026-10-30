package app.aapswear.g7watch

import app.aapswear.g7.G7ConnectionState
import app.aapswear.g7.G7ProtocolState
import app.aapswear.g7.G7SessionState

internal fun G7ProtocolState.toConnectionState(): G7ConnectionState =
    when (this) {
        G7ProtocolState.SCANNING -> G7ConnectionState.SCANNING
        G7ProtocolState.CONNECTING -> G7ConnectionState.CONNECTING
        G7ProtocolState.DISCOVERING, G7ProtocolState.DISCOVERING_SERVICES, G7ProtocolState.ENABLING_NOTIFICATIONS -> G7ConnectionState.DISCOVERING
        G7ProtocolState.AUTHENTICATION_START, G7ProtocolState.AUTHENTICATING, G7ProtocolState.AUTHENTICATED,
        G7ProtocolState.BONDING, G7ProtocolState.REQUESTING_GLUCOSE, G7ProtocolState.RECEIVING_GLUCOSE,
        -> G7ConnectionState.CONNECTED
        else -> G7ConnectionState.DISCONNECTED
    }

internal fun G7ProtocolState.toSessionState(): G7SessionState =
    when (this) {
        G7ProtocolState.AUTHENTICATED, G7ProtocolState.REQUESTING_GLUCOSE, G7ProtocolState.RECEIVING_GLUCOSE -> G7SessionState.ACTIVE
        G7ProtocolState.AUTHENTICATION_START, G7ProtocolState.AUTHENTICATING, G7ProtocolState.BONDING -> G7SessionState.AUTHENTICATING
        G7ProtocolState.WAITING_FOR_NEXT_READING -> G7SessionState.WAITING_FOR_NEXT_READING
        G7ProtocolState.RECOVERING, G7ProtocolState.ERROR -> G7SessionState.RECOVERING
        else -> G7SessionState.INITIAL_SETUP
    }

internal fun G7ProtocolState.label(): String =
    when (this) {
        G7ProtocolState.SCANNING -> "Sensor wird gesucht"
        G7ProtocolState.CONNECTING -> "Sensor wird verbunden"
        G7ProtocolState.DISCOVERING_SERVICES -> "G7-Dienste werden geprüft"
        G7ProtocolState.ENABLING_NOTIFICATIONS -> "G7-Datenkanäle werden geöffnet"
        G7ProtocolState.AUTHENTICATION_START, G7ProtocolState.AUTHENTICATING -> "Sensor wird authentifiziert"
        G7ProtocolState.BONDING -> "Sensor wird verbunden"
        G7ProtocolState.AUTHENTICATED -> "Sensor ist authentifiziert"
        G7ProtocolState.REQUESTING_GLUCOSE -> "Glukosewert wird angefordert"
        G7ProtocolState.RECEIVING_GLUCOSE -> "Glukosewert wird geprüft"
        G7ProtocolState.RECOVERING -> "Nächstes Sensorfenster wird abgewartet"
        else -> name.replace('_', ' ')
    }

internal fun G7ProtocolState.diagnosticCode(): String =
    when (this) {
        G7ProtocolState.SCANNING, G7ProtocolState.CONNECTING, G7ProtocolState.DISCOVERING,
        G7ProtocolState.DISCOVERING_SERVICES, G7ProtocolState.ENABLING_NOTIFICATIONS,
        -> "G7-BLE-110"
        G7ProtocolState.RECOVERING -> "G7-BLE-133"
        G7ProtocolState.AUTHENTICATION_START, G7ProtocolState.AUTHENTICATING, G7ProtocolState.BONDING,
        G7ProtocolState.AUTHENTICATED,
        -> "G7-AUTH-110"
        G7ProtocolState.REQUESTING_GLUCOSE, G7ProtocolState.RECEIVING_GLUCOSE,
        G7ProtocolState.WAITING_FOR_NEXT_READING,
        -> "G7-DATA-110"
        else -> "G7-STATE-100"
    }
