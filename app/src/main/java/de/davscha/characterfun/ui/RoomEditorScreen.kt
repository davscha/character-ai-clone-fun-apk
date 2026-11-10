package de.davscha.characterfun.ui

import kotlinx.coroutines.launch

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.characterfun.data.Room
import de.davscha.characterfun.i18n.tr

/** Raum anlegen oder ändern: Name, zwei bis vier Figuren, Szene. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomEditorScreen(vm: AppViewModel, roomId: String?) {
    val existing = roomId?.let { vm.room(it) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var members by remember { mutableStateOf(existing?.memberIds ?: emptyList()) }
    var scenario by remember { mutableStateOf(existing?.scenario ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val valid = members.size in 2..MAX_MEMBERS

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Raum löschen?")) },
            text = { Text(tr("Der Raum und sein Gespräch werden gelöscht. Die Figuren bleiben.")) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteRoom(existing.id); vm.back() }) { Text(tr("Löschen")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Abbrechen")) } },
        )
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Zurück")) }
            Text(
                if (existing == null) tr("Neuer Raum") else tr("Raum bearbeiten"),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (existing != null) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    if (uri != null) scope.launch { android.widget.Toast.makeText(ctx, vm.exportRoom(existing, uri), android.widget.Toast.LENGTH_LONG).show() }
                }
                TextButton(onClick = { exportLauncher.launch("${existing.name.ifBlank { "Raum" }.replace(Regex("[^\\p{L}\\p{N} _-]"), "")}.raum.json") }) {
                    Text("📤 " + tr("Exportieren"))
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, tr("Löschen")) }
            }
            Button(
                enabled = valid,
                onClick = {
                    val names = members.mapNotNull { vm.character(it)?.name }
                    val room = (existing ?: Room()).copy(
                        name = name.trim().ifBlank { names.joinToString(" & ") },
                        memberIds = members,
                        scenario = scenario.trim(),
                    )
                    vm.saveRoom(room)
                    if (existing == null) {
                        vm.back()
                        vm.openRoom(room)
                    } else vm.back()
                },
            ) { Text(if (existing == null) tr("Raum öffnen") else tr("Speichern")) }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(tr("Name des Raums")) },
                placeholder = { Text(tr("z. B. Teestunde bei Oma Hilde")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(tr("Figuren (%s von 2–4)", members.size), fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.characters.forEach { c ->
                    val selected = c.id in members
                    FilterChip(
                        selected = selected,
                        enabled = selected || members.size < MAX_MEMBERS,
                        onClick = { members = if (selected) members - c.id else members + c.id },
                        label = { Text("${c.emoji} ${c.name}") },
                    )
                }
            }

            OutlinedTextField(
                value = scenario,
                onValueChange = { scenario = it },
                label = { Text(tr("Szene (optional)")) },
                placeholder = { Text(tr("Wo treffen sich alle? z. B. Alle sitzen zusammen auf einem Piratenschiff im Sturm.")) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                tr("Im Raum antworten alle Figuren nacheinander. Mit „Weiter“ reden sie ohne dich weiter, ein Tipp auf einen Avatar bestimmt, wer als Nächstes spricht."),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MAX_MEMBERS = 4
