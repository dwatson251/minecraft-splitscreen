#!/bin/bash
# First-run setup (and re-run after an update): creates Prism Launcher instances P1..P4 for Minecraft 26.3 + Fabric,
# installs the mods, and adds offline accounts Player2..Player4. Prism must not be running (accounts.json is
# rewritten). P1 plays with the Microsoft account already signed in to Prism.
#   mcss-setup.sh <mods dir>
# MCSS_PRISM_DIR  Prism's data folder (default: the Flathub build's)
# MCSS_HEAP_MB    max heap per client (default 2048)
set -euo pipefail
MODS=${1:?usage: mcss-setup.sh <mods dir>}
PRISM=${MCSS_PRISM_DIR:-$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher}
HEAP=${MCSS_HEAP_MB:-2048}
MC=26.3
LOADER=0.20.1
PORT=25599

[ -d "$PRISM" ] || { echo "Prism Launcher data folder not found: $PRISM (open Prism Launcher once)"; exit 1; }
mkdir -p "$PRISM/instances"

python3 - "$PRISM" "$MC" "$LOADER" "$PORT" "$HEAP" <<'PY'
import json, os, sys, time, uuid, hashlib
prism, mc, loader, port, heap = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5])

# offline guests; vanilla derives offline UUIDs from "OfflinePlayer:<name>", use the same so inventories stick
path = os.path.join(prism, "accounts.json")
acc = json.load(open(path)) if os.path.exists(path) else {"accounts": [], "formatVersion": 3}
if not any(a.get("type") == "MSA" for a in acc["accounts"]):
    sys.exit("No Microsoft account in Prism Launcher yet: open Prism, Accounts -> Add Microsoft, then re-run.")
names = {a.get("profile", {}).get("name") for a in acc["accounts"]}
for n in range(2, 5):
    name = "Player%d" % n
    if name in names:
        continue
    md5 = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
    md5[6] = md5[6] & 0x0f | 0x30; md5[8] = md5[8] & 0x3f | 0x80
    acc["accounts"].append({
        "type": "Offline",
        "ygg": {"extra": {"clientToken": uuid.uuid4().hex, "userName": name}, "iat": int(time.time()), "token": "0"},
        "profile": {"id": bytes(md5).hex(), "name": name, "skin": {"id": "", "url": "", "variant": ""}, "capes": []},
    })
json.dump(acc, open(path + ".tmp", "w"), indent=4)
os.replace(path + ".tmp", path)

for n in range(1, 5):
    inst = os.path.join(prism, "instances", "P%d" % n)
    os.makedirs(os.path.join(inst, "minecraft", "mods"), exist_ok=True)
    json.dump({"formatVersion": 1, "components": [
        {"uid": "net.minecraft", "version": mc, "important": True},
        {"uid": "net.fabricmc.intermediary", "version": mc, "dependencyOnly": True},
        {"uid": "net.fabricmc.fabric-loader", "version": loader},
    ]}, open(os.path.join(inst, "mmc-pack.json"), "w"), indent=4)
    cfg = {
        "InstanceType": "OneSix", "name": "Splitscreen P%d" % n, "iconKey": "default",
        # the glue mod reads its player number from Prism's INST_ID (= folder name P<n>); Prism 11's per-instance
        # Env setting isn't used because it silently drops values it can't parse
        "OverrideEnv": "false", "Env": "{}",
        "OverrideMemory": "true", "MaxMemAlloc": str(heap), "MinMemAlloc": "512",
        "OverrideConsole": "true", "ShowConsole": "false", "AutoCloseConsole": "true", "ShowConsoleOnError": "false",
        "OverrideWindow": "true", "LaunchMaximized": "false", "MinecraftWinWidth": "640", "MinecraftWinHeight": "400",
        "AutomaticJava": "true",
    }
    p = os.path.join(inst, "instance.cfg")
    old = {}
    if os.path.exists(p):  # keep Prism's own bookkeeping (play time, Java path it picked)
        for line in open(p):
            k, _, v = line.rstrip("\n").partition("=")
            old[k] = v
    old.update(cfg)
    # Prism reads this with QSettings: a value with commas is a list unless quoted, so quote JSON values
    def ini(v):
        if not any(c in v for c in ',;="\\'):
            return v
        if v.startswith('"') and v.endswith('"'):
            return v  # already quoted by Prism
        return '"' + v.replace('\\', '\\\\').replace('"', '\\"') + '"'
    open(p, "w").write("[General]\n" + "".join("%s=%s\n" % (k, ini(v)) for k, v in old.items() if k != "[General]"))
    opts = os.path.join(inst, "minecraft", "options.txt")
    if not os.path.exists(opts):  # first run only; players' later changes win
        lines = ["pauseOnLostFocus:false", "onboardAccessibility:false", "skipMultiplayerWarning:true",
                 "joinedFirstServer:true", "renderDistance:%d" % (10 if n == 1 else 8), "simulationDistance:8",
                 "fullscreen:false"]
        if n > 1:
            lines.append("soundCategory_master:0.0")
        open(opts, "w").write("\n".join(lines) + "\n")
    print("instance P%d ready" % n)
PY

for n in 1 2 3 4; do
	dst="$PRISM/instances/P$n/minecraft/mods"
	rm -f "$dst"/*.jar
	cp "$MODS"/*.jar "$dst/"
done
echo "mods installed: $(ls "$MODS" | tr '\n' ' ')"
