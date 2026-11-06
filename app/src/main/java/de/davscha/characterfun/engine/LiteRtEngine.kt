package de.davscha.characterfun.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

/**
 * Gemma 4 über Googles LiteRT-LM – dieselbe Technik wie in der AI Edge Gallery.
 * Versucht zuerst den Grafikchip, notfalls den Prozessor.
 */
class LiteRtEngine(private val context: Context) : ChatEngine {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    override suspend fun load(modelPath: String): Accelerator = withContext(Dispatchers.IO) {
        close()
        // Reihenfolge: Grafikchip mit Mehr-Token-Vorhersage (schneller), Grafikchip ohne, Prozessor
        val attempts = listOf(
            Triple(Accelerator.GPU, true, Backend.GPU()),
            Triple(Accelerator.GPU, false, Backend.GPU()),
            Triple(Accelerator.CPU, false, Backend.CPU()),
        )
        var lastError: Throwable? = null
        for ((accelerator, speculative, backend) in attempts) {
            try {
                engine = createEngine(modelPath, backend, speculative)
                Log.i(TAG, "Modell geladen auf $accelerator (MTP=$speculative)")
                return@withContext accelerator
            } catch (e: Throwable) {
                Log.w(TAG, "Laden auf $accelerator (MTP=$speculative) fehlgeschlagen", e)
                lastError = e
            }
        }
        throw IllegalStateException(lastError?.message ?: "Modell konnte nicht geladen werden", lastError)
    }

    @OptIn(ExperimentalApi::class)
    private fun createEngine(modelPath: String, backend: Backend, speculative: Boolean): Engine {
        ExperimentalFlags.enableSpeculativeDecoding = speculative
        try {
            val e = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    maxNumTokens = MAX_TOKENS,
                    // Beschleunigt das zweite Laden
                    cacheDir = context.cacheDir.absolutePath,
                )
            )
            try {
                e.initialize()
            } catch (t: Throwable) {
                runCatching { e.close() }
                throw t
            }
            return e
        } finally {
            ExperimentalFlags.enableSpeculativeDecoding = false
        }
    }

    override fun startConversation(systemPrompt: String, history: List<Pair<Boolean, String>>, temperature: Double) {
        val e = engine ?: error("Kein Modell geladen")
        conversation?.let { old -> runCatching { old.close() } }
        conversation = e.createConversation(
            ConversationConfig(
                systemInstruction = systemPrompt.takeIf { it.isNotBlank() }?.let { Contents.of(it) },
                initialMessages = history
                    .filter { (_, text) -> text.isNotBlank() }
                    .map { (fromUser, text) -> if (fromUser) Message.user(text) else Message.model(text) },
                samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = temperature),
            )
        )
    }

    override fun send(text: String): Flow<String> = callbackFlow {
        val c = conversation ?: error("Kein Gespräch gestartet")
        c.sendMessageAsync(
            Contents.of(Content.Text(text)),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    trySend(message.toString())
                }

                override fun onDone() {
                    close()
                }

                override fun onError(throwable: Throwable) {
                    // Abbruch per Stopp-Knopf ist kein Fehler
                    if (throwable is CancellationException) close() else close(throwable)
                }
            },
            mapOf("enable_thinking" to false),
        )
        awaitClose { }
    }.buffer(Channel.UNLIMITED) // kein Wortstück darf verloren gehen

    override fun stop() {
        runCatching { conversation?.cancelProcess() }
    }

    override fun close() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
    }

    private companion object {
        const val TAG = "LiteRtEngine"
        const val MAX_TOKENS = 4096
    }
}
