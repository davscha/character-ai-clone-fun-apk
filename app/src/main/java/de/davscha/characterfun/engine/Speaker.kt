package de.davscha.characterfun.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Vorlesen über die Sprachausgabe von Android (läuft offline, kann Deutsch).
 * Jede Figur hat eine eigene Stimmlage und ein eigenes Tempo.
 */
class Speaker(context: Context, private val onSpeakingChanged: (String?) -> Unit) {

    private var ready = false
    private var pending: Triple<String, Float, Float>? = null
    private var pendingId: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.GERMANY
            pending?.let { (text, pitch, rate) -> speak(pendingId ?: "", text, pitch, rate) }
            pending = null
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = onSpeakingChanged(utteranceId)
            override fun onDone(utteranceId: String) = onSpeakingChanged(null)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = onSpeakingChanged(null)
            override fun onStop(utteranceId: String, interrupted: Boolean) = onSpeakingChanged(null)
        })
    }

    /** Liest [text] vor; *Handlungen zwischen Sternchen* werden übersprungen. */
    fun speak(id: String, text: String, pitch: Float, rate: Float) {
        val clean = text.replace(Regex("\\*[^*]*\\*"), " ").replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return
        if (!ready) {
            pending = Triple(clean, pitch, rate)
            pendingId = id
            return
        }
        tts.setPitch(pitch)
        tts.setSpeechRate(rate)
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        pending = null
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
