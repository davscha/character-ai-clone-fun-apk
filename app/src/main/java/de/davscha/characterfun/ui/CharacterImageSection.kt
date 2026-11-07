package de.davscha.characterfun.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.davscha.characterfun.data.Character
import de.davscha.characterfun.data.FundusItem
import de.davscha.characterfun.data.FundusKind
import de.davscha.characterfun.i18n.tr
import kotlinx.coroutines.launch

/** Figurenblatt: Aussehen, Startwert, Stil, Schrank und Anprobieren (Bilder über ComfyUI). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterImageSection(vm: AppViewModel, c: Character, onChange: (Character) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var painting by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var helping by remember { mutableStateOf(false) }
    var styleMenu by remember { mutableStateOf(false) }
    var tryOn by remember { mutableStateOf<FundusItem?>(null) }

    tryOn?.let { TryOnDialog(vm, c, it, onAvatar = { name -> onChange(c.copy(avatarFile = name)) }) { tryOn = null } }

    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Text(tr("Bilder (ComfyUI)"), fontWeight = FontWeight.SemiBold)
    ComfyStatusBar(vm)

    // ---- Aussehen ----
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = c.appearance,
            onValueChange = { onChange(c.copy(appearance = it)) },
            label = { Text(tr("Aussehen (Tags, ohne Kleidung)")) },
            placeholder = { Text("long hair, blonde hair, green eyes, glasses") },
            minLines = 2,
            modifier = Modifier.weight(1f),
        )
        // Gemma liest den Steckbrief und schreibt Tags
        IconButton(
            onClick = {
                helping = true
                scope.launch {
                    val text = listOf(c.name, c.shortDescription, c.longDescription, c.definition).filter { it.isNotBlank() }.joinToString(". ")
                    runCatching { vm.tagsFromDescription(text, null) }
                        .onSuccess { onChange(c.copy(appearance = it)) }
                        .onFailure { Toast.makeText(context, it.message ?: tr("Fehler"), Toast.LENGTH_LONG).show() }
                    helping = false
                }
            },
            enabled = !helping && (c.longDescription.isNotBlank() || c.shortDescription.isNotBlank()),
        ) {
            if (helping) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.AutoFixHigh, tr("Aus dem Steckbrief schreiben lassen"))
        }
    }

    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val file = vm.fundusImage(c.appearancePreview)
            if (file != null) AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(c.emoji.ifBlank { "🙂" }, fontSize = 48.sp)
            if (painting) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("$seconds s", fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    painting = true
                    error = null
                    seconds = 0
                    scope.launch {
                        runCatching { vm.paintCharacter(c, null) { seconds = it } }
                            .onSuccess { onChange(c.copy(appearancePreview = it)) }
                            .onFailure { error = it.message ?: tr("Fehler") }
                        painting = false
                    }
                },
                enabled = vm.comfyReady && c.appearance.isNotBlank() && !painting,
            ) {
                Icon(Icons.Default.Brush, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (c.appearancePreview.isBlank()) tr("Vorschau malen") else tr("Neu malen"))
            }
            if (c.appearancePreview.isNotBlank()) {
                TextButton(onClick = {
                    scope.launch {
                        vm.adoptAvatar(c.appearancePreview, c.id, fullBody = false)?.let { onChange(c.copy(avatarFile = it)) }
                    }
                }) { Text(tr("Als Avatar übernehmen")) }
            }
            Text(
                tr("Mit Standard-Outfit, falls gewählt."),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }

    // ---- Startwert und Stil ----
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = if (c.imageSeed > 0) c.imageSeed.toString() else "",
            onValueChange = { v -> onChange(c.copy(imageSeed = v.filter { it.isDigit() }.take(12).toLongOrNull() ?: 0)) },
            label = { Text(tr("Startwert (leer = Zufall)")) },
            singleLine = true,
            modifier = Modifier.width(220.dp),
        )
        IconButton(onClick = { onChange(c.copy(imageSeed = (1..999_999_999L).random())) }) { Icon(Icons.Default.Casino, tr("Würfeln")) }
        Box {
            TextButton(onClick = { styleMenu = true }) {
                Text("🎨 " + (vm.fundus.styles.firstOrNull { it.id == c.styleId }?.name ?: tr("Standard-Stil")))
            }
            DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                DropdownMenuItem(text = { Text(tr("Standard-Stil")) }, onClick = { styleMenu = false; onChange(c.copy(styleId = "")) })
                vm.fundus.styles.forEach { s ->
                    DropdownMenuItem(text = { Text(s.name) }, onClick = { styleMenu = false; onChange(c.copy(styleId = s.id)) })
                }
            }
        }
    }
    Text(
        tr("Ein fester Startwert sorgt dafür, dass die Figur auf allen Bildern ähnlicher aussieht."),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // ---- Schrank ----
    Text(tr("Schrank – was hat %s zum Anziehen?", c.name.ifBlank { tr("die Figur") }), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (vm.fundus.clothing.isEmpty()) {
        Text(tr("Noch keine Kleidung im Fundus."), fontSize = 13.sp)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.fundus.clothing.forEach { item ->
            val inside = item.id in c.wardrobe
            FilterChip(
                selected = inside,
                onClick = {
                    val w = if (inside) c.wardrobe - item.id else c.wardrobe + item.id
                    onChange(c.copy(wardrobe = w, defaultOutfitId = if (inside && c.defaultOutfitId == item.id) "" else c.defaultOutfitId))
                },
                label = { Text(item.name) },
            )
        }
    }
    // Ausgewählte Teile: Standard festlegen und anprobieren
    c.wardrobe.mapNotNull { vm.clothing(it) }.forEach { item ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(c.copy(defaultOutfitId = if (c.defaultOutfitId == item.id) "" else item.id)) }) {
                Icon(
                    if (c.defaultOutfitId == item.id) Icons.Default.Star else Icons.Default.StarBorder,
                    tr("Standard-Outfit"),
                    tint = if (c.defaultOutfitId == item.id) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(item.name, modifier = Modifier.weight(1f))
            TextButton(onClick = { tryOn = item }, enabled = vm.comfyReady) { Text("👗 " + tr("Anprobieren")) }
        }
    }
    if (c.wardrobe.isNotEmpty()) {
        Text(tr("Stern = Standard-Outfit (das trägt die Figur, wenn nichts anderes gewählt ist)."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Die Figur in einem Outfit malen – mit „Als Avatar übernehmen“ */
@Composable
private fun TryOnDialog(vm: AppViewModel, c: Character, outfit: FundusItem, onAvatar: (String) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var image by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var seconds by remember { mutableStateOf(0) }
    var round by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(round) {
        image = null
        error = null
        seconds = 0
        runCatching { vm.paintCharacter(c, outfit, randomSeed = round > 0) { seconds = it } }
            .onSuccess { image = it }
            .onFailure { error = it.message ?: tr("Fehler") }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("%s in „%s“", c.name, outfit.name)) },
        text = {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val file = image?.let { vm.fundusImage(it) }
                when {
                    file != null -> AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.size(8.dp))
                        Text(tr("ComfyUI malt … %s s", seconds))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Schließen")) } },
        dismissButton = {
            Row {
                TextButton(onClick = { round++ }, enabled = image != null || error != null) { Text(tr("Neu malen")) }
                TextButton(
                    onClick = {
                        val img = image ?: return@TextButton
                        scope.launch {
                            vm.adoptAvatar(img, c.id, fullBody = true)?.let { onAvatar(it) }
                            onClose()
                        }
                    },
                    enabled = image != null,
                ) { Text(tr("Als Avatar übernehmen")) }
            }
        },
    )
}
