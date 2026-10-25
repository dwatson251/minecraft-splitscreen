#!/bin/bash
# In-game smoke test: starts the real client with the glue mod and the mods from dist/mods, lets DevShots walk
# Player 1 through its screens (profiles, create profile, settings, a guest's waiting screen, the quit prompt, a
# world, the pause menu), and checks that every screen appeared and nothing failed (mixins, exceptions, crashes).
# Uses a virtual display (xvfb-run) when there is none. Writes build/smoke/result.txt: first line ✅/❌ summary,
# then details; screenshots in build/smoke/shots, the game log in build/smoke/client.log.
#   scripts/smoke-test.sh          (after scripts/fetch-mods.sh; JAVA_HOME = a JDK 25 or one in .tools/)
set -uo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=$ROOT/build/smoke
DEV=$ROOT/.tools/dev
RUN=$DEV/instances/P1/minecraft
SHOTS=(profiles create-profile settings waiting quit-confirm in-game pause-menu)
NEEDED=("[mcss] P1 plays as profile Alice" "[mcss] Quit Game now asks first" "[mcss] published '"
	"[mcss] dev screenshots done")
FAILURES="Mixin apply failed|Unreported exception|Crash report|controller handling failed|Exception in thread \"Render thread\""

rm -rf "$OUT" && mkdir -p "$OUT/shots" "$RUN/mods" "$DEV/mcss-profiles" "$DEV/mcss-control"
rm -f "$RUN/mods/"*.jar "$DEV/mcss-profiles/"*.json "$DEV/mcss-control/"*
for j in "$ROOT"/dist/mods/*.jar; do
	case "$(basename "$j")" in fabric-api-*) ;; *) cp "$j" "$RUN/mods/" ;; esac  # Fabric API is a dev dependency
done
cp "$ROOT"/test/smoke/profiles/*.json "$DEV/mcss-profiles/"
cp "$ROOT/test/smoke/options.txt" "$RUN/options.txt"
# keep Bob "in use by another view", as in the README picture
( while sleep 2; do echo P2 > "$DEV/mcss-control/profile-bob"; done ) &
KEEPER=$!
trap 'kill $KEEPER 2>/dev/null' EXIT

JDK=$(ls -d "$ROOT"/.tools/jdk-25* 2>/dev/null | head -1 || true)
export JAVA_HOME=${JDK:-${JAVA_HOME:-}}
export MCSS_PLAYER=1 MCSS_DEV_SHOTS=$OUT/shots
RUNNER=()
if [ -z "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ]; then
	# a virtual display with GLX (Mesa's software OpenGL), so the game can open its window without a GPU
	RUNNER=(xvfb-run -a -s "-screen 0 1280x720x24 +extension GLX +render -noreset")
	export LIBGL_ALWAYS_SOFTWARE=1
fi
# its own process group, so a stuck run can be stopped as a whole (by that exact group id)
setsid "${RUNNER[@]}" timeout 1200 "$ROOT/glue-mod/gradlew" --console=plain -p "$ROOT/glue-mod" runClient \
	> "$OUT/client.log" 2>&1 < /dev/null &
GAME=$!
# no point waiting out the timeout when the game can't open a window at all
while kill -0 "$GAME" 2>/dev/null; do
	if grep -q "Failed to create backend Vulkan" "$OUT/client.log" && ! grep -q "Using graphics backend" "$OUT/client.log"; then
		echo "no graphics backend, stopping the client" >> "$OUT/client.log"
		kill -- "-$GAME" 2>/dev/null
		break
	fi
	sleep 5
done
wait "$GAME"
CODE=$?

problems=()
[ "$CODE" -ne 0 ] && problems+=("the client exited with code $CODE")
for s in "${SHOTS[@]}"; do [ -s "$OUT/shots/$s.png" ] || problems+=("no $s screenshot"); done
for n in "${NEEDED[@]}"; do grep -qF -- "$n" "$OUT/client.log" || problems+=("never logged: $n"); done
grep -E -- "$FAILURES" "$OUT/client.log" > "$OUT/failures.txt" && problems+=("errors in the log")

{
	if [ ${#problems[@]} -eq 0 ]; then
		echo "✅ passed: ${#SHOTS[@]} screens, world created and shared"
	else
		echo "❌ failed: $(IFS=';'; echo "${problems[*]}" | sed 's/;/; /g')"
		echo "--- errors"
		head -40 "$OUT/failures.txt"
		echo "--- end of the game log"
		grep -v -E "Realms|player/attributes" "$OUT/client.log" | tail -40
	fi
} > "$OUT/result.txt"
cat "$OUT/result.txt"
[ ${#problems[@]} -eq 0 ]
