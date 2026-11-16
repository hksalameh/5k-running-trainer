#!/usr/bin/env python3
"""Copy Charon (male) and Despina (female) voice sprites into the app.

Only run with validated 22-clip audio manifests produced from user-approved
voices; previous Taim/Sana sprites are not used.
"""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COACH = ROOT / "app/src/main/java/com/rakdatak/app/PhoneWorkoutCoach.kt"
PACK_DIR = ROOT / "voice-lab"
ANDROID_ASSETS = ROOT / "app/src/main/assets/voice_packs"

def make_spec(voice: str) -> str:
    name = f"rakdatak_ar_{voice}_v4"
    manifest = json.loads((PACK_DIR / f"{name}.json").read_text(encoding="utf-8"))
    lines = [
        f"        val {voice.upper()}_PACK = VoicePackSpec(",
        f'            filename = "{name}.wav",',
        f'            assetPath = "voice_packs/{name}.wav",',
        "            clips = mapOf(",
    ]
    lines += [
        f'                "{key}" to VoiceClip({clip["startMs"]}, {clip["endMs"]}),'
        for key, clip in manifest.items()
    ]
    lines += ["            ),", "        )"]
    shutil.copy2(PACK_DIR / f"{name}.wav", ANDROID_ASSETS / f"{name}.wav")
    return "\n".join(lines)

def main() -> None:
    ANDROID_ASSETS.mkdir(parents=True, exist_ok=True)
    current = COACH.read_text(encoding="utf-8")
    start = current.index("        val CHARON_PACK = VoicePackSpec(")
    finish = current.index("\n    }\n}\n\n@Composable", start)
    replacement = make_spec("charon") + "\n\n" + make_spec("despina")
    COACH.write_text(current[:start] + replacement + current[finish:], encoding="utf-8")
    print("Charon male and Despina female offline packs installed.")

if __name__ == "__main__":
    main()
