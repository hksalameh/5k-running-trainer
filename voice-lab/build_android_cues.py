#!/usr/bin/env python3
"""Create individually playable Android raw MP3 cues from validated WAV sprites.

Android MediaPlayer seeking within a long WAV sprite is not sufficiently
reliable across devices. Each cue gets its own playable resource instead.
"""
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "voice-lab"
TARGET = ROOT / "app/src/main/res/raw"


def build() -> None:
    TARGET.mkdir(parents=True, exist_ok=True)
    for voice in ("charon", "despina"):
        metadata = json.loads((SOURCE / f"rakdatak_ar_{voice}_v4.json").read_text(encoding="utf-8"))
        source = SOURCE / "sources" / f"rakdatak_ar_{voice}_v4.wav"
        if len(metadata) != 20:
            raise ValueError(f"Unexpected phrase count for {voice}: {len(metadata)}")
        for key, bounds in metadata.items():
            start_ms = bounds["startMs"]
            end_ms = bounds["endMs"]
            if start_ms < 0 or end_ms - start_ms < 600:
                raise ValueError(f"Invalid {voice}/{key}: {bounds}")
            out = TARGET / f"rk_{voice}_{key}.mp3"
            subprocess.run([
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-i", str(source),
                "-ss", f"{start_ms/1000:.3f}",
                "-t", f"{(end_ms-start_ms)/1000:.3f}",
                "-ac", "1", "-ar", "22050", "-c:a", "libmp3lame",
                "-b:a", "64k", str(out),
            ], check=True)
            if out.stat().st_size < 2000:
                raise ValueError(f"Invalid encoded cue {out}")
        print(f"{voice}: encoded {len(metadata)} separate MP3 clips")


if __name__ == "__main__":
    build()
