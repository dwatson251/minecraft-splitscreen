#!/usr/bin/env python3
"""Builds the README banner and the Steam artwork from the two generated backgrounds in assets/art.
   scripts/make-art.py        (needs Pillow)
Backgrounds: hero-bg.png (wide, 1920x640) and tall-bg.png (portrait). Titles are drawn here, not by the image model."""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ART, STEAM = os.path.join(ROOT, "assets/art"), os.path.join(ROOT, "appimage/steam")
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
os.makedirs(STEAM, exist_ok=True)

def cover(im, w, h, anchor=(0.5, 0.5)):
    s = max(w / im.width, h / im.height)
    im = im.resize((round(im.width * s) + 1, round(im.height * s) + 1), Image.LANCZOS)
    x, y = round((im.width - w) * anchor[0]), round((im.height - h) * anchor[1])
    return im.crop((x, y, x + w, y + h))

def title(canvas, cx, cy, size, lines=("MINECRAFT", "SPLITSCREEN"), sub=None):
    """Blocky white title with a soft shadow, centred at (cx, cy)."""
    f = ImageFont.truetype(FONT, size)
    lh = int(size * 1.08)
    top = cy - lh * len(lines) // 2
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    sd, d = ImageDraw.Draw(shadow), None
    for i, t in enumerate(lines):
        sd.text((cx + size // 22, top + i * lh + size // 22), t, font=f, fill=(0, 0, 0, 200), anchor="mt")
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(size // 28)))
    d = ImageDraw.Draw(canvas)
    for i, t in enumerate(lines):
        d.text((cx, top + i * lh), t, font=f, fill=(255, 255, 255, 255), stroke_width=max(2, size // 40),
               stroke_fill=(40, 28, 20, 255), anchor="mt")
    if sub:
        g = ImageFont.truetype(FONT, max(14, size // 4))
        d.text((cx, top + len(lines) * lh + size // 8), sub, font=g, fill=(255, 236, 190, 255), stroke_width=2,
               stroke_fill=(40, 28, 20, 255), anchor="mt")

def darken_left(im, strength=0.55, width=0.6):
    """Lets a left-hand title read over the bright sky."""
    g = Image.new("L", im.size, 0)
    px = g.load()
    for x in range(int(im.width * width)):
        a = int(255 * strength * (1 - x / (im.width * width)) ** 1.5)
        for y in range(im.height):
            px[x, y] = a
    out = im.copy()
    out.alpha_composite(Image.merge("RGBA", (*Image.new("RGB", im.size, (30, 18, 12)).split(), g)))
    return out

hero = Image.open(os.path.join(ART, "hero-bg.png")).convert("RGBA")
tall = Image.open(os.path.join(ART, "tall-bg.png")).convert("RGBA")
SUB = "2-4 players  ·  one window  ·  one controller each"

# README banner: title on the left, over the sky
b = darken_left(cover(hero, 1920, 640))
title(b, 470, 215, 100, sub=SUB)
b.convert("RGB").save(os.path.join(ROOT, "docs/banner.png"), optimize=True)

# Steam: hero (no text, Steam draws the logo over it), logo, portrait grid, wide grid, icon
cover(hero, 1920, 620).convert("RGB").save(os.path.join(STEAM, "hero.png"), optimize=True)
logo = Image.new("RGBA", (960, 400), (0, 0, 0, 0))
title(logo, 480, 150, 130, sub="SPLITSCREEN FOR 2-4 PLAYERS")
logo.crop(logo.getbbox()).save(os.path.join(STEAM, "logo.png"), optimize=True)
p = cover(tall, 600, 900, (0.5, 0.6))
title(p, 300, 150, 76)
p.convert("RGB").save(os.path.join(STEAM, "grid-tall.png"), optimize=True)
w = darken_left(cover(hero, 920, 430), 0.6, 0.7)
title(w, 270, 175, 62, sub="2-4 PLAYERS")
w.convert("RGB").save(os.path.join(STEAM, "grid-wide.png"), optimize=True)
ic = cover(hero, 512, 512, (0.5, 0.75))
m = Image.new("L", (512, 512), 0)
ImageDraw.Draw(m).rounded_rectangle((0, 0, 511, 511), 72, fill=255)
ic.putalpha(m)
ic.save(os.path.join(STEAM, "icon.png"), optimize=True)
print("art written")
