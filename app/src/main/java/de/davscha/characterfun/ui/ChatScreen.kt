package de.davscha.characterfun.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Neues Gespräch?") },
            text = { Text("Das bisherige Gespräch mit ${c.name} wird gelöscht.") },
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
                itemsIndexed(vm.messages) { i, msg ->
                    val isLast = i == vm.messages.lastIndex
                    val streaming = vm.isGenerating && isLast
                    // Neu würfeln geht bei der letzten Antwort, wenn davor eine Nachricht von dir steht
                    val canReroll = isLast && !msg.fromUser && i > 0 && vm.messages[i - 1].fromUser
                    MessageBubble(c, avatarFile, msg, streaming, if (canReroll) vm else null)
                }
            }
            EngineOverlay(vm, Modifier.align(Alignment.Center))
        }

        // Gesprächsstarter, solange noch nichts geschrieben wurde
        if (vm.chatReady && vm.messages.none { it.fromUser } && c.starters.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(c.starters) { s -> SuggestionChip(onClick = { vm.send(s) }, label = { Text(s) }) }
            }
        }
        InputBar(vm, c)
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
                    TextButton(onClick = { vm.navigate(Screen.Models) }) { Text("Zu den Modellen") }
                    Button(onClick = { vm.retryChat() }) { Text("Nochmal versuchen") }
                }
            }
            else -> {
                Text("Das Modell ist nicht geladen.", textAlign = TextAlign.Center)
                Button(onClick = { vm.retryChat() }) { Text("${vm.selectedModel.title} laden") }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    c: Character,
    avatarFile: java.io.File?,
    msg: ChatMessage,
    streaming: Boolean,
    /** nur bei der letzten Antwort gesetzt: dann gibt es Neu würfeln und Varianten-Pfeile */
    rerollVm: AppViewModel?,
) {
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
            Box(
                Modifier
                    .widthIn(max = 640.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (msg.fromUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = roleplayText(msg.text.ifEmpty { if (streaming) "…" else "" }),
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
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
private fun InputBar(vm: AppViewModel, c: Character) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Nachricht an ${c.name} …") },
            enabled = vm.chatReady,
            modifier = Modifier.weight(1f),
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
        )
        Spacer(Modifier.width(10.dp))
        if (vm.isGenerating) {
            FilledIconButton(onClick = { vm.stop() }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.Stop, "Stopp")
            }
        } else {
            FilledIconButton(
                onClick = { vm.send(text); text = "" },
                enabled = vm.chatReady && text.isNotBlank(),
                modifier = Modifier.size(52.dp),
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Senden") }
        }
    }
}
