#!/usr/bin/env python3
"""Adds this AppImage to Steam as a non-Steam game, with its artwork.
  mcss-steam.py running                      exit 0 if Steam is running (it rewrites shortcuts.vdf on exit)
  mcss-steam.py found                        exit 0 if a Steam account was found
  mcss-steam.py add <exe> <artwork dir>      add the shortcut for every Steam account on this machine
MCSS_STEAM_DIRS (colon-separated) replaces the Steam folders searched, for tests.
Only appends: existing shortcuts are read and written back unchanged, after a backup of shortcuts.vdf."""
import os, struct, sys, time, zlib

NAME = "Minecraft Splitscreen"
HOME = os.path.expanduser("~")
STEAM_DIRS = [HOME + "/.local/share/Steam", HOME + "/.steam/steam", HOME + "/.steam/root",
              HOME + "/.var/app/com.valvesoftware.Steam/.local/share/Steam"]


def parse(b, i=0):
    """Binary VDF map starting at i: ([(type, key, value)], next index). Types: 0 map, 1 string, 2 int32, ..."""
    out = []
    while True:
        t = b[i]
        i += 1
        if t == 8:
            return out, i
        j = b.index(b"\0", i)
        key = b[i:j].decode("utf-8", "surrogateescape")
        i = j + 1
        if t == 0:
            v, i = parse(b, i)
        elif t == 1:
            j = b.index(b"\0", i)
            v, i = b[i:j].decode("utf-8", "surrogateescape"), j + 1
        elif t in (2, 3, 6):
            v, i = b[i:i + 4], i + 4
        elif t in (7, 10):
            v, i = b[i:i + 8], i + 8
        else:
            raise ValueError("unknown VDF type %d" % t)
        out.append((t, key, v))


def dump(m):
    out = bytearray()
    for t, k, v in m:
        out += bytes([t]) + k.encode("utf-8", "surrogateescape") + b"\0"
        if t == 0:
            out += dump(v)
        elif t == 1:
            out += v.encode("utf-8", "surrogateescape") + b"\0"
        else:
            out += v
    return bytes(out) + b"\x08"


def app_id(exe):
    """The id Steam derives for a shortcut (also the artwork file prefix)."""
    return zlib.crc32((exe + NAME).encode()) | 0x80000000


def entry(exe, icon):
    s = lambda k, v: (1, k, v)
    i = lambda k, v: (2, k, struct.pack("<I", v))
    return [i("appid", app_id(exe)), s("AppName", NAME), s("Exe", exe), s("StartDir", os.path.dirname(exe.strip('"')) + "/"),
            s("icon", icon), s("ShortcutPath", ""), s("LaunchOptions", ""), i("IsHidden", 0), i("AllowDesktopConfig", 1),
            i("AllowOverlay", 1), i("OpenVR", 0), i("Devkit", 0), s("DevkitGameID", ""), i("DevkitOverrideAppID", 0),
            i("LastPlayTime", 0), s("FlatpakAppID", ""), (0, "tags", [])]


def add(config, exe, art):
    """Adds the shortcut + artwork under one account's config folder. Returns 'added' or 'already there'."""
    path = os.path.join(config, "shortcuts.vdf")
    shortcuts = []
    if os.path.exists(path):
        raw = open(path, "rb").read()
        top, _ = parse(raw)
        shortcuts = next((v for t, k, v in top if k.lower() == "shortcuts"), [])
        if dump(top) != raw:
            raise ValueError("shortcuts.vdf didn't round-trip; leaving it alone")
        open(path + ".mcss-backup-" + time.strftime("%Y%m%d-%H%M%S"), "wb").write(raw)
    quoted = '"%s"' % exe
    grid = os.path.join(config, "grid")
    os.makedirs(grid, exist_ok=True)
    aid = app_id(quoted)
    for src, dst in (("grid-tall.png", "%dp.png"), ("grid-wide.png", "%d.png"), ("hero.png", "%d_hero.png"),
                     ("logo.png", "%d_logo.png"), ("icon.png", "%d_icon.png")):
        if os.path.exists(os.path.join(art, src)):
            with open(os.path.join(art, src), "rb") as f, open(os.path.join(grid, dst % aid), "wb") as o:
                o.write(f.read())
    if any(k == "Exe" and v == quoted for _, _, m in shortcuts if isinstance(m, list) for t, k, v in m if t == 1):
        return "already there"
    shortcuts.append((0, str(len(shortcuts)), entry(quoted, os.path.join(grid, "%d_icon.png" % aid))))
    tmp = path + ".tmp"
    open(tmp, "wb").write(dump([(0, "shortcuts", shortcuts)]))
    os.replace(tmp, path)
    return "added"


def configs():
    seen = set()
    for d in (os.environ.get("MCSS_STEAM_DIRS", "").split(":") if "MCSS_STEAM_DIRS" in os.environ else STEAM_DIRS):
        ud = os.path.join(d, "userdata")
        if not os.path.isdir(ud) or os.path.realpath(ud) in seen:
            continue
        seen.add(os.path.realpath(ud))
        for u in sorted(os.listdir(ud)):
            if u.isdigit() and u != "0":
                yield os.path.join(ud, u, "config")


def running():
    for p in os.listdir("/proc"):
        if p.isdigit():
            try:
                if open("/proc/%s/comm" % p).read().strip() in ("steam", "steamwebhelper"):
                    return True
            except OSError:
                pass
    return False


if __name__ == "__main__":
    if sys.argv[1:] == ["found"]:
        sys.exit(0 if next(configs(), None) else 1)
    if sys.argv[1:] == ["running"]:
        sys.exit(0 if running() else 1)
    if len(sys.argv) == 4 and sys.argv[1] == "add":
        found = list(configs())
        if not found:
            print("no Steam account found")
            sys.exit(2)
        for c in found:
            print("%s: %s" % (c, add(c, sys.argv[2], sys.argv[3])))
        sys.exit(0)
    sys.exit(__doc__)
