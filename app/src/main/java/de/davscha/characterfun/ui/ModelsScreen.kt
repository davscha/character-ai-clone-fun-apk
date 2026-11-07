package de.davscha.characterfun.ui

import de.davscha.characterfun.i18n.AppLanguage
import de.davscha.characterfun.i18n.I18n
import de.davscha.characterfun.i18n.tr
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.davscha.characterfun.engine.OllamaModel
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.characterfun.model.ModelCatalog
import de.davscha.characterfun.model.ModelInfo

/** Seite "Modelle": herunterladen, auswählen, Speicherort, Kreativität. */
@Composable
fun ModelsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Text(tr("Einstellungen"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                vm.crashNotice?.let { CrashNotice(it) { vm.dismissCrashNotice() } }

                SectionTitle(tr("Sprache der Oberfläche"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { l ->
                        FilterChip(selected = I18n.lang == l, onClick = { vm.changeLanguage(l) }, label = { Text(l.label) })
                    }
                }
                Text(
                    tr("Die Figuren antworten ebenfalls in dieser Sprache. Die mitgelieferten Beispiel-Figuren sind auf Deutsch geschrieben."),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionTitle(tr("Über dich"))
                OutlinedTextField(
                    value = vm.aboutUser,
                    onValueChange = { vm.changeAboutUser(it) },
                    placeholder = { Text(tr("z. B. Ich heiße David, wohne in Brandenburg, mag Wrestling und Tennis.")) },
                    supportingText = { Text(tr("Das wissen alle Figuren über dich. Gilt ab der nächsten Nachricht.")) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionTitle(tr("KI-Quelle"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !vm.useOllama,
                        onClick = { vm.setSource(false) },
                        label = { Text(tr("Intern – auf diesem Gerät")) },
                    )
                    FilterChip(
                        selected = vm.useOllama,
                        onClick = { vm.setSource(true) },
                        label = { Text(tr("Lokal – Ollama im Netzwerk")) },
                    )
                }

                if (vm.useOllama) {
                    OllamaSettings(vm)
                    EngineStatus(vm)
                } else {
                    SectionTitle(tr("Modelle"))
                    StorageHint(vm)
                    vm.allModels.filter { it.url.isNotEmpty() }.forEach { model -> ModelCard(vm, model) }
                    EngineStatus(vm)

                    SectionTitle(tr("Eigene Modelle"))
                    Text(
                        tr("Jede .litertlm-Datei im Ordner CharacterFun/Modelle erscheint hier – zum Beispiel Gemma 4 12B von Hugging Face. Achtung: Modelle, die nicht für Android gedacht sind, können die App beim Laden abstürzen lassen."),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    vm.allModels.filter { it.url.isEmpty() }.forEach { model -> ModelCard(vm, model) }
                    ImportBox(vm)
                }

                SectionTitle(tr("Kreativität"))
                Text(
                    "%.1f – niedrig = vorhersehbar, hoch = verrückter. Gilt ab dem nächsten Gespräch.".format(vm.temperature),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = vm.temperature.toFloat(),
                    onValueChange = { vm.changeTemperature((it * 10).toInt() / 10.0) },
                    valueRange = 0.1f..1.5f,
                )

                SectionTitle(tr("Stimmungs-Anzeige"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("Die Figur zeigt ihre Laune als Emoji neben dem Namen, z. B. 😄 oder 😠."),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.Switch(checked = vm.moodEnabled, onCheckedChange = { vm.toggleMood() })
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** Ollama auf dem PC: Adresse, Port, Verbindungstest und Modellauswahl mit Bild-Check. */
@Composable
private fun OllamaSettings(vm: AppViewModel) {
    var portText by remember(vm.ollamaPort) { mutableStateOf(vm.ollamaPort.toString()) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = vm.ollamaHost,
            onValueChange = { vm.changeOllamaHost(it) },
            label = { Text(tr("IP-Adresse des PCs")) },
            placeholder = { Text("192.168.178.20") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = portText,
            onValueChange = { t ->
                portText = t.filter { it.isDigit() }.take(5)
                portText.toIntOrNull()?.takeIf { it in 1..65535 }?.let { vm.changeOllamaPort(it) }
            },
            label = { Text(tr("Port")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { vm.testOllama() }, enabled = vm.ollamaStatus !is OllamaStatus.Testing) { Text(tr("Verbindung testen")) }
        Spacer(Modifier.width(12.dp))
        when (val st = vm.ollamaStatus) {
            OllamaStatus.Untested -> Text(tr("Noch nicht getestet"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OllamaStatus.Testing -> {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(tr("Prüfe …"), fontSize = 13.sp)
            }
            is OllamaStatus.Ok -> Text(
                tr("Verbunden · Ollama %s · %s Modelle", st.version, st.models.size),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
            is OllamaStatus.Failed -> {}
        }
    }
    (vm.ollamaStatus as? OllamaStatus.Failed)?.let {
        Text(it.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
    }
    Text(
        tr("Tipp: Ollama muss im Netzwerk erreichbar sein – auf dem PC die Umgebungsvariable OLLAMA_HOST=0.0.0.0 setzen und Ollama neu starten."),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    (vm.ollamaStatus as? OllamaStatus.Ok)?.let { ok ->
        if (ok.models.isEmpty()) {
            Text(tr("Auf dem PC ist noch kein Modell installiert (z. B. ollama pull gemma4:e4b)."), fontSize = 13.sp)
        }
        if (ok.models.isNotEmpty()) OllamaModelPicker(vm, ok.models)
    } ?: run {
        if (vm.ollamaModel.isNotBlank()) {
            Text(tr("Gewähltes Modell: %s", vm.ollamaModel), fontSize = 13.sp)
        }
    }
}

/** Auswahlliste (Combobox) für die Ollama-Modelle – übersichtlich auch bei vielen Modellen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OllamaModelPicker(vm: AppViewModel, models: List<OllamaModel>) {
    var expanded by remember { mutableStateOf(false) }
    val current = models.firstOrNull { it.name == vm.ollamaModel }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current?.name ?: tr("Modell wählen"),
            onValueChange = {},
            readOnly = true,
            label = { Text(tr("Ollama-Modell")) },
            supportingText = {
                current?.let {
                    Text(tr("%.1f GB · %s").format(it.sizeBytes / 1e9, if (it.vision) tr("kann Bilder ✓") else tr("keine Bilder ✗")))
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { m ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, fontWeight = if (m.name == vm.ollamaModel) FontWeight.SemiBold else FontWeight.Normal)
                                Text(tr("%.1f GB").format(m.sizeBytes / 1e9), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (m.vision) tr("Bilder ✓") else tr("Bilder ✗"),
                                fontSize = 12.sp,
                                color = if (m.vision) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        vm.selectOllamaModel(m.name)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** Hinweis nach einem Absturz beim Laden */
@Composable
fun CrashNotice(text: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(tr("OK")) }
    }
}

/** Eigene .litertlm-Datei auswählen und in den Modell-Ordner kopieren. */
@Composable
private fun ImportBox(vm: AppViewModel) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }
    when (val st = vm.importState) {
        is DownloadState.Moving -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val fraction = if (st.total > 0) st.copied.toFloat() / st.total else 0f
            Text(tr("%s wird kopiert …", vm.importName), fontSize = 13.sp)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                "%.2f / %.2f GB".format(st.copied / 1e9, st.total / 1e9),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is DownloadState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${vm.importName}: ${st.message}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.dismissImportError() }) { Text(tr("OK")) }
        }
        else -> OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text(tr(".litertlm-Datei wählen"))
        }
    }
}

/** Bittet um die Erlaubnis, Modelle dauerhaft in /CharacterFun/Modelle zu speichern. */
@Composable
private fun StorageHint(vm: AppViewModel) {
    val context = LocalContext.current
    if (vm.sharedStorage) {
        Text(
            tr("Gespeichert in CharacterFun/Modelle – bleibt auch bei Neuinstallation erhalten."),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(tr("Modelle dauerhaft speichern"), fontWeight = FontWeight.SemiBold)
        Text(
            tr("Ohne Erlaubnis liegen die Modelle in der App und sind nach einer Neuinstallation weg. Mit \"Zugriff auf alle Dateien\" landen sie im Ordner CharacterFun/Modelle und bleiben."),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
            )
        }) { Text(tr("Erlaubnis erteilen")) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ModelCard(vm: AppViewModel, model: ModelInfo) {
    val selected = vm.selectedModel == model
    val state = vm.downloads[model.id] ?: DownloadState.Missing
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .clickable { vm.select(model) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(model.title, fontWeight = FontWeight.SemiBold)
                Text(
                    "${tr(model.description)} · %.1f GB".format(model.sizeBytes / 1e9),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state is DownloadState.Done) {
                IconButton(onClick = { vm.delete(model) }) {
                    Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        when (state) {
            DownloadState.Missing -> TextButton(onClick = { vm.download(model) }) { Text(tr("Herunterladen")) }
            is DownloadState.Failed -> {
                Text(state.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                TextButton(onClick = { vm.download(model) }) { Text(tr("Weiter herunterladen")) }
            }
            is DownloadState.Running -> {
                val fraction = if (state.total > 0) state.downloaded.toFloat() / state.total else 0f
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "%.2f / %.2f GB".format(state.downloaded / 1e9, state.total / 1e9),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.cancelDownload(model) }) { Text(tr("Pause")) }
                }
            }
            is DownloadState.Moving -> {
                val fraction = if (state.total > 0) state.copied.toFloat() / state.total else 0f
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Text(tr("Wird in den dauerhaften Ordner verschoben …"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DownloadState.Done -> Text(tr("Bereit"), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun EngineStatus(vm: AppViewModel) {
    val downloaded = vm.downloads[vm.selectedModel.id] is DownloadState.Done
    when (val s = vm.engineState) {
        EngineState.Idle -> Text(
            if (vm.useOllama) tr("Das Ollama-Modell wird geladen, sobald du einen Chat öffnest.")
            else if (downloaded) tr("%s wird geladen, sobald du einen Chat öffnest.", vm.selectedModel.title)
            else tr("Lade %s herunter, um loszulegen.", vm.selectedModel.title),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is EngineState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(tr("%s wird geladen …", s.modelTitle), fontSize = 13.sp)
        }

        is EngineState.Ready -> Text(
            tr("%s läuft auf: %s · geladen in %s s", s.model.title, tr(s.accelerator.label), "%.1f".format(s.loadSeconds)),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary,
        )

        is EngineState.Failed -> Column {
            Text(tr("Laden fehlgeschlagen: %s", s.message), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)

        }
    }
}

