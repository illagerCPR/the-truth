#!/usr/bin/env python3
"""Generates the M8 assets of The Truth (docs/02 M8):

- five synthesized "data language" sound clips (docs/00 §8): the placeholder
  vanilla sounds of M3 are replaced by short scan pulses, synthesized from
  sine sweeps and noise (no external recordings, no licensing burden);
- the data-stream particle sprites (cyan / white motes);
- the Certus mark texture that replaces the M7 geometric fill;
- the Certus Solidifier GUI background.

Writes directly into src/main/resources/assets/thetruth/ and runs ffmpeg
(WAV -> Ogg Vorbis) for the sounds.
"""

import math
import os
import random
import struct
import subprocess
import sys
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/thetruth")
SOUNDS = os.path.join(ASSETS, "sounds")
TMP = os.path.join(ROOT, "build", "m8_audio")

SAMPLE_RATE = 44100
random.seed(0xF0F0)


def write_wav(path, samples):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        frames = b"".join(
            struct.pack("<h", max(-32767, min(32767, int(s * 32767)))) for s in samples)
        w.writeframes(frames)


def seconds(n):
    return int(n * SAMPLE_RATE)


def sweep(dur, f0, f1, curve=1.0):
    """Sine sweep with exponential interpolation between f0 and f1."""
    n = seconds(dur)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / n
        f = f0 * (f1 / f0) ** (t ** curve)
        phase += 2 * math.pi * f / SAMPLE_RATE
        out.append(math.sin(phase))
    return out


def env_exp(samples, rate):
    return [s * math.exp(-rate * i / SAMPLE_RATE) for i, s in enumerate(samples)]


def env_adsr(samples, attack, release):
    n = len(samples)
    a = seconds(attack)
    r = seconds(release)
    out = []
    for i, s in enumerate(samples):
        g = 1.0
        if i < a:
            g = i / max(1, a)
        if i > n - r:
            g = min(g, (n - i) / max(1, r))
        out.append(s * g)
    return out


def noise(dur):
    return [random.uniform(-1.0, 1.0) for _ in range(seconds(dur))]


def gen_data_ingest():
    """Short ascending scan pulse: data written into the ledger."""
    s = sweep(0.35, 880, 1760)
    s = env_exp(s, 8.0)
    s = [x + 0.3 * y for x, y in zip(s, env_exp(sweep(0.35, 1760, 3520), 8.0))]
    peak = max(abs(x) for x in s)
    return [x / peak * 0.8 for x in s]


def gen_uncertainty_warning():
    """Two soft low bumps: the ledger counting down toward deletion."""
    total = seconds(0.55)
    out = [0.0] * total
    for start in (0.0, 0.24):
        bump = sweep(0.14, 160, 90)
        bump = env_adsr(bump, 0.01, 0.10)
        base = seconds(start)
        for i, v in enumerate(bump):
            if base + i < total:
                out[base + i] += v
    peak = max(abs(x) for x in out)
    return [x / peak * 0.85 for x in out]


def gen_uncertainty_collapse():
    """Falling sweep plus a burst of noise: matter collapses into unformed data."""
    s = sweep(0.8, 1200, 140, curve=0.6)
    s = env_exp(s, 4.5)
    n = env_exp(noise(0.22), 14.0)
    for i, v in enumerate(n):
        s[i] += 0.6 * v
    peak = max(abs(x) for x in s)
    return [x / peak * 0.85 for x in s]


def gen_uncertainty_settle():
    """Rising soft tone: sizes settle back to their records."""
    s = sweep(0.5, 300, 900)
    s = env_adsr(s, 0.08, 0.22)
    peak = max(abs(x) for x in s)
    return [x / peak * 0.7 for x in s]


def gen_uncertainty_scramble():
    """Random frequency hops: stack sizes being shuffled by the curve."""
    total = seconds(0.4)
    out = []
    seg = seconds(0.05)
    phase = 0.0
    while len(out) < total:
        f = random.uniform(220, 1800)
        local = [math.sin(phase + 2 * math.pi * (phase + 2 * math.pi * f * i / SAMPLE_RATE) * 0)
                 for i in range(seg)]
        # simpler: fresh phase per segment
        local = []
        ph = 0.0
        for i in range(seg):
            ph += 2 * math.pi * f / SAMPLE_RATE
            local.append(math.sin(ph))
        out.extend(local)
    out = out[:total]
    out = env_adsr(out, 0.005, 0.12)
    decay = [x * (1 - 0.5 * i / total) for i, x in enumerate(out)]
    peak = max(abs(x) for x in decay)
    return [x / peak * 0.7 for x in decay]


CLIPS = {
    "data_ingest": gen_data_ingest,
    "uncertainty_warning": gen_uncertainty_warning,
    "uncertainty_collapse": gen_uncertainty_collapse,
    "uncertainty_settle": gen_uncertainty_settle,
    "uncertainty_scramble": gen_uncertainty_scramble,
}


def gen_sounds():
    os.makedirs(SOUNDS, exist_ok=True)
    os.makedirs(TMP, exist_ok=True)
    for name, gen in CLIPS.items():
        wav = os.path.join(TMP, name + ".wav")
        ogg = os.path.join(SOUNDS, name + ".ogg")
        write_wav(wav, gen())
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", wav,
             "-c:a", "libvorbis", "-qscale:a", "4", ogg],
            check=True)
        print("sound:", name)


# ------------------------------------------------------------------ textures

from PIL import Image  # noqa: E402


def gen_particles():
    directory = os.path.join(ASSETS, "textures/particle")
    os.makedirs(directory, exist_ok=True)
    # A soft square mote: solid core, slight glow. Cyan and white variants.
    cyan = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    white = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    for x in range(8):
        for y in range(8):
            d = max(abs(x - 3.5), abs(y - 3.5))
            if d <= 1.2:
                cyan.putpixel((x, y), (127, 223, 207, 255))
                white.putpixel((x, y), (233, 255, 248, 255))
            elif d <= 2.4:
                cyan.putpixel((x, y), (127, 223, 207, 130))
                white.putpixel((x, y), (233, 255, 248, 120))
            elif d <= 3.4:
                cyan.putpixel((x, y), (127, 223, 207, 40))
                white.putpixel((x, y), (233, 255, 248, 36))
    cyan.save(os.path.join(directory, "data_stream.png"))
    white.save(os.path.join(directory, "data_stream_2.png"))
    desc = '{"textures": ["thetruth:data_stream", "thetruth:data_stream_2"]}'
    os.makedirs(os.path.join(ASSETS, "particles"), exist_ok=True)
    with open(os.path.join(ASSETS, "particles/data_stream.json"), "w") as f:
        f.write(desc + "\n")
    print("particles: data_stream (2 sprites)")


def gen_mark():
    """The Certus mark: a tidy cyan bracket motif on a transparent ground."""
    directory = os.path.join(ASSETS, "textures/item")
    os.makedirs(directory, exist_ok=True)
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    cyan = (127, 223, 207, 255)
    glow = (127, 223, 207, 90)
    bright = (233, 255, 248, 255)
    # Hollow 4x4 square at (2,1)-(5,4) with a bright centre dot.
    box = [(2, 1), (3, 1), (4, 1), (5, 1),
           (2, 2), (5, 2),
           (2, 3), (5, 3),
           (2, 4), (3, 4), (4, 4), (5, 4)]
    for x, y in box:
        img.putpixel((x, y), cyan)
    img.putpixel((3, 2), bright)
    # Faint glow ring just outside the box.
    for x, y in [(1, 1), (1, 2), (1, 3), (1, 4), (6, 1), (6, 2), (6, 3), (6, 4),
                 (2, 0), (3, 0), (4, 0), (5, 0), (2, 5), (3, 5), (4, 5), (5, 5)]:
        img.putpixel((x, y), glow)
    img.save(os.path.join(directory, "certus_mark.png"))
    print("texture: item/certus_mark.png")


def gen_solidifier_gui():
    """Standard 176x166 container background in the low-information style."""
    directory = os.path.join(ASSETS, "textures/gui")
    os.makedirs(directory, exist_ok=True)
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    # Panel
    img.paste((200, 208, 206, 255), (0, 0, 176, 166))
    for x in range(176):
        img.putpixel((x, 0), (230, 234, 232, 255))
        img.putpixel((x, 165), (120, 128, 126, 255))
    for y in range(166):
        img.putpixel((0, y), (230, 234, 232, 255))
        img.putpixel((175, y), (120, 128, 126, 255))

    def slot(x, y):
        img.paste((140, 148, 146, 255), (x, y, x + 18, y + 18))
        img.paste((58, 62, 60, 255), (x + 1, y + 1, x + 17, y + 17))

    slot(61, 33)
    slot(109, 33)
    for row in range(3):
        for column in range(9):
            slot(7 + column * 18, 83 + row * 18)
    for column in range(9):
        slot(7 + column * 18, 141)

    # Arrow between the slots (data-cyan, quiet).
    arrow = (127, 223, 207, 255)
    for x in range(82, 106):
        img.putpixel((x, 42), arrow)
        img.putpixel((x, 43), arrow)
    for i in range(4):
        for y in range(38 + i, 47 - i):
            img.putpixel((106 + i, y), arrow)

    # Power gauge frame (right side; the fill is drawn live by the screen).
    gauge = (154, 243, 212, 255)
    for y in range(16, 72):
        img.putpixel((151, y), gauge)
        img.putpixel((157, y), gauge)
    for x in range(151, 158):
        img.putpixel((x, 16), gauge)
        img.putpixel((x, 72), gauge)

    img.save(os.path.join(directory, "certus_solidifier.png"))
    print("texture: gui/certus_solidifier.png")


def main():
    gen_sounds()
    gen_particles()
    gen_mark()
    gen_solidifier_gui()


if __name__ == "__main__":
    sys.exit(main())
