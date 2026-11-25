"""Invented artwork for the fake server: posters, backdrops, stills, avatars (needs Pillow)."""
import colorsys
import hashlib
import io
import math
import random
from functools import lru_cache

from PIL import Image, ImageDraw, ImageFilter, ImageFont


def _rgb(h, s, v):
    return tuple(int(c * 255) for c in colorsys.hsv_to_rgb(h % 1, s, v))


def _font(size, bold=True):
    for name in (("arialbd.ttf", "DejaVuSans-Bold.ttf") if bold else ("arial.ttf", "DejaVuSans.ttf")):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def _scene(seed, w, h):
    rnd = random.Random(int(hashlib.md5(seed.encode()).hexdigest(), 16))
    hue = rnd.random()
    img = Image.new("RGB", (w, h))
    top, bottom = _rgb(hue, 0.55, 0.75), _rgb(hue + 0.12, 0.8, 0.12)
    px = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        px.line([(0, y), (w, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)))
    glow = Image.new("RGB", (w, h))
    r = h * rnd.uniform(0.12, 0.22)
    cx, cy = w * rnd.uniform(0.55, 0.8), h * rnd.uniform(0.2, 0.4)
    ImageDraw.Draw(glow).ellipse([cx - r, cy - r, cx + r, cy + r], fill=_rgb(hue + 0.5, 0.35, 1))
    glow = glow.filter(ImageFilter.GaussianBlur(h / 40))
    img = Image.composite(glow, img, glow.convert("L"))
    d = ImageDraw.Draw(img)
    for layer in range(4):
        base = h * (0.5 + layer * 0.12)
        amp, freq, ph = h * 0.08, rnd.uniform(1, 3), rnd.random() * 6
        pts = [(x, base + amp * math.sin(ph + x / w * freq * 6.28) + amp * 0.4 * math.sin(ph * 2 + x / w * 17))
               for x in range(0, w + 8, 8)]
        d.polygon(pts + [(w, h), (0, h)], fill=_rgb(hue + 0.05 * layer, 0.7, 0.4 - layer * 0.09))
    return img


def _jpeg(img):
    out = io.BytesIO()
    img.convert("RGB").save(out, "JPEG", quality=85)
    return out.getvalue()


@lru_cache(maxsize=512)
def art(kind, seed):
    if kind == "poster":
        w, h = 342, 513
        img = _scene(seed, w, h)
        d = ImageDraw.Draw(img)
        words, lines, line = seed.split(), [], ""
        for word in words:
            if line and len(line) + len(word) > 11:
                lines.append(line)
                line = word
            else:
                line = (line + " " + word).strip()
        lines.append(line)
        y = h - 40 - 44 * len(lines)
        for text in lines:
            d.text((22, y), text.upper(), font=_font(36), fill="white")
            y += 44
        return _jpeg(img)
    if kind == "avatar":
        img = _scene(seed, 320, 320)
        return _jpeg(img)
    if kind == "thumb":
        return _jpeg(_scene(seed, 320, 180))
    return _jpeg(_scene(seed, 1280, 720))
