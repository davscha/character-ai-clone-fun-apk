package de.davscha.characterfun.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Antwort des Stimmen-Servers auf /status */
data class VoiceServerInfo(val device: String, val voices: List<String>)

/** Spricht mit dem kleinen Stimmen-Server auf dem PC (server/stimmen_server.py). */
class VoiceServerClient(host: String, port: Int) {

    private val base = "http://${host.trim()}:$port"

    suspend fun status(): VoiceServerInfo = withContext(Dispatchers.IO) {
        val o = JSONObject(request("GET", "/status", null, null, 5000).decodeToString())
        val arr = o.optJSONArray("stimmen")
        VoiceServerInfo(o.optString("geraet", "?"), List(arr?.length() ?: 0) { arr!!.optString(it) })
    }

    /** Stimmprobe hochladen; gibt die gespeicherte Länge in Sekunden zurück. */
    suspend fun upload(voice: String, audio: ByteArray): Double = withContext(Dispatchers.IO) {
        val o = JSONObject(request("PUT", "/stimmen/$voice", audio, "application/octet-stream", 60_000).decodeToString())
        o.optDouble("sekunden", 0.0)
    }

    /** Text in der geklonten Stimme sprechen lassen – Ergebnis ist eine WAV-Datei. */
    suspend fun speak(text: String, voice: String, expression: Float, language: String = "de"): ByteArray =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("text", text).put("stimme", voice).put("sprache", language)
                .put("ausdruck", expression.toDouble())
            request("POST", "/sprechen", body.toString().toByteArray(), "application/json", 180_000)
        }

    private fun request(method: String, path: String, body: ByteArray?, type: String?, timeoutMs: Int): ByteArray {
        val c = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = minOf(timeoutMs, 5000)
            readTimeout = timeoutMs
        }
        try {
            if (body != null) {
                c.doOutput = true
                type?.let { c.setRequestProperty("Content-Type", it) }
                c.outputStream.use { it.write(body) }
            }
            if (c.responseCode !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.readText().orEmpty()
                throw IOException(runCatching { JSONObject(err).optString("detail") }.getOrNull()?.ifEmpty { null }
                    ?: "Stimmen-Server antwortet mit Fehler ${c.responseCode}")
            }
            return c.inputStream.readBytes()
        } finally {
            c.disconnect()
        }
    }
}

/** Nimmt eine Stimmprobe auf und liefert sie als WAV-Datei (24 kHz, mono, 16 Bit). */
class WavRecorder {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val pcm = ByteArrayOutputStream()
    @Volatile private var running = false

    @SuppressLint("MissingPermission") // die Erlaubnis holt der Editor vorher ein
    fun start() {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4)
        pcm.reset()
        record = r
        running = true
        r.startRecording()
        thread = Thread {
            val buf = ByteArray(min)
            while (running) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0) synchronized(pcm) { pcm.write(buf, 0, n) }
                if (pcm.size() >= RATE * 2 * MAX_SECONDS) running = false
            }
        }.apply { start() }
    }

    val seconds: Float get() = synchronized(pcm) { pcm.size() / (RATE * 2f) }

    val isRunning get() = running

    /** Aufnahme beenden und als WAV zurückgeben. */
    fun stop(): ByteArray {
        running = false
        thread?.join(1000)
        record?.run { runCatching { stop() }; release() }
        record = null
        val data = synchronized(pcm) { pcm.toByteArray() }
        return wav(data)
    }

    private fun wav(data: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }
        return header.array() + data
    }

    companion object {
        const val RATE = 24_000
        const val MAX_SECONDS = 20
    }
}
