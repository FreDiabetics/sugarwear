package app.sugarlicious.mobile

import app.sugarlicious.model.CanonicalTrendPolicy
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.Trend
import app.sugarlicious.model.TrendRateProfile
import app.sugarlicious.model.ValueProvenance

/**
 * AndroidAPS is authoritative whenever its External Companion Apps broadcast contains a trend:
 * AAPS forwards lastBG.trendArrow directly. This resolver only supplies a fallback when AAPS
 * reports no usable arrow.
 */
internal object TrendArrowResolver {
    data class Resolution(
        val trend: Trend,
        val rateMgDlPerMinute: Double? = null,
        val provenance: ValueProvenance = ValueProvenance.UNAVAILABLE,
    )

    fun resolve(
        therapyBridgeTrend: Trend,
        current: GlucoseState,
        history: List<GlucoseSample>,
        cloudEndpointDirection: String? = null,
    ): Trend = resolveWithRate(therapyBridgeTrend, current, history, cloudEndpointDirection).trend

    fun resolveWithRate(
        therapyBridgeTrend: Trend,
        current: GlucoseState,
        history: List<GlucoseSample>,
        cloudEndpointDirection: String? = null,
    ): Resolution {
        current.deltaMgDl?.takeIf(Double::isFinite)?.let { delta ->
            return Resolution(
                trend = CanonicalTrendPolicy.fromFiveMinuteDelta(delta, TrendRateProfile.SUGARLICIOUS),
                rateMgDlPerMinute = delta / 5.0,
                provenance = ValueProvenance.DERIVED,
            )
        }
        if (therapyBridgeTrend != Trend.UNKNOWN) return Resolution(therapyBridgeTrend, provenance = ValueProvenance.SOURCE)

        CanonicalTrendPolicy.fromDirection(cloudEndpointDirection).takeUnless { it == Trend.UNKNOWN }?.let {
            return Resolution(it, provenance = ValueProvenance.SOURCE)
        }
        val currentSample =
            GlucoseSample(
                valueMgDl = current.valueMgDl,
                measuredAtEpochMs = current.measuredAtEpochMs,
                source = current.source,
                sensorId = current.sensorId,
                sessionId = current.sessionId,
                sequenceNumber = current.sequenceNumber,
                receivedAtEpochMs = current.receivedAtEpochMs,
                quality = current.quality,
            )
        val derived = CanonicalTrendPolicy.derive(currentSample, history, TrendRateProfile.SUGARLICIOUS)
        return Resolution(
            derived?.trend ?: Trend.UNKNOWN,
            derived?.rateMgDlPerMinute,
            if (derived == null) ValueProvenance.UNAVAILABLE else ValueProvenance.DERIVED,
        )
    }

    fun directionToTrend(direction: String?): Trend? = CanonicalTrendPolicy.fromDirection(direction).takeUnless { it == Trend.UNKNOWN }
}
