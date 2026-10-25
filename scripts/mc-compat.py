#!/usr/bin/env python3
"""Minecraft version watch: is there a newer Minecraft than the one this repo is pinned to, and are the pieces
we depend on ready for it? Used by .github/workflows/minecraft-watch.yml; works locally too.

    scripts/mc-compat.py check [--version V] [--snapshots]   writes build/compat/check.json, prints KEY=VALUE lines
                                                              (target, current, is_new, deps_ready) for GITHUB_OUTPUT
    scripts/mc-compat.py apply                                pins glue-mod/gradle.properties to check.json's target
    scripts/mc-compat.py report [--build R] [--smoke R] [--run-url U]
                                                              writes build/compat/report.md for the issue

The pins: minecraft_version, loader_version (latest stable Fabric Loader), fabric_api_version and
controlify_version (newest Modrinth builds for the version). YACL and Sodium are fetched by scripts/fetch-mods.sh.
"""
import argparse
import json
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROPS = ROOT / "glue-mod" / "gradle.properties"
OUT = ROOT / "build" / "compat"
UA = {"User-Agent": "minecraft-splitscreen-compat (github.com/dwatson251/minecraft-splitscreen)"}
# what every view loads; all must have a build for the version (see scripts/fetch-mods.sh)
MODS = {"fabric-api": "Fabric API", "controlify": "Controlify", "yacl": "YetAnotherConfigLib", "sodium": "Sodium"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return json.load(r)


def props():
    return dict(re.findall(r"^(\w+)=(.*)$", PROPS.read_text(), re.M))


def modrinth(slug, version):
    q = f"game_versions=%5B%22{version}%22%5D&loaders=%5B%22fabric%22%5D"
    versions = get(f"https://api.modrinth.com/v2/project/{slug}/version?{q}")
    return versions[0]["version_number"] if versions else None


def check(args):
    manifest = get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
    current = props()["minecraft_version"]
    target = args.version or manifest["latest"]["snapshot" if args.snapshots else "release"]
    known = {v["id"]: v for v in manifest["versions"]}
    if target not in known:
        sys.exit(f"Mojang doesn't list Minecraft {target}")
    loaders = [v["version"] for v in get("https://meta.fabricmc.net/v2/versions/loader") if v.get("stable")]
    fabric_games = {v["version"] for v in get("https://meta.fabricmc.net/v2/versions/game")}
    deps = {slug: modrinth(slug, target) for slug in MODS}
    result = {
        "current": current,
        "target": target,
        "type": known[target]["type"],
        "released": known[target]["releaseTime"],
        "is_new": target != current,
        "fabric_supports_game": target in fabric_games,
        "loader": loaders[0] if loaders else None,
        "mods": deps,
    }
    result["deps_ready"] = result["fabric_supports_game"] and all(deps.values())
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "check.json").write_text(json.dumps(result, indent=2))
    for k in ("target", "current", "is_new", "deps_ready"):
        v = result[k]
        print(f"{k}={str(v).lower() if isinstance(v, bool) else v}")


def apply(_):
    c = json.loads((OUT / "check.json").read_text())
    text = PROPS.read_text()
    pins = {"minecraft_version": c["target"], "loader_version": c["loader"],
            "fabric_api_version": c["mods"]["fabric-api"], "controlify_version": c["mods"]["controlify"]}
    for key, value in pins.items():
        if value:
            text = re.sub(rf"^{key}=.*$", f"{key}={value}", text, flags=re.M)
    PROPS.write_text(text)
    print("\n".join(f"{k}={v}" for k, v in pins.items()))


def report(args):
    c = json.loads((OUT / "check.json").read_text())
    tick = lambda ok: "✅" if ok else "❌"
    lines = [f"Minecraft **{c['target']}** ({c['type']}, released {c['released'][:10]}) is out; this repo is on "
             f"**{c['current']}**.", "", "### Dependencies", "", "| | Build for this version |", "| --- | --- |",
             f"| Fabric (game supported) | {tick(c['fabric_supports_game'])} |",
             f"| Fabric Loader | {c['loader'] or '❌ none'} |"]
    lines += [f"| {name} | {c['mods'][slug] or '❌ not yet'} |" for slug, name in MODS.items()]
    lines += ["", "### Results", "", "| Step | Result |", "| --- | --- |"]
    for label, path in (("Compile the glue mod", args.build), ("In-game smoke test", args.smoke)):
        r = Path(path).read_text().strip() if path and Path(path).exists() else "skipped"
        lines.append(f"| {label} | {r.splitlines()[0]} |")
    for label, path in (("Compile output", args.build), ("Smoke test output", args.smoke)):
        if path and Path(path).exists() and len(Path(path).read_text().splitlines()) > 1:
            body = "\n".join(Path(path).read_text().splitlines()[1:])
            lines += ["", f"<details><summary>{label}</summary>", "", "```", body[-6000:], "```", "", "</details>"]
    if args.run_url:
        lines += ["", f"Logs and screenshots: {args.run_url}"]
    if not c["deps_ready"]:
        lines += ["", "Some dependencies have no build for this version yet; this check runs again every day."]
    (OUT / "report.md").write_text("\n".join(lines) + "\n")
    print((OUT / "report.md").read_text())


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("check")
    c.add_argument("--version")
    c.add_argument("--snapshots", action="store_true")
    sub.add_parser("apply")
    r = sub.add_parser("report")
    r.add_argument("--build")
    r.add_argument("--smoke")
    r.add_argument("--run-url")
    a = p.parse_args()
    {"check": check, "apply": apply, "report": report}[a.cmd](a)
