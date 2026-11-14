package app.sugarlicious.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.mobile.ui.theme.SugarliciousRadius
import app.sugarlicious.mobile.ui.theme.SugarliciousSpacing
import app.sugarlicious.mobile.ui.theme.SugarliciousTheme
import app.sugarlicious.model.DiagnosticEvent
import app.sugarlicious.model.DiagnosticSeverity
import app.sugarlicious.storage.DiagnosticEventStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val store by lazy { DiagnosticEventStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SugarliciousColors.apply(SugarliciousColorStore.load(getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)))
        if (intent.getBooleanExtra(WEAR_TRANSFER_ONLY, false)) configureOneUiWidgetSettingsWindow(window)
        setContent {
            if (intent.getBooleanExtra(WEAR_TRANSFER_ONLY, false)) {
                OneUiWidgetSettingsTheme {
                    val events by store.events.collectAsState(initial = emptyList())
                    val transfers = wearTransferEvents(events)
                    WearTransferLogScreen(transfers, ::finish, { copyEvents(transfers) }, { shareEvents(transfers) })
                }
                return@setContent
            }
            SugarliciousTheme {
                val events by store.events.collectAsState(initial = emptyList())
                DiagnosticScreen(
                    events = events,
                    onBack = ::finish,
                    onRefreshWatch = ::refreshWatch,
                    onCopy = { copyEvents(events) },
                    onShare = { shareEvents(events) },
                    onExportBundle = { exportBundle(events) },
                    onClear = { scope.launch { store.clear() } },
                )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshWatch() {
        scope.launch {
            val nodes = runCatching { requestWatchDiagnostics(applicationContext) }.getOrDefault(0)
            Toast
                .makeText(
                    this@DiagnosticActivity,
                    if (nodes >
                        0
                    ) {
                        "Watch-Diagnose angefordert"
                    } else {
                        "Keine Watch erreichbar"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    private fun copyEvents(events: List<DiagnosticEvent>) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Sugarlicious Diagnose", formatDiagnosticEvents(events)))
        Toast.makeText(this, "Diagnose kopiert", Toast.LENGTH_SHORT).show()
    }

    private fun shareEvents(events: List<DiagnosticEvent>) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Sugarlicious Diagnose")
                    .putExtra(Intent.EXTRA_TEXT, formatDiagnosticEvents(events)),
                "Diagnose teilen",
            ),
        )
    }

    private fun exportBundle(events: List<DiagnosticEvent>) {
        scope.launch(Dispatchers.IO) {
            runCatching { DiagnosticBundleExporter.create(applicationContext, events) }
                .onSuccess { file ->
                    val uri = FileProvider.getUriForFile(applicationContext, "$packageName.files", file)
                    val share =
                        Intent(Intent.ACTION_SEND)
                            .setType("application/zip")
                            .putExtra(Intent.EXTRA_SUBJECT, "Sugarlicious Diagnosepaket")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runOnUiThread { startActivity(Intent.createChooser(share, "Diagnosepaket exportieren")) }
                }.onFailure { error ->
                    runOnUiThread {
                        Toast
                            .makeText(
                                this@DiagnosticActivity,
                                "Export fehlgeschlagen: ${error.message}",
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                }
        }
    }
}

@Composable
private fun DiagnosticScreen(
    events: List<DiagnosticEvent>,
    onBack: () -> Unit,
    onRefreshWatch: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onExportBundle: () -> Unit,
    onClear: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf<DiagnosticSeverity?>(null) }
    val filtered =
        events.filter { event ->
            (severity == null || event.severity == severity) &&
                (
                    query.isBlank() ||
                        listOf(event.code, event.module, event.message, event.origin).any { it.contains(query, ignoreCase = true) }
                )
        }

    Scaffold(containerColor = SugarliciousColors.Background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = SugarliciousSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(SugarliciousSpacing.Sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = SugarliciousSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(SugarliciousSpacing.Sm),
            ) {
                SugarliciousAction("‹", onBack, compact = true)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "SUGARLICIOUS",
                        color = SugarliciousColors.Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text("Diagnose", color = SugarliciousColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Code, Modul oder Meldung suchen") },
                singleLine = true,
                shape = RoundedCornerShape(SugarliciousRadius.Card),
                modifier = Modifier.fillMaxWidth(),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SugarliciousColors.TextPrimary,
                        unfocusedTextColor = SugarliciousColors.TextPrimary,
                        focusedContainerColor = SugarliciousColors.Surface,
                        unfocusedContainerColor = SugarliciousColors.Surface,
                        focusedBorderColor = SugarliciousColors.Primary,
                        unfocusedBorderColor = SugarliciousColors.Border,
                        focusedLabelColor = SugarliciousColors.Primary,
                        unfocusedLabelColor = SugarliciousColors.TextSecondary,
                        cursorColor = SugarliciousColors.Primary,
                    ),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SeverityChip("Alle", severity == null) { severity = null }
                SeverityChip("Warnungen", severity == DiagnosticSeverity.WARNING) { severity = DiagnosticSeverity.WARNING }
                SeverityChip("Fehler", severity == DiagnosticSeverity.ERROR) { severity = DiagnosticSeverity.ERROR }
            }

            Surface(
                color = SugarliciousColors.Surface,
                shape = RoundedCornerShape(SugarliciousRadius.Card),
                modifier = Modifier.fillMaxWidth().border(1.dp, SugarliciousColors.Border, RoundedCornerShape(SugarliciousRadius.Card)),
            ) {
                Column(
                    modifier = Modifier.padding(SugarliciousSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(SugarliciousSpacing.Sm),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(SugarliciousSpacing.Sm)) {
                        SugarliciousAction("Watch abrufen", onRefreshWatch)
                        SugarliciousAction("Kopieren", onCopy)
                        SugarliciousAction("Teilen", onShare)
                    }
                    SugarliciousAction("Diagnosepaket exportieren", onExportBundle)
                    Text(
                        "Diagnose löschen",
                        color = SugarliciousColors.Red,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onClear).padding(vertical = 5.dp),
                    )
                }
            }

            Text(
                "${filtered.size} von ${events.size} Ereignissen · maximal 7 Tage / 1000 Einträge",
                color = SugarliciousColors.TextSecondary,
                fontSize = 12.sp,
            )

            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(filtered, key = DiagnosticEvent::id) { event -> DiagnosticEventCard(event) }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun SeverityChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Bold) },
        shape = RoundedCornerShape(SugarliciousRadius.Pill),
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = SugarliciousColors.Surface,
                labelColor = SugarliciousColors.TextSecondary,
                selectedContainerColor = SugarliciousColors.SurfaceSelected,
                selectedLabelColor = SugarliciousColors.Primary,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = SugarliciousColors.Border,
                selectedBorderColor = SugarliciousColors.Primary,
            ),
    )
}

@Composable
private fun SugarliciousAction(
    label: String,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    Surface(
        color = SugarliciousColors.SurfaceHigh,
        shape = RoundedCornerShape(SugarliciousRadius.Pill),
        modifier =
            Modifier
                .border(1.dp, SugarliciousColors.Border, RoundedCornerShape(SugarliciousRadius.Pill))
                .clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = SugarliciousColors.TextPrimary,
            fontSize = if (compact) 20.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = if (compact) 13.dp else 12.dp, vertical = if (compact) 5.dp else 9.dp),
        )
    }
}

@Composable
private fun DiagnosticEventCard(event: DiagnosticEvent) {
    val accent =
        when (event.severity) {
            DiagnosticSeverity.ERROR -> SugarliciousColors.Red
            DiagnosticSeverity.WARNING -> SugarliciousColors.Yellow
            else -> SugarliciousColors.Primary
        }
    Surface(
        color = SugarliciousColors.Surface,
        shape = RoundedCornerShape(SugarliciousRadius.Card),
        modifier = Modifier.fillMaxWidth().border(1.dp, SugarliciousColors.Border, RoundedCornerShape(SugarliciousRadius.Card)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(event.code, color = SugarliciousColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(event.severity.name, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "${event.origin} · ${event.module} · ${diagnosticTime(event.occurredAtEpochMs)}",
                color = SugarliciousColors.TextSecondary,
                fontSize = 11.sp,
            )
            Text(event.message, color = SugarliciousColors.TextPrimary)
            if (event.metadata.isNotEmpty()) {
                Text(
                    event.metadata.entries.joinToString(" · ") { "${it.key}=${it.value}" },
                    color = SugarliciousColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

internal fun formatDiagnosticEvents(events: List<DiagnosticEvent>): String =
    events.joinToString("\n") { event ->
        buildString {
            append(diagnosticTime(event.occurredAtEpochMs))
            append(" | ${event.origin} | ${event.severity} | ${event.module} | ${event.code} | ${event.message}")
            if (event.metadata.isNotEmpty()) append(" | ${event.metadata.entries.joinToString(",") { "${it.key}=${it.value}" }}")
        }
    }

private fun diagnosticTime(timestamp: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
