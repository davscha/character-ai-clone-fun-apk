package de.davscha.characterfun.ui

import androidx.compose.foundation.layout.Box

import androidx.compose.material.icons.filled.Checklist

import de.davscha.characterfun.i18n.tr
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.characterfun.data.Category
import de.davscha.characterfun.data.Character
import de.davscha.characterfun.data.ReplyLength
import de.davscha.characterfun.model.ModelCatalog
import kotlinx.coroutines.launch

/** Figur erstellen oder bearbeiten – die Felder wie im character.ai-Editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(vm: AppViewModel, characterId: String?) {
    val original = remember(characterId) { characterId?.let { vm.character(it) } ?: Character() }
    var c by remember(characterId) { mutableStateOf(original) }
    var starters by remember(characterId) { mutableStateOf((original.starters + List(3) { "" }).take(3)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            vm.importAvatar(uri, c.id)?.let { name -> c = c.copy(avatarFile = name) }
        }
    }

    fun save() {
        vm.saveCharacter(c.copy(name = c.name.trim(), starters = starters.map { it.trim() }.filter { it.isNotEmpty() }))
        vm.back()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // Kopfzeile
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Text(
                if (characterId == null) tr("Neue Figur") else tr("Figur bearbeiten"),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (characterId != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error)
                }
            }
            Button(onClick = { save() }, enabled = c.name.isNotBlank(), modifier = Modifier.padding(end = 16.dp)) {
                Text(tr("Speichern"))
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AiBox(vm, c) { generated ->
                    c = generated
                    starters = (generated.starters + List(3) { "" }).take(3)
                }
                ProfileCheck(vm, c.copy(starters = starters.filter { it.isNotBlank() }))

                // Womit ein neues Gespräch beginnt: Ort und Kleidung aus dem Fundus
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Start:"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LocationPicker(vm, vm.location(c.defaultLocationId)) { c = c.copy(defaultLocationId = it) }
                    OutfitPicker(vm, c, vm.clothing(c.defaultOutfitId), noneLabel = tr("Keine besondere Kleidung")) { id ->
                        // Das Start-Outfit kommt automatisch mit in den Schrank
                        c = c.copy(defaultOutfitId = id, wardrobe = if (id.isBlank() || id in c.wardrobe) c.wardrobe else c.wardrobe + id)
                    }
                }

                // Avatar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c, vm.repo.avatar(c), size = 96.dp)
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Default.Image, null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Bild wählen"))
                        }
                        if (c.avatarFile.isNotEmpty()) {
                            TextButton(onClick = { c = c.copy(avatarFile = "") }) { Text(tr("Bild entfernen")) }
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    OutlinedTextField(
                        value = c.emoji,
                        onValueChange = { c = c.copy(emoji = it.take(8)) },
                        label = { Text(tr("oder Emoji")) },
                        singleLine = true,
                        modifier = Modifier.width(140.dp),
                    )
                }

                Field(tr("Name"), c.name, tr("z. B. Kapitän Kraken")) { c = c.copy(name = it) }

                Text(tr("Geschlecht"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    de.davscha.characterfun.data.Gender.entries.forEach { g ->
                        FilterChip(selected = c.gender == g, onClick = { c = c.copy(gender = g) }, label = { Text(tr(g.label)) })
                    }
                }

                Text(tr("Kategorie"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { cat ->
                        FilterChip(selected = c.category == cat, onClick = { c = c.copy(category = cat) }, label = { Text(tr(cat.label)) })
                    }
                }

                Field(
                    tr("Kurzbeschreibung"), c.shortDescription,
                    tr("Ein Satz für die Karte, z. B. \"Angeberischer Tintenfisch-Pirat\""),
                ) { c = c.copy(shortDescription = it) }

                Field(
                    tr("Begrüßung"), c.greeting,
                    tr("Der erste Satz der Figur im Chat"), minLines = 2,
                ) { c = c.copy(greeting = it) }

                Field(
                    tr("Startszene (optional)"), c.scenario,
                    tr("Wo und wie beginnt das Gespräch? z. B. \"Nachts in einer verrauchten Hafenkneipe\""), minLines = 2,
                ) { c = c.copy(scenario = it) }

                Field(
                    tr("Definition (Persönlichkeit)"), c.definition,
                    tr("Wie denkt, redet und verhält sich die Figur? Kurze, klare Sätze funktionieren am besten."),
                    minLines = 5,
                ) { c = c.copy(definition = it) }

                Field(
                    tr("Hintergrund (optional)"), c.longDescription,
                    tr("Aussehen, Vorgeschichte, Umgebung …"), minLines = 3,
                ) { c = c.copy(longDescription = it) }

                Text(tr("Antwortlänge"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReplyLength.entries.forEach { len ->
                        FilterChip(selected = c.replyLength == len, onClick = { c = c.copy(replyLength = len) }, label = { Text(tr(len.label)) })
                    }
                }

                Text(
                    if (vm.useOllama) tr("Lieblingsmodell (gilt nur im Modus \"Intern\" – gerade ist Ollama aktiv)") else tr("Lieblingsmodell"),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = c.preferredModelId.isEmpty(),
                        onClick = { c = c.copy(preferredModelId = "") },
                        label = { Text(tr("Wie in den Einstellungen")) },
                    )
                    ModelCatalog.all.forEach { m ->
                        val ready = vm.downloads[m.id] == DownloadState.Done
                        FilterChip(
                            selected = c.preferredModelId == m.id,
                            onClick = { c = c.copy(preferredModelId = m.id) },
                            label = { Text(if (ready) m.title else tr("%s (nicht geladen)", m.title)) },
                        )
                    }
                }

                VoiceSection(vm, c) { c = it }

                CharacterImageSection(vm, c) { c = it }

                Text(tr("Gesprächsstarter (optional)"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                starters.forEachIndexed { i, s ->
                    OutlinedTextField(
                        value = s,
                        onValueChange = { v -> starters = starters.toMutableList().also { it[i] = v } },
                        placeholder = { Text(tr("Vorschlag %s", i + 1)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.padding(bottom = 32.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("%s löschen?", c.name.ifBlank { tr("Figur") })) },
            text = { Text(tr("Die Figur ist danach weg.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteCharacter(c.id)
                    vm.back()
                }) { Text(tr("Löschen"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Abbrechen")) } },
        )
    }
}

@Composable
private fun Field(label: String, value: String, hint: String, minLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(hint) },
        singleLine = minLines == 1,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "Mit KI erstellen": Stichpunkte rein, fertiges Profil raus – danach lässt sich alles anpassen. */
@Composable
private fun AiBox(vm: AppViewModel, current: Character, onGenerated: (Character) -> Unit) {
    var notes by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(tr("Mit KI erstellen"), fontWeight = FontWeight.SemiBold)
        }
        Text(
            tr("Ein paar Stichpunkte genügen – die KI schreibt Name, Begrüßung, Persönlichkeit und Gesprächsstarter. Vorhandene Felder werden überschrieben."),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            placeholder = { Text(tr("z. B. mürrischer Zwerg, Schmied, liebt Bier und Rätsel, berlinert")) },
            enabled = !busy,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    error = null
                    busy = true
                    progress = 0
                    scope.launch {
                        try {
                            onGenerated(vm.generateProfile(notes, current) { progress = it })
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: tr("Das hat nicht geklappt.")
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = notes.isNotBlank() && !busy,
            ) { Text(if (current.definition.isBlank()) tr("Profil erstellen") else tr("Neu erstellen")) }
            Spacer(Modifier.width(12.dp))
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        vm.engineState is EngineState.Loading -> tr("Modell wird geladen …")
                        progress == 0 -> tr("Die KI denkt nach …")
                        else -> tr("Die KI schreibt … (%s Zeichen)", progress)
                    },
                    fontSize = 13.sp,
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
    }
}

/** Stimme beim Vorlesen: Android-Stimme, Tonhöhe und Tempo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSection(vm: AppViewModel, c: Character, onChange: (Character) -> Unit) {
    var allLanguages by remember { mutableStateOf(vm.voices.firstOrNull { it.name == c.voiceName }?.isGerman == false) }
    var expanded by remember { mutableStateOf(false) }
    val shown = vm.voices.filter { allLanguages || it.isGerman }
    val current = vm.voices.firstOrNull { it.name == c.voiceName }

    Text(tr("Stimme beim Vorlesen"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current?.label ?: tr("Standardstimme"),
            onValueChange = {},
            readOnly = true,
            label = { Text(tr("Stimme")) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(tr("Standardstimme")) },
                onClick = { onChange(c.copy(voiceName = "")); expanded = false },
            )
            shown.forEach { v ->
                DropdownMenuItem(
                    text = { Text(v.label, fontWeight = if (v.name == c.voiceName) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = {
                        val updated = c.copy(voiceName = v.name)
                        onChange(updated)
                        expanded = false
                        vm.previewVoice(updated) // gleich reinhören
                    },
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = allLanguages, onCheckedChange = { allLanguages = it })
        Text(
            tr("Auch Stimmen anderer Sprachen zeigen – liest mit Akzent vor, z. B. eine englische oder französische Stimme"),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (vm.voices.none { it.isGerman }) {
        Text(
            tr("Keine passende Stimme gefunden – in den Android-Einstellungen unter \"Text-in-Sprache-Ausgabe\" Stimmdaten installieren."),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Tonhöhe"), fontSize = 13.sp, modifier = Modifier.width(80.dp))
        Slider(
            value = c.voicePitch,
            onValueChange = { onChange(c.copy(voicePitch = (it * 20).toInt() / 20f)) },
            valueRange = 0.25f..3.0f,
            modifier = Modifier.weight(1f),
        )
        Text(tr("%.2f").format(c.voicePitch), fontSize = 13.sp, modifier = Modifier.width(48.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Tempo"), fontSize = 13.sp, modifier = Modifier.width(80.dp))
        Slider(
            value = c.voiceRate,
            onValueChange = { onChange(c.copy(voiceRate = (it * 20).toInt() / 20f)) },
            valueRange = 0.25f..3.0f,
            modifier = Modifier.weight(1f),
        )
        Text(tr("%.2f").format(c.voiceRate), fontSize = 13.sp, modifier = Modifier.width(48.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.previewVoice(c) }) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, null)
            Spacer(Modifier.width(8.dp))
            Text(tr("Probe hören"))
        }
        TextButton(onClick = { onChange(c.copy(voicePitch = 1f, voiceRate = 1f)) }) { Text(tr("Tonhöhe und Tempo zurücksetzen")) }
    }
}

/** Steckbrief-Check: die KI gibt Tipps, was fehlt oder sich widerspricht */
@Composable
private fun ProfileCheck(vm: AppViewModel, c: Character) {
    val scope = rememberCoroutineScope()
    var tips by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = {
                running = true
                tips = null
                scope.launch {
                    tips = runCatching { vm.checkProfile(c) }.getOrElse { "⚠ " + (it.message ?: tr("Fehler")) }
                    running = false
                }
            },
            enabled = !running && c.name.isNotBlank(),
        ) {
            if (running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Checklist, null)
            Spacer(Modifier.width(8.dp))
            Text(if (running) tr("Gemma liest den Steckbrief …") else tr("Steckbrief prüfen"))
        }
        tips?.let {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Column {
                    Text(it, fontSize = 14.sp)
                    TextButton(onClick = { tips = null }) { Text(tr("Ausblenden")) }
                }
            }
        }
    }
}
