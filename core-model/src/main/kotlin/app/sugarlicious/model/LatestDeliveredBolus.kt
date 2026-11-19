package app.sugarlicious.model

/** A delivered amount with its actual source timestamp; never inferred from changing IOB. */
data class DeliveredBolus(
    val units: Double,
    val deliveredAtEpochMs: Long,
)

fun latestDeliveredBolus(state: TherapyDisplayState?, nowEpochMs: Long): DeliveredBolus? {
    if (state == null) return null
    val kinds = setOf(TherapyEventKind.SMB, TherapyEventKind.MANUAL_CORRECTION, TherapyEventKind.MEAL_BOLUS)
    return buildList {
        state.therapyEvents.filter { it.validated && it.kind in kinds }.forEach { add(DeliveredBolus(it.insulinUnits ?: it.amount, it.timestampEpochMs)) }
        state.therapyHistory.forEach { sample -> sample.smbUnits?.let { units -> sample.smbDeliveredAtEpochMs?.let { add(DeliveredBolus(units, it)) } } }
        state.loop?.let { loop ->
            val units = loop.smbUnits
            val timestamp = loop.smbAtEpochMs
            if (units != null && timestamp != null) add(DeliveredBolus(units, timestamp))
        }
    }.filter { it.units.isFinite() && it.units > 0.0 && it.deliveredAtEpochMs in 1..nowEpochMs }
        .maxWithOrNull(compareBy<DeliveredBolus> { it.deliveredAtEpochMs }.thenBy { it.units })
}
