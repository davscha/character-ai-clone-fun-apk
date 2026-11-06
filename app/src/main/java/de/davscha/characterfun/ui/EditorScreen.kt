package de.davscha.characterfun.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.characterfun.data.Category
import de.davscha.characterfun.data.Character
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
                if (characterId == null) "Neue Figur" else "Figur bearbeiten",
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
                Text("Speichern")
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
                            Text("Bild wählen")
                        }
                        if (c.avatarFile.isNotEmpty()) {
                            TextButton(onClick = { c = c.copy(avatarFile = "") }) { Text("Bild entfernen") }
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    OutlinedTextField(
                        value = c.emoji,
                        onValueChange = { c = c.copy(emoji = it.take(8)) },
                        label = { Text("oder Emoji") },
                        singleLine = true,
                        modifier = Modifier.width(140.dp),
                    )
                }

                Field("Name", c.name, "z. B. Kapitän Kraken") { c = c.copy(name = it) }

                Text("Kategorie", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { cat ->
                        FilterChip(selected = c.category == cat, onClick = { c = c.copy(category = cat) }, label = { Text(cat.label) })
                    }
                }

                Field(
                    "Kurzbeschreibung", c.shortDescription,
                    "Ein Satz für die Karte, z. B. \"Angeberischer Tintenfisch-Pirat\"",
                ) { c = c.copy(shortDescription = it) }

                Field(
                    "Begrüßung", c.greeting,
                    "Der erste Satz der Figur im Chat", minLines = 2,
                ) { c = c.copy(greeting = it) }

                Field(
                    "Definition (Persönlichkeit)", c.definition,
                    "Wie denkt, redet und verhält sich die Figur? Kurze, klare Sätze funktionieren am besten.",
                    minLines = 5,
                ) { c = c.copy(definition = it) }

                Field(
                    "Hintergrund (optional)", c.longDescription,
                    "Aussehen, Vorgeschichte, Umgebung …", minLines = 3,
                ) { c = c.copy(longDescription = it) }

                Text("Gesprächsstarter (optional)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                starters.forEachIndexed { i, s ->
                    OutlinedTextField(
                        value = s,
                        onValueChange = { v -> starters = starters.toMutableList().also { it[i] = v } },
                        placeholder = { Text("Vorschlag ${i + 1}") },
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
            title = { Text("${c.name.ifBlank { "Figur" }} löschen?") },
            text = { Text("Die Figur ist danach weg.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteCharacter(c.id)
                    vm.back()
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
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
