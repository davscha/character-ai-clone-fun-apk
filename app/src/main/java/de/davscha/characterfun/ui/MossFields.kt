package de.davscha.characterfun.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.characterfun.data.Character
import de.davscha.characterfun.data.FundusItem
import de.davscha.characterfun.i18n.tr
import kotlinx.coroutines.launch

/** Auf Deutsch beschreiben – Gemma füllt die englischen Felder */
@Composable
fun GemmaHelper(label: String, placeholder: String, run: suspend (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, placeholder = { Text(placeholder) }, maxLines = 3, modifier = Modifier.weight(1f))
        IconButton(onClick = {
            busy = true
            scope.launch {
                runCatching { run(text) }.onFailure { Toast.makeText(context, it.message ?: tr("Fehler"), Toast.LENGTH_LONG).show() }
                busy = false
            }
        }, enabled = !busy) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.AutoFixHigh, tr("Gemma füllt aus"))
        }
    }
}

@Composable
private fun Field(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, placeholder = { Text(placeholder) }, maxLines = 3, modifier = Modifier.fillMaxWidth())
}

/** Aussehen einer Figur für MOSS Image (Englisch) */
@Composable
fun LooksFields(vm: AppViewModel, c: Character, onChange: (Character) -> Unit) {
    Text(tr("Aussehen fürs Bild (Englisch, ohne Kleidung)"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    GemmaHelper(tr("Auf Deutsch beschreiben (leer = aus dem Steckbrief)"), tr("z. B. Mitte 20, dunkler welliger Bob, Sommersprossen")) { onChange(vm.describeLooks(c, it)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = c.age, onValueChange = { onChange(c.copy(age = it.take(12))) }, label = { Text(tr("Alter")) }, singleLine = true, modifier = Modifier.width(100.dp))
        OutlinedTextField(value = c.hair, onValueChange = { onChange(c.copy(hair = it)) }, label = { Text(tr("Haare")) }, placeholder = { Text("chin length wavy bob, dark brown") }, maxLines = 3, modifier = Modifier.weight(1f))
    }
    Field(tr("Gesicht"), c.face, "angular face, high cheekbones, straight nose, full lips") { onChange(c.copy(face = it)) }
    Field(tr("Augen"), c.eyes, "almond-shaped green eyes, calm intense gaze") { onChange(c.copy(eyes = it)) }
    Field(tr("Körper"), c.body, "slender athletic build, average height") { onChange(c.copy(body = it)) }
    Field(tr("Haut"), c.skin, "fair skin, light freckles across the nose") { onChange(c.copy(skin = it)) }
    Field(tr("Besonderheiten"), c.appearance, "glasses, small scar on the chin") { onChange(c.copy(appearance = it)) }
}

/** Ort für MOSS Image (Englisch) */
@Composable
fun PlaceFields(vm: AppViewModel, item: FundusItem, onChange: (FundusItem) -> Unit) {
    GemmaHelper(tr("Auf Deutsch beschreiben"), tr("z. B. alte Fabrikhalle, rostige Treppe, Sonnenstrahlen")) { onChange(vm.describeFundus(de.davscha.characterfun.data.FundusKind.LOCATION, item, it)) }
    Field(tr("Hintergrund (Englisch)"), item.background, "vast abandoned warehouse hall, rusted steel staircase, cracked concrete floor") { onChange(item.copy(background = it)) }
    Field(tr("Licht (Englisch)"), item.lights, "sunbeams through the roof windows, hazy light shafts") { onChange(item.copy(lights = it)) }
}

/** Kleidung Teil für Teil (Englisch) */
@Composable
fun ClothingPieces(vm: AppViewModel, item: FundusItem, onChange: (FundusItem) -> Unit) {
    GemmaHelper(tr("Auf Deutsch beschreiben"), tr("z. B. braune Feldjacke offen, rotes Hemd, Jeans, Lederstiefel")) { onChange(vm.describeFundus(de.davscha.characterfun.data.FundusKind.CLOTHING, item, it)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item.pieces.forEachIndexed { i, (part, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = part, onValueChange = { v -> onChange(item.copy(pieces = item.pieces.toMutableList().also { it[i] = v to text })) },
                    label = { Text(tr("Teil")) }, placeholder = { Text("jacket") }, singleLine = true, modifier = Modifier.width(120.dp),
                )
                OutlinedTextField(
                    value = text, onValueChange = { v -> onChange(item.copy(pieces = item.pieces.toMutableList().also { it[i] = part to v })) },
                    label = { Text(tr("Beschreibung (Englisch)")) }, maxLines = 3, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onChange(item.copy(pieces = item.pieces.filterIndexed { k, _ -> k != i })) }) { Icon(Icons.Default.Close, tr("Entfernen")) }
            }
        }
        TextButton(onClick = { onChange(item.copy(pieces = item.pieces + ("" to ""))) }) { Icon(Icons.Default.Add, null); Text(" " + tr("Teil hinzufügen")) }
        if (item.pieces.isEmpty()) Text(tr("Noch keine Teile."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
