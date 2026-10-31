#!/usr/bin/env python3
"""Build a Cadillac bootanimation.zip from the splash GIF, in the format this tablet uses.

The tablet's stock file is 600x1024 (the panel's native portrait size) at 3 fps. The animation is
drawn upright in landscape and rotated to the native orientation. Installing it needs root or a
custom ROM (/system/media/bootanimation.zip is read-only); this script only builds the file.

    python tablet-setup/make_bootanimation.py
"""
import io
import math
import zipfile
from pathlib import Path
from PIL import Image, ImageFilter

root = Path(__file__).resolve().parents[1]
gif = Image.open(root / 'legacy' / 'design-assets' / 'splash-source.gif')
BLACK_POINT, SCALE, FPS = 18, 3, 15
edge = [0 if v <= BLACK_POINT else min(255, max(0, round(255 / (1 + math.exp(-(v - 128) / 26.0)) * 1.04 - 3))) for v in range(256)]

frames = []
for index in range(gif.n_frames):
    gif.seek(index)
    frames.append(gif.convert('RGB'))
box = None
for frame in frames:
    b = frame.convert('L').point(lambda v: 255 if v > BLACK_POINT else 0).getbbox()
    if b:
        box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
pad = 4
box = (box[0] - pad, box[1] - pad, box[2] + pad, box[3] + pad)
crest_width = box[2] - box[0]
target_width = 420                      # width of the crest on the 1024x600 landscape canvas
ratio = target_width / crest_width

def render(frame):
    crop = frame.crop(box)
    size = (round(crop.width * ratio), round(crop.height * ratio))
    big = crop.resize((size[0] * 2, size[1] * 2), Image.LANCZOS).filter(ImageFilter.UnsharpMask(2.0, 110, 1)).point(edge * 3)
    big = big.resize(size, Image.LANCZOS)
    canvas = Image.new('RGB', (1024, 600), (0, 0, 0))
    canvas.paste(big, ((1024 - size[0]) // 2, (600 - size[1]) // 2))
    return canvas.rotate(-90, expand=True)        # native 600x1024

def png(image):
    buffer = io.BytesIO()
    image.save(buffer, 'PNG', optimize=True)
    return buffer.getvalue()

out = Path(__file__).resolve().parent / 'bootanimation' / 'bootanimation.zip'
out.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(out, 'w', zipfile.ZIP_STORED) as z:       # boot animations must be stored, not compressed
    z.writestr('desc.txt', f'600 1024 {FPS}\np 1 0 part0\np 0 0 part1\n')
    # The GIF runs about 4.5 s at ~15 fps; keep every frame once, then hold the last one.
    for number, frame in enumerate(frames, start=1):
        z.writestr(f'part0/{number:03d}.png', png(render(frame)))
    z.writestr('part1/001.png', png(render(frames[-1])))
print(f'{out} frames={len(frames)} size={out.stat().st_size // 1024} KB')
