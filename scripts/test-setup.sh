#!/bin/bash
# Checks that mcss-setup.sh installs missing mods but never removes or replaces ones the player put in mods/.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
export MCSS_PRISM_DIR=$T/prism
mkdir -p "$MCSS_PRISM_DIR"
echo '{"formatVersion":3,"accounts":[{"type":"MSA","profile":{"name":"Host"}}]}' > "$MCSS_PRISM_DIR/accounts.json"

jar() { # jar <file> <mod id> [padding]
	local d; d=$(mktemp -d)
	echo "{\"schemaVersion\":1,\"id\":\"$2\",\"version\":\"1\"}" > "$d/fabric.mod.json"
	echo "${3:-}" > "$d/pad"
	(cd "$d" && python3 -c "import sys,zipfile;z=zipfile.ZipFile(sys.argv[1],'w');[z.write(f) for f in ('fabric.mod.json','pad')]" "$1")
	rm -rf "$d"
}
fail() { echo "FAIL: $*"; exit 1; }

B=$T/bundle; mkdir "$B"
jar "$B/sodium-2.jar" sodium; jar "$B/splitscreen-glue-2.jar" splitscreen-glue; jar "$B/yacl-2.jar" yacl
run() { "$ROOT/appimage/mcss-setup.sh" "$B" > "$T/out"; }
M=$MCSS_PRISM_DIR/instances/P1/minecraft/mods
mkdir -p "$M"
# state left by an older version: its own jars (no manifest), a stale glue, a player's mod, a disabled mod
jar "$M/sodium-1.jar" sodium; jar "$M/splitscreen-glue-1.jar" splitscreen-glue
jar "$M/mymod.jar" mymod; jar "$M/other.jar.disabled" other

run
[ -f "$M/mymod.jar" ] && [ -f "$M/other.jar.disabled" ] || fail "player's files were removed"
[ -f "$M/sodium-2.jar" ] && [ ! -f "$M/sodium-1.jar" ] || fail "old managed sodium not upgraded"
[ -f "$M/splitscreen-glue-2.jar" ] && [ ! -f "$M/splitscreen-glue-1.jar" ] || fail "glue not replaced"
[ -f "$M/yacl-2.jar" ] || fail "missing mod not installed"
cp "$M/.mcss-managed" "$T/m1"; ls "$M" > "$T/l1"
run; cmp "$M/.mcss-managed" "$T/m1" && ls "$M" | cmp - "$T/l1" || fail "second run changed things"

# player swaps in their own sodium: it must be kept, ours must not come back
rm "$M/sodium-2.jar"; jar "$M/sodium-player.jar" sodium; run
[ -f "$M/sodium-player.jar" ] && [ ! -f "$M/sodium-2.jar" ] || fail "player's sodium was replaced"

# a mod we shipped before but no longer bundle is retired; the player's files still stay
jar "$M/old-thing.jar" old-thing; echo '["old-thing.jar","yacl-2.jar","splitscreen-glue-2.jar"]' > "$M/.mcss-managed"
run
[ ! -f "$M/old-thing.jar" ] || fail "retired managed mod not removed"
[ -f "$M/mymod.jar" ] || fail "player's mod removed"
echo "setup tests passed"
