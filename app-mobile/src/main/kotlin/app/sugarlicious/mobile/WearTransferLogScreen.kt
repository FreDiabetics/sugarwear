package app.sugarlicious.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sugarlicious.model.DiagnosticEvent
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun WearTransferLogScreen(
    events: List<DiagnosticEvent>,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var query by remember { mutableStateOf("") }
    val scroll = rememberLazyListState()
    val headerClearance = measuredSettingsCompactTitle("Mobile → Wear", settingsWindowWidthDp()).heightDp + SETTINGS_SUBMENU_CARD_GAP_DP
    val filtered =
        events.filter { event ->
            query.isBlank() || (listOf(event.code, event.message) + event.metadata.values).any { it.contains(query, ignoreCase = true) }
        }
    OneUiWidgetSettingsSurface(onBack = onBack, lazyScroll = scroll, title = "Mobile → Wear") { _ ->
        val expansion = LocalWidgetHeaderExpansionDp.current
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = (headerClearance + expansion).dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Kategorie, Pfad oder Ergebnis suchen", color = OneUiWidgetStyle.primary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        TextFieldDefaults.colors(
                            focusedTextColor = OneUiWidgetStyle.primary,
                            unfocusedTextColor = OneUiWidgetStyle.primary,
                            focusedContainerColor = OneUiWidgetStyle.card,
                            unfocusedContainerColor = OneUiWidgetStyle.card,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = OneUiWidgetStyle.accent,
                        ),
                )
            }
            item {
                Row {
                    TextButton(onClick = onCopy) { Text("Kopieren", color = OneUiWidgetStyle.primary) }
                    TextButton(onClick = onShare) { Text("Teilen", color = OneUiWidgetStyle.primary) }
                }
                Text(
                    "${filtered.size} Einträge · neueste 300. Gemeinsamer Diagnosespeicher: maximal 7 Tage / 1000 Einträge. API-Annahme bestätigt keinen Empfang auf Wear. Protokollierung erfolgt im Hintergrund; bei voller Warteschlange oder Prozessende können Einträge fehlen.",
                    color = OneUiWidgetStyle.secondary,
                    fontSize = 12.sp,
                )
            }
            items(filtered, key = DiagnosticEvent::id) { event ->
                Surface(color = OneUiWidgetStyle.card, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(event.metadata["category"].orEmpty(), color = OneUiWidgetStyle.accent, fontWeight = FontWeight.Bold)
                        Text(SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS", locale).format(Date(event.occurredAtEpochMs)), color = OneUiWidgetStyle.secondary, fontSize = 12.sp)
                        Text(event.message, color = OneUiWidgetStyle.primary)
                        Text(event.metadata.entries.joinToString(" · ") { "${it.key}=${it.value}" }, color = OneUiWidgetStyle.primary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
