package de.davscha.characterfun.ui

import de.davscha.characterfun.Edition

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.davscha.characterfun.data.AiHelpers
import de.davscha.characterfun.data.Character
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.IconButton
import de.davscha.characterfun.i18n.tr
import kotlinx.coroutines.launch

/**
 * Bild als Nachricht: Du wählst ein Bild, die KI schaut es sich an und schreibt daraus deine Nachricht.
 * [allowAttach] = das Bild darf zusätzlich mitgehen (im Einzelchat); [onDone] bekommt Text und ggf. Bildpfad.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageMessageDialog(
    vm: AppViewModel,
    targets: List<Character>,
    allowAttach: Boolean,
    onClose: () -> Unit,
    onDone: (text: String, imagePath: String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(AiHelpers.imageModes.first()) }
    var result by remember { mutableStateOf("") }
    var attach by remember { mutableStateOf(false) }
    // Bild malen lassen statt auswählen
    var paintMode by remember { mutableStateOf(false) }
    var moment by remember { mutableStateOf(AppViewModel.SceneMoment()) }
    var paintLocation by remember { mutableStateOf(vm.chatLocationId) }
    // Wer ist mit auf dem Bild? (mehrere möglich – MOSS hält sie über die Namen auseinander)
    var who by remember { mutableStateOf<List<Character>>(emptyList()) }
    var painting by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }
    var suggesting by remember { mutableStateOf(false) }
    val busy = vm.helperBusy || painting

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            path = vm.importImage(uri)
            result = ""
            if (path == null) Toast.makeText(context, tr("Bild konnte nicht geladen werden"), Toast.LENGTH_LONG).show()
        }
    }
    fun openPicker() = pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun generate() {
        val p = path ?: return
        scope.launch {
            runCatching { vm.describeImage(p, hint, mode.second, targets) }
                .onSuccess { result = it }
                .onFailure { Toast.makeText(context, it.message ?: tr("Fehler"), Toast.LENGTH_LONG).show() }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Bild als Nachricht")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tr("Die KI schaut sich dein Bild an und macht daraus deine nächste Nachricht – als würdest du den Moment gerade erleben."),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Auswählen oder malen
                // Immer sichtbar: zwischen Auswählen und Malen wechseln – auch nach dem Malen
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !paintMode,
                        onClick = { paintMode = false; openPicker() },
                        enabled = !busy,
                        label = { Text("🖼️ " + tr("Bild auswählen")) },
                    )
                    if (Edition.PRO) FilterChip(
                        selected = paintMode,
                        onClick = { paintMode = true },
                        enabled = vm.comfyReady && !busy,
                        label = { Text("🎨 " + tr("Bild malen")) },
                    )
                }
                if (paintMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LocationPicker(vm, vm.location(paintLocation), enabled = !painting) { paintLocation = it }
                        }
                        // Wer ist mit auf dem Bild?
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = who.isEmpty(), onClick = { who = emptyList() }, label = { Text(tr("Niemand")) })
                            targets.forEach { t ->
                                val on = who.any { it.id == t.id }
                                FilterChip(selected = on, onClick = { who = if (on) who.filter { it.id != t.id } else who + t }, label = { Text("${t.emoji} ${t.name}") })
                            }
                        }
                        MomentEditor(who, moment, enabled = !painting && !suggesting, busy = suggesting, onAsk = {
                            suggesting = true
                            scope.launch {
                                val recent = if (targets.size > 1) vm.recentRoomText() else vm.recentChatText()
                                runCatching { vm.sceneMoment(who, recent, vm.location(paintLocation)) }.onSuccess { moment = it }
                                    .onFailure { Toast.makeText(context, it.message ?: tr("Fehler"), Toast.LENGTH_LONG).show() }
                                suggesting = false
                            }
                        }) { moment = it }
                        if (painting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(tr("ComfyUI malt … %s s", seconds))
                            }
                        } else {
                            Button(
                                onClick = {
                                    painting = true
                                    seconds = 0
                                    scope.launch {
                                        val again = path != null
                                        runCatching { vm.paintMoment(who, vm.location(paintLocation), moment, randomSeed = again) { seconds = it } }
                                            .onSuccess { p ->
                                                path = p
                                                result = ""
                                                // Selbst gemaltes Bild geht standardmäßig mit in den Chat
                                                attach = allowAttach
                                                // Die Figur ist selbst auf dem Bild – dann spricht die Nachricht sie direkt an
                                                if (hint.isBlank() && who.isNotEmpty()) hint = tr("Auf dem Bild ist %s.", who.joinToString(" & ") { it.name })
                                            }
                                            .onFailure { Toast.makeText(context, it.message ?: tr("Fehler"), Toast.LENGTH_LONG).show() }
                                        painting = false
                                    }
                                },
                                enabled = vm.comfyReady && (!moment.isEmpty || who.isEmpty()),
                            ) { Text(if (path == null) tr("Malen") else tr("Neu malen")) }
                        }
                    }
                }
                // Bild oder Platzhalter zum Antippen
                if (!paintMode || path != null) Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 260.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = !busy) { openPicker() },
                    contentAlignment = Alignment.Center,
                ) {
                    val p = path
                    if (p == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(40.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(tr("Bild auswählen"))
                        }
                    } else {
                        AsyncImage(
                            model = java.io.File(p),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    label = { Text(tr("Hinweis (optional)")) },
                    placeholder = { Text(tr("z. B. Ich bin am Strand angekommen / Das ist mein Kater Felix")) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiHelpers.imageModes.forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(tr(m.first)) })
                    }
                }
                if (vm.helperBusy && !painting && !suggesting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(tr("Gemma schaut sich das Bild an …"))
                    }
                } else if (busy) {
                    // ComfyUI malt gerade – oben steht der Fortschritt
                } else if (result.isBlank()) {
                    Button(onClick = { generate() }, enabled = path != null) { Text(tr("Nachricht schreiben")) }
                }
                if (result.isNotBlank() && !busy) {
                    OutlinedTextField(
                        value = result,
                        onValueChange = { result = it },
                        label = { Text(tr("Deine Nachricht")) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 8,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { generate() }) {
                            Icon(Icons.Default.Casino, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Neu würfeln"))
                        }
                        if (allowAttach) {
                            Spacer(Modifier.width(12.dp))
                            Checkbox(checked = attach, onCheckedChange = { attach = it })
                            Text(tr("Bild mitschicken"), fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDone(result.trim(), if (attach) path else null); onClose() },
                enabled = result.isNotBlank() && !busy,
            ) { Text(tr("Ins Eingabefeld")) }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text(tr("Abbrechen")) } },
    )
}
