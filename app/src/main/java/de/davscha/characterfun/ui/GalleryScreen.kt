package de.davscha.characterfun.ui

import androidx.compose.foundation.layout.size

import de.davscha.characterfun.i18n.tr
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FileOpen
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.characterfun.data.Category
import de.davscha.characterfun.data.Character

/** Startseite: alle Figuren als Karten, mit Kategorie-Filter und Suche. */
@Composable
fun GalleryScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    // null = "Alle", "eigene" = selbst erstellte, sonst Name der Kategorie
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    // Zufallsfigur: null = läuft nicht, sonst geschriebene Zeichen
    var randomProgress by remember { mutableStateOf<Int?>(null) }
    randomProgress?.let { p ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("🎲 " + tr("Zufallsfigur")) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(if (p == 0) tr("Gemma würfelt eine Figur aus …") else tr("Gemma schreibt das Profil … (%s Zeichen)", p))
                }
            },
        )
    }

    val shown = vm.characters.filter { c ->
        val matchesFilter = when (filter) {
            null -> true
            "eigene" -> !c.builtIn
            else -> c.category.name == filter
        }
        val q = query.trim()
        val matchesQuery = q.isEmpty() || c.name.contains(q, true) || c.shortDescription.contains(q, true)
        matchesFilter && matchesQuery
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.navigate(Screen.Editor(null)) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(tr("Figur erstellen")) },
                containerColor = MaterialTheme.colorScheme.primary,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Kopfzeile: Name, Suche, Modelle
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Character Fun"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(modelLine(vm), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(tr("Figur suchen")) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.widthIn(max = 360.dp),
                )
                Spacer(Modifier.width(12.dp))
                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) scope.launch { Toast.makeText(context, vm.importCharacter(uri), Toast.LENGTH_LONG).show() }
                }
                TextButton(onClick = {
                    randomProgress = 0
                    scope.launch {
                        runCatching { vm.createRandomCharacter { randomProgress = it } }
                            .onSuccess { vm.navigate(Screen.Editor(it.id)) }
                            .onFailure { Toast.makeText(context, it.message ?: tr("Fehler"), Toast.LENGTH_LONG).show() }
                        randomProgress = null
                    }
                }, enabled = randomProgress == null) {
                    Text("🎲")
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Zufallsfigur"))
                }
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }) {
                    Icon(Icons.Default.FileOpen, null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Importieren"))
                }
                TextButton(onClick = { vm.navigate(Screen.Models) }) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Einstellungen"))
                }
            }

            vm.crashNotice?.let {
                Box(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) { CrashNotice(it) { vm.dismissCrashNotice() } }
            }

            // Weiter chatten: die letzten Gespräche
            if (vm.recentChats.isNotEmpty() && query.isBlank()) {
                Text(
                    tr("Weiter chatten"),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    items(vm.recentChats, key = { it.characterId }) { chat ->
                        vm.character(chat.characterId)?.let { c -> RecentChatCard(vm, c, chat.preview) }
                    }
                }
            }

            // Rooms: mehrere Figuren in einem Chat
            if (query.isBlank()) {
                Text(
                    tr("Rooms"),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    items(vm.rooms, key = { it.id }) { r -> RoomCard(vm, r) }
                    item {
                        Card(
                            onClick = { vm.navigate(Screen.RoomEditor(null)) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.width(200.dp).height(72.dp),
                        ) {
                            Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Raum erstellen"), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Kategorie-Tabs
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(tr("Alle")) }) }
                item { FilterChip(selected = filter == "eigene", onClick = { filter = "eigene" }, label = { Text(tr("Eigene")) }) }
                items(Category.entries.filter { cat -> vm.characters.any { it.category == cat } }) { cat ->
                    FilterChip(selected = filter == cat.name, onClick = { filter = cat.name }, label = { Text(tr(cat.label)) })
                }
            }

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (filter == "eigene" && query.isBlank()) "Noch keine eigenen Figuren – tippe auf \"Figur erstellen\"."
                        else tr("Keine Figur gefunden."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.id }) { c -> CharacterCard(vm, c) }
                }
            }
        }
    }
}

private fun modelLine(vm: AppViewModel): String = when (val s = vm.engineState) {
    is EngineState.Ready -> tr("%s bereit · %s", s.model.title, tr(s.accelerator.label))
    is EngineState.Loading -> tr("%s wird geladen …", s.modelTitle)
    else -> if (vm.downloads[vm.selectedModel.id] == DownloadState.Done) tr("Modell: %s", vm.selectedModel.title)
    else tr("Noch kein Modell – oben rechts unter \"Einstellungen\" herunterladen")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CharacterCard(vm: AppViewModel, c: Character) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { Toast.makeText(context, vm.exportCharacter(c, uri), Toast.LENGTH_SHORT).show() }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("%s löschen?", c.name)) },
            text = { Text(tr("Die Figur und das Gespräch mit ihr sind danach weg.")) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteCharacter(c.id) }) {
                    Text(tr("Löschen"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Abbrechen")) } },
        )
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
            .clip(RoundedCornerShape(16.dp))
            // Tippen = Chat, lange drücken = Menü
            .combinedClickable(onClick = { vm.openChat(c) }, onLongClick = { menuOpen = true }),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(c, vm.repo.avatar(c), size = 84.dp)
                Spacer(Modifier.height(12.dp))
                Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    c.shortDescription,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (c.builtIn) tr(c.category.label) else tr("%s · eigene", tr(c.category.label)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, "Mehr", tint = Color.White.copy(alpha = 0.6f))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("Bearbeiten")) },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuOpen = false; vm.navigate(Screen.Editor(c.id)) },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Exportieren")) },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = {
                            menuOpen = false
                            exportLauncher.launch("${c.name.ifBlank { "Figur" }.replace(Regex("[^\\p{L}\\p{N} _-]"), "")}.figur.json")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Löschen"), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; confirmDelete = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentChatCard(vm: AppViewModel, c: Character, preview: String) {
    Card(
        onClick = { vm.openChat(c) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.width(280.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c, vm.repo.avatar(c), size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    preview,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RoomCard(vm: AppViewModel, r: de.davscha.characterfun.data.Room) {
    val members = vm.roomMembers(r)
    val last = r.messages.lastOrNull()
    val preview = when {
        last == null -> members.joinToString(", ") { it.name }
        last.fromUser -> tr("Du: %s", last.text)
        else -> "${vm.character(last.speakerId)?.name ?: "?"}: ${last.text}"
    }
    Card(
        onClick = { vm.openRoom(r) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.width(300.dp).height(72.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Avatare leicht überlappend
            Box(Modifier.width((28 + 18 * (members.size.coerceAtMost(4) - 1).coerceAtLeast(0)).dp)) {
                members.take(4).forEachIndexed { i, c ->
                    Box(Modifier.padding(start = (18 * i).dp)) { Avatar(c, vm.repo.avatar(c), size = 28.dp) }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(r.name.ifBlank { tr("Raum") }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(preview, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
