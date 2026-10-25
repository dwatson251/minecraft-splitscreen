#!/usr/bin/env python3
"""README split-screen pictures: runs 2-4 real dev clients side by side (one world, a profile each), every client
captures its own view at the size its tile has on the Deck, and the views are composed in the KWin tiler's
layout (appimage/kwin/mcss-tile). Development only; see DevSplit.java.

    uv run --with pillow scripts/dev-split.py [layout ...]     layouts: 2p-horizontal 2p-vertical 3p-horizontal
                                                                3p-vertical 4p (default: all)
Needs: glue-mod built with `./gradlew build devClasspath`, a JDK 25 in .tools/, and the dev instances in
.tools/dev/instances/P1..P4 (mods + options.txt) with profiles Alice, Bob, Kai, Zuri in .tools/dev/mcss-profiles.
"""
import os
import subprocess
import sys
import time
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DEV = ROOT / ".tools" / "dev"
JAVA = next((ROOT / ".tools").glob("jdk-25*")) / "bin" / "java"
CLASSPATH = (ROOT / "glue-mod" / "build" / "dev-classpath.txt").read_text().strip()
LAUNCH_CFG = ROOT / "glue-mod" / ".gradle" / "loom-cache" / "launch.cfg"
OUT = ROOT / "docs" / "screenshots"
W, H = 1280, 800

# the tiler's slots (main.js) on the Deck's 1280x800 screen: (x, y, width, height) per player
HW, HH = W // 2, H // 2
QUAD = [(0, 0, HW, HH), (HW, 0, HW, HH), (0, HH, HW, HH), (HW, HH, HW, HH)]
LAYOUTS = {
    "2p-horizontal": [(0, 0, W, HH), (0, HH, W, HH)],
    "2p-vertical": [(0, 0, HW, H), (HW, 0, HW, H)],
    "3p-horizontal": [(0, 0, W, HH), QUAD[2], QUAD[3]],
    "3p-vertical": [(0, 0, HW, H), QUAD[1], QUAD[3]],
    "4p": QUAD,
}


def run(name, slots):
    shots = DEV / "split-shots" / name
    shots.mkdir(parents=True, exist_ok=True)
    for f in shots.glob("*.png"):
        f.unlink()
    control = DEV / "mcss-control"
    control.mkdir(exist_ok=True)
    for f in control.iterdir():
        f.unlink()
    procs = []
    for k, (_, _, w, h) in enumerate(slots, start=1):
        env = dict(os.environ, MCSS_PLAYER=str(k), MCSS_DEV_SPLIT=str(shots), MCSS_DEV_PLAYERS=str(len(slots)))
        cmd = [str(JAVA), "-Xmx1G", "-cp", CLASSPATH, f"-Dfabric.dli.config={LAUNCH_CFG}", "-Dfabric.dli.env=client",
               "-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient", "--enable-native-access=ALL-UNNAMED",
               "net.fabricmc.devlaunchinjector.Main", "--width", str(w), "--height", str(h)]
        log = open(shots / f"P{k}.log", "w")
        procs.append(subprocess.Popen(cmd, cwd=DEV / "instances" / f"P{k}" / "minecraft", env=env, stdout=log,
                                      stderr=subprocess.STDOUT))
        print(f"{name}: started P{k} ({w}x{h}), pid {procs[-1].pid}", flush=True)
        time.sleep(25 if k == 1 else 12)  # host first, then one guest at a time
    deadline = time.time() + 420
    while time.time() < deadline and any(p.poll() is None for p in procs):
        time.sleep(2)
    for p in procs:
        if p.poll() is None:
            print(f"{name}: pid {p.pid} still running, stopping it", flush=True)
            p.kill()
    views = [shots / f"P{k}.png" for k in range(1, len(slots) + 1)]
    missing = [v.name for v in views if not v.exists()]
    if missing:
        print(f"{name}: missing {missing}, see {shots}/*.log", flush=True)
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
    ok = all([run(n, LAYOUTS[n]) for n in wanted])
    sys.exit(0 if ok else 1)
