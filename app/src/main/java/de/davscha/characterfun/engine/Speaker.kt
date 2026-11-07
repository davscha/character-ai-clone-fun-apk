package de.davscha.characterfun.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import de.davscha.characterfun.i18n.I18n
import java.util.Locale

/** Eine Stimme der Android-Sprachausgabe */
data class VoiceInfo(
    val name: String,
    val locale: Locale,
    /** true = braucht Internet (Online-Stimme von Google) */
    val online: Boolean,
) {
    /** Stimme in der Sprache der Oberfläche? */
    val isGerman get() = locale.language == I18n.lang.locale.language

    /** Lesbarer Name, z. B. "Deutsch (Deutschland) · deb · offline" */
    val label: String
        get() {
            // Google-Stimmen heißen z. B. "de-de-x-deb-local" – das Kürzel in der Mitte unterscheidet sie
            val short = name.split("-").let { p -> if (p.size >= 4 && p[2] == "x") p[3] else name }
            return "${locale.getDisplayName(I18n.lang.locale)} · $short · ${if (online) "online" else "offline"}"
        }
}

/**
 * Vorlesen über die Sprachausgabe von Android (offline möglich, kann Deutsch).
 * Jede Figur hat ihre eigene Stimme, Tonhöhe und ihr eigenes Tempo.
 */
class Speaker(
    context: Context,
    private val onSpeakingChanged: (String?) -> Unit,
    private val onVoicesReady: (List<VoiceInfo>) -> Unit,
) {

    private var ready = false
    private var pending: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = I18n.lang.locale
            onVoicesReady(loadVoices())
            pending?.invoke()
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

    /** Alle installierten Stimmen – deutsche zuerst, Offline-Stimmen vor Online-Stimmen. */
    private fun loadVoices(): List<VoiceInfo> =
        runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet<Voice>())
            .filter { !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .map { VoiceInfo(it.name, it.locale, it.isNetworkConnectionRequired) }
            .sortedWith(compareBy<VoiceInfo>({ !it.isGerman }, { it.locale.toLanguageTag() }, { it.online }, { it.name }))

    /** Liest [text] vor; *Handlungen zwischen Sternchen* werden übersprungen. */
    fun speak(id: String, text: String, pitch: Float, rate: Float, voiceName: String = "") {
        val clean = cleanForSpeech(text)
        if (clean.isEmpty()) return
        val action = {
            val voice = voiceName.takeIf { it.isNotEmpty() }?.let { n -> tts.voices?.firstOrNull { it.name == n } }
            if (voice != null) tts.voice = voice else tts.language = I18n.lang.locale
            tts.setPitch(pitch)
            tts.setSpeechRate(rate)
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id)
        }
        if (ready) action() else pending = { action() }
    }

    fun stop() {
        pending = null
        tts.stop()
    }

    companion object {
        /** *Handlungen zwischen Sternchen* werden nicht vorgelesen. */
        fun cleanForSpeech(text: String) =
            de.davscha.characterfun.data.Mood.strip(text).replace(Regex("\\*[^*]*\\*"), " ").replace(Regex("\\s+"), " ").trim()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
