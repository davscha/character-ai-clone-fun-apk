# Character Fun

> **Ein KI-Projekt:** Code, Oberfläche und Dokumentation dieser App wurden vollständig von **Claude** (KI-Assistent von Anthropic) geschrieben. Idee, Wünsche und Tests auf dem echten Gerät kamen vom Repo-Inhaber.

Kleiner character.ai-Klon für Android-Tablets – just for fun. Figuren anlegen und mit ihnen chatten, komplett offline: Die Sprachmodelle laufen direkt auf dem Tablet.

## Stand: Meilenstein 1 – Modell-Test

- **Gemma 4 E2B** (2,6 GB, schnell) und **Gemma 4 E4B** (3,7 GB, klüger) herunterladen, pausieren, fortsetzen, löschen
- Modell über Googles **LiteRT-LM** auf dem Grafikchip laden (automatischer Ausweich auf den Prozessor)
- Probe-Chat mit einer Test-Figur (Kapitän Kraken), Definition und Begrüßung frei änderbar
- Antworten erscheinen Wort für Wort, Stopp-Knopf, Messwerte unter jeder Antwort (Zeit bis zum ersten Wort, Gesamtzeit)

## Installieren

1. Auf dem Tablet unter **Releases → latest** die Datei `CharacterFun.apk` laden.
2. Öffnen und die Installation aus unbekannten Quellen erlauben.
3. Neue Builds lassen sich einfach drüber installieren.

Beim ersten Start links ein Modell herunterladen (am besten im WLAN), dann **laden** und losschreiben.

## Technik

Kotlin · Jetpack Compose · LiteRT-LM 0.18.1 · APK-Build per GitHub Actions

Die App spricht nur über die Schnittstelle `ChatEngine` mit dem Modell. Version 1 hat eine Umsetzung (`LiteRtEngine`); Ollama oder eigene GGUF-Dateien wären später weitere Klassen.
