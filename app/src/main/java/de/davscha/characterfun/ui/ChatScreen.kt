package de.davscha.characterfun.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.characterfun.data.Character
import de.davscha.characterfun.data.ChatMessage

/** Chat mit einer Figur. */
@Composable
fun ChatScreen(vm: AppViewModel) {
    val c = vm.chatCharacter ?: return
    val avatarFile = vm.repo.avatar(c)
    val listState = rememberLazyListState()
    val lastText = vm.messages.lastOrNull()?.text
    // Beim Streamen immer zur neuesten Nachricht scrollen
    LaunchedEffect(vm.messages.size, lastText) {
        if (vm.messages.isNotEmpty()) listState.scrollToItem(vm.messages.lastIndex, Int.MAX_VALUE)
    }

    var confirmRestart by remember { mutableStateOf(false) }
    var showMemories by remember { mutableStateOf(false) }
    if (showMemories) MemoriesDialog(vm, c) { showMemories = false }

    // Nachricht bearbeiten
    var editIndex by remember { mutableStateOf(-1) }
    var editText by remember { mutableStateOf("") }
    if (editIndex in vm.messages.indices) {
        val fromUser = vm.messages[editIndex].fromUser
        AlertDialog(
            onDismissRequest = { editIndex = -1 },
            title = { Text("Nachricht bearbeiten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (fromUser && editIndex < vm.messages.lastIndex) {
                        Text(
                            "Alles nach dieser Nachricht fällt weg, ${c.name} antwortet neu.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(value = editText, onValueChange = { editText = it }, modifier = Modifier.fillMaxWidth(), maxLines = 8)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.editMessage(editIndex, editText); editIndex = -1 },
                    enabled = editText.isNotBlank(),
                ) { Text(if (fromUser) "Senden" else "Speichern") }
            },
            dismissButton = { TextButton(onClick = { editIndex = -1 }) { Text("Abbrechen") } },
        )
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Neues Gespräch?") },
            text = { Text("Das bisherige Gespräch mit ${c.name} wird gelöscht. Angeheftete Erinnerungen bleiben.") },
            confirmButton = {
                TextButton(onClick = { confirmRestart = false; vm.restartChat() }) { Text("Neu anfangen") }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Abbrechen") } },
        )
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // Kopfzeile
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Avatar(c, avatarFile, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(
                    statusLine(vm),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { vm.toggleAutoRead() }) {
                Icon(
                    if (vm.autoRead) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    if (vm.autoRead) "Automatisch vorlesen: an" else "Automatisch vorlesen: aus",
                    tint = if (vm.autoRead) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { showMemories = true }) {
                Icon(Icons.Default.PushPin, null)
                Spacer(Modifier.width(6.dp))
                Text(if (vm.memories.isEmpty()) "Erinnerungen" else "Erinnerungen (${vm.memories.size})")
            }
            IconButton(onClick = { vm.navigate(Screen.Editor(c.id)) }) { Icon(Icons.Default.Edit, "Figur bearbeiten") }
            TextButton(onClick = { confirmRestart = true }, enabled = !vm.isGenerating) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("Neues Gespräch")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Text(
                        "Alles, was ${c.name} sagt, ist erfunden.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (c.scenario.isNotBlank()) {
                    item {
                        // Startszene wie eine Regieanweisung über dem Gespräch
                        Text(
                            c.scenario,
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 560.dp).padding(bottom = 8.dp),
                        )
                    }
                }
                itemsIndexed(vm.messages) { i, msg ->
                    val isLast = i == vm.messages.lastIndex
                    val streaming = vm.isGenerating && isLast
                    // Neu würfeln geht bei der letzten Antwort, wenn davor eine Nachricht von dir steht
                    val canReroll = isLast && !msg.fromUser && i > 0 && vm.messages[i - 1].fromUser
                    MessageBubble(
                        c, avatarFile, msg, streaming, if (canReroll) vm else null, vm.summarizing,
                        busy = vm.isGenerating || vm.suggesting,
                        onPin = { vm.addMemory(msg.text) },
                        onEdit = { editText = msg.text; editIndex = i },
                        onDelete = { vm.deleteMessage(i) },
                        speaking = vm.speakingId == "msg-$i",
                        onSpeak = { vm.speak(i) },
                    )
                }
            }
            EngineOverlay(vm, Modifier.align(Alignment.Center))
        }

        var input by rememberSaveable { mutableStateOf("") }

        // Ideen von der KI: Tipp setzt den Vorschlag ins Eingabefeld
        if (vm.suggesting || vm.suggestions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (vm.suggesting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Gemma sammelt Ideen …", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Ideen:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(vm.suggestions) { idea ->
                            SuggestionChip(
                                onClick = { input = idea; vm.clearSuggestions() },
                                label = { Text(roleplayText(idea), maxLines = 2) },
                                modifier = Modifier.widthIn(max = 320.dp),
                            )
                        }
                    }
                    IconButton(onClick = { vm.clearSuggestions() }) { Icon(Icons.Default.Close, "Ideen schließen") }
                }
            }
        } else if (vm.chatReady && vm.messages.none { it.fromUser } && c.starters.isNotEmpty()) {
            // Gesprächsstarter, solange noch nichts geschrieben wurde
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(c.starters) { s -> SuggestionChip(onClick = { vm.send(s) }, label = { Text(s) }) }
            }
        }
        InputBar(vm, c, input) { input = it }
    }
}

private fun statusLine(vm: AppViewModel): String = when (val s = vm.engineState) {
    is EngineState.Ready -> "${s.model.title} · ${s.accelerator.label}"
    is EngineState.Loading -> "${s.modelTitle} wird geladen …"
    is EngineState.Failed -> "Modell nicht bereit"
    EngineState.Idle -> "Modell nicht geladen"
}

/** Hinweis in der Mitte, solange das Modell lädt oder fehlt. */
@Composable
private fun EngineOverlay(vm: AppViewModel, modifier: Modifier) {
    val s = vm.engineState
    if (vm.chatReady) return
    Column(
        modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
            .widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            vm.preparing -> {
                CircularProgressIndicator()
                Text(
                    if (s is EngineState.Loading) "${s.modelTitle} wird geladen …\nDas dauert beim ersten Mal ein paar Sekunden."
                    else "Gespräch wird vorbereitet …",
                    textAlign = TextAlign.Center,
                )
            }
            s is EngineState.Failed -> {
                Text(s.message, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.useOllama) {
                        TextButton(onClick = { vm.switchToInternal() }) { Text("Auf intern wechseln") }
                    } else {
                        TextButton(onClick = { vm.navigate(Screen.Models) }) { Text("Zu den Einstellungen") }
                    }
                    Button(onClick = { vm.retryChat() }) { Text("Nochmal versuchen") }
                }
            }
            else -> {
                Text("Das Modell ist nicht geladen.", textAlign = TextAlign.Center)
                Button(onClick = { vm.retryChat() }) { Text("${vm.chatModel.title} laden") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    c: Character,
    avatarFile: java.io.File?,
    msg: ChatMessage,
    streaming: Boolean,
    /** nur bei der letzten Antwort gesetzt: dann gibt es Neu würfeln und Varianten-Pfeile */
    rerollVm: AppViewModel?,
    summarizing: Boolean,
    busy: Boolean,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    speaking: Boolean,
    onSpeak: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!msg.fromUser) {
            Avatar(c, avatarFile, size = 40.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(horizontalAlignment = if (msg.fromUser) Alignment.End else Alignment.Start) {
            if (!msg.fromUser) {
                Text(c.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            }
            if (msg.image.isNotEmpty()) {
                AsyncImage(
                    model = java.io.File(msg.image),
                    contentDescription = "Geschicktes Bild",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .widthIn(max = 320.dp)
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
            if (msg.text.isNotEmpty() || streaming || msg.image.isEmpty()) Box(
                Modifier
                    .widthIn(max = 640.dp)
                    .clip(RoundedCornerShape(16.dp))
                    // Lange drücken: Nachricht als Erinnerung anheften
                    .combinedClickable(onClick = {}, onLongClick = { if (msg.text.isNotBlank()) menuOpen = true })
                    .background(
                        if (msg.fromUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = roleplayText(
                        msg.text.ifEmpty { if (streaming && summarizing) "*erinnert sich an das Bisherige …*" else if (streaming) "…" else "" }
                    ),
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Als Erinnerung anheften") },
                        leadingIcon = { Icon(Icons.Default.PushPin, null) },
                        onClick = { menuOpen = false; onPin() },
                    )
                    DropdownMenuItem(
                        text = { Text("Bearbeiten") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        enabled = !busy,
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Löschen", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        enabled = !busy,
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Vorlesen (bzw. anhalten) – bei jeder Antwort der Figur
                if (!msg.fromUser && !streaming && msg.text.isNotBlank()) {
                    IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (speaking) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                            if (speaking) "Vorlesen anhalten" else "Vorlesen",
                            modifier = Modifier.size(18.dp),
                            tint = if (speaking) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (rerollVm != null && !streaming) {
                    if (msg.variants.size > 1) {
                        IconButton(onClick = { rerollVm.selectVariant(-1) }, enabled = msg.selected > 0, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vorherige Variante")
                        }
                        Text("${msg.selected + 1}/${msg.variants.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(
                            onClick = { rerollVm.selectVariant(1) },
                            enabled = msg.selected < msg.variants.lastIndex,
                            modifier = Modifier.size(32.dp),
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nächste Variante") }
                    }
                    IconButton(onClick = { rerollVm.regenerate() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Casino, "Neu würfeln", modifier = Modifier.size(18.dp))
                    }
                }
                msg.stats?.let {
                    Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

/** *Handlungen zwischen Sternchen* werden kursiv und etwas blasser angezeigt, wie bei character.ai. */
@Composable
private fun roleplayText(text: String): androidx.compose.ui.text.AnnotatedString {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    return androidx.compose.ui.text.buildAnnotatedString {
        val parts = text.split('*')
        parts.forEachIndexed { i, part ->
            if (i % 2 == 1 && i < parts.lastIndex) {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic, color = dim))
                append(part)
                pop()
            } else {
                append(part)
            }
        }
    }
}

@Composable
private fun InputBar(vm: AppViewModel, c: Character, text: String, onText: (String) -> Unit) {
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.attachImage(uri)
    }
    // Vorschau des angehängten Bildes
    vm.pendingImage?.let { path ->
        Row(Modifier.padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = java.io.File(path),
                contentDescription = "Angehängtes Bild",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text("Bild geht mit der nächsten Nachricht mit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { vm.clearPendingImage() }) { Icon(Icons.Default.Close, "Bild entfernen") }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            placeholder = { Text("Nachricht an ${c.name} …") },
            enabled = vm.chatReady,
            modifier = Modifier.weight(1f),
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
        )
        Spacer(Modifier.width(6.dp))
        // Bild anhängen – nur, wenn das Modell Bilder ansehen kann
        IconButton(
            onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = vm.imagesSupported && !vm.isGenerating,
            modifier = Modifier.size(52.dp),
        ) { Icon(Icons.Default.AddPhotoAlternate, "Bild schicken") }
        // Ideen-Knopf: Gemma schlägt fünf mögliche nächste Nachrichten vor
        IconButton(
            onClick = { vm.suggestReplies() },
            enabled = vm.chatReady && !vm.isGenerating && !vm.suggesting,
            modifier = Modifier.size(52.dp),
        ) { Icon(Icons.Default.Lightbulb, "Ideen für meine Antwort", tint = MaterialTheme.colorScheme.secondary) }
        Spacer(Modifier.width(6.dp))
        if (vm.isGenerating) {
            FilledIconButton(onClick = { vm.stop() }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.Stop, "Stopp")
            }
        } else {
            FilledIconButton(
                onClick = { vm.send(text); onText("") },
                enabled = vm.chatReady && (text.isNotBlank() || vm.pendingImage != null),
                modifier = Modifier.size(52.dp),
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Senden") }
        }
    }
}

/** Angeheftete Erinnerungen und die automatische Zusammenfassung des aktuellen Chats. */
@Composable
private fun MemoriesDialog(vm: AppViewModel, c: Character, onClose: () -> Unit) {
    var newMemory by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Erinnerungen von ${c.name}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Das hier vergisst ${c.name} nie, auch in langen Gesprächen. Tipp: Lange auf eine Nachricht drücken, um sie anzuheften.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                vm.memories.forEachIndexed { i, m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("• $m", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.removeMemory(i) }) {
                            Icon(Icons.Default.Close, "Entfernen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newMemory,
                        onValueChange = { newMemory = it },
                        placeholder = { Text("z. B. Wir haben den Schatz auf der Nebelinsel versteckt") },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                    )
                    IconButton(onClick = { vm.addMemory(newMemory); newMemory = "" }, enabled = newMemory.isNotBlank()) {
                        Icon(Icons.Default.Add, "Hinzufügen")
                    }
                }
                if (vm.summary.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("Zusammenfassung (automatisch)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(vm.summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { vm.clearSummary() }) { Text("Zusammenfassung löschen") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Fertig") } },
    )
}
