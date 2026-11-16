#!/usr/bin/env python3
"""Reusable Arabic speech generator: Jordanian Edge voices or offline Piper.

Edge: high-quality speech requires an internet connection at generation time.
Piper: runs fully locally after installing the model; only a male Arabic voice
is included in the officially indexed ar_JO Piper voices.
"""
import argparse
import asyncio
import json
import subprocess
import wave
from pathlib import Path

import edge_tts


EDGE_VOICES = {"male": "ar-JO-TaimNeural", "female": "ar-JO-SanaNeural"}
SAMPLE_RATE = 22_050

PHRASES = {
    "warmup_300": "بنبدأ بخمس دقايق إحماء بالمشي. أول دقيقة بسرعة اثنين، وبعدها بتزيد السرعة حسب راحتك.",
    "warmup_speed_60": "خلّصت أول دقيقة من الإحماء. زوّد سرعة المشي للسرعة اللي بتناسبك.",
    "run_60": "المرحلة الجايّة ركض لمدة دقيقة، بلّش.",
    "run_90": "المرحلة الجايّة ركض لمدة دقيقة ونص، بلّش.",
    "run_120": "المرحلة الجايّة ركض لمدة دقيقتين، بلّش.",
    "run_180": "المرحلة الجايّة ركض لمدة ثلاث دقايق، بلّش.",
    "run_300": "المرحلة الجايّة ركض لمدة خمس دقايق، بلّش.",
    "run_480": "المرحلة الجايّة ركض لمدة ثمان دقايق، بلّش.",
    "run_600": "المرحلة الجايّة ركض لمدة عشر دقايق، بلّش.",
    "run_1200": "المرحلة الجايّة ركض لمدة عشرين دقيقة، بلّش.",
    "run_1500": "المرحلة الجايّة ركض لمدة خمس وعشرين دقيقة، بلّش.",
    "run_1680": "المرحلة الجايّة ركض لمدة ثمان وعشرين دقيقة، بلّش.",
    "run_1800": "المرحلة الجايّة ركض لمدة ثلاثين دقيقة متواصلة، بلّش.",
    "walk_90": "المرحلة الجايّة مشي لمدة دقيقة ونص، بلّش.",
    "walk_120": "المرحلة الجايّة مشي لمدة دقيقتين، بلّش.",
    "walk_150": "المرحلة الجايّة مشي لمدة دقيقتين ونص، بلّش.",
    "walk_180": "المرحلة الجايّة مشي لمدة ثلاث دقايق، بلّش.",
    "walk_300": "المرحلة الجايّة مشي لمدة خمس دقايق، بلّش.",
    "cooldown_300": "المرحلة الجايّة تهدئة بالمشي لمدة خمس دقايق، بلّش.",
    "finished": "يعطيك العافية، خلّص التمرين واتحفظ تلقائي.",
}


def convert_ffmpeg(source: Path, target: Path) -> None:
    command = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source)]
    if target.suffix.lower() == ".wav":
        command += ["-ac", "1", "-ar", str(SAMPLE_RATE), "-c:a", "pcm_s16le"]
    else:
        command += ["-ac", "1", "-ar", str(SAMPLE_RATE), "-b:a", "64k"]
    subprocess.run(command + [str(target)], check=True)


async def generate_edge(text: str, voice: str, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    await edge_tts.Communicate(text, EDGE_VOICES[voice], rate="-4%").save(str(target))


def generate_piper(text: str, target: Path, model: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    command = ["piper", "--model", str(model), "--output_file", str(target)]
    subprocess.run(command, input=text, text=True, check=True)


async def build_pack(output: Path, only_voice: str | None = None) -> None:
    output.mkdir(parents=True, exist_ok=True)
    semaphore = asyncio.Semaphore(2)
    for voice in ([only_voice] if only_voice else ["male", "female"]):
        clips_dir = output / "clips" / voice

        async def one(key: str, phrase: str) -> None:
            original = clips_dir / f"{key}.mp3"
            if original.exists() and original.stat().st_size > 1_000:
                return
            async with semaphore:
                await generate_edge(phrase, voice, original)

        await asyncio.gather(*(one(k, p) for k, p in PHRASES.items()))

        wave_file = output / f"rakdatak_ar_{voice}_v2.wav"
        segments = {}
        frame_count = 0
        silence_frames = int(SAMPLE_RATE * 0.20)
        with wave.open(str(wave_file), "wb") as destination:
            destination.setparams((1, 2, SAMPLE_RATE, 0, "NONE", "not compressed"))
            for key in PHRASES:
                wav_part = clips_dir / f"{key}.wav"
                convert_ffmpeg(clips_dir / f"{key}.mp3", wav_part)
                with wave.open(str(wav_part), "rb") as source:
                    assert source.getnchannels() == 1
                    assert source.getframerate() == SAMPLE_RATE
                    assert source.getsampwidth() == 2
                    data = source.readframes(source.getnframes())
                    duration = source.getnframes()
                start_ms = round(frame_count * 1000 / SAMPLE_RATE)
                destination.writeframesraw(data)
                frame_count += duration
                end_ms = round(frame_count * 1000 / SAMPLE_RATE)
                segments[key] = {"startMs": start_ms, "endMs": end_ms}
                destination.writeframesraw(b"\x00\x00" * silence_frames)
                frame_count += silence_frames
        (output / f"rakdatak_ar_{voice}_v2.json").write_text(
            json.dumps(segments, ensure_ascii=False, indent=2), encoding="utf-8",
        )
        print(f"{voice}: {wave_file} ({frame_count/SAMPLE_RATE:.1f}s)")


async def main() -> None:
    parser = argparse.ArgumentParser(description="Arabic Jordanian text to speech")
    parser.add_argument("--backend", choices=["edge", "piper"], default="edge")
    parser.add_argument("--voice", choices=["male", "female"], default="male")
    parser.add_argument("--text", help="Arabic narration or workout cue")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--model", type=Path, default=Path("ar_JO-kareem-medium.onnx"))
    parser.add_argument("--pack", action="store_true", help="Generate male/female Rakdatak audio sprite")
    args = parser.parse_args()
    if args.pack:
        if args.backend != "edge":
            parser.error("Pack generation with two voices uses Edge; Piper currently supports male only.")
        await build_pack(args.output or Path("voice-pack"))
        return
    if not args.text or not args.output:
        parser.error("Supply both --text and --output.")
    if args.backend == "piper":
        if args.voice != "male":
            parser.error("The installed Arabic Piper model has no female speaker.")
        raw_output = args.output if args.output.suffix == ".wav" else args.output.with_suffix(".wav")
        generate_piper(args.text, raw_output, args.model)
        if raw_output != args.output:
            convert_ffmpeg(raw_output, args.output)
    else:
        raw_output = args.output if args.output.suffix == ".mp3" else args.output.with_suffix(".mp3")
        await generate_edge(args.text, args.voice, raw_output)
        if raw_output != args.output:
            convert_ffmpeg(raw_output, args.output)
    print(f"Saved: {args.output}")


if __name__ == "__main__":
    asyncio.run(main())
