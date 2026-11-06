package de.davscha.characterfun.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var query by rememberSaveable { mutableStateOf("") }
    // null = "Alle", "eigene" = selbst erstellte, sonst Name der Kategorie
    var filter by rememberSaveable { mutableStateOf<String?>(null) }

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
                text = { Text("Figur erstellen") },
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
                    Text("Character Fun", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(modelLine(vm), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Figur suchen") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.widthIn(max = 360.dp),
                )
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { vm.navigate(Screen.Models) }) {
                    Icon(Icons.Default.Memory, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Modelle")
                }
            }

            vm.crashNotice?.let {
                Box(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) { CrashNotice(it) { vm.dismissCrashNotice() } }
            }

            // Kategorie-Tabs
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Alle") }) }
                item { FilterChip(selected = filter == "eigene", onClick = { filter = "eigene" }, label = { Text("Eigene") }) }
                items(Category.entries.filter { cat -> vm.characters.any { it.category == cat } }) { cat ->
                    FilterChip(selected = filter == cat.name, onClick = { filter = cat.name }, label = { Text(cat.label) })
                }
            }

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (filter == "eigene" && query.isBlank()) "Noch keine eigenen Figuren – tippe auf \"Figur erstellen\"."
                        else "Keine Figur gefunden.",
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
    is EngineState.Ready -> "${s.model.title} bereit · ${s.accelerator.label}"
    is EngineState.Loading -> "${s.modelTitle} wird geladen …"
    else -> if (vm.downloads[vm.selectedModel.id] == DownloadState.Done) "Modell: ${vm.selectedModel.title}"
    else "Noch kein Modell – oben rechts unter \"Modelle\" herunterladen"
}

@Composable
private fun CharacterCard(vm: AppViewModel, c: Character) {
    Card(
        onClick = { vm.openChat(c) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().height(236.dp),
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
                    if (c.builtIn) c.category.label else "${c.category.label} · eigene",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(
                onClick = { vm.navigate(Screen.Editor(c.id)) },
                modifier = Modifier.align(Alignment.TopEnd),
            ) { Icon(Icons.Default.Edit, "Bearbeiten", tint = Color.White.copy(alpha = 0.6f)) }
        }
    }
}
