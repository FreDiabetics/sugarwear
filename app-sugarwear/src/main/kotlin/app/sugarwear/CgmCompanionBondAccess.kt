package app.sugarwear

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build

internal data class CgmOwnedCompanionAssociation(
    val id: Int,
    val address: String?,
)

internal interface CgmCompanionAssociationBackend {
    fun ownedAssociations(): List<CgmOwnedCompanionAssociation>

    fun isBonded(address: String): Boolean

    fun associate(address: String, pending: (IntentSender) -> Unit, created: (String?) -> Unit, failed: () -> Unit)

    fun cancelPending()
}

@SuppressLint("MissingPermission")
private class AndroidCgmCompanionAssociationBackend(
    private val activity: Activity,
) : CgmCompanionAssociationBackend {
    private val manager get() = activity.getSystemService(CompanionDeviceManager::class.java)

    override fun ownedAssociations(): List<CgmOwnedCompanionAssociation> =
        manager?.myAssociations.orEmpty().map { CgmOwnedCompanionAssociation(it.id, it.deviceMacAddress?.toString()) }

    override fun isBonded(address: String): Boolean =
        activity
            .getSystemService(BluetoothManager::class.java)
            ?.adapter
            ?.getRemoteDevice(address)
            ?.bondState == BluetoothDevice.BOND_BONDED

    override fun associate(address: String, pending: (IntentSender) -> Unit, created: (String?) -> Unit, failed: () -> Unit) {
        check(activity.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP))
        val service = checkNotNull(manager)
        val request =
            AssociationRequest
                .Builder()
                .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
                .setSingleDevice(true)
                .build()
        service.associate(
            request,
            activity.mainExecutor,
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) = pending(intentSender)

                override fun onAssociationCreated(associationInfo: AssociationInfo) = created(associationInfo.deviceMacAddress?.toString())

                override fun onFailure(error: CharSequence?) = failed()
            },
        )
    }

    override fun cancelPending() {
        if (Build.VERSION.SDK_INT >= 34) {
            manager?.buildAssociationCancellationIntent()?.sendIntent(activity, 0, null, null, null)
        }
    }
}

/** System consent for an existing, exact bonded peer; never creates a Bluetooth bond or starts CGM. */
internal class CgmCompanionBondAccess(
    private val activity: Activity,
    private val backend: CgmCompanionAssociationBackend = AndroidCgmCompanionAssociationBackend(activity),
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val unlinkAddresses: () -> Set<String> = { cgmRecordedSensorAddresses(activity) },
    private val launchConsent: (IntentSender, Int) -> Unit = { sender, request ->
        activity.startIntentSenderForResult(sender, request, null, 0, 0, 0)
    },
) {
    private var generation = 0L
    private var active = false
    private var failure: ((String) -> Unit)? = null
    private var expectedAddress: String? = null
    private var activeRequestCode: Int? = null

    fun ensureForUnlink(onReady: () -> Unit, onFailure: (String) -> Unit) {
        // Android 16+ needs an app-owned association before removeBond can unpair
        // even a previously bonded sensor. Explicit disconnect may not bypass consent.
        ensure(onReady, onFailure) {
            val addresses = unlinkAddresses()
            // Never request consent for two identities just because stale metadata differs.
            require(addresses.size <= 1) { "Ambiguous G7 unlink target" }
            addresses.filter { backend.isBonded(it) }
        }
    }

    fun ensureForAuthenticatedSensor(address: String, onReady: () -> Unit, onFailure: (String) -> Unit) {
        // A completed G7 bond is sufficient for collection. Companion association is a
        // separate ownership capability used for removal; Pixel Wear OS rejects creating
        // such associations for third-party apps even after successful Bluetooth consent.
        val bonded = runCatching { address.isNotBlank() && backend.isBonded(address) }.getOrDefault(false)
        if (bonded) onReady() else onFailure("Bluetooth-Verbindung ist noch nicht abgeschlossen")
    }

    private fun ensure(onReady: () -> Unit, onFailure: (String) -> Unit, addresses: () -> List<String>) {
        if (active) {
            onFailure("Bluetooth-Freigabe läuft bereits")
            return
        }
        if (sdkInt < 36) {
            onReady()
            return
        }
        active = true
        failure = onFailure
        val token = ++generation
        activeRequestCode = REQUEST_ASSOCIATION + ((token - 1) % 1024).toInt()
        try {
            val peers = addresses().distinctBy { it.uppercase(java.util.Locale.ROOT) }
            next(token, peers, 0, onReady)
        } catch (_: Exception) {
            fail(token, "Bluetooth-Freigabe konnte nicht vorbereitet werden")
        }
    }

    private fun next(token: Long, addresses: List<String>, index: Int, onReady: () -> Unit) {
        if (!active || token != generation) return
        if (index == addresses.size) {
            active = false
            failure = null
            expectedAddress = null
            onReady()
            return
        }
        val address = addresses[index]
        if (!backend.isBonded(address) || backend.ownedAssociations().any { it.address.equals(address, ignoreCase = true) }) {
            next(token, addresses, index + 1, onReady)
            return
        }
        expectedAddress = address
        backend.associate(
            address,
            pending = { sender ->
                if (active && token == generation && expectedAddress == address) {
                    try {
                        launchConsent(sender, checkNotNull(activeRequestCode))
                    } catch (_: Exception) {
                        fail(token, "Android-Bluetooth-Freigabe konnte nicht geöffnet werden")
                    }
                }
            },
            created = { actual ->
                if (active && token == generation && expectedAddress == address) {
                    if (!address.equals(actual, ignoreCase = true)) {
                        fail(token, "Android hat eine andere Bluetooth-Zuordnung gemeldet")
                    } else {
                        expectedAddress = null
                        try {
                            next(token, addresses, index + 1, onReady)
                        } catch (_: Exception) {
                            fail(token, "Bluetooth-Freigabe konnte nicht abgeschlossen werden")
                        }
                    }
                }
            },
            failed = { if (expectedAddress == address) fail(token, "Android-Bluetooth-Freigabe wurde nicht erteilt") },
        )
    }

    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode !in REQUEST_ASSOCIATION until REQUEST_ASSOCIATION + 1024) return false
        if (!active || requestCode != activeRequestCode) return true
        if (resultCode != Activity.RESULT_OK) cancel()
        return true
    }

    fun cancel() {
        if (!active) return
        val token = generation
        // Invalidate callbacks before asking the system to dismiss pending consent.
        fail(token, "Bluetooth-Freigabe abgebrochen")
        runCatching { backend.cancelPending() }
    }

    private fun fail(token: Long, message: String) {
        if (!active || token != generation) return
        active = false
        expectedAddress = null
        ++generation
        val callback = failure
        failure = null
        callback?.invoke(message)
    }

    companion object {
        internal const val REQUEST_ASSOCIATION = 0x4737
    }
}

/** Only matching associations owned by this app are removed, after the real bond is gone. */
internal fun removeConfirmedCgmCompanionBond(
    address: String,
    associations: List<CgmOwnedCompanionAssociation>,
    requestRemoval: (Int) -> Boolean,
    confirmGone: () -> Boolean,
    disassociate: (Int) -> Unit,
    alreadyGone: Boolean = false,
): CgmBondRemovalStatus {
    val owned = associations.filter { it.address.equals(address, ignoreCase = true) }
    return try {
        if (!alreadyGone) {
            if (owned.isEmpty()) return CgmBondRemovalStatus.FAILED
            if (!requestRemoval(owned.first().id)) return CgmBondRemovalStatus.FAILED
            if (!confirmGone()) return CgmBondRemovalStatus.FAILED
        }
        owned.forEach { disassociate(it.id) }
        CgmBondRemovalStatus.REMOVED
    } catch (_: Exception) {
        CgmBondRemovalStatus.FAILED
    }
}

@SuppressLint("NewApi", "MissingPermission")
internal fun removeCgmBondThroughCompanion(context: Context, address: String, device: BluetoothDevice): CgmBondRemovalStatus {
    val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return CgmBondRemovalStatus.FAILED
    return removeConfirmedCgmCompanionBond(
        address,
        manager.myAssociations.map { CgmOwnedCompanionAssociation(it.id, it.deviceMacAddress?.toString()) },
        manager::removeBond,
        { awaitCgmBondRemoval(readBondState = { device.bondState }) },
        manager::disassociate,
        alreadyGone = device.bondState == BluetoothDevice.BOND_NONE,
    )
}
