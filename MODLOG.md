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
