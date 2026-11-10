package de.davscha.characterfun.ui

import de.davscha.characterfun.data.ImagePrompt

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
    var moment by remember { mutableStateOf(AppViewModel.SceneMoment()) }
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
            runCatching { vm.sceneMoment(listOf(c), recent(), vm.location(locationId)) }.onSuccess { moment = it }.onFailure { error = it.message }
            describing = false
        }
    }
    fun paint() {
        painting = true
        error = null
        seconds = 0
        scope.launch {
            runCatching { vm.paintMoment(listOf(c), vm.location(locationId), moment, randomSeed = rounds > 0, outfits = mapOf(c.id to outfit)) { seconds = it } }
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
                MomentEditor(listOf(c), moment, enabled = !describing && !painting, busy = describing, onAsk = { describe() }) { moment = it }
                if (!c.hasLooks) {
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

/**
 * Der Moment fürs Bild: pro Figur Haltung und Gesichtsausdruck, dazu das Zusammenspiel (action) und die Kamera.
 * Gemma füllt alles aus ([onAsk]), du kannst jedes Feld ändern.
 */
@Composable
fun MomentEditor(
    people: List<Character>, m: AppViewModel.SceneMoment, enabled: Boolean, busy: Boolean, onAsk: () -> Unit,
    onChange: (AppViewModel.SceneMoment) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Was passiert gerade?") + " (English)", fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onAsk, enabled = enabled) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.AutoFixHigh, tr("Gemma beschreibt den Moment"))
            }
        }
        people.forEach { c ->
            val mo = m.poses[c.id] ?: ImagePrompt.Moment()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = mo.pose, onValueChange = { v -> onChange(m.copy(poses = m.poses + (c.id to mo.copy(pose = v)))) },
                    label = { Text(tr("Haltung von %s", c.name)) }, placeholder = { Text("sitting, leaning back, knees up") },
                    enabled = enabled, maxLines = 3, modifier = Modifier.weight(1.4f),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = mo.expression, onValueChange = { v -> onChange(m.copy(poses = m.poses + (c.id to mo.copy(expression = v)))) },
                    label = { Text(tr("Gesicht")) }, placeholder = { Text("slight smile") },
                    enabled = enabled, maxLines = 2, modifier = Modifier.weight(1f),
                )
            }
        }
        androidx.compose.material3.OutlinedTextField(
            value = m.action, onValueChange = { onChange(m.copy(action = it)) },
            label = { Text(if (people.size > 1) tr("Zusammenspiel (mit Namen)") else tr("Handlung mit Dingen und Umgebung")) },
            placeholder = { Text(if (people.size > 1) "Angus stands behind Alice, his hands on her shoulders" else "she holds a cup of coffee with both hands") },
            enabled = enabled, maxLines = 3, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = m.camera, onValueChange = { onChange(m.copy(camera = it)) }, label = { Text(tr("Kamera")) }, placeholder = { Text("cowboy shot, dutch angle") },
                enabled = enabled, singleLine = true, modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.OutlinedTextField(
                value = m.lights, onValueChange = { onChange(m.copy(lights = it)) }, label = { Text(tr("Licht")) }, placeholder = { Text("warm evening light") },
                enabled = enabled, singleLine = true, modifier = Modifier.weight(1f),
            )
        }
    }
}
