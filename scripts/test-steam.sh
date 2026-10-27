#!/bin/bash
# Checks that mcss-steam.py appends our shortcut to Steam's shortcuts.vdf without touching the existing ones.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
export MCSS_STEAM_DIRS=$T/steam
C=$T/steam/userdata/1234/config; mkdir -p "$C" "$T/art"
for f in grid-tall grid-wide hero logo icon; do echo "$f" > "$T/art/$f.png"; done
fail() { echo "FAIL: $*"; exit 1; }
PY="python3 $ROOT/appimage/mcss-steam.py"

# an existing shortcut in the format Steam writes
python3 - "$C/shortcuts.vdf" <<'PY'
import sys, struct
sys.path.insert(0, "appimage")
import importlib.util
spec = importlib.util.spec_from_file_location("s", "appimage/mcss-steam.py"); s = importlib.util.module_from_spec(spec); spec.loader.exec_module(s)
e = s.entry('"/usr/bin/other"', "")
e[1] = (1, "AppName", "Other Game")
open(sys.argv[1], "wb").write(s.dump([(0, "shortcuts", [(0, "0", e)])]))
PY
cp "$C/shortcuts.vdf" "$T/before"

$PY add /home/me/MinecraftSplitscreen.AppImage "$T/art" | grep -q added || fail "not added"
python3 - "$C/shortcuts.vdf" "$T/before" <<'PY' || exit 1
import sys, importlib.util
spec = importlib.util.spec_from_file_location("s", "appimage/mcss-steam.py"); s = importlib.util.module_from_spec(spec); spec.loader.exec_module(s)
now, _ = s.parse(open(sys.argv[1], "rb").read()); before, _ = s.parse(open(sys.argv[2], "rb").read())
assert s.dump(now) == open(sys.argv[1], "rb").read(), "output isn't well-formed"
sc, old = now[0][2], before[0][2]
assert len(sc) == 2 and sc[0] == old[0], "existing shortcut changed"
d = {k: v for t, k, v in sc[1][2]}
assert d["AppName"] == "Minecraft Splitscreen" and d["Exe"] == '"/home/me/MinecraftSplitscreen.AppImage"', d
PY
[ "$(ls "$C"/shortcuts.vdf.mcss-backup-* | wc -l)" = 1 ] || fail "no backup"
cmp "$C"/shortcuts.vdf.mcss-backup-* "$T/before" || fail "backup differs"
for n in p "" _hero _logo _icon; do ls "$C"/grid/*"$n.png" > /dev/null 2>&1 || fail "missing artwork $n"; done
[ "$(ls "$C"/grid | wc -l)" = 5 ] || fail "artwork count"

$PY add /home/me/MinecraftSplitscreen.AppImage "$T/art" | grep -q "already there" || fail "added twice"
python3 -c "
import importlib.util
spec = importlib.util.spec_from_file_location('s', 'appimage/mcss-steam.py'); s = importlib.util.module_from_spec(spec); spec.loader.exec_module(s)
assert len(s.parse(open('$C/shortcuts.vdf','rb').read())[0][0][2]) == 2" || fail "duplicate entry"

# no shortcuts.vdf yet
rm "$C/shortcuts.vdf"; $PY add /x/y.AppImage "$T/art" | grep -q added || fail "fresh file"
echo "steam tests passed"
