package de.davscha.characterfun.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.davscha.characterfun.data.FundusItem
import de.davscha.characterfun.data.FundusKind
import de.davscha.characterfun.data.StyleItem
import de.davscha.characterfun.i18n.tr
import kotlinx.coroutines.launch

/** Fundus: Orte, Kleidung und Stile für alle Figuren – mit Vorschaubildern aus ComfyUI. */
@Composable
fun FundusScreen(vm: AppViewModel) {
    // 0 = Orte, 1 = Kleidung, 2 = Stile
    var tab by rememberSaveable { mutableStateOf(0) }
    var editItem by remember { mutableStateOf<FundusItem?>(null) }
    var editStyle by remember { mutableStateOf<StyleItem?>(null) }
    val kind = if (tab == 0) FundusKind.LOCATION else FundusKind.CLOTHING

    editItem?.let { FundusItemDialog(vm, kind, it) { editItem = null } }
    editStyle?.let { StyleDialog(vm, it) { editStyle = null } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (tab == 2) editStyle = StyleItem() else editItem = FundusItem() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(tr("Hinzufügen")) },
                containerColor = MaterialTheme.colorScheme.primary,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Zurück")) }
                Column(Modifier.weight(1f)) {
                    Text(tr("Fundus"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("Orte, Kleidung und Stile für alle Figuren – für Bilder, Chat und Geschichten."),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("🏞️ " + tr("Orte") + " (${vm.fundus.locations.size})") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("👗 " + tr("Kleidung") + " (${vm.fundus.clothing.size})") })
                FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("🎨 " + tr("Stile") + " (${vm.fundus.styles.size})") })
            }
            // Wie bei Ollama: PC nicht erreichbar -> Bild-Knöpfe ausgegraut
            androidx.compose.runtime.LaunchedEffect(Unit) { if (!vm.comfyOk) vm.testComfy() }
            ComfyStatusBar(vm, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (tab == 2) {
                    items(vm.fundus.styles, key = { it.id }) { s ->
                        FundusCard(
                            vm, s.id, s.preview, "🎨", s.name.ifBlank { s.prompt.take(30) },
                            s.prompt,
                            isDefault = vm.fundus.defaultStyle?.id == s.id,
                        ) { editStyle = s }
                    }
                } else {
                    val list = if (tab == 0) vm.fundus.locations else vm.fundus.clothing
                    items(list, key = { it.id }) { item ->
                        FundusCard(vm, item.id, item.preview, if (tab == 0) "🏞️" else "👗", item.name, item.tags) { editItem = item }
                    }
                }
            }
        }
    }
}

@Composable
private fun FundusCard(
    vm: AppViewModel, id: String, preview: String, emoji: String, title: String, subtitle: String,
    isDefault: Boolean = false, onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        PreviewBox(vm, id, preview, emoji, Modifier.fillMaxWidth().aspectRatio(1f))
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (isDefault) Icon(Icons.Default.Star, tr("Standard"), tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            }
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Vorschaubild, Platzhalter oder Ladekreis */
@Composable
private fun PreviewBox(vm: AppViewModel, id: String, preview: String, emoji: String, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val file = vm.fundusImage(preview)
        if (file != null) {
            AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(emoji, fontSize = 44.sp)
        }
        if (vm.previewBusy[id] == true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

/** Ort oder Kleidung bearbeiten */
@Composable
private fun FundusItemDialog(vm: AppViewModel, kind: FundusKind, original: FundusItem, onClose: () -> Unit) {
    var item by remember { mutableStateOf(original) }
    var description by remember { mutableStateOf("") }
    var helping by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isNew = vm.fundus.let { f -> (if (kind == FundusKind.LOCATION) f.locations else f.clothing).none { it.id == original.id } }
    // Vorschau kommt aus dem gespeicherten Eintrag (wird im Hintergrund aktualisiert)
    val saved = (if (kind == FundusKind.LOCATION) vm.fundus.locations else vm.fundus.clothing).firstOrNull { it.id == item.id }
    val preview = saved?.preview ?: item.preview

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("„%s“ löschen?", item.name)) },
            confirmButton = { TextButton(onClick = { vm.deleteFundusItem(kind, item.id); onClose() }) { Text(tr("Löschen")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Abbrechen")) } },
        )
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (kind == FundusKind.LOCATION) tr("Ort") else tr("Kleidung")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = item.name,
                    onValueChange = { item = item.copy(name = it) },
                    label = { Text(tr("Name")) },
                    placeholder = { Text(if (kind == FundusKind.LOCATION) tr("z. B. Gemütliches Café") else tr("z. B. Alltag mit Mantel")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TagInput(
                    value = item.tags,
                    onChange = { item = item.copy(tags = it) },
                    label = tr("Tags (Englisch, mit Kommas)"),
                    field = if (kind == FundusKind.LOCATION) de.davscha.characterfun.data.TagField.LOCATION else de.davscha.characterfun.data.TagField.CLOTHING,
                    placeholder = if (kind == FundusKind.LOCATION) "cafe, indoors, warm lighting" else "brown coat, white sweater, black pants",
                    modifier = Modifier.fillMaxWidth(),
                )
                // Gemma hilft beim Schreiben der Tags
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(tr("Oder beschreiben, Gemma macht Tags daraus")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            helping = true
                            scope.launch {
                                runCatching { vm.tagsFromDescription(description, kind) }
                                    .onSuccess { tags -> item = item.copy(tags = tags, name = item.name.ifBlank { description.trim() }) }
                                    .onFailure { Toast.makeText(context, it.message ?: tr("Fehler"), Toast.LENGTH_LONG).show() }
                                helping = false
                            }
                        },
                        enabled = description.isNotBlank() && !helping,
                    ) {
                        if (helping) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.AutoFixHigh, tr("Tags schreiben lassen"))
                    }
                }
                if (kind == FundusKind.CLOTHING) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("Vorschau trägt:"), fontSize = 13.sp)
                        FilterChip(selected = item.model == "1girl", onClick = { item = item.copy(model = "1girl") }, label = { Text("👩 " + tr("Frau")) })
                        FilterChip(selected = item.model == "1boy", onClick = { item = item.copy(model = "1boy") }, label = { Text("👨 " + tr("Mann")) })
                    }
                }
                PreviewBox(
                    vm, item.id, preview, if (kind == FundusKind.LOCATION) "🏞️" else "👗",
                    Modifier.size(300.dp).clip(RoundedCornerShape(12.dp)),
                )
                vm.previewError[item.id]?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(
                    onClick = { vm.paintPreview(kind, item.copy(preview = preview)) },
                    enabled = vm.comfyReady && item.tags.isNotBlank() && vm.previewBusy[item.id] != true,
                ) {
                    Icon(Icons.Default.Brush, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (preview.isBlank()) tr("Vorschau malen") else tr("Neu malen"))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.saveFundusItem(kind, item.copy(name = item.name.trim(), tags = item.tags.trim(), preview = preview)); onClose() },
                enabled = item.name.isNotBlank() && item.tags.isNotBlank(),
            ) { Text(tr("Speichern")) }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { confirmDelete = true }) { Text(tr("Löschen"), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onClose) { Text(tr("Abbrechen")) }
            }
        },
    )
}

/** Stil (in Worten) bearbeiten */
@Composable
private fun StyleDialog(vm: AppViewModel, original: StyleItem, onClose: () -> Unit) {
    var style by remember { mutableStateOf(original) }
    var makeDefault by remember { mutableStateOf(vm.fundus.defaultStyle?.id == original.id) }
    var loraMenu by remember { mutableStateOf(false) }
    val isNew = vm.fundus.styles.none { it.id == original.id }
    val preview = vm.fundus.styles.firstOrNull { it.id == style.id }?.preview ?: style.preview

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Stil")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = style.name,
                    onValueChange = { style = style.copy(name = it) },
                    label = { Text(tr("Name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = style.prompt,
                    onValueChange = { style = style.copy(prompt = it) },
                    label = { Text(tr("Stil in Worten (Englisch)")) },
                    placeholder = { Text("classic Disney animation style, soft colors") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(tr("Steht vorne in jedem Bild-Prompt. MOSS Image versteht ganze Sätze und Wortgruppen."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = makeDefault, onCheckedChange = { makeDefault = it })
                    Text(tr("Standard-Stil für alle Bilder"))
                }
                PreviewBox(vm, style.id, preview, "🎨", Modifier.size(300.dp).clip(RoundedCornerShape(12.dp)))
                vm.previewError[style.id]?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(
                    onClick = { vm.paintStylePreview(style.copy(preview = preview)) },
                    enabled = vm.comfyReady && style.prompt.isNotBlank() && vm.previewBusy[style.id] != true,
                ) {
                    Icon(Icons.Default.Brush, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (preview.isBlank()) tr("Vorschau malen") else tr("Neu malen"))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.saveStyle(style.copy(name = style.name.trim().ifBlank { style.prompt.take(30) }, preview = preview), makeDefault); onClose() },
                enabled = style.prompt.isNotBlank(),
            ) { Text(tr("Speichern")) }
        },
        dismissButton = {
            Row {
                if (!isNew && vm.fundus.styles.size > 1) TextButton(onClick = { vm.deleteStyle(style.id); onClose() }) {
                    Text(tr("Löschen"), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onClose) { Text(tr("Abbrechen")) }
            }
        },
    )
}

/** Hinweis, wenn ComfyUI fehlt oder der PC nicht erreichbar ist – mit „Erneut prüfen“ */
@Composable
fun ComfyStatusBar(vm: AppViewModel, modifier: Modifier = Modifier) {
    when {
        !vm.comfyConfigured -> Text(
            tr("Für Bilder erst ComfyUI in den Einstellungen eintragen."),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
        vm.comfyTesting && !vm.comfyOk -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(tr("Prüfe Verbindung zum PC …"), fontSize = 13.sp)
        }
        !vm.comfyOk -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("PC bzw. ComfyUI nicht erreichbar – Bilder sind ausgegraut."),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(onClick = { vm.testComfy() }) { Text(tr("Erneut prüfen")) }
        }
    }
}
