package app.aapswear.g7watch

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import app.aapswear.g7.CollectorCycleClassification
import app.aapswear.g7.CollectorCycleTiming
import app.aapswear.g7.CollectorDiagnosticAttempt
import app.aapswear.g7.DirectConnectResult
import app.aapswear.g7.G7CollectorHealth
import app.aapswear.g7.G7FailureClass
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7Sensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class G7BlePolicyTest {
    @Test fun `interrupted initial bond searches across next sensor window within remaining budget`() {
        val sensor = G7Sensor("setup", deviceAddress = "AA:BB:CC:DD:EE:FF")
        val remaining = 12L * 60_000L
        assertEquals(remaining, g7DiscoveryScanTimeoutMs(sensor, remaining, pairingRecoveryRequired = true, fallbackUsed = false))
        assertEquals(20_000L, g7DiscoveryScanTimeoutMs(sensor, 20_000L, pairingRecoveryRequired = true, fallbackUsed = false))
    }

    @Test fun `normal known sensor scans and fallback remain short`() {
        val sensor = G7Sensor("established", deviceAddress = "AA:BB:CC:DD:EE:FF")
        assertEquals(G7_RECONNECT_SCAN_TIMEOUT_MS, g7DiscoveryScanTimeoutMs(sensor, 12L * 60_000L, false, false))
        assertEquals(G7_FALLBACK_SCAN_TIMEOUT_MS, g7DiscoveryScanTimeoutMs(sensor, null, false, true))
        assertEquals(G7_RECONNECT_SCAN_TIMEOUT_MS, g7DiscoveryScanTimeoutMs(sensor, null, false, false))
    }

    @Test fun `no callback remains the failure cause when the late fallback scan is empty`() {
        val failure = g7FailureClass("G7-BLE-FALLBACK-107", false, false, DirectConnectResult.NO_CALLBACK)
        assertEquals(G7FailureClass.DIRECT_NO_CALLBACK, failure)
        val health = G7CollectorHealth(consecutiveFailures = 1, lastFailureClass = failure)
        assertEquals(G7ReconnectStrategy.BOUNDED_SCAN, effectiveG7ReconnectStrategy(G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT, health))
    }

    @Test fun `fallback does not replace an authentication failure with a prior radio failure`() {
        assertEquals(G7FailureClass.AUTH_FAILURE, g7FailureClass("G7-AUTH-204", true, false, DirectConnectResult.NO_CALLBACK))
    }

    @Test fun `notification payload is copied before asynchronous delivery`() {
        val platformBuffer = byteArrayOf(1, 2, 3)

        val delivered = copyG7NotificationValue(platformBuffer)
        platformBuffer[0] = 9

        assertTrue(delivered.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test fun `registry permits exactly one active GATT generation`() {
        G7GattGenerationRegistry.resetForTest()
        val first = G7GattGenerationRegistry.acquire(1)
        val second = G7GattGenerationRegistry.acquire(2)
        assertFalse(G7GattGenerationRegistry.isActive(first))
        assertTrue(G7GattGenerationRegistry.isActive(second))
        assertTrue(second.generation > first.generation)
        G7GattGenerationRegistry.invalidate(second)
    }

    @Test fun `known candidate without first reading retains initial pairing deadline`() {
        val state =
            G7PersistedState(
                sensor = G7Sensor("new-sensor", deviceAddress = "AA:BB:CC:DD:EE:FF"),
            )

        assertEquals(
            G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS + 2L * 60_000L,
            collectorAttemptDeadlineMs(state),
        )
    }

    @Test fun `stored key is not replayed when Android bond is absent`() {
        val key = byteArrayOf(1, 2, 3)

        assertEquals(null, usableG7SharedKey(key, BluetoothDevice.BOND_NONE))
        assertTrue(usableG7SharedKey(key, BluetoothDevice.BOND_BONDING)!!.contentEquals(key))
        assertTrue(usableG7SharedKey(key, BluetoothDevice.BOND_BONDED)!!.contentEquals(key))
        assertTrue(usableG7SharedKey(key, null)!!.contentEquals(key))
        assertEquals(null, usableG7SharedKey(null, BluetoothDevice.BOND_NONE))
        assertTrue(shouldResumeG7Pairing(key, BluetoothDevice.BOND_NONE))
        assertFalse(shouldResumeG7Pairing(key, BluetoothDevice.BOND_BONDING))
        assertFalse(shouldResumeG7Pairing(key, BluetoothDevice.BOND_BONDED))
        assertFalse(shouldResumeG7Pairing(null, BluetoothDevice.BOND_NONE))
    }

    @Test fun `authenticated address anchors an addressless sensor change`() {
        val sensor = G7Sensor("new-sensor", sessionId = "new-session")

        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            restoreAuthenticatedG7Address(sensor, "AA:BB:CC:DD:EE:FF").deviceAddress,
        )
    }

    @Test fun `authenticated address never replaces an established sensor address`() {
        val sensor =
            G7Sensor(
                "active-sensor",
                sessionId = "active-session",
                deviceAddress = "11:22:33:44:55:66",
            )

        assertEquals(
            "11:22:33:44:55:66",
            restoreAuthenticatedG7Address(sensor, "AA:BB:CC:DD:EE:FF").deviceAddress,
        )
    }

    @Test fun `missing authenticated address leaves sensor discovery open`() {
        val sensor = G7Sensor("new-sensor", sessionId = "new-session")

        assertEquals(sensor, restoreAuthenticatedG7Address(sensor, null))
        assertEquals(sensor, restoreAuthenticatedG7Address(sensor, ""))
    }

    @Test fun `bonding waits through asynchronous initial bond none state`() {
        assertEquals(
            G7BondWaitDecision.KEEP_WAITING,
            g7BondWaitDecision(BluetoothDevice.BOND_NONE, observedBonding = false),
        )
        assertEquals(
            G7BondWaitDecision.KEEP_WAITING,
            g7BondWaitDecision(BluetoothDevice.BOND_BONDING, observedBonding = false),
        )
        assertEquals(
            G7BondWaitDecision.BONDED,
            g7BondWaitDecision(BluetoothDevice.BOND_BONDED, observedBonding = true),
        )
    }

    @Test fun `bonding none is terminal only after bonding was observed`() {
        assertEquals(
            G7BondWaitDecision.FAILED,
            g7BondWaitDecision(BluetoothDevice.BOND_NONE, observedBonding = true),
        )
    }

    @Test fun `direct connect callbacks retain actionable platform outcomes`() {
        assertEquals(
            DirectConnectResult.SUCCESS,
            classifyDirectConnectCallback(BluetoothGatt.GATT_SUCCESS, android.bluetooth.BluetoothProfile.STATE_CONNECTED),
        )
        assertEquals(
            DirectConnectResult.STATUS_133,
            classifyDirectConnectCallback(133, android.bluetooth.BluetoothProfile.STATE_DISCONNECTED),
        )
        assertEquals(
            DirectConnectResult.STATUS_19,
            classifyDirectConnectCallback(19, android.bluetooth.BluetoothProfile.STATE_DISCONNECTED),
        )
        assertEquals(
            DirectConnectResult.DISCONNECTED_EARLY,
            classifyDirectConnectCallback(BluetoothGatt.GATT_SUCCESS, android.bluetooth.BluetoothProfile.STATE_DISCONNECTED),
        )
        assertEquals(
            DirectConnectResult.OTHER_STATUS,
            classifyDirectConnectCallback(8, android.bluetooth.BluetoothProfile.STATE_DISCONNECTED),
        )
    }

    @Test fun `radio degraded mode starts only with third consecutive complete fallback miss`() {
        val attempts =
            listOf(
                CollectorDiagnosticAttempt(3, 3, classification = CollectorCycleClassification.FALLBACK_SCAN_FAILED),
                CollectorDiagnosticAttempt(2, 2, classification = CollectorCycleClassification.FALLBACK_SCAN_FAILED),
                CollectorDiagnosticAttempt(1, 1, classification = CollectorCycleClassification.SUCCESS_FRESH),
            )
        assertEquals(2, consecutiveRadioFailures(attempts, 4))
        assertEquals(RADIO_DEGRADED_CLUSTER_THRESHOLD, 1 + consecutiveRadioFailures(attempts, 4))
        assertEquals(0, consecutiveRadioFailures(attempts, 2))
    }

    @Test fun `confirmed no callback radio cluster requests one bounded runtime recycle`() {
        val cycle =
            CollectorCycleTiming(
                directConnectResult = DirectConnectResult.NO_CALLBACK,
                fallbackScanUsed = true,
                scanTotalResults = 0,
            )

        assertTrue(
            shouldRecycleG7Runtime(
                cycle = cycle,
                radioFailureStreak = RADIO_DEGRADED_CLUSTER_THRESHOLD,
                lastRecycleAtEpochMs = null,
                nowEpochMs = 2_000_000L,
            ),
        )
        assertFalse(
            shouldRecycleG7Runtime(
                cycle = cycle,
                radioFailureStreak = RADIO_DEGRADED_CLUSTER_THRESHOLD + 1,
                lastRecycleAtEpochMs = 1_999_000L,
                nowEpochMs = 2_000_000L,
            ),
        )
    }

    @Test fun `runtime recycle is not used for an ordinary sensor miss`() {
        assertFalse(
            shouldRecycleG7Runtime(
                cycle =
                    CollectorCycleTiming(
                        directConnectResult = DirectConnectResult.DEVICE_UNAVAILABLE,
                        fallbackScanUsed = true,
                        scanTotalResults = 0,
                    ),
                radioFailureStreak = RADIO_DEGRADED_CLUSTER_THRESHOLD,
                lastRecycleAtEpochMs = null,
                nowEpochMs = 2_000_000L,
            ),
        )
    }

    @Test fun `unrelated scan traffic does not suppress recycle when paired sensor is absent`() {
        val cycle =
            CollectorCycleTiming(
                directConnectResult = DirectConnectResult.NO_CALLBACK,
                fallbackScanUsed = true,
                scanTotalResults = 755,
                scanExactAddressResults = 0,
            )

        assertTrue(
            shouldRecycleG7Runtime(
                cycle = cycle,
                radioFailureStreak = RADIO_DEGRADED_CLUSTER_THRESHOLD,
                lastRecycleAtEpochMs = null,
                nowEpochMs = 2_000_000L,
            ),
        )
    }

    @Test fun `no callback streak uses durable health instead of alternating attempt shapes`() {
        val health =
            G7CollectorHealth(
                consecutiveFailures = 8,
                lastFailureClass = G7FailureClass.DIRECT_NO_CALLBACK,
            )

        assertEquals(9, nextRadioFailureStreak(health, G7FailureClass.SCAN_RADIO_FAILURE))
    }

    @Test fun `non radio failure starts a new radio streak`() {
        val health =
            G7CollectorHealth(
                consecutiveFailures = 8,
                lastFailureClass = G7FailureClass.AUTH_FAILURE,
            )

        assertEquals(1, nextRadioFailureStreak(health, G7FailureClass.DIRECT_NO_CALLBACK))
    }

    @Test fun `confirmed fallback sensor permits one final bounded status 133 retry`() {
        assertEquals(1, maxGatt133RetriesForCycle(fallbackSensorConfirmed = false))
        assertEquals(2, maxGatt133RetriesForCycle(fallbackSensorConfirmed = true))
    }

    @Test fun `gatt failure after fallback discovery remains part of radio failure cluster`() {
        val attempts =
            listOf(
                CollectorDiagnosticAttempt(
                    attemptId = 3,
                    startedAtEpochMs = 3,
                    classification = CollectorCycleClassification.GATT_CONNECT_FAILED,
                    cycle = CollectorCycleTiming(fallbackScanUsed = true),
                ),
                CollectorDiagnosticAttempt(
                    attemptId = 2,
                    startedAtEpochMs = 2,
                    classification = CollectorCycleClassification.FALLBACK_SCAN_FAILED,
                ),
            )

        assertEquals(2, consecutiveRadioFailures(attempts, currentAttemptId = 4))
    }

    @Test fun `completed scan only miss remains part of degraded radio cluster`() {
        assertTrue(
            isCompleteRadioFailure(
                classification = CollectorCycleClassification.NO_ADVERTISEMENT,
                cycle =
                    CollectorCycleTiming(
                        scanStartedAt = 1_000L,
                        scanEndedAt = 61_000L,
                        scanExactAddressResults = 0,
                    ),
            ),
        )
    }

    @Test fun `initial pairing keeps scanning for up to thirty minutes`() {
        assertEquals(
            G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS,
            g7ScanTimeoutMs(G7Sensor("new-sensor")),
        )
    }

    @Test fun `known sensor reconnect uses shorter targeted scan`() {
        assertEquals(60_000L, G7_RECONNECT_SCAN_TIMEOUT_MS)
        assertEquals(
            G7_RECONNECT_SCAN_TIMEOUT_MS,
            g7ScanTimeoutMs(G7Sensor("known-sensor", deviceAddress = "AA:BB:CC:DD:EE:FF")),
        )
    }

    @Test fun `known address direct reconnect is default fast path`() {
        assertTrue(shouldUseDirectReconnect(G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT, "AA:BB:CC:DD:EE:FF"))
        assertFalse(shouldUseDirectReconnect(G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT, null))
        assertFalse(shouldUseDirectReconnect(G7ReconnectStrategy.BOUNDED_SCAN, "AA:BB:CC:DD:EE:FF"))
        assertEquals(15_000L, G7_FALLBACK_SCAN_TIMEOUT_MS)
    }

    @Test fun `recoverable direct timeout enters fallback discovery only once`() {
        assertTrue(
            shouldUseFallbackDiscovery(
                G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT,
                "AA:BB:CC:DD:EE:FF",
                fallbackUsed = false,
                recoverable = true,
            ),
        )
        assertFalse(
            shouldUseFallbackDiscovery(
                G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT,
                "AA:BB:CC:DD:EE:FF",
                fallbackUsed = true,
                recoverable = true,
            ),
        )
        assertFalse(
            shouldUseFallbackDiscovery(
                G7ReconnectStrategy.BOUNDED_SCAN,
                "AA:BB:CC:DD:EE:FF",
                fallbackUsed = false,
                recoverable = true,
            ),
        )
    }

    @Test fun `connect callback timeout enters fallback without wasting another sensor window`() {
        assertFalse(shouldRetryNoCallbackDirectly(G7_DIRECT_CONNECT_TIMEOUT_ERROR_CODE, 0, fallbackUsed = false))
        assertFalse(shouldRetryNoCallbackDirectly(G7_DIRECT_CONNECT_TIMEOUT_ERROR_CODE, 1, fallbackUsed = false))
        assertFalse(shouldRetryNoCallbackDirectly(G7_DIRECT_CONNECT_TIMEOUT_ERROR_CODE, 0, fallbackUsed = true))
        assertFalse(shouldRetryNoCallbackDirectly(G7_GATT_133_ERROR_CODE, 0, fallbackUsed = false))
    }

    @Test fun `operation callback timeout still receives one clean direct retry`() {
        assertTrue(shouldRetryNoCallbackDirectly(G7_DISCOVERY_CALLBACK_TIMEOUT_ERROR_CODE, 0, fallbackUsed = false))
        assertFalse(shouldRetryNoCallbackDirectly(G7_DISCOVERY_CALLBACK_TIMEOUT_ERROR_CODE, 1, fallbackUsed = false))
    }

    @Test fun `repeated radio failures temporarily switch known sensor to bounded scan`() {
        val degraded =
            G7CollectorHealth(
                consecutiveFailures = RADIO_DEGRADED_CLUSTER_THRESHOLD,
                lastFailureClass = G7FailureClass.DIRECT_NO_CALLBACK,
            )

        assertEquals(
            G7ReconnectStrategy.BOUNDED_SCAN,
            effectiveG7ReconnectStrategy(G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT, degraded),
        )
    }

    @Test fun `known sensor gets a direct probe after three degraded scan windows`() {
        val strategies =
            (RADIO_DEGRADED_CLUSTER_THRESHOLD..RADIO_DEGRADED_CLUSTER_THRESHOLD + 4).map { failures ->
                effectiveG7ReconnectStrategy(
                    G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT,
                    G7CollectorHealth(
                        consecutiveFailures = failures,
                        lastFailureClass = G7FailureClass.SENSOR_NOT_ADVERTISING,
                    ),
                )
            }

        assertEquals(
            listOf(
                G7ReconnectStrategy.BOUNDED_SCAN,
                G7ReconnectStrategy.BOUNDED_SCAN,
                G7ReconnectStrategy.BOUNDED_SCAN,
                G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT,
                G7ReconnectStrategy.BOUNDED_SCAN,
            ),
            strategies,
        )
    }

    @Test fun `degraded known sensor scan miss can recycle runtime without a direct callback`() {
        val scanOnlyMiss =
            CollectorCycleTiming(
                scanStartedAt = 1_000L,
                scanEndedAt = 61_000L,
                scanTotalResults = 755,
                scanExactAddressResults = 0,
                radioDegradedCluster = true,
            )

        assertTrue(
            shouldRecycleG7Runtime(
                cycle = scanOnlyMiss,
                radioFailureStreak = RADIO_DEGRADED_CLUSTER_THRESHOLD,
                knownSensorAddress = true,
                lastRecycleAtEpochMs = null,
                nowEpochMs = 2_000_000L,
            ),
        )
        assertFalse(
            shouldRecycleG7Runtime(
                cycle = scanOnlyMiss.copy(radioDegradedCluster = false),
                radioFailureStreak = RADIO_DEGRADED_CLUSTER_THRESHOLD,
                knownSensorAddress = false,
                lastRecycleAtEpochMs = null,
                nowEpochMs = 2_000_000L,
            ),
        )
    }

    @Test fun `status 133 cluster can recycle runtime after paired sensor fallback miss`() {
        val status133Miss =
            CollectorCycleTiming(
                directConnectResult = DirectConnectResult.STATUS_133,
                fallbackScanUsed = true,
                scanEndedAt = 16_000L,
                scanExactAddressResults = 0,
            )

        assertTrue(
            shouldRecycleG7Runtime(
                cycle = status133Miss,
                radioFailureStreak = RADIO_DEGRADED_CLUSTER_THRESHOLD,
                lastRecycleAtEpochMs = null,
                nowEpochMs = 2_000_000L,
            ),
        )
    }

    @Test fun `healthy and non radio failures retain configured direct strategy`() {
        assertEquals(
            G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT,
            effectiveG7ReconnectStrategy(G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT, G7CollectorHealth()),
        )
        assertEquals(
            G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT,
            effectiveG7ReconnectStrategy(
                G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT,
                G7CollectorHealth(
                    consecutiveFailures = RADIO_DEGRADED_CLUSTER_THRESHOLD,
                    lastFailureClass = G7FailureClass.AUTH_FAILURE,
                ),
            ),
        )
    }

    @Test fun `explicit bounded scan strategy is never overridden`() {
        assertEquals(
            G7ReconnectStrategy.BOUNDED_SCAN,
            effectiveG7ReconnectStrategy(G7ReconnectStrategy.BOUNDED_SCAN, G7CollectorHealth()),
        )
    }

    @Test fun `known sensor address rejects a different nearby G7`() {
        assertFalse(knownG7AddressMatches("AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66")!!)
        assertTrue(knownG7AddressMatches("AA:BB:CC:DD:EE:FF", "aa:bb:cc:dd:ee:ff")!!)
    }

    @Test fun `missing known address leaves initial candidate selection open`() {
        assertEquals(null, knownG7AddressMatches(null, "11:22:33:44:55:66"))
    }

    @Test fun `non connectable advertisement is never used for GATT`() {
        assertFalse(isConnectableG7Advertisement(false))
        assertTrue(isConnectableG7Advertisement(true))
    }

    @Test fun `successful stale write callback is ignored instead of rejected`() {
        val expected = UUID.fromString("f8083535-849e-531c-c594-30f1f86a4ea5")
        val stale = UUID.fromString("f8083534-849e-531c-c594-30f1f86a4ea5")

        assertEquals(
            G7WriteCallbackDisposition.STALE_SUCCESS,
            classifyG7WriteCallback(expected, stale, BluetoothGatt.GATT_SUCCESS),
        )
    }

    @Test fun `successful callback for expected characteristic completes write`() {
        val expected = UUID.fromString("f8083535-849e-531c-c594-30f1f86a4ea5")

        assertEquals(
            G7WriteCallbackDisposition.EXPECTED_SUCCESS,
            classifyG7WriteCallback(expected, expected, BluetoothGatt.GATT_SUCCESS),
        )
    }

    @Test fun `failed callback for expected characteristic is rejected`() {
        val expected = UUID.fromString("f8083535-849e-531c-c594-30f1f86a4ea5")

        assertEquals(
            G7WriteCallbackDisposition.EXPECTED_FAILURE,
            classifyG7WriteCallback(expected, expected, BluetoothGatt.GATT_FAILURE),
        )
    }

    @Test fun `failed callback for stale characteristic cannot fail current write`() {
        val expected = UUID.fromString("f8083535-849e-531c-c594-30f1f86a4ea5")
        val stale = UUID.fromString("f8083534-849e-531c-c594-30f1f86a4ea5")

        assertFalse(
            shouldFailCurrentG7Write(
                classifyG7WriteCallback(expected, stale, BluetoothGatt.GATT_FAILURE),
            ),
        )
        assertTrue(
            shouldFailCurrentG7Write(
                classifyG7WriteCallback(expected, expected, BluetoothGatt.GATT_FAILURE),
            ),
        )
    }
}
