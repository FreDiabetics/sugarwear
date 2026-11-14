package app.sugarlicious.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.model.TherapyDisplayFormatter
import app.sugarlicious.model.TherapyDisplayState
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SoftMotionGlucoseDetails(state: TherapyDisplayState?, now: Long, value: String, delta: String, unit: String, onDismiss: () -> Unit) {
    val known = TherapyDisplayFormatter.isGlucoseKnown(state)
    val glucose = state?.glucose
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = SugarliciousColors.Surface,
        title = { Text("Glukosedetails", color = SugarliciousColors.TextPrimary) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val rows =
                    listOf(
                        "Glukose" to "$value $unit",
                        "Delta" to "$delta $unit",
                        "Trend" to (glucose?.trend?.let(TherapyDisplayFormatter::trendArrow) ?: "\u2014"),
                        "Messzeitpunkt" to (glucose?.measuredAtEpochMs?.takeIf { known }?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it)) } ?: "\u2014"),
                        "Datenquelle" to TherapyDisplayFormatter.sourceName(glucose?.source),
                        "Status" to TherapyDisplayFormatter.freshnessLabel(TherapyDisplayFormatter.freshness(state, now)),
                    )
                rows.forEach { (label, content) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, Modifier.weight(1f), color = SugarliciousColors.TextSecondary)
                        Text(content, Modifier.weight(1.2f), color = SugarliciousColors.TextPrimary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schlie\u00dfen", color = SugarliciousColors.BrandGreen) } },
    )
}

internal data class SoftMotionHeaderStatus(
    val freshness: app.sugarlicious.model.Freshness,
    val hasValue: Boolean,
    val waveActive: Boolean,
    val label: String?,
)

internal fun softMotionHeaderStatus(state: TherapyDisplayState?, now: Long): SoftMotionHeaderStatus {
    val freshness = TherapyDisplayFormatter.freshness(state, now)
    return SoftMotionHeaderStatus(
        freshness,
        TherapyDisplayFormatter.isGlucoseKnown(state) && freshness !in setOf(app.sugarlicious.model.Freshness.NO_DATA, app.sugarlicious.model.Freshness.ERROR),
        freshness == app.sugarlicious.model.Freshness.CURRENT || freshness == app.sugarlicious.model.Freshness.DELAYED,
        freshness.takeUnless { it == app.sugarlicious.model.Freshness.CURRENT }?.let(TherapyDisplayFormatter::freshnessLabel),
    )
}
