#!/usr/bin/env python3
"""Synthesise tools/voice_lines.txt with Kokoro-82M (Apache-2.0) into raw 24 kHz WAVs.

Run by .github/workflows/voice.yml (needs internet for the model download); the ffmpeg post-processing that turns
these into the ship's-computer announcer lives in the workflow. The voice is one of Kokoro's own stock voices: it is
an original synthetic voice and is not modelled on any real game's narrator.

usage: voice_synth.py <lines.txt> <out_dir> [voice] [speed]
"""
import os
import sys

import numpy as np
import soundfile as sf
from kokoro import KPipeline


def main():
    lines_path, out_dir = sys.argv[1], sys.argv[2]
    voice = sys.argv[3] if len(sys.argv) > 3 else "af_heart"
    speed = float(sys.argv[4]) if len(sys.argv) > 4 else 0.95
    os.makedirs(out_dir, exist_ok=True)
    # lang_code 'a' = American English for af_* voices, 'b' = British for bf_*/bm_*
    pipeline = KPipeline(lang_code="b" if voice.startswith("b") else "a")
    chimes = []
    with open(lines_path, encoding="utf-8") as f:
        for raw in f:
            raw = raw.strip()
            if not raw or raw.startswith("#"):
                continue
            parts = raw.split("|")
            key, text = parts[0].strip(), parts[1].strip()
            if len(parts) > 2 and parts[2].strip() == "chime":
                chimes.append(key)
            chunks = [audio.numpy() if hasattr(audio, "numpy") else np.asarray(audio)
                      for _, _, audio in pipeline(text, voice=voice, speed=speed)]
            sf.write(os.path.join(out_dir, key + ".wav"), np.concatenate(chunks), 24000)
            print(f"{key}: {text}")
    with open(os.path.join(out_dir, "chime.list"), "w") as f:
        f.write("\n".join(chimes) + "\n")


if __name__ == "__main__":
    main()
