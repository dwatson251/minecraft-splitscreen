# Minecraft Splitscreen for Steam Deck

2–4 player splitscreen Minecraft Java 26.3 as **one AppImage window**, so Steam's Game Mode (gamescope) shows
every player at once instead of focusing a single game window. Each player gets their own controller.

How it works: the AppImage starts a nested KWin (already on SteamOS) as one window. Inside it, one Minecraft
client per player is tiled: two players top/bottom, three with P1 across the top, four in quadrants. P1 uses your
Microsoft account. Whatever world P1 opens is shared on LAN, and P2–P4, offline guests `Player2`–`Player4`, join
it automatically. A small Fabric mod (`glue-mod/`) does the joining, the window titles, the player-count screen
and the controller assignment. Controlify handles the controller input itself.

## Requirements
- Steam Deck (SteamOS 3.8+), or any Linux desktop with KWin 6.
- [Prism Launcher](https://prismlauncher.org) from Flathub (Discover → Prism Launcher), signed in with a Microsoft
  account that owns Minecraft. Close Prism before starting the AppImage.
- About 2.5 GB RAM per player. Four players fit in the Deck's 16 GB, with lower render distances for guests.

## Install
1. Download `MinecraftSplitscreen-*.AppImage` from Releases into `~/Applications/` and make it executable
   (Dolphin: Properties → Permissions → "Is executable").
2. Open Prism Launcher once, add your Microsoft account (Accounts → Manage Accounts → Add Microsoft), then close
   it.
3. In Desktop Mode, Steam → Add a Game → Add a Non-Steam Game → Browse → pick the AppImage.
4. Switch to Game Mode and launch it.

The first launch creates Prism instances `P1`–`P4` (Minecraft 26.3 + Fabric + the mods) and the offline guest
accounts, and Prism downloads Minecraft and Java 25. `--setup` repeats that step, for example after an update.

## Players and controllers
P1 starts alone and asks **How many players?** (1–4). Pick with the Deck's controls; the other players' views
start one after another. The **Players: N** button at the top left of P1's title screen changes the count later
(lowering it closes the extra views). Then open or create any world as P1; the others join as soon as it's open,
and rejoin by themselves if P1 switches worlds.

Controllers are detected automatically:
- The Deck's own controls belong to P1, and other pads go to P2, P3, P4 in the order they're found.
- A player without a controller sees "Connect a controller to play". A pad plugged in later goes to the
  lowest-numbered player still waiting.
- If a pad disconnects, its player takes the next free one.
- Pads keep working while another player's view has focus.

Under Steam the clients read the real controllers, not Steam Input's virtual pads. Steam's virtual pads all look
the same to the game, so they can't be told apart.

## Logs
`~/.local/state/mc-splitscreen/` holds `apprun.log`, `kwin.log` and `prism.log`. Each client's game log is in
Prism's `instances/P<n>/minecraft/logs/latest.log`.

## Building
```
scripts/fetch-mods.sh        # Fabric API, Controlify, YACL, Sodium from Modrinth (SHA-1 checked) -> dist/mods
scripts/build-appimage.sh    # glue mod + AppImage -> dist/MinecraftSplitscreen-x86_64.AppImage
```
Needs a JDK 25 (`JAVA_HOME`, or unpacked in `.tools/`), python3 with Pillow, and curl. CI
(`.github/workflows/release.yml`) builds on every push. Pushing a `v*` tag publishes the AppImage as a GitHub
release.

The AppImage contains no Minecraft or Prism Launcher files. The game is downloaded by your own Prism Launcher with
your own account.
