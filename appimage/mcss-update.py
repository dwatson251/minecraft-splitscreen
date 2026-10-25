#!/usr/bin/env python3
"""Self-update for the AppImage (AppRun runs this before the game starts). Standard library only.

    mcss-update.py check <current version>
        Newest GitHub release newer than <current> (semver)? Prints "<tag> <asset url> <size> <sha256 or ->",
        else nothing; exits 0 when an update is available, 1 otherwise (including any network or parse failure,
        so a launch is never held up by it).
    mcss-update.py download <url> <size> <sha256 or -> <dest> [progress file]
        Downloads to <dest>.part, writing a percentage to the progress file as it goes, checks the size, the
        SHA-256 (when GitHub gave one) and that it's an ELF file, then moves it over <dest> in one step. Exits
        non-zero and leaves <dest> alone on any failure.

MCSS_UPDATE_LABEL is written before the percentage ("Updating to v0.8.1... 42%"); MCSS_UPDATE_API overrides the
release URL (tests).
"""
import hashlib
import json
import os
import re
import sys
import urllib.request

API = os.environ.get("MCSS_UPDATE_API",
                     "https://api.github.com/repos/dwatson251/minecraft-splitscreen/releases/latest")
LABEL = os.environ.get("MCSS_UPDATE_LABEL", "")  # progress text before the percentage
TIMEOUT = 4
SEMVER = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$")


def parse(version):
    """(major, minor, patch, is_release, pre) or None. A pre-release ranks below its release."""
    m = SEMVER.match(version.strip())
    if not m:
        return None
    pre = m.group(4)
    return int(m.group(1)), int(m.group(2)), int(m.group(3)), pre is None, pre or ""


def newer(candidate, current):
    a, b = parse(candidate), parse(current)
    return a is not None and b is not None and a > b


def request(url):
    return urllib.request.Request(url, headers={"User-Agent": "minecraft-splitscreen-updater",
                                                "Accept": "application/vnd.github+json"})


def check(current):
    try:
        with urllib.request.urlopen(request(API), timeout=TIMEOUT) as r:
            release = json.load(r)
        tag = release["tag_name"]
    except Exception:  # offline, rate-limited, unexpected reply: just start the game
        return 1
    if release.get("draft") or release.get("prerelease") or not newer(tag, current):
        return 1
    want = f"MinecraftSplitscreen-{tag}-x86_64.AppImage"
    asset = next((a for a in release.get("assets", []) if a.get("name") == want), None)
    if not asset:
        return 1
    digest = asset.get("digest") or ""
    sha = digest[len("sha256:"):] if digest.startswith("sha256:") else "-"
    print(tag, asset["browser_download_url"], asset["size"], sha)
    return 0


def download(url, size, sha, dest, progress=None):
    size = int(size)
    part = dest + ".part"
    h = hashlib.sha256()
    got = 0
    shown = -1
    try:
        with urllib.request.urlopen(request(url), timeout=30) as r, open(part, "wb") as f:
            while True:
                chunk = r.read(1 << 16)
                if not chunk:
                    break
                f.write(chunk)
                h.update(chunk)
                got += len(chunk)
                pct = got * 100 // size if size else 0
                if progress and pct != shown:
                    shown = pct
                    with open(progress + ".tmp", "w") as p:
                        p.write(f"{LABEL} {pct}%" if LABEL else f"{pct}%")
                    os.replace(progress + ".tmp", progress)
        with open(part, "rb") as f:
            magic = f.read(4)
        if got != size:
            raise ValueError(f"size {got}, expected {size}")
        if sha != "-" and h.hexdigest() != sha.lower():
            raise ValueError("SHA-256 doesn't match")
        if magic != b"\x7fELF":
            raise ValueError("not an AppImage (ELF) file")
        os.chmod(part, 0o755)
        os.replace(part, dest)
    except Exception as e:
        print(f"update failed: {e}", file=sys.stderr)
        try:
            os.remove(part)
        except OSError:
            pass
        return 1
    return 0


def main(argv):
    if len(argv) == 3 and argv[1] == "check":
        return check(argv[2])
    if len(argv) in (6, 7) and argv[1] == "download":
        return download(*argv[2:])
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
