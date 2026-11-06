package de.davscha.characterfun.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.characterfun.model.ModelCatalog
import de.davscha.characterfun.model.ModelInfo

/** Seite "Modelle": herunterladen, auswählen, Speicherort, Kreativität. */
@Composable
fun ModelsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Text("Modelle", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StorageHint(vm)
                ModelCatalog.all.forEach { model -> ModelCard(vm, model) }
                EngineStatus(vm)

                SectionTitle("Kreativität")
                Text(
                    "%.1f – niedrig = vorhersehbar, hoch = verrückter. Gilt ab dem nächsten Gespräch.".format(vm.temperature),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = vm.temperature.toFloat(),
                    onValueChange = { vm.changeTemperature((it * 10).toInt() / 10.0) },
                    valueRange = 0.1f..1.5f,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** Bittet um die Erlaubnis, Modelle dauerhaft in /CharacterFun/Modelle zu speichern. */
@Composable
private fun StorageHint(vm: AppViewModel) {
    val context = LocalContext.current
    if (vm.sharedStorage) {
        Text(
            "Gespeichert in CharacterFun/Modelle – bleibt auch bei Neuinstallation erhalten.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Modelle dauerhaft speichern", fontWeight = FontWeight.SemiBold)
        Text(
            "Ohne Erlaubnis liegen die Modelle in der App und sind nach einer Neuinstallation weg. " +
                "Mit \"Zugriff auf alle Dateien\" landen sie im Ordner CharacterFun/Modelle und bleiben.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
            )
        }) { Text("Erlaubnis erteilen") }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ModelCard(vm: AppViewModel, model: ModelInfo) {
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
            is DownloadState.Moving -> {
                val fraction = if (state.total > 0) state.copied.toFloat() / state.total else 0f
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Text("Wird in den dauerhaften Ordner verschoben …", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DownloadState.Done -> Text("Bereit", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun EngineStatus(vm: AppViewModel) {
    val downloaded = vm.downloads[vm.selectedModel.id] is DownloadState.Done
    when (val s = vm.engineState) {
        EngineState.Idle -> Text(
            if (downloaded) "${vm.selectedModel.title} wird geladen, sobald du einen Chat öffnest."
            else "Lade ${vm.selectedModel.title} herunter, um loszulegen.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is EngineState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("${s.modelTitle} wird geladen …", fontSize = 13.sp)
        }

        is EngineState.Ready -> Text(
            "${s.model.title} läuft auf dem ${s.accelerator.label} · geladen in %.1f s".format(s.loadSeconds),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary,
        )

        is EngineState.Failed -> Column {
            Text("Laden fehlgeschlagen: ${s.message}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)

        }
    }
}

