package de.davscha.characterfun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import de.davscha.characterfun.data.Character
import de.davscha.characterfun.data.FundusItem
import de.davscha.characterfun.i18n.tr
import kotlinx.coroutines.launch

/** Auswahl eines Ortes aus dem Fundus (Knopf mit Menü) */
@Composable
fun LocationPicker(vm: AppViewModel, current: FundusItem?, enabled: Boolean = true, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) {
            Text("📍 " + (current?.name ?: tr("Kein Ort")), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(tr("Kein Ort")) }, onClick = { open = false; onPick("") })
            vm.fundus.locations.forEach { l ->
                DropdownMenuItem(text = { Text(l.name) }, onClick = { open = false; onPick(l.id) })
            }
        }
    }
}

/** Auswahl der Kleidung: erst der Schrank der Figur, mit „alle zeigen“ der ganze Fundus */
@Composable
fun OutfitPicker(vm: AppViewModel, c: Character, current: FundusItem?, enabled: Boolean = true, noneLabel: String? = null, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var all by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) {
            Text("👗 " + (current?.name ?: noneLabel ?: tr("Standard")), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(noneLabel ?: (tr("Standard-Outfit") + (vm.clothing(c.defaultOutfitId)?.let { " (${it.name})" } ?: ""))) },
                onClick = { open = false; onPick("") },
            )
            val list = if (all || c.wardrobe.isEmpty()) vm.fundus.clothing else c.wardrobe.mapNotNull { vm.clothing(it) }
            list.forEach { o ->
                DropdownMenuItem(text = { Text(o.name) }, onClick = { open = false; onPick(o.id) })
            }
            if (c.wardrobe.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(if (all) tr("Nur der eigene Schrank") else tr("Alle aus dem Fundus zeigen")) },
                    onClick = { all = !all },
                )
            }
        }
    }
}

/**
 * „Bild von der Szene“: Ort und Kleidung wählen, Gemma beschreibt den Moment als Tags, ComfyUI malt.
 * [onDone] bekommt Bildpfad, Ort-Id und Kleidungs-Id (damit der Chat die Auswahl übernehmen kann).
 */
@Composable
fun SceneImageDialog(
    vm: AppViewModel,
    c: Character,
    initialLocationId: String,
    initialOutfitId: String,
    recent: () -> String,
    allowOutfit: Boolean = true,
    onClose: () -> Unit,
    onDone: (path: String, locationId: String, outfitId: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var locationId by remember { mutableStateOf(initialLocationId) }
    var outfitId by remember { mutableStateOf(initialOutfitId) }
    var tags by remember { mutableStateOf("") }
    var describing by remember { mutableStateOf(false) }
    var painting by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }
    var image by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var rounds by remember { mutableStateOf(0) }
    val outfit = vm.clothing(outfitId.ifEmpty { c.defaultOutfitId })

    fun describe() {
        describing = true
        scope.launch {
            runCatching { vm.sceneTags(c, recent()) }.onSuccess { tags = it }.onFailure { error = it.message }
            describing = false
        }
    }
    fun paint() {
        painting = true
        error = null
        seconds = 0
        scope.launch {
            runCatching { vm.paintScene(c, vm.location(locationId), outfit, tags, randomSeed = rounds > 0) { seconds = it } }
                .onSuccess { image = it; rounds++ }
                .onFailure { error = it.message ?: tr("Fehler") }
            painting = false
        }
    }
    // Gemma beschreibt den Moment gleich beim Öffnen
    LaunchedEffect(Unit) { describe() }

    AlertDialog(
        onDismissRequest = { if (!painting) onClose() },
        title = { Text(tr("Bild von der Szene")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LocationPicker(vm, vm.location(locationId), enabled = !painting) { locationId = it }
                    if (allowOutfit) OutfitPicker(vm, c, vm.clothing(outfitId), enabled = !painting) { outfitId = it }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text(tr("Was passiert gerade?") + " (English)") },
                        placeholder = { Text("She waves and smiles, upper body, warm light") },
                        minLines = 2,
                        enabled = !describing && !painting,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { describe() }, enabled = !describing && !painting) {
                        if (describing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.AutoFixHigh, tr("Gemma beschreibt den Moment"))
                    }
                }
                if (c.appearance.isBlank()) {
                    Text(
                        tr("Tipp: Im Figurenblatt das Aussehen eintragen, dann sieht %s auf jedem Bild gleich aus.", c.name),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val p = image
                    when {
                        painting -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.size(8.dp))
                            Text(tr("ComfyUI malt … %s s", seconds))
                        }
                        p != null -> AsyncImage(model = java.io.File(p), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        else -> Button(onClick = { paint() }, enabled = vm.comfyReady && !describing) {
                            Icon(Icons.Default.PhotoCamera, null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Malen"))
                        }
                    }
                }
                error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                ComfyStatusBar(vm)
            }
        },
        confirmButton = {
            TextButton(onClick = { image?.let { onDone(it, locationId, outfitId) }; onClose() }, enabled = image != null && !painting) {
                Text(tr("In den Chat"))
            }
        },
        dismissButton = {
            Row {
                if (image != null) TextButton(onClick = { paint() }, enabled = !painting && vm.comfyReady) { Text(tr("Neu malen")) }
                TextButton(onClick = onClose, enabled = !painting) { Text(tr("Abbrechen")) }
            }
        },
    )
}

/** Bild groß ansehen – Tipp irgendwo schließt */
@Composable
fun FullImageDialog(path: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.92f)).clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(model = java.io.File(path), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp))
        }
    }
}
