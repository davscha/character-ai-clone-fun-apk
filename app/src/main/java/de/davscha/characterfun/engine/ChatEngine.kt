package de.davscha.characterfun.engine

import kotlinx.coroutines.flow.Flow

/** Auf welchem Chip das Modell gerade rechnet. */
enum class Accelerator(val label: String) { GPU("Grafikchip"), CPU("Prozessor") }

/**
 * Die eine Schnittstelle, über die die App mit einem Sprachmodell redet.
 * Version 1 hat nur [LiteRtEngine]; Ollama oder GGUF wären später weitere Klassen.
 */
interface ChatEngine {
    /** Lädt das Modell. Dauert einige Sekunden – nie im UI-Thread aufrufen. */
    suspend fun load(modelPath: String): Accelerator

    /**
     * Startet ein neues Gespräch. [systemPrompt] beschreibt die Figur,
     * die Begrüßung ist die erste Nachricht der Figur.
     */
    fun startConversation(systemPrompt: String, greeting: String?, temperature: Double)

    /** Schickt eine Nachricht; die Antwort kommt Stück für Stück. */
    fun send(text: String): Flow<String>

    /** Bricht eine laufende Antwort ab. */
    fun stop()

    /** Gibt Modell und Speicher frei. */
    fun close()
}
