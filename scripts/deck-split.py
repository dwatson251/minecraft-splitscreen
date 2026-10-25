#!/usr/bin/env python3
"""README split-screen pictures from a real Steam Deck: runs the installed AppImage over SSH with the dev-only
DevSplit flags (players join without controllers, each view saves its own picture at its real tile size), copies
the views back and composes them in the tiler's layout into docs/screenshots/split-<layout>.png.
Development only; the Deck's own profiles, settings and worlds are put back afterwards.

    uv run --with pillow scripts/deck-split.py [layout ...]    layouts as in dev-split.py (default: all)
    MCSS_DECK=deck@steamdeck.local  MCSS_DECK_APPIMAGE=~/Applications/MinecraftSplitscreen-x86_64.AppImage
"""
import json
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DECK = os.environ.get("MCSS_DECK", "deck@steamdeck.local")
APPIMAGE = os.environ.get("MCSS_DECK_APPIMAGE", "$HOME/Applications/MinecraftSplitscreen-x86_64.AppImage")
PRISM = "$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher"
PROFILES = ROOT / "test" / "smoke" / "profiles"
OUT = ROOT / "docs" / "screenshots"
W, H = 1280, 800
HW, HH = W // 2, H // 2
QUAD = [(0, 0, HW, HH), (HW, 0, HW, HH), (0, HH, HW, HH), (HW, HH, HW, HH)]
LAYOUTS = {  # the tiler's slots (appimage/kwin/mcss-tile) per layout
    "2p-horizontal": [(0, 0, W, HH), (0, HH, W, HH)],
    "2p-vertical": [(0, 0, HW, H), (HW, 0, HW, H)],
    "3p-horizontal": [(0, 0, W, HH), QUAD[2], QUAD[3]],
    "3p-vertical": [(0, 0, HW, H), QUAD[1], QUAD[3]],
    "4p": QUAD,
}


def ssh(script, timeout=120):
    r = subprocess.run(["ssh", "-o", "BatchMode=yes", DECK, "bash -s"], input=script, text=True, capture_output=True,
                       timeout=timeout)
    if r.returncode != 0:
        raise RuntimeError(f"ssh failed ({r.returncode}): {r.stderr.strip()[-500:]}")
    return r.stdout


def setup():
    """Adds the sample profiles the Deck doesn't have yet (never touches existing ones); returns the added names."""
    writes = "\n".join(
        f"""[ -e "$D/{p.stem}.json" ] || {{ cat > "$D/{p.stem}.json" <<'J'\n{p.read_text().strip()}\nJ\necho {p.stem}; }}"""
        for p in sorted(PROFILES.glob("*.json")))
    out = ssh(f"""set -e
P="{PRISM}"; D="$P/mcss-profiles"; mkdir -p "$D" "$P/mcss-dev-split"
{writes}
c="$P/instances/P1/minecraft/config/splitscreen.properties"
if [ -e "$c" ]; then cp "$c" "$c.mcss-dev-bak"; fi
""")
    return out.split()


def teardown(added):
    rm_profiles = " ".join(f'"$P/mcss-profiles/{n}.json"' for n in added)
    ssh(f"""P="{PRISM}"
{('rm -f ' + rm_profiles) if added else ':'}
c="$P/instances/P1/minecraft/config/splitscreen.properties"
if [ -e "$c.mcss-dev-bak" ]; then mv "$c.mcss-dev-bak" "$c"; else rm -f "$c"; fi
find "$P/instances/P1/minecraft/saves" -maxdepth 1 -type d -name 'DevSplit-*' -exec rm -rf {{}} +
rm -rf "$P/mcss-dev-split"
""")


def run(name, slots):
    vertical = "vertical" in name
    shots = f"{PRISM}/mcss-dev-split/{name}"
    ssh(f"""set -e
P="{PRISM}"; rm -rf "{shots}"; mkdir -p "{shots}" "$P/instances/P1/minecraft/config"
printf 'layout={'vertical' if vertical else 'horizontal'}\\n' > "$P/instances/P1/minecraft/config/splitscreen.properties"
systemctl --user reset-failed mcss-split 2>/dev/null || true
systemd-run --user --unit=mcss-split --collect -p RuntimeMaxSec=600 --setenv=WAYLAND_DISPLAY=wayland-0 \\
  --setenv=DISPLAY=:0 --setenv=MCSS_DEV_SPLIT="{shots}" --setenv=MCSS_DEV_PLAYERS={len(slots)} {APPIMAGE}
""")
    print(f"{name}: started on the Deck", flush=True)
    deadline = time.time() + 660
    while time.time() < deadline:
        time.sleep(10)
        if ssh("systemctl --user is-active -q mcss-split && echo running || echo done").strip() == "done":
            break
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["scp", "-q", "-o", "BatchMode=yes", f"{DECK}:{shots.replace('$HOME', '~')}/*.png", tmp],
                       timeout=120)
        views = [Path(tmp) / f"P{k}.png" for k in range(1, len(slots) + 1)]
        missing = [v.name for v in views if not v.exists()]
        if missing:
            print(f"{name}: missing {missing}; see the Deck's ~/.local/state/mc-splitscreen and instance logs", flush=True)
            return False
        canvas = Image.new("RGB", (W, H))
        for view, (x, y, w, h) in zip(views, slots):
            canvas.paste(Image.open(view).convert("RGB").resize((w, h)), (x, y))
        OUT.mkdir(parents=True, exist_ok=True)
        canvas.save(OUT / f"split-{name}.png", optimize=True)
    print(f"{name}: wrote {OUT / f'split-{name}.png'}", flush=True)
    return True


if __name__ == "__main__":
    wanted = sys.argv[1:] or list(LAYOUTS)
    added = setup()
    try:
        ok = all([run(n, LAYOUTS[n]) for n in wanted])
    finally:
        teardown(added)
    sys.exit(0 if ok else 1)
