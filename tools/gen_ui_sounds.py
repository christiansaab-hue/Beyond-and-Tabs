#!/usr/bin/env python3
"""Generates the lobby UI sounds (bar/.../sounds/ui/*.ogg) from nothing but maths.

Every sound here is ORIGINAL and procedurally synthesised (sines, a few odd harmonics, seeded noise, envelopes and
simple filters). The brief was "the crisp feel of a late-90s RTS lobby" - servo clicks, relay chunks, two-tone
chirps - but no game's audio was sampled, traced or copied; nothing is loaded from disk. Re-running this script
reproduces the files bit-for-bit apart from the Vorbis encoder's own output (noise is seeded).

Needs python3 + numpy and ffmpeg with libvorbis on PATH. Usage:
    python3 tools/gen_ui_sounds.py [--out DIR] [--wav-dir DIR]
Output: 44.1 kHz mono OGG Vorbis, each < 0.6 s, peak normalised to -3 dBFS, DC removed.
"""
import argparse
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
PEAK_DBFS = -3.0
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_OUT = os.path.join(ROOT, "bar", "src", "main", "resources", "assets", "reignofnether", "sounds", "ui")


# ---------------------------------------------------------------- building blocks

def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def silence(dur):
    return np.zeros(int(SR * dur))


def env_exp(dur, tau, attack=0.001):
    """Fast linear attack, exponential decay with time constant tau (s), and a 4 ms release so a note that is cut
    off before it has died away doesn't end in a click."""
    t = t_axis(dur)
    e = np.exp(-t / tau)
    a = max(1, int(SR * attack))
    e[:a] *= np.linspace(0.0, 1.0, a)
    r = min(len(e) // 2, int(SR * 0.004))
    if r > 1:
        e[-r:] *= np.linspace(1.0, 0.0, r)
    return e


def sine(freq, dur, phase=0.0):
    """Sine whose frequency may be a constant or an array (a sweep); integrates phase so sweeps stay smooth."""
    n = int(SR * dur)
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    return np.sin(2 * np.pi * np.cumsum(f) / SR + phase)


def square_soft(freq, dur, harmonics=5):
    """Band-limited square: odd harmonics only, so it stays crisp without aliasing fizz."""
    out = np.zeros(int(SR * dur))
    for k in range(1, 2 * harmonics, 2):
        if freq * k < SR / 2.2:
            out += sine(freq * k, dur) / k
    return out


def noise(dur, seed):
    return np.random.default_rng(seed).uniform(-1.0, 1.0, int(SR * dur))


def biquad(x, kind, f0, q=0.707):
    """RBJ cookbook biquad (lowpass / highpass / bandpass). Plain loop: the clips are tiny."""
    w0 = 2 * np.pi * f0 / SR
    alpha = np.sin(w0) / (2 * q)
    c = np.cos(w0)
    if kind == "lp":
        b = [(1 - c) / 2, 1 - c, (1 - c) / 2]
    elif kind == "hp":
        b = [(1 + c) / 2, -(1 + c), (1 + c) / 2]
    elif kind == "bp":
        b = [alpha, 0.0, -alpha]
    else:
        raise ValueError(kind)
    a = [1 + alpha, -2 * c, 1 - alpha]
    b = [v / a[0] for v in b]
    a1, a2 = a[1] / a[0], a[2] / a[0]
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i, xi in enumerate(x):
        yi = b[0] * xi + b[1] * x1 + b[2] * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, xi, y1, yi
        y[i] = yi
    return y


def mix(*parts):
    """Sums (offset_seconds, signal) pairs into one buffer."""
    n = max(int(SR * off) + len(sig) for off, sig in parts)
    out = np.zeros(n)
    for off, sig in parts:
        s = int(SR * off)
        out[s:s + len(sig)] += sig
    return out


def echo(x, delay, gain, taps=2):
    out = np.concatenate([x, np.zeros(int(SR * delay * taps))])
    for k in range(1, taps + 1):
        d = int(SR * delay * k)
        out[d:d + len(x)] += x * (gain ** k)
    return out


def click(seed, dur=0.006, hp=2500.0):
    """A dry mechanical tick: a few ms of highpassed noise with a steep decay."""
    return biquad(noise(dur, seed), "hp", hp) * env_exp(dur, dur / 4, attack=0.0002)


def finish(x, max_dur=0.58):
    """Trim, remove DC, short edge fades (no clicks at the ends), normalise peak to PEAK_DBFS."""
    x = x[: int(SR * max_dur)]
    x = x - np.mean(x)
    fi, fo = int(SR * 0.0008), int(SR * 0.012)
    x[:fi] *= np.linspace(0, 1, fi)
    x[-fo:] *= np.linspace(1, 0, fo)
    x = x - np.mean(x)   # the fades shift the mean a hair; take it out again
    peak = np.max(np.abs(x))
    return x * (10 ** (PEAK_DBFS / 20) / peak) if peak > 0 else x


def bell(f0, dur, partials, tau):
    """Inharmonic struck-metal tone: (ratio, amplitude, decay multiplier) per partial."""
    out = np.zeros(int(SR * dur))
    for ratio, amp, dm in partials:
        out += amp * sine(f0 * ratio, dur) * env_exp(dur, tau * dm)
    return out


# ---------------------------------------------------------------- the sounds

def hover_tick():
    # the quietest thing in the set: a 15 ms high blip, played at low volume in game
    d = 0.015
    return finish(mix((0.0, 0.6 * sine(4200, d) * env_exp(d, 0.003)), (0.0, 0.5 * click(11, 0.004, 5000))))


def servo(rising):
    # a tiny actuator: a buzzy motor sweep that ends (or starts) on a hard mechanical stop
    d = 0.085
    f = np.linspace(170, 330, int(SR * d)) if rising else np.linspace(330, 170, int(SR * d))
    motor = biquad(sine(f, d) + 0.5 * sine(f * 2.01, d) + 0.25 * sine(f * 3.02, d), "bp", 900, 0.9)
    motor *= np.minimum(1.0, t_axis(d) / 0.01) * np.linspace(1.0, 0.6, int(SR * d))
    stop = mix((0.0, click(21 if rising else 22, 0.008, 1800)), (0.0, 0.6 * sine(2600, 0.02) * env_exp(0.02, 0.004)))
    if rising:
        return finish(mix((0.0, 0.35 * motor), (d - 0.004, stop)))
    return finish(mix((0.0, stop), (0.006, 0.35 * motor)))


def relay_chunk():
    # a heavy relay pulling in: low body thump + short metallic ring + contact noise
    d = 0.2
    thump = sine(np.linspace(150, 90, int(SR * d)), d) * env_exp(d, 0.025)
    ring = bell(1700, d, [(1.0, 0.5, 1.0), (1.71, 0.35, 0.7), (2.53, 0.25, 0.5), (3.9, 0.12, 0.35)], 0.05)
    contact = biquad(noise(0.02, 31), "bp", 3200, 1.2) * env_exp(0.02, 0.004)
    bounce = click(32, 0.005, 2200)
    return finish(mix((0.0, 1.0 * thump), (0.0, 0.45 * ring), (0.0, 0.8 * contact), (0.018, 0.4 * bounce)))


def confirm_chirp():
    # the rising two-tone confirm: a low then a high band-limited square, crisp edges, tiny gap
    d1, d2 = 0.055, 0.075
    a = square_soft(990, d1, 4) * env_exp(d1, 0.04, 0.002)
    b = square_soft(1485, d2, 4) * env_exp(d2, 0.05, 0.002)
    return finish(biquad(mix((0.0, a), (0.07, b)), "lp", 7000))


def denied_buzz():
    # two short low, rough pulses: unmistakably "no", but not harsh
    d = 0.11
    tone = square_soft(118, d, 9) + 0.4 * square_soft(121, d, 9)   # a slight detune gives the rasp
    tone *= 0.65 + 0.35 * np.sign(np.sin(2 * np.pi * 34 * t_axis(d)))
    env = np.minimum(1.0, t_axis(d) / 0.004) * np.minimum(1.0, (d - t_axis(d)) / 0.012)
    pulse = biquad(tone * env, "lp", 2400)
    return finish(mix((0.0, pulse), (0.14, pulse)))


def ready_chime():
    # a bright rising three-note "go": soft bell partials, the last note rings longest
    notes = [(784.0, 0.0), (1046.5, 0.075), (1568.0, 0.15)]
    parts = []
    for i, (f, off) in enumerate(notes):
        d = 0.42 - off
        tau = 0.08 if i < 2 else 0.14
        parts.append((off, bell(f, d, [(1.0, 1.0, 1.0), (2.0, 0.25, 0.6), (2.76, 0.18, 0.4)], tau)))
    parts.append((0.0, 0.5 * click(41, 0.005, 3000)))
    return finish(mix(*parts))


def unready_click():
    # the chime's opposite: one falling blip and a dull latch
    d = 0.09
    blip = sine(np.linspace(900, 520, int(SR * d)), d) * env_exp(d, 0.03)
    latch = biquad(noise(0.012, 51), "bp", 1400, 1.5) * env_exp(0.012, 0.003)
    return finish(mix((0.0, 0.8 * latch), (0.004, blip)))


def join_chirp():
    # someone sat down: a soft, quick upward glide, low-passed so it sits under the chat
    d = 0.11
    f = 620 * (1000 / 620) ** (t_axis(d) / d)
    s = sine(f, d) * np.sin(np.pi * np.minimum(1.0, t_axis(d) / d)) ** 2
    return finish(biquad(s + 0.15 * sine(f * 2, d), "lp", 3000))


def stinger_sunforged():
    # brass bell + latch click: a warm struck bell (minor-third hum partial), bright but short
    d = 0.55
    b = bell(660, d, [(0.5, 0.35, 1.6), (1.0, 1.0, 1.0), (1.19, 0.45, 0.8), (1.5, 0.3, 0.7),
                      (2.0, 0.35, 0.5), (2.66, 0.15, 0.35)], 0.16)
    return finish(mix((0.0, 0.7 * click(61, 0.006, 2200)), (0.008, b)))


def stinger_gravebound():
    # two low hollow bone knocks: resonant noise bursts + low modes, with a dry crypt echo
    def knock(seed, f):
        d = 0.12
        body = biquad(noise(d, seed), "bp", f * 2.3, 6.0) * env_exp(d, 0.02)
        mode = sine(f, d) * env_exp(d, 0.03) + 0.5 * sine(f * 2.37, d) * env_exp(d, 0.015)
        return 1.6 * body + mode
    dry = mix((0.0, knock(71, 190)), (0.13, 0.75 * knock(72, 160)))
    return finish(biquad(echo(dry, 0.09, 0.28, 2), "lp", 3500))


def stinger_horde():
    # a war drum: pitch-dropping skin thump with a slap of noise, then a lighter second hit
    def hit(seed, amp):
        d = 0.26
        f = 55 + 85 * np.exp(-t_axis(d) / 0.03)
        body = sine(f, d) * env_exp(d, 0.08, 0.001)
        skin = biquad(noise(0.04, seed), "lp", 1800) * env_exp(0.04, 0.008)
        return amp * mix((0.0, body), (0.0, 0.6 * skin))
    # a little drive gives the thump its weight on small speakers
    return finish(np.tanh(1.8 * mix((0.0, hit(81, 1.0)), (0.2, hit(82, 0.6)))))


def stinger_verdant():
    # hollow wooden chime: marimba-like partials (fundamental + ~4x), two notes a fifth apart
    def bar_note(f, d):
        return bell(f, d, [(1.0, 1.0, 1.0), (3.93, 0.3, 0.25), (9.2, 0.06, 0.12)], 0.09)
    return finish(mix((0.0, 0.4 * click(91, 0.004, 1500)), (0.003, bar_note(587.3, 0.4)), (0.1, 0.9 * bar_note(880, 0.45))))


def stinger_tidewrought():
    # sonar ping with a water-drip pluck in front: a clean sine with a long tail and a short echo
    drip_d = 0.035
    drip = sine(np.linspace(700, 1900, int(SR * drip_d)), drip_d) * env_exp(drip_d, 0.01)
    ping_d = 0.45
    ping = (sine(1480, ping_d) + 0.2 * sine(2960, ping_d) * env_exp(ping_d, 0.04)) * env_exp(ping_d, 0.11, 0.004)
    return finish(mix((0.0, 0.7 * drip), (0.05, echo(ping, 0.11, 0.25, 1)[: int(SR * 0.52)])))


SOUNDS = {
    "hover_tick": hover_tick,
    "servo_open": lambda: servo(True),
    "servo_close": lambda: servo(False),
    "relay_chunk": relay_chunk,
    "confirm_chirp": confirm_chirp,
    "denied_buzz": denied_buzz,
    "ready_chime": ready_chime,
    "unready_click": unready_click,
    "join_chirp": join_chirp,
    "faction_sunforged": stinger_sunforged,
    "faction_gravebound": stinger_gravebound,
    "faction_horde": stinger_horde,
    "faction_verdant": stinger_verdant,
    "faction_tidewrought": stinger_tidewrought,
}


def write_wav(path, x):
    pcm = np.clip(np.round(x * 32767), -32768, 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--wav-dir", default=None, help="also keep the 16-bit WAV masters here")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    total = 0
    with tempfile.TemporaryDirectory() as tmp:
        for name, fn in SOUNDS.items():
            x = fn()
            assert len(x) / SR < 0.6, name
            wav = os.path.join(args.wav_dir or tmp, name + ".wav")
            if args.wav_dir:
                os.makedirs(args.wav_dir, exist_ok=True)
            write_wav(wav, x)
            ogg = os.path.join(args.out, name + ".ogg")
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-ar", str(SR), "-ac", "1",
                            "-c:a", "libvorbis", "-q:a", "4", "-map_metadata", "-1", ogg], check=True)
            size = os.path.getsize(ogg)
            total += size
            print(f"{name:22s} {len(x) / SR:5.3f} s  {size / 1024:5.1f} KB")
    print(f"total {total / 1024:.1f} KB")
    if total > 300 * 1024:
        sys.exit("over the 300 KB budget")


if __name__ == "__main__":
    main()
