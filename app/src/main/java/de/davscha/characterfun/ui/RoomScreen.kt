package de.davscha.characterfun.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.characterfun.data.RoomMessage
import de.davscha.characterfun.i18n.I18n
import de.davscha.characterfun.i18n.tr

/** Gruppenchat: mehrere Figuren antworten nacheinander. */
@Composable
fun RoomScreen(vm: AppViewModel) {
    val room = vm.currentRoom ?: return
    val members = vm.roomMembers(room)
    val listState = rememberLazyListState()
    val lastText = room.messages.lastOrNull()?.text
    LaunchedEffect(room.messages.size, lastText) {
        if (room.messages.isNotEmpty()) listState.scrollToItem(room.messages.lastIndex + 1, Int.MAX_VALUE)
    }
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(tr("Neues Gespräch?")) },
            text = { Text(tr("Das bisherige Gespräch in diesem Raum wird gelöscht. Figuren und Szene bleiben.")) },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearRoom() }) { Text(tr("Neu anfangen")) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("Abbrechen")) } },
        )
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // Kopfzeile: Tipp auf einen Avatar = diese Figur spricht als Nächstes
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Zurück")) }
            Column(Modifier.weight(1f)) {
                Text(room.name.ifBlank { tr("Raum") }, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(roomStatus(vm), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            members.forEach { c ->
                val talking = vm.roomSpeakerId == c.id
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (talking) MaterialTheme.colorScheme.secondary else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                        .clickable(enabled = !vm.roomBusy) { vm.roomContinue(c.id) }
                        .padding(3.dp)
                ) { Avatar(c, vm.repo.avatar(c), size = 38.dp) }
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { vm.toggleAutoRead() }) {
                Icon(
                    if (vm.autoRead) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    tr("Automatisch vorlesen"),
                    tint = if (vm.autoRead) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.navigate(Screen.RoomEditor(room.id)) }, enabled = !vm.roomBusy) { Icon(Icons.Default.Edit, tr("Raum bearbeiten")) }
            IconButton(onClick = { confirmClear = true }, enabled = !vm.roomBusy) { Icon(Icons.Default.Refresh, tr("Neues Gespräch")) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        tr("Alles, was die Figuren sagen, ist erfunden."),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    if (room.scenario.isNotBlank()) {
                        Text(
                            room.scenario,
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 560.dp),
                        )
                    }
                    if (room.messages.isEmpty()) {
                        Text(
                            tr("Schreib etwas – oder tippe auf „Weiter“, dann fangen die Figuren an."),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
            itemsIndexed(room.messages) { i, m ->
                RoomBubble(
                    vm, m,
                    streaming = vm.roomBusy && i == room.messages.lastIndex && !m.fromUser,
                    speaking = vm.speakingId == "raum-$i",
                    onSpeak = { vm.speakRoom(i) },
                    onDelete = { vm.deleteRoomMessage(i) },
                )
            }
        }

        roomError(vm)?.let { err ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(err, fontSize = 13.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                if (vm.useOllama) TextButton(onClick = { vm.switchToInternal() }) { Text(tr("Auf intern wechseln")) }
                else TextButton(onClick = { vm.navigate(Screen.Models) }) { Text(tr("Zu den Einstellungen")) }
            }
        }

        RoomInputBar(vm)
    }
}

private fun roomStatus(vm: AppViewModel): String {
    val speaker = vm.roomSpeakerId?.let { vm.character(it) }
    return when {
        speaker != null -> tr("%s schreibt …", speaker.name)
        vm.engineState is EngineState.Loading -> tr("%s wird geladen …", (vm.engineState as EngineState.Loading).modelTitle)
        vm.roomBusy -> tr("Gespräch wird vorbereitet …")
        else -> vm.roomMembers(vm.currentRoom ?: return "").joinToString(", ") { it.name }
    }
}

private fun roomError(vm: AppViewModel): String? =
    (vm.engineState as? EngineState.Failed)?.message?.takeIf { !vm.roomBusy }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoomBubble(
    vm: AppViewModel,
    m: RoomMessage,
    streaming: Boolean,
    speaking: Boolean,
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = if (m.fromUser) null else vm.character(m.speakerId)
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        if (c != null) {
            Avatar(c, vm.repo.avatar(c), size = 40.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
            if (!m.fromUser) {
                Text(c?.name ?: "?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            }
            Box(
                Modifier
                    .widthIn(max = 640.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(onClick = {}, onLongClick = { if (!vm.roomBusy) menuOpen = true })
                    .background(if (m.fromUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(roleplayText(m.text.ifEmpty { if (streaming) "…" else "" }), fontSize = 16.sp, lineHeight = 22.sp)
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("Löschen"), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
            if (c != null && !streaming && m.text.isNotBlank()) {
                IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (speaking) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                        tr("Vorlesen"),
                        modifier = Modifier.size(18.dp),
                        tint = if (speaking) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomInputBar(vm: AppViewModel) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val context = LocalContext.current
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            val joined = (text.text.trimEnd() + " " + spoken).trim()
            text = TextFieldValue(joined, TextRange(joined.length))
        }
    }
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(tr("Nachricht an alle …")) },
            modifier = Modifier.weight(1f),
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
        )
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = {
                val sel = text.selection
                val t = text.text
                text = if (sel.collapsed) TextFieldValue(t.substring(0, sel.start) + "**" + t.substring(sel.start), TextRange(sel.start + 1))
                else TextFieldValue(t.substring(0, sel.min) + "*" + t.substring(sel.min, sel.max) + "*" + t.substring(sel.max), TextRange(sel.max + 2))
            },
            modifier = Modifier.size(52.dp),
        ) { Text("*…*", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold) }
        IconButton(
            onClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, I18n.lang.tag)
                try {
                    speech.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, tr("Keine Spracherkennung auf diesem Gerät gefunden"), Toast.LENGTH_LONG).show()
                }
            },
            enabled = !vm.roomBusy,
            modifier = Modifier.size(52.dp),
        ) { Icon(Icons.Default.Mic, tr("Spracheingabe")) }
        Spacer(Modifier.width(6.dp))
        if (vm.roomBusy) {
            if (vm.roomSpeakerId == null) CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp)
            FilledIconButton(onClick = { vm.stopRoom() }, modifier = Modifier.size(52.dp)) { Icon(Icons.Default.Stop, tr("Stopp")) }
        } else {
            // Weiter: die nächste Figur redet, ohne dass du etwas schreibst
            FilledTonalButton(onClick = { vm.roomContinue() }, modifier = Modifier.padding(end = 6.dp)) {
                Icon(Icons.Default.SkipNext, null)
                Spacer(Modifier.width(4.dp))
                Text(tr("Weiter"))
            }
            FilledIconButton(
                onClick = { vm.sendToRoom(text.text); text = TextFieldValue("") },
                enabled = text.text.isNotBlank(),
                modifier = Modifier.size(52.dp),
            ) { Icon(Icons.AutoMirrored.Filled.Send, tr("Senden")) }
        }
    }
}
