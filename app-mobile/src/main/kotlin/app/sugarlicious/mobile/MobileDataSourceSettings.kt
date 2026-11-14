package app.sugarlicious.mobile

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sugarlicious.datasource.manufacturer.ManufacturerBroadcastAdapter

/** Embedded in General, sharing its scroll and header instead of launching another window. */
@Composable internal fun MobileDataSourceSettingsPanel(settings: MobileSettingsViewModel, model: MobileSourceSettingsViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(model, lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, _ ->
                model.setVisible(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
            }
        lifecycle.addObserver(observer)
        model.setVisible(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        onDispose {
            lifecycle.removeObserver(observer)
            model.setVisible(false)
        }
    }
    val operationError by model.operationError.collectAsStateWithLifecycle()
    val snapshot by model.state.collectAsStateWithLifecycle()
    val page by settings.uiState.collectAsStateWithLifecycle()
    val configuration = snapshot.configuration
    val status = snapshot.shareStatus
    val cloudEndpointStatus = snapshot.cloudEndpointStatus
    val expandedCards = page.expandedSources
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) model.setManufacturer(MobileGlucoseSource.DEXCOM_BROADCAST, true)
        }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val error = snapshot.observationError.ifBlank { operationError }
        if (error.isNotBlank()) Text(error, color = OneUiWidgetStyle.secondary, fontSize = 14.sp)
        if (!snapshot.loaded) return@Column
        if (configuration.enabledGlucoseSources.size > 1) {
            Surface(color = OneUiWidgetStyle.card, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(app.sugarlicious.uishared.R.drawable.ic_alert), "Warnung", tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(28.dp))
                    Text("Mehrere Glukosequellen aktiv: " + configuration.enabledGlucoseSources.sortedBy { it.ordinal }.joinToString { it.label } + ". Verwendet wird der frischeste gültige Messwert.", color = OneUiWidgetStyle.primary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        val therapyBridgeActive = MobileGlucoseSource.ANDROID_APS in configuration.enabledGlucoseSources || configuration.therapyBridgeTherapyEnabled || configuration.therapyBridgePredictionsEnabled
        SourceCard("AndroidAPS", therapyBridgeActive, { model.setTherapyBridge(it) }, expandedCards, settings::expandSource) {
            WidgetToggleSetting("Glukosewerte", MobileGlucoseSource.ANDROID_APS in configuration.enabledGlucoseSources) { model.setSource(MobileGlucoseSource.ANDROID_APS, it) }
            WidgetToggleSetting("Vorhersagen", configuration.therapyBridgePredictionsEnabled) { model.setPredictions(it) }
            WidgetToggleSetting("Therapiedaten", configuration.therapyBridgeTherapyEnabled) { model.setTherapy(it) }
        }
        val manufacturer = snapshot.preferredManufacturer
        val manufacturerActive = configuration.enabledGlucoseSources.any { it == MobileGlucoseSource.DEXCOM_BROADCAST || it == MobileGlucoseSource.DEXCOM_SHARE }

        fun selectManufacturer(source: MobileGlucoseSource, enabled: Boolean) {
            if (enabled && source == MobileGlucoseSource.DEXCOM_BROADCAST && ContextCompat.checkSelfPermission(context, ManufacturerBroadcastAdapter.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                permission.launch(ManufacturerBroadcastAdapter.PERMISSION)
            } else {
                model.setManufacturer(source, enabled)
            }
        }
        SourceCard("BYODA", manufacturerActive, { selectManufacturer(manufacturer, it) }, expandedCards, settings::expandSource) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(MobileGlucoseSource.DEXCOM_BROADCAST, MobileGlucoseSource.DEXCOM_SHARE).forEach { source ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        SourceBrandIcon(36.dp)
                        Text(source.label, color = OneUiWidgetStyle.primary, fontSize = 14.sp)
                        RadioButton(selected = manufacturer == source, onClick = { selectManufacturer(source, true) })
                    }
                }
            }
            if (manufacturer == MobileGlucoseSource.DEXCOM_SHARE) ShareCredentialsFields(status, model)
        }
        val cloudEndpoint = snapshot.cloudEndpoint
        val cloudEndpointGlucose = MobileGlucoseSource.NIGHTSCOUT in configuration.enabledGlucoseSources
        SourceCard("Nightscout", cloudEndpointGlucose || cloudEndpoint.enabled, { model.setCloudEndpoint(it) }, expandedCards, settings::expandSource) {
            WidgetToggleSetting("Glukosewerte", cloudEndpointGlucose) { model.setSource(MobileGlucoseSource.NIGHTSCOUT, it) }
            WidgetToggleSetting("Therapiedaten", cloudEndpoint.enabled, enabled = cloudEndpoint.isValid() || cloudEndpoint.enabled) { model.setCloudEndpointTherapy(it) }
            Text("Nightscout ergänzt Boli und Kohlenhydrate. IOB, COB und Basal liefert weiterhin AndroidAPS.", color = OneUiWidgetStyle.secondary, fontSize = 13.sp)
            CloudEndpointAccessFields(if (cloudEndpointGlucose) cloudEndpointStatus else "", model)
        }
        SourceCard("xDrip+", MobileGlucoseSource.XDRIP_PLUS in configuration.enabledGlucoseSources, { model.setSource(MobileGlucoseSource.XDRIP_PLUS, it) }, expandedCards, settings::expandSource) {
            Text("In xDrip+ unter Inter-App-Einstellungen »Lokal senden« und »Kompatibler Broadcast« aktivieren. Als Empfänger app.sugarlicious eintragen oder das Empfängerfeld leer lassen.", color = OneUiWidgetStyle.secondary, fontSize = 14.sp)
        }
        SourceCard("Libre patched app", MobileGlucoseSource.LIBRE_PATCHED in configuration.enabledGlucoseSources, { model.setSource(MobileGlucoseSource.LIBRE_PATCHED, it) }, expandedCards, settings::expandSource) {
            Text("Empfängt berechnete Scanwerte und den dabei mitgelieferten Verlauf der gepatchten Libre-App. Für kontinuierliche, aufbereitete Werte xDrip+ verwenden; ungeglättete Live-Rohwerte werden nicht übernommen.", color = OneUiWidgetStyle.secondary, fontSize = 14.sp)
        }
    }
}

@Composable private fun SourceBrandIcon(size: androidx.compose.ui.unit.Dp) {
    // The canonical foreground includes adaptive-icon insets; compensate visually,
    // keeping a normal settings-icon layout box and the original vector artwork.
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        SugarliciousIcon(
            R.drawable.ic_foreground,
            null,
            tint = OneUiWidgetStyle.primary,
            colored = false,
            modifier = Modifier.requiredSize(size * (60f / 28f)),
        )
    }
}

@Composable private fun SourceCard(title: String, active: Boolean, onActive: (Boolean) -> Unit, expandedCards: Map<String, Boolean>, onExpanded: (String, Boolean) -> Unit, content: (@Composable () -> Unit)? = null) {
    val expanded = expandedCards[title] == true
    val nameInteraction = remember { MutableInteractionSource() }
    val namePressed by nameInteraction.collectIsPressedAsState()
    Surface(color = OneUiWidgetStyle.card, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                SourceBrandIcon(28.dp)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    Text(title, color = OneUiWidgetStyle.primary, fontSize = 16.sp, modifier = Modifier.background(if (namePressed) OneUiWidgetStyle.primary.copy(alpha = .10f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(4.dp)).semantics { stateDescription = if (expanded) "Geöffnet" else "Geschlossen" }.clickable(interactionSource = nameInteraction, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = if (expanded) "Zuklappen" else "Aufklappen") { onExpanded(title, !expanded) })
                }
                WidgetSettingsToggle(active, {
                    onExpanded(title, it)
                    onActive(it)
                }, true, title)
            }
            AnimatedVisibility(expanded && content != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = OneUiWidgetStyle.secondary.copy(alpha = .16f))
                    content?.invoke()
                }
            }
        }
    }
}

@Composable private fun ShareCredentialsFields(status: String, model: MobileSourceSettingsViewModel) {
    val snapshot by model.state.collectAsStateWithLifecycle()
    val draft by model.share.collectAsStateWithLifecycle()
    val saved = snapshot.share
    val account = draft.account
    val password = draft.password
    val region = draft.region
    val feedback = draft.feedback
    val editing = draft.editing
    if (!editing) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dexcom-Konto", color = OneUiWidgetStyle.primary, fontSize = 16.sp)
                Text(saved?.account.orEmpty(), color = OneUiWidgetStyle.secondary, fontSize = 14.sp)
            }
            TextButton(onClick = model::editShare) { Text("Ändern") }
        }
        Text(if (saved?.region == "us") "USA" else "International", color = OneUiWidgetStyle.secondary, fontSize = 14.sp)
        if (status.isNotBlank()) Text(status, color = OneUiWidgetStyle.secondary, fontSize = 13.sp)
        return
    }
    val fieldColor = OneUiWidgetStyle.secondary.copy(alpha = .08f)
    val colors =
        TextFieldDefaults.colors(
            focusedContainerColor = fieldColor,
            unfocusedContainerColor = fieldColor,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedTextColor = OneUiWidgetStyle.primary,
            unfocusedTextColor = OneUiWidgetStyle.primary,
            focusedLabelColor = OneUiWidgetStyle.secondary,
            unfocusedLabelColor = OneUiWidgetStyle.secondary,
        )
    TextField(
        account,
        { model.updateShare(account = it) },
        label = { Text("Benutzername", fontSize = 13.sp) },
        textStyle =
            androidx.compose.ui.text
                .TextStyle(fontSize = 16.sp),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        colors = colors,
        modifier = Modifier.fillMaxWidth(),
    )
    TextField(
        password,
        { model.updateShare(password = it) },
        label = { Text(if (saved == null) "Passwort" else "Passwort gespeichert", fontSize = 13.sp) },
        textStyle =
            androidx.compose.ui.text
                .TextStyle(fontSize = 16.sp),
        shape = RoundedCornerShape(16.dp),
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        colors = colors,
        modifier = Modifier.fillMaxWidth(),
    )
    OneUiWidgetChoiceRow("Region", region, listOf("ous", "us"), { if (it == "us") "USA" else "International" }) { model.updateShare(region = it) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = model::cancelShare, enabled = !draft.saving) { Text("Abbrechen") }
        TextButton(onClick = model::saveShare, enabled = !draft.saving) { Text("Speichern", fontWeight = FontWeight.Bold) }
    }
    if (feedback.isNotBlank()) Text(feedback, color = OneUiWidgetStyle.secondary, fontSize = 13.sp)
}

@Composable private fun CloudEndpointAccessFields(status: String, model: MobileSourceSettingsViewModel) {
    val snapshot by model.state.collectAsStateWithLifecycle()
    val draft by model.cloudEndpoint.collectAsStateWithLifecycle()
    val saved = snapshot.cloudEndpoint
    val url = draft.url
    val secret = draft.secret
    val authMode = draft.authMode
    val editing = draft.editing
    val feedback = draft.feedback
    if (!editing) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Nightscout-Server", color = OneUiWidgetStyle.primary, fontSize = 16.sp)
                Text(saved.normalizedBaseUrl, color = OneUiWidgetStyle.secondary, fontSize = 14.sp)
            }
            TextButton(onClick = model::editCloudEndpoint) { Text("Ändern") }
        }
        if (status.isNotBlank()) Text(status, color = OneUiWidgetStyle.secondary, fontSize = 13.sp)
        return
    }
    val colors = TextFieldDefaults.colors(focusedContainerColor = OneUiWidgetStyle.secondary.copy(alpha = .08f), unfocusedContainerColor = OneUiWidgetStyle.secondary.copy(alpha = .08f), focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, focusedTextColor = OneUiWidgetStyle.primary, unfocusedTextColor = OneUiWidgetStyle.primary)
    TextField(url, { model.updateCloudEndpoint(url = it) }, label = { Text("HTTPS-Adresse") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = colors, modifier = Modifier.fillMaxWidth())
    OneUiWidgetChoiceRow("Zugang", authMode, CloudEndpointAuthMode.entries, { if (it == CloudEndpointAuthMode.API_SECRET) "API-Secret" else "Zugriffstoken" }) { model.updateCloudEndpoint(authMode = it) }
    TextField(secret, { model.updateCloudEndpoint(secret = it) }, label = { Text("Zugang gespeichert / neu eingeben") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(16.dp), colors = colors, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = model::cancelCloudEndpoint, enabled = !draft.saving) { Text("Abbrechen") }
        TextButton(onClick = model::saveCloudEndpoint, enabled = !draft.saving) { Text("Speichern", fontWeight = FontWeight.Bold) }
    }
    if (feedback.isNotBlank()) Text(feedback, color = OneUiWidgetStyle.secondary, fontSize = 13.sp)
}
