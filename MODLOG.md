# MODLOG: Minecraft splitscreen on Steam Deck

## 2026-10-24
- Recon (Deck, SSH deck@steamdeck.local): SteamOS 3.8.28 (build 20260922.1), gamescope 3.16.23.6, KWin 6.4.3,
  Xwayland present, no sway/cage/weston, 8 threads, 14 GiB usable RAM. Prism Launcher flatpak 11.1.1, no accounts,
  one empty 26.2 instance, no saves -> nothing to back up.
- Steam Input exposes the Deck pad as "Microsoft X-Box 360 pad 0" (virtual).
- Modrinth: Controlify 3.5.3+mc26.3 (needs YACL + Fabric API), Sodium mc26.3-0.9.3-beta.1. Controlify has
  "Out of Focus Input" and per-instance controller selection + profile lock between instances.
- Route change vs plan: SteamOS ships KWin 6.4.3, which nests (`--x11-display`, `--wayland-display`, `--virtual`,
  `--width/--height`). A KWin script (appimage/kwin/mcss-tile) tiles windows; verified in nested virtual KWin on the
  Deck: 2 -> top/bottom 1280x400, 3 -> P1 full-width top + two 640x400 below. No sway bundling needed.
- KWin script console.log only reaches stderr with QT_FORCE_STDERR_LOGGING=1 (otherwise journald).
- Prism 11.1.1 drops per-instance `Env` JSON it can't parse (saved back as `{}`), but always exports
  `INST_ID` (= instance folder) to the game. The glue reads its player number from INST_ID=P<n>.
- SteamOS has logind KillUserProcesses=True: anything started over SSH dies at logout (nohup/setsid too).
  Test runs go through `systemd-run --user --unit=mcss-test`.
- Inside flatpak, the nested socket shows up as WAYLAND_DISPLAY=wayland-0 (flatpak remaps it); windows still land
  in the nested KWin.
- First 2-player run (Desktop Mode, forced 2): P1 created + published world "Splitscreen" on 25599, Player2
  (offline) joined in 6 s, tiler 1280x400 top/bottom. Outside Steam, SDL saw the raw pads (DualSense SDL-1, Steam
  Deck SDL-2). User confirmed that each pad moves only its own player, including while unfocused.
- Pad order: Steam Deck first, then discovery order.
- Packaged AppImage, 2 players: Deck pad -> P1, DualSense -> P2, existing world reopened, P2 joined. About 7.3 GB RAM
  in use with 2 clients, 8.1 GB with 3.
- User asked for automatic controller detection and a player count from launch options (default 2). Claims are
  files in <prism>/mcss-pads/P<n> (timestamp + uid, stale after 5 s); a lower player number wins a conflict;
  players without a pad get a toast.
- Bug: a client with no claim kept the pad Controlify picked at startup (DualSense drove both P2 and P3). Fix:
  setCurrentController(null) and clear profile.controllerUid. My fix then crashed P1 by calling getActiveProfile
  before Controlify discovered controllers ("No active Controlify profile"). Fix: skip until
  getControllerManager() is present, and wrap the tick in try/catch. User confirmed: DualSense -> P2 only,
  P3 idle with the toast. Replug -> P2 again.
- GitHub: dwatson251/minecraft-splitscreen. The HTTPS gh token lacks the `workflow` scope, so the remote uses SSH.

## 2026-10-24, playtest feedback (Game Mode, Deck + PS4 pad)
- Bug: the Deck drove both players. Under Steam the game only saw Steam Input's virtual pads, both named "Steam
  Controller" with the same base uid (`461adb…`, second one `-1`), so the Deck-first rule never applied and Steam
  Input decided the routing. Fix: AppRun gives the clients
  SDL_GAMECONTROLLER_IGNORE_DEVICES=0x28DE/0x11FF and unsets SteamVirtualGamepadInfo and
  SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT, so they read the real devices as outside Steam. Pad logs now include the
  GUID and HID VID/PID. NOT yet verified in Game Mode.
- Feature: P1 boots to the title screen and opens any world, which is auto-published (the forced "Splitscreen"
  world is gone). Guests still auto-join (the user chose that over a join button).
- Feature: P1 picks the player count on a Controlify-navigable PlayerCountScreen and writes
  <prism>/mcss-control/players. AppRun starts or stops P2-P4 to match (stops by exact java PID). The launch-option
  count was removed at the user's request.
- Prism failed with "Could not load the Qt platform plugin xcb" when started from a Game Mode session
  (QT_QPA_PLATFORM=xcb, while X11 is hidden). Prism now gets QT_QPA_PLATFORM=wayland.
- In Desktop Mode, focus snapped to "1": Steam's desktop config turns Deck input into keyboard/mouse too, and
  Controlify's mode switching reset focus to the first widget. Fix: Controlify mixedInput=true (its tooltip
  recommends that with Steam Input), and the preferred button is added first. Then a heavy press skipped two
  options (the same press arrived through both paths). Fix: a 200 ms debounce in PlayerCountScreen.changeFocus.
- User confirmed in Desktop Mode: one step per press, picking 2 started P2, P1 opened "New World", it was
  published and Player2 auto-joined in 6 s.
- Screenshots of the Deck desktop over SSH: `spectacle -b -n -f -o /tmp/x.png` with WAYLAND_DISPLAY=wayland-0,
  XDG_RUNTIME_DIR and the session bus set.
