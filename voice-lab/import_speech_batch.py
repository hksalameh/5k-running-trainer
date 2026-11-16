#!/usr/bin/env python3
"""Split a consented TTS narration batch at long silences into exact workout cues.

The input must have the same spoken phrases as voice_lab.PHRASES, in insertion
order, separated by >=1.2 second silences. Never infer clip durations by text.
"""
import argparse
import json
import re
import subprocess
import wave
from pathlib import Path
from voice_lab import PHRASES

SAMPLE_RATE = 22050


def probe_silences(
    source: Path, threshold_db: int = -43, minimum_seconds: float = 1.2
) -> list[tuple[float, float]]:
    result = subprocess.run(
        [
            "ffmpeg", "-hide_banner", "-nostats", "-i", str(source),
            "-af", f"silencedetect=noise={threshold_db}dB:d={minimum_seconds}",
            "-f", "null", "NUL",
        ],
        text=True,
        capture_output=True,
        check=False,
    )
    starts = re.findall(r"silence_start:\s*([\d.]+)", result.stderr)
    ends = re.findall(r"silence_end:\s*([\d.]+)", result.stderr)
    if len(starts) != len(ends):
        raise ValueError(f"Silence detector has unpaired boundaries: {len(starts)} vs {len(ends)}")
    return list(zip(map(float, starts), map(float, ends)))


def build(source: Path, target: Path, metadata: Path, minimum_seconds: float = 1.2) -> None:
    silences = probe_silences(source, minimum_seconds=minimum_seconds)
    keys = list(PHRASES)
    if len(silences) != len(keys) - 1:
        raise ValueError(
            f"Found {len(silences)} long silences but {len(keys)-1} are required; "
            "do NOT guess a segment map."
        )

    temp = target.with_suffix(".decoded.wav")
    subprocess.run(
        [
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
            "-i", str(source), "-ac", "1", "-ar", str(SAMPLE_RATE),
            "-c:a", "pcm_s16le", str(temp),
        ],
        check=True,
    )
    with wave.open(str(temp), "rb") as reader:
        frames = reader.readframes(reader.getnframes())
        total_frames = reader.getnframes()

    ends = [max(0.0, start - 0.025) for start, _ in silences]
    begins = [0.0] + [end + 0.025 for _, end in silences]
    ends += [total_frames / SAMPLE_RATE]
    if len(begins) != len(keys) or len(ends) != len(keys):
        raise ValueError("Wrong number of audio cues")

    target.parent.mkdir(parents=True, exist_ok=True)
    manifest = {}
    cursor = 0
    with wave.open(str(target), "wb") as writer:
        writer.setnchannels(1)
        writer.setsampwidth(2)
        writer.setframerate(SAMPLE_RATE)
        for key, begin, end in zip(keys, begins, ends):
            first = int(begin * SAMPLE_RATE)
            last = int(end * SAMPLE_RATE)
            if last <= first:
                raise ValueError(f"Empty phrase: {key}")
            start_ms = round(cursor * 1000 / SAMPLE_RATE)
            writer.writeframesraw(frames[first*2:last*2])
            cursor += last - first
            end_ms = round(cursor * 1000 / SAMPLE_RATE)
            if end_ms - start_ms < 500:
                raise ValueError(f"Suspiciously short cue: {key}")
            manifest[key] = {"startMs": start_ms, "endMs": end_ms}
            writer.writeframesraw(b"\x00\x00" * int(SAMPLE_RATE * 0.2))
            cursor += int(SAMPLE_RATE * 0.2)
    metadata.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    temp.unlink(missing_ok=True)
    print(json.dumps({"source":str(source),"target":str(target),"clip_count":len(keys),
                      "seconds":round(cursor/SAMPLE_RATE,2)},ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--min-silence", type=float, default=1.2)
    args = parser.parse_args()
    build(args.input, args.output, args.manifest, minimum_seconds=args.min_silence)
