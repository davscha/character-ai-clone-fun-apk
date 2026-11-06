# Stimmen-Server einrichten (Windows-PC)

Der Server spricht die Antworten der Figuren in geklonten Stimmen. Er läuft auf dem PC
(mit Grafikkarte), die App auf dem Tablet schickt ihm die Texte übers Heimnetz.

Alle Befehle funktionieren auch per SSH aus Termux (Eingabeaufforderung, nicht PowerShell).

## 1. Einmalig: einrichten

Python 3.12 muss installiert sein (`py -3.12 --version`). Erst die Server-Dateien holen
(curl ist in Windows eingebaut):

```
mkdir %USERPROFILE%\character-fun-server
cd %USERPROFILE%\character-fun-server
set RAW=https://raw.githubusercontent.com/davscha/character-ai-clone-fun-apk/main/server
curl -LO %RAW%/stimmen_server.py
curl -LO %RAW%/requirements.txt
curl -LO %RAW%/start.bat
```

Dann einrichten:

```
py -3.12 -m venv venv
venv\Scripts\activate
pip install torch torchaudio --index-url https://download.pytorch.org/whl/cu128
pip install chatterbox-tts --no-deps
pip install -r requirements.txt
```

Warum so umständlich? `chatterbox-tts` verlangt PyTorch 2.6 – das kennt die RTX 50er-Serie
(Blackwell) aber noch nicht. Deshalb erst ein aktuelles PyTorch mit CUDA 12.8 installieren
und Chatterbox dann ohne seine eigenen Abhängigkeiten.

Firewall für das Heimnetz öffnen (einmalig):

```
netsh advfirewall firewall add rule name="Character Fun Stimmen" dir=in action=allow protocol=TCP localport=8020 remoteip=LocalSubnet
```

## 2. Starten

```
cd %USERPROFILE%\character-fun-server
venv\Scripts\activate
python stimmen_server.py
```

Beim ersten Start lädt Chatterbox sein Modell herunter (einige GB). Wenn
`Stimmen-Server läuft auf Port 8020` erscheint, ist er bereit. Oder einfach `start.bat` doppelklicken.

## 3. In der App

Einstellungen → **Stimmen-Server** → IP des PCs (z. B. `192.168.178.41`), Port `8020` →
**Verbindung testen**. Dann im Editor einer Figur bei „Stimme“ auf **Geklont (PC)** stellen,
10–15 Sekunden einsprechen oder eine Audiodatei wählen – fertig.

Die Stimmproben liegen im Ordner `server/stimmen` (eine WAV-Datei pro Figur).

**Bitte nur eigene Stimmen klonen oder die von Leuten, die einverstanden sind.**
