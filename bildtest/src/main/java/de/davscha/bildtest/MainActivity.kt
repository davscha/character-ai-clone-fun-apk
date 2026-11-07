package de.davscha.bildtest

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Bildtest: Text rein, 512×512-Bild raus – komplett auf dem Tablet mit Bonsai Image 4B (LiteRT, CPU). */
class MainActivity : ComponentActivity() {

    // Was ist geladen, was fehlt
    private var hasStorage by mutableStateOf(false)
    private val missing = mutableStateListOf<ModelFile>()
    private var downloadJob: Job? = null
    private var downloadText by mutableStateOf("")
    private var downloadFraction by mutableStateOf(0f)

    // Bild erzeugen
    private var prompt by mutableStateOf("a cozy cabin in a snowy forest at night, warm light in the windows, digital painting")
    private var steps by mutableStateOf(4)
    private var seed by mutableStateOf("")
    private var running by mutableStateOf(false)
    private var stage by mutableStateOf("")
    private var fraction by mutableStateOf(0f)
    private val log = mutableStateListOf<String>()
    private var image by mutableStateOf<Bitmap?>(null)
    private var caption by mutableStateOf("")

    private var tokenizer: QwenTokenizer? = null
    private var pipeline: BonsaiPipeline? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Screen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        hasStorage = Environment.isExternalStorageManager()
        missing.clear()
        if (hasStorage) missing.addAll(Models.files.filter { !it.file.exists() })
    }

    @Composable
    private fun Screen() {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Bildtest", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Bonsai Image 4B über LiteRT – komplett offline auf dem Tablet. Rechnet auf dem Prozessor, " +
                        "ein Bild dauert ein paar Minuten.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    !hasStorage -> StorageBox()
                    missing.isNotEmpty() -> DownloadBox()
                    else -> GenerateBox()
                }
            }
        }
    }

    @Composable
    private fun StorageBox() {
        Text("Die Modelle (rund 4 GB) kommen nach CharacterFun/Bildmodell, damit sie eine Neuinstallation überstehen. Dafür braucht die App Zugriff auf alle Dateien.")
        Button(onClick = {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        }) { Text("Zugriff erlauben") }
    }

    @Composable
    private fun DownloadBox() {
        val total = Models.files.sumOf { it.sizeBytes }
        Text("Noch zu laden: ${missing.size} von ${Models.files.size} Dateien (gesamt %.1f GB).".format(total / 1e9))
        missing.forEach { Text("• ${it.path}  (%.0f MB)".format(it.sizeBytes / 1e6), fontSize = 13.sp) }
        if (downloadJob?.isActive == true) {
            LinearProgressIndicator(progress = { downloadFraction }, modifier = Modifier.fillMaxWidth())
            Text(downloadText, fontSize = 13.sp)
            OutlinedButton(onClick = { downloadJob?.cancel() }) { Text("Pause") }
        } else {
            if (downloadText.isNotEmpty()) Text(downloadText, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            Button(onClick = { startDownload() }) { Text("Herunterladen (WLAN empfohlen)") }
        }
    }

    private fun startDownload() {
        downloadText = ""
        downloadJob = lifecycleScope.launch {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            try {
                for (f in Models.files.filter { !it.file.exists() }) {
                    Downloader.download(f) { done, all ->
                        downloadFraction = if (all > 0) done.toFloat() / all else 0f
                        downloadText = "${f.path}: %.0f von %.0f MB".format(done / 1e6, all / 1e6)
                    }
                    refresh()
                }
                downloadText = ""
            } catch (e: kotlinx.coroutines.CancellationException) {
                downloadText = "Pausiert – geht beim nächsten Mal an derselben Stelle weiter."
            } catch (e: Exception) {
                downloadText = "Fehler: ${e.message}"
            } finally {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                refresh()
            }
        }
    }

    @Composable
    private fun GenerateBox() {
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Was soll aufs Bild? (Englisch klappt am besten)") },
            minLines = 3,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Schritte:")
            listOf(2, 4, 6, 8).forEach { s ->
                FilterChip(selected = steps == s, onClick = { steps = s }, enabled = !running, label = { Text("$s") })
            }
        }
        Text("Mehr Schritte = mehr Details, aber länger. 4 ist der Standard des Modells.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = seed,
            onValueChange = { v -> seed = v.filter { it.isDigit() }.take(9) },
            label = { Text("Startwert (leer = Zufall)") },
            singleLine = true,
            enabled = !running,
            modifier = Modifier.width(260.dp),
        )
        if (running) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text(stage)
            OutlinedButton(onClick = { pipeline?.cancelled = true; stage = "Breche nach diesem Schritt ab …" }) { Text("Abbrechen") }
        } else {
            Button(onClick = { generate() }, enabled = prompt.isNotBlank()) { Text("Bild erzeugen") }
        }
        image?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(512.dp).clip(RoundedCornerShape(12.dp)),
            )
            Text(caption, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { save(bmp)?.let { Toast.makeText(this@MainActivity, "Gespeichert unter Bilder/Bildtest", Toast.LENGTH_LONG).show() } }) { Text("Speichern") }
                OutlinedButton(onClick = { save(bmp)?.let { share(it) } }) { Text("Teilen") }
            }
        }
        if (log.isNotEmpty()) {
            Spacer(Modifier.size(4.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                log.forEach { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            }
        }
    }

    private fun generate() {
        if (running) return
        running = true
        log.clear()
        image = null
        val s = seed.toLongOrNull() ?: (0..999_999L).random().also { seed = it.toString() }
        val p = prompt.trim()
        val n = steps
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val meta = JSONObject(Models.meta.file.readText())
                    val tok = tokenizer ?: QwenTokenizer(Models.vocab.file.inputStream(), Models.merges.file.inputStream()).also { tokenizer = it }
                    val pipe = pipeline ?: BonsaiPipeline(Models.dir, meta).also { pipeline = it }
                    pipe.generate(
                        tokenizer = tok, prompt = p, seed = s, steps = n,
                        status = { line -> runOnUiThread { log += line } },
                        progress = { label, f -> runOnUiThread { stage = germanStage(label); fraction = f.toFloat() } },
                    )
                }
                val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                val px = IntArray(512 * 512) { i ->
                    Color.rgb(result.rgb[i * 3].toInt() and 0xFF, result.rgb[i * 3 + 1].toInt() and 0xFF, result.rgb[i * 3 + 2].toInt() and 0xFF)
                }
                bmp.setPixels(px, 0, 512, 0, 0, 512, 512)
                image = bmp
                val perStep = result.stepSeconds.average()
                caption = "Startwert $s · $n Schritte · %.0f s gesamt (%.0f s pro Schritt)".format(result.seconds, perStep)
            } catch (e: BonsaiPipeline.Cancelled) {
                log += "abgebrochen"
            } catch (e: Throwable) {
                log += "FEHLER: $e"
            } finally {
                running = false
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun germanStage(label: String): String = when {
        label.startsWith("Encoding") -> "Text wird verstanden …"
        label.startsWith("Loading DiT") -> "Bildmodell wird geladen (2,1 GB) …"
        label.startsWith("Step") -> label.replace("Step", "Schritt").replace(" of ", " von ")
        label.startsWith("Decoding") -> "Bild wird entwickelt …"
        label == "Done" -> "Fertig"
        else -> label
    }

    private fun save(bmp: Bitmap): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "bildtest-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Bildtest")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return uri
    }

    private fun share(uri: Uri) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, null))
    }
}

/** Eine Datei des Modells auf Hugging Face */
class ModelFile(val path: String, val sizeBytes: Long) {
    val file: File get() = File(Models.dir, path)
    val url: String get() = "https://huggingface.co/litert-community/Bonsai-Image-ternary-4B/resolve/main/$path"
}

object Models {
    val dir: File get() = File(Environment.getExternalStorageDirectory(), "CharacterFun/Bildmodell").apply { mkdirs() }

    private const val GIB = 1L shl 30
    val meta = ModelFile("pipeline_meta.json", 20_000)
    val vocab = ModelFile("tokenizer/vocab.json", 2_800_000)
    val merges = ModelFile("tokenizer/merges.txt", 1_700_000)
    // Größen nur für die Anzeige – die echte Größe meldet der Server
    val files = listOf(
        meta, vocab, merges,
        ModelFile("vae_dec_fp32.tflite", (0.19 * GIB).toLong()),
        ModelFile("textenc_int4.tflite", (1.68 * GIB).toLong()),
        ModelFile("dit_int4b32.tflite", (2.11 * GIB).toLong()),
    )
}
