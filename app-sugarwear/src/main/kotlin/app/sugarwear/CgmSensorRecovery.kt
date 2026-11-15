package app.sugarwear

internal data class CgmBondCandidate(
    val address: String,
    val advertisedName: String?,
    val isBonded: Boolean,
    val companionAssociationId: Int? = null,
)

internal sealed interface CgmRecoveryState {
    data object NotRequired : CgmRecoveryState
    data object BluetoothUnavailable : CgmRecoveryState
    data object PermissionRequired : CgmRecoveryState
    data object NoCandidate : CgmRecoveryState
    data class CandidateSelectionRequired(
        val candidates: List<CgmBondCandidate>,
    ) : CgmRecoveryState

    data class ExistingBondDetected(
        val candidate: CgmBondCandidate,
    ) : CgmRecoveryState

    data class ReauthenticationRequired(
        val candidate: CgmBondCandidate,
    ) : CgmRecoveryState

    data class BondRepairRequired(
        val candidate: CgmBondCandidate,
        val reason: String,
    ) : CgmRecoveryState

    data class Failure(
        val reason: String,
    ) : CgmRecoveryState
}

internal object CgmSensorRecoveryPlanner {
    fun determine(
        bluetoothAvailable: Boolean,
        permissionGranted: Boolean,
        recordedAddress: String?,
        candidates: List<CgmBondCandidate>,
    ): CgmRecoveryState {
        if (!bluetoothAvailable) return CgmRecoveryState.BluetoothUnavailable
        if (!permissionGranted) return CgmRecoveryState.PermissionRequired
        if (candidates.isEmpty()) return CgmRecoveryState.NoCandidate

        val recordedMatch = recordedAddress?.let { addr ->
            candidates.firstOrNull { it.address.equals(addr, ignoreCase = true) }
        }

        if (recordedMatch != null) {
            return if (recordedMatch.isBonded) {
                CgmRecoveryState.ExistingBondDetected(recordedMatch)
            } else {
                CgmRecoveryState.ReauthenticationRequired(recordedMatch)
            }
        }

        val bondedCandidates = candidates.filter { it.isBonded }
        return when {
            bondedCandidates.size == 1 -> CgmRecoveryState.ExistingBondDetected(bondedCandidates.first())
            candidates.size == 1 -> CgmRecoveryState.ReauthenticationRequired(candidates.first())
            candidates.size > 1 -> CgmRecoveryState.CandidateSelectionRequired(candidates)
            else -> CgmRecoveryState.NoCandidate
        }
    }
}
