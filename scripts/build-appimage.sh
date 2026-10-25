#!/bin/bash
# Builds dist/MinecraftSplitscreen-x86_64.AppImage: AppRun + first-run setup + KWin tiler + the glue mod and the
# Modrinth mods. No Minecraft or Prism Launcher files go in; those come from the user's own install.
#   scripts/build-appimage.sh
# Needs a JDK 25 (JAVA_HOME, or one unpacked in .tools/), python3 with Pillow, curl.
# OUT=<file> names the AppImage (default dist/MinecraftSplitscreen-x86_64.AppImage).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
JDK=$(ls -d "$ROOT"/.tools/jdk-25* 2>/dev/null | head -1 || true)
JDK=${JDK:-${JAVA_HOME:-}}
[ -n "$JDK" ] || { echo "set JAVA_HOME to a JDK 25, or unpack one in .tools/ (see README)"; exit 1; }
OUT=${OUT:-$ROOT/dist/MinecraftSplitscreen-x86_64.AppImage}
[ -n "$(ls "$ROOT"/dist/mods/*.jar 2>/dev/null)" ] || "$ROOT/scripts/fetch-mods.sh"
(cd "$ROOT/glue-mod" && JAVA_HOME=$JDK ./gradlew --console=plain -q build)

APP=$ROOT/dist/AppDir
rm -rf "$APP" && mkdir -p "$APP/mods"
cp -r "$ROOT/appimage/AppRun" "$ROOT/appimage/mcss-setup.sh" "$ROOT/appimage/kwin" "$ROOT/appimage/loading.qml" \
	"$ROOT/appimage/mcss-update.py" "$APP/"
# the version the self-update compares with GitHub's latest release (the release workflow checks it matches the tag)
sed -n 's/^version=//p' "$ROOT/glue-mod/gradle.properties" > "$APP/VERSION"
cp "$ROOT"/dist/mods/*.jar "$APP/mods/"
# only this version's jar: build/libs keeps old ones, and Fabric refuses to load a mod twice
cp "$ROOT/glue-mod/build/libs/splitscreen-glue-$(sed -n 's/^version=//p' "$ROOT/glue-mod/gradle.properties").jar" "$APP/mods/"
cat > "$APP/minecraft-splitscreen.desktop" <<DESK
[Desktop Entry]
Type=Application
Name=Minecraft Splitscreen
Comment=2-4 player splitscreen Minecraft in one window, one controller per player
Exec=AppRun
Icon=minecraft-splitscreen
Categories=Game;
DESK
python3 - "$APP/minecraft-splitscreen.png" <<'PY'
import sys
from PIL import Image, ImageDraw
im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
d = ImageDraw.Draw(im)
d.rounded_rectangle((8, 8, 248, 248), 36, fill=(40, 44, 52))
for i, c in enumerate([(94, 160, 72), (122, 92, 60), (86, 140, 210), (220, 180, 70)]):
    x, y = 28 + (i % 2) * 104, 28 + (i // 2) * 104
    d.rounded_rectangle((x, y, x + 96, y + 96), 14, fill=c)
im.save(sys.argv[1])
PY
ln -sf minecraft-splitscreen.png "$APP/.DirIcon"

TOOL=$ROOT/.tools/appimagetool-x86_64.AppImage
if [ ! -x "$TOOL" ]; then
	curl -fsSL -o "$TOOL" https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage
	chmod +x "$TOOL"
fi
ARCH=x86_64 "$TOOL" --appimage-extract-and-run --no-appstream "$APP" "$OUT" >/dev/null
ls -la "$OUT"
