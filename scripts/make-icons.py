#!/usr/bin/env python3
"""Draws the controller family icons used in the README (docs/icons/*.svg). No dependencies.
   scripts/make-icons.py"""
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../docs/icons")
os.makedirs(OUT, exist_ok=True)
BODY = ("M32 18 H96 C112 18 120 38 124 64 C126 78 118 88 108 84 C98 80 94 66 86 62 H42 C34 66 30 80 20 84 "
        "C10 88 2 80 4 64 C8 38 16 18 32 18 Z")
FACE = {"N": (94, 30), "E": (106, 42), "S": (94, 54), "W": (82, 42)}  # face button centres
INK, DARK = "#e8ecf2", "#1f2430"


def svg(body, parts):
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 96" width="128" height="96">'
            '<path d="%s" fill="%s" stroke="%s" stroke-width="3" stroke-linejoin="round"/>%s</svg>\n'
            % (BODY, body[0], body[1], "".join(parts)))


def stick(x, y):
    return '<circle cx="%d" cy="%d" r="8" fill="%s" stroke="%s" stroke-width="2"/>' % (x, y, DARK, INK)


def dpad(x, y, ink=INK):
    return ('<path d="M%d %d h6 v6 h6 v6 h-6 v6 h-6 v-6 h-6 v-6 h6 z" fill="%s"/>' % (x - 3, y - 9, ink))


def letter(pos, ch, fill, ink=DARK):
    x, y = FACE[pos]
    return ('<circle cx="%d" cy="%d" r="6.5" fill="%s"/><text x="%d" y="%d" font-family="sans-serif" font-weight="700" '
            'font-size="9" text-anchor="middle" fill="%s">%s</text>' % (x, y, fill, x, y + 3.2, ink, ch))


def shape(pos, kind, color):
    x, y = FACE[pos]
    d = {"tri": "M%d %d l5.5 9.5 h-11 z" % (x, y - 5.5),
         "cross": "M%d %d l10 10 M%d %d l-10 10" % (x - 5, y - 5, x + 5, y - 5),
         "square": "M%d %d h9 v9 h-9 z" % (x - 4.5, y - 4.5)}
    if kind == "circle":
        return '<circle cx="%d" cy="%d" r="5" fill="none" stroke="%s" stroke-width="2.2"/>' % (x, y, color)
    return '<path d="%s" fill="none" stroke="%s" stroke-width="2.2" stroke-linecap="round"/>' % (d[kind], color)


icons = {
    "xbox": svg(("#3f8f4f", "#1f5a2c"), [stick(30, 36), dpad(46, 58), stick(80, 62),
                letter("N", "Y", "#f2c231"), letter("E", "B", "#e0483d"), letter("S", "A", "#58b868"), letter("W", "X", "#3d7fe0")]),
    "playstation": svg(("#3a62b8", "#1f3a7a"), [dpad(34, 38), stick(48, 62), stick(80, 62),
                shape("N", "tri", "#5fd0a0"), shape("E", "circle", "#f06a6a"), shape("S", "cross", "#7aa8ff"),
                shape("W", "square", "#f09ad0")]),
    "switch": svg(("#c9ced8", "#7b8392"), [stick(30, 36), dpad(46, 58, DARK), stick(80, 62),
                letter("N", "X", DARK, INK), letter("E", "A", DARK, INK), letter("S", "B", DARK, INK), letter("W", "Y", DARK, INK)]),
    "steam-deck": ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 96" width="128" height="96">'
                   '<rect x="3" y="16" width="122" height="62" rx="20" fill="#2b3340" stroke="#0f141c" stroke-width="3"/>'
                   '<rect x="30" y="24" width="68" height="44" rx="4" fill="#0f141c" stroke="%s" stroke-width="1.5"/>'
                   '<circle cx="64" cy="46" r="9" fill="none" stroke="%s" stroke-width="2.5"/>'
                   '<circle cx="14" cy="34" r="6" fill="%s"/><circle cx="114" cy="56" r="6" fill="%s"/>'
                   '<circle cx="108" cy="34" r="3.5" fill="%s"/><circle cx="116" cy="40" r="3.5" fill="%s"/>'
                   '<circle cx="108" cy="46" r="3.5" fill="%s"/><circle cx="100" cy="40" r="3.5" fill="%s"/>'
                   '<path d="M8 50 h12 M14 44 v12" stroke="%s" stroke-width="3" stroke-linecap="round"/></svg>\n'
                   % (INK, INK, INK, INK, INK, INK, INK, INK, INK)),
    "generic": svg(("#5b6472", "#2e3540"), [stick(30, 36), dpad(46, 58), stick(80, 62)] +
                   ['<circle cx="%d" cy="%d" r="5" fill="%s"/>' % (x, y, INK) for x, y in FACE.values()]),
}
for name, s in icons.items():
    open(os.path.join(OUT, name + ".svg"), "w").write(s)
print("icons:", ", ".join(icons))
