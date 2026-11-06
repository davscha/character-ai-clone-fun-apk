"""
Stimmen-Server für Character Fun
================================

Ein kleiner Server für den PC: Er speichert Stimmproben und spricht Texte in diesen
Stimmen – mit Chatterbox Multilingual (Resemble AI, MIT-Lizenz, kann Deutsch).

Die App redet über drei Adressen mit ihm:
  GET  /status              – läuft der Server? Welche Stimmen gibt es?
  PUT  /stimmen/{name}      – Stimmprobe hochladen (Audiodatei im Body)
  POST /sprechen            – {"text": ..., "stimme": ..., "sprache": "de"} → WAV zurück

Start:  python stimmen_server.py          (hört auf allen Netzwerk-Adressen, Port 8020)
"""

import io
import re
import threading
from pathlib import Path

import librosa
import soundfile as sf
import torch
import uvicorn
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import Response
from pydantic import BaseModel

from chatterbox.mtl_tts import ChatterboxMultilingualTTS

PORT = 8020
STIMMEN_ORDNER = Path(__file__).parent / "stimmen"
STIMMEN_ORDNER.mkdir(exist_ok=True)

# Modell einmal beim Start laden – auf der Grafikkarte, falls vorhanden
GERAET = "cuda" if torch.cuda.is_available() else "cpu"
print(f"Lade Chatterbox Multilingual auf {GERAET} … (beim ersten Mal wird es heruntergeladen)")
modell = ChatterboxMultilingualTTS.from_pretrained(device=GERAET)
print("Modell bereit.")

# Die Grafikkarte kann immer nur eine Sache gleichzeitig sprechen
sperre = threading.Lock()

app = FastAPI(title="Character Fun – Stimmen-Server")


def sicherer_name(name: str) -> str:
    """Nur Buchstaben, Ziffern, - und _ – damit niemand fremde Pfade unterschiebt."""
    sauber = re.sub(r"[^A-Za-z0-9_-]", "_", name)[:80]
    if not sauber:
        raise HTTPException(400, "Ungültiger Stimmen-Name")
    return sauber


def stimm_datei(name: str) -> Path:
    return STIMMEN_ORDNER / f"{sicherer_name(name)}.wav"


@app.get("/status")
def status():
    return {
        "ok": True,
        "modell": "chatterbox-multilingual",
        "geraet": GERAET,
        "stimmen": sorted(p.stem for p in STIMMEN_ORDNER.glob("*.wav")),
    }


@app.put("/stimmen/{name}")
async def stimme_speichern(name: str, request: Request):
    """Nimmt eine Audiodatei an (WAV, MP3, M4A …) und speichert sie als WAV."""
    daten = await request.body()
    if len(daten) < 1000:
        raise HTTPException(400, "Die Aufnahme ist zu kurz oder leer.")
    if len(daten) > 30_000_000:
        raise HTTPException(400, "Die Aufnahme ist zu groß (höchstens 30 MB).")
    try:
        audio, rate = librosa.load(io.BytesIO(daten), sr=None, mono=True)
    except Exception as e:
        raise HTTPException(400, f"Audio konnte nicht gelesen werden: {e}")
    dauer = len(audio) / rate
    if dauer < 3:
        raise HTTPException(400, f"Die Aufnahme ist nur {dauer:.1f} s lang – bitte mindestens 5 Sekunden.")
    # Mehr als 20 Sekunden bringen nichts – nur die ersten 20 behalten
    audio = audio[: int(rate * 20)]
    sf.write(stimm_datei(name), audio, rate)
    return {"ok": True, "stimme": sicherer_name(name), "sekunden": round(min(dauer, 20), 1)}


class Sprechauftrag(BaseModel):
    text: str
    stimme: str
    sprache: str = "de"
    ausdruck: float = 0.5  # 0.25 ruhig … 1.0 sehr ausdrucksstark


@app.post("/sprechen")
def sprechen(auftrag: Sprechauftrag):
    datei = stimm_datei(auftrag.stimme)
    if not datei.exists():
        raise HTTPException(404, f"Keine Stimmprobe für '{auftrag.stimme}' vorhanden.")
    text = auftrag.text.strip()[:1000]
    if not text:
        raise HTTPException(400, "Kein Text")
    ausdruck = min(max(auftrag.ausdruck, 0.25), 1.5)
    with sperre:
        wav = modell.generate(
            text,
            language_id=auftrag.sprache,
            audio_prompt_path=str(datei),
            exaggeration=ausdruck,
            # Bei viel Ausdruck wird die Sprache schneller – weniger cfg gleicht das aus
            cfg_weight=0.5 if ausdruck <= 0.6 else 0.3,
        )
    puffer = io.BytesIO()
    sf.write(puffer, wav.squeeze(0).cpu().numpy(), modell.sr, format="WAV")
    return Response(content=puffer.getvalue(), media_type="audio/wav")


if __name__ == "__main__":
    print(f"Stimmen-Server läuft auf Port {PORT} – in der App die IP dieses PCs eintragen.")
    uvicorn.run(app, host="0.0.0.0", port=PORT)
