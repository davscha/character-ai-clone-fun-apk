# To-do – Character Fun

Stand: 7. November 2026. Abhaken mit `[x]`, sobald etwas fertig ist.

## Schnell gemacht, großer Nutzen

- [x] **1. Nachrichten bearbeiten und löschen** – eigene Nachricht antippen, ändern, die Figur antwortet neu
- [x] **2. Antwortlänge pro Figur** – kurz / mittel / lang im Editor
- [x] **3. Lieblingsmodell pro Figur** – E2B oder E4B (vorerst kein 12B), die App schaltet beim Öffnen automatisch um
- [x] **4. Startszene pro Figur** – z. B. „Ihr sitzt nachts in einer verrauchten Hafenkneipe“, die Figur steigt direkt ein

## Mittlerer Aufwand, macht richtig Spaß

- [x] **5. Vorlesen** – Antworten per Android-Sprachausgabe (offline), eigene Stimmlage und Tempo pro Figur
- [x] **6. Spracheingabe** – Mikrofon-Knopf über die Android-Spracherkennung
- [x] **7. Bilder an die Figur schicken** – Gemma 4 E2B/E4B verstehen Bilder, die Figur kommentiert das Foto
- [x] **8. Figuren exportieren und importieren** – als Datei inkl. Bild, zum Teilen und Sichern

## Größere Brocken

- [x] **9. Rooms (Gruppenchat)** – mehrere Figuren in einem Chat, die abwechselnd antworten
- [x] **10. Tablet-Layout** – Chatliste links, Chat rechts
- [x] **11. Ollama-Anbindung** – KI-Quelle Intern oder Lokal (Ollama), IP/Port, Verbindungstest, Modellauswahl mit Bild-Check
- [x] **12. Sprache der Oberfläche** – Deutsch, Englisch, Spanisch, Französisch, in den Einstellungen wählbar

## Runde 2 – Spiele (Etappe A)

- [x] **13. 20 Fragen** – die Figur denkt sich etwas aus, du rätst mit Ja/Nein (oder umgekehrt)
- [x] **14. Wer bin ich?** – die Figur spielt heimlich eine Person/ein Tier, du findest es heraus
- [x] **15. Text-Abenteuer** – Figur als Spielleiter, drei Möglichkeiten pro Runde, optional Würfel und Lebenspunkte
- [x] **16. Geschichten-Kette** – abwechselnd je ein Satz einer Geschichte

## Runde 2 – Room-Spiele (Etappe B)

- [x] **17. Quiz-Abend im Room** – eine Figur fragt, die anderen raten mit, Punkte-Tabelle
- [x] **18. Werwolf/Detektiv im Room** – eine Figur ist heimlich der Täter, du entlarvst sie

## Runde 2 – KI-Hilfen (Etappe C)

- [x] **19. „Weiterschreiben“** – die Figur hängt einen Absatz an ihre letzte Antwort an
- [x] **20. Stil-Knopf** – deine Nachricht lustiger / dramatischer / im Erzählmodus umschreiben
- [x] **21. Steckbrief-Check** – die KI prüft ein Profil auf Lücken und Widersprüche
- [x] **22. Erinnerungs-Vorschläge** – nach dem Gespräch 2–3 Vorschläge zum Anheften
- [x] **23. Kapitel-Zusammenfassung** – langer Chat als kurze Geschichte zum Nachlesen/Teilen

## Runde 2 – Spaß (Etappe D)

- [x] **24. Zufallsfigur** – die KI würfelt eine komplett neue Figur
- [x] **25. Stimmungs-Anzeige** – Laune der Figur als Emoji, ändert sich im Gespräch
- [x] **26. Figuren-Tagebuch** – nach dem Chat ein Tagebucheintrag der Figur über dich
- [x] **27. Brief schreiben lassen** – Geburtstagsbrief, Weihnachtsgedicht usw.
- [x] **28. Chat als Comic-Bild exportieren** – hübscher Screenshot zum Teilen

## Extra

- [x] **33. Bild als Nachricht** – die KI schaut sich ein Bild an und schreibt daraus deine Nachricht (auch im Room); Geschlecht für dich und jede Figur, damit Anrede und Ich-Form stimmen

## Runde 3 – Bilder mit ComfyUI (PC 192.168.178.41, Port 8080)

Grundeinstellungen: Positiv-Basis `masterpiece, highres, PandorasBoxStyle`, Negativ-Basis `lowres, photorealistic, photo background, multiple views` – beides in den Einstellungen änderbar. Standard-LoRA: `NoobAi\PandorasBoxStyle.safetensors` (Stärke 1). Das Trigger-Tag eines LoRA-Stils = Dateiname ohne Ordner und ohne `.safetensors` (z. B. `PandorasBoxStyle`) – die App setzt es automatisch hinter die Basis. Davids Workflow (SDXL, 12 Schritte, euler, cfg 4, 1024×1024) dient als Vorlage; die App tauscht nur Prompt, Startwert und LoRA aus.

- [x] **F1. ComfyUI-Anbindung + Fundus** – Verbindung/Test in den Einstellungen, Basis-Prompts, Workflow-Vorlage; eigenes Menü „Fundus“ mit gemeinsamen Listen für alle Figuren: Orte, Kleidung (je Name + Tags + Vorschaubild) und Stile (LoRA aus ComfyUI-Liste, Stärke; Trigger-Tag automatisch aus dem Dateinamen)
- [x] **F2. Figurenblatt** – Aussehen (Tags + Vorschau), Schrank (Kleidung aus dem Fundus ankreuzen, Standard-Outfit), „Anprobieren“, Vorschau als Avatar übernehmen, eigener Startwert
- [x] **F3. Im Chat** – Ort und Kleidung wählen (Hinweis der Spielleitung bei Wechsel, Figur weiß Bescheid), „Bild von der Szene“ (Gemma schreibt die Szenen-Tags), auch in der Bildhilfe und im Room (eine Figur pro Bild)
- [x] **F4. Bilderbuch** – Bilder aus dem Chat mit Erzähltext als PDF zum Blättern und Teilen

## Runde 4 – Tag-Liste (aus Davids tags.csv)

- [x] **T1. Gemmas Tags aufräumen** – Schreibvarianten → echte Tags, falsches Fach raus (z. B. Kleidung im Aussehen)
- [x] **T2. Vorschläge beim Tippen** in allen Tag-Feldern, beliebteste zuerst
- [x] **T3. Tag-Baukasten** – Reiter je Fach (Haare, Augen, Oberteil, Schuhe, Ort, Ausdruck, Kamera …) zum Antippen, mit Suche
- [x] **T4. Aussehen würfeln** im Figurenblatt
- [x] **T5. Gemma bekommt bewährte Tags** für „Bild von der Szene“

## Runde 2 – Praktisches (Etappe E)

- [ ] **29. Suche in alten Chats**
- [ ] **30. Mehrere Gesprächsstränge pro Figur**
- [ ] **31. Hell/Dunkel und Schriftgröße**
- [ ] **32. Rooms exportieren und importieren**

## Erledigt

- [x] Gemma 4 E2B/E4B über LiteRT-LM auf dem Grafikchip, Download mit Fortsetzen
- [x] Modelle, Figuren und Chats dauerhaft in `CharacterFun/` (überstehen Neuinstallationen)
- [x] Eigene `.litertlm`-Modelle laden, Absturzschutz beim Laden
- [x] Galerie mit Kategorien und Suche, 6 Beispiel-Figuren, Editor, Löschen per ⋮-Menü
- [x] Figur mit KI erstellen (Stichpunkte → Profil)
- [x] Chat: Streaming, Stopp, Gesprächsstarter, *Handlungen* kursiv
- [x] Chats speichern, „Weiter chatten“, Antworten neu würfeln mit Varianten
- [x] Erinnerungen: „Über dich“, angeheftete Erinnerungen, automatische Zusammenfassung
- [x] Ideen-Knopf: fünf Vorschläge für die nächste Nachricht
