package de.davscha.characterfun.ui

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
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.characterfun.model.ModelCatalog
import de.davscha.characterfun.model.ModelInfo

/** Tablet-Layout: links Modell und Figur, rechts der Chat. */
@Composable
fun TestChatScreen(vm: TestChatViewModel) {
    Row(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        SidePanel(vm, Modifier.width(360.dp).fillMaxHeight())
        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
        ChatPanel(vm, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun SidePanel(vm: TestChatViewModel, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Character Fun", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Modell-Test (Meilenstein 1)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

        SectionTitle("Modell")
        ModelCatalog.all.forEach { model -> ModelCard(vm, model) }

        EngineStatus(vm)

        SectionTitle("Probe-Figur")
        OutlinedTextField(
            value = vm.systemPrompt,
            onValueChange = { vm.systemPrompt = it },
            label = { Text("Definition (Persönlichkeit)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            maxLines = 10,
        )
        OutlinedTextField(
            value = vm.greeting,
            onValueChange = { vm.greeting = it },
            label = { Text("Begrüßung") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
        )
        Text(
            "Kreativität: %.1f".format(vm.temperature),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = vm.temperature.toFloat(),
            onValueChange = { vm.temperature = (it * 10).toInt() / 10.0 },
            valueRange = 0.1f..1.5f,
        )
        OutlinedButton(
            onClick = vm::restartConversation,
            enabled = vm.engineState is EngineState.Ready,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("Neues Gespräch mit dieser Figur")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ModelCard(vm: TestChatViewModel, model: ModelInfo) {
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
                    "${model.description} · %.1f GB".format(model.sizeBytes / 1e9),
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
            DownloadState.Missing -> TextButton(onClick = { vm.download(model) }) { Text("Herunterladen") }
            is DownloadState.Failed -> {
                Text(state.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                TextButton(onClick = { vm.download(model) }) { Text("Weiter herunterladen") }
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
                    TextButton(onClick = { vm.cancelDownload(model) }) { Text("Pause") }
                }
            }
            DownloadState.Done -> Text("Bereit", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun EngineStatus(vm: TestChatViewModel) {
    val downloaded = vm.downloads[vm.selectedModel.id] is DownloadState.Done
    when (val s = vm.engineState) {
        EngineState.Idle -> Button(
            onClick = vm::loadSelected,
            enabled = downloaded,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (downloaded) "${vm.selectedModel.title} laden" else "Erst herunterladen") }

        is EngineState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("${s.modelTitle} wird geladen …", fontSize = 13.sp)
        }

        is EngineState.Ready -> Text(
            "${s.modelTitle} läuft auf dem ${s.accelerator.label} · geladen in %.1f s".format(s.loadSeconds),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary,
        )

        is EngineState.Failed -> Column {
            Text("Laden fehlgeschlagen: ${s.message}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            TextButton(onClick = vm::loadSelected) { Text("Nochmal versuchen") }
        }
    }
}

@Composable
private fun ChatPanel(vm: TestChatViewModel, modifier: Modifier) {
    val listState = rememberLazyListState()
    val lastText = vm.messages.lastOrNull()?.text
    // Beim Streamen immer zur neuesten Nachricht scrollen
    LaunchedEffect(vm.messages.size, lastText) {
        if (vm.messages.isNotEmpty()) listState.scrollToItem(vm.messages.lastIndex, Int.MAX_VALUE)
    }

    Column(modifier.imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar("K")
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Probe-Figur", fontWeight = FontWeight.SemiBold)
                Text("Alles, was Figuren sagen, ist erfunden.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.engineState !is EngineState.Ready) {
                Text(
                    "Links ein Modell herunterladen und laden – dann geht's los.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(vm.messages) { i, msg ->
                    val streaming = vm.isGenerating && i == vm.messages.lastIndex
                    MessageBubble(msg, streaming)
                }
            }
        }

        InputBar(vm)
    }
}

@Composable
private fun Avatar(letter: String) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) { Text(letter, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
}

@Composable
private fun MessageBubble(msg: ChatMessage, streaming: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!msg.fromUser) {
            Avatar("K")
            Spacer(Modifier.width(10.dp))
        }
        Column(horizontalAlignment = if (msg.fromUser) Alignment.End else Alignment.Start) {
            Box(
                Modifier
                    .widthIn(max = 620.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (msg.fromUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = msg.text.ifEmpty { if (streaming) "…" else "" },
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                )
            }
            msg.stats?.let {
                Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
            }
        }
    }
}

@Composable
private fun InputBar(vm: TestChatViewModel) {
    var text by remember { mutableStateOf("") }
    val ready = vm.engineState is EngineState.Ready
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Nachricht an die Figur …") },
            enabled = ready,
            modifier = Modifier.weight(1f),
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
        )
        Spacer(Modifier.width(10.dp))
        if (vm.isGenerating) {
            FilledIconButton(onClick = vm::stop, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.Stop, "Stopp")
            }
        } else {
            FilledIconButton(
                onClick = { vm.send(text); text = "" },
                enabled = ready && text.isNotBlank(),
                modifier = Modifier.size(52.dp),
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Senden") }
        }
    }
    Spacer(Modifier.height(4.dp))
}
