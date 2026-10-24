#!/bin/bash
# Downloads the third-party mods every splitscreen client runs (Fabric 26.3) from Modrinth into dist/mods/,
# checking each file's SHA-1, and copies the Controlify jar the glue compiles against into .tools/.
#   scripts/fetch-mods.sh            latest 26.3 Fabric release of each
# Override the game version with MC=26.3.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
MC=${MC:-26.3}
OUT=$ROOT/dist/mods
mkdir -p "$OUT" "$ROOT/.tools"
rm -f "$OUT"/*.jar
# fabric-api: required by the glue; controlify + yacl: controller input; sodium: frame rate with 4 clients
for slug in fabric-api controlify yacl sodium; do
	read -r url sha file < <(curl -fsS "https://api.modrinth.com/v2/project/$slug/version?game_versions=%5B%22$MC%22%5D&loaders=%5B%22fabric%22%5D" |
		python3 -c 'import json,sys
v=json.load(sys.stdin)
if not v: sys.exit("no %s build for this version" % sys.argv[1])
f=[x for x in v[0]["files"] if x["primary"]][0]
print(f["url"], f["hashes"]["sha1"], f["filename"])' "$slug")
	curl -fsSL -o "$OUT/$file" "$url"
	echo "$sha  $OUT/$file" | sha1sum -c --quiet
	echo "$slug: $file"
	if [ "$slug" = controlify ]; then
		cp "$OUT/$file" "$ROOT/.tools/controlify-$(sed -n 's/^controlify_version=//p' "$ROOT/glue-mod/gradle.properties").jar"
	fi
done
