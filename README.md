# Minecraft Splitscreen for Steam Deck

2–4 player splitscreen Minecraft Java 26.3 as **one AppImage window**, so Steam's Game Mode (gamescope) shows
every player at once instead of focusing a single game window. Each player gets their own controller.

How it works: the AppImage starts a nested KWin (already on SteamOS) as one window. Inside it, one Minecraft
client per player is tiled: top/bottom or side by side (your choice), four in quadrants. P1 uses your
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
accounts, and Prism downloads Minecraft and Java 25. Every launch refreshes the mods in those instances, so a new
AppImage takes effect by itself (`--setup` only does that step and exits).

## Players and controllers
P1 starts alone on **Press START to play**:
- The first controller to press START is Player 1. The game doesn't go on to the menus until it knows Player 1's
  controller.
- Every other controller that presses START becomes the next player and gets its own view (about 30 s to load).
  That works on the start screen and at any time later in game, up to 4 players.
- A new view shows **Waiting for Player 1's world…** (guests never see the menus) and "Press any button on your
  controller". After a couple of presses it finds its controller. It joins Player 1's world as soon as it's
  open, and **Cancel** closes that view.
- Player 1 presses Continue, then opens or creates any world. The others join it as soon as it's open, and
  rejoin by themselves if P1 switches worlds.
- **Players 2–4 leave** by leaving the world (Disconnect in their pause menu): their view closes straight away.
  When Player 1 closes or switches worlds, the others stay and rejoin the next one. The players after them move up a place, and a new join
  goes on the end.
- **Player 1 quitting ends the whole session** and closes the AppImage.
- If a controller disconnects, its player keeps their place. START on any free controller reconnects them.
- The **Splitscreen** button (top left of Player 1's title screen and pause menu) opens Splitscreen settings:
  - **Layout:** Horizontal (stacked: 2 players top/bottom; with 3, Player 1 across the top) or Vertical (side by
    side: 2 players left/right; with 3, Player 1 down the left). 4 players are always quadrants. It switches
    immediately and is remembered.
  - **Players:** who is playing with which controller.

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
