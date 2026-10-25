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

## 2026-10-24, "Press START to play" (v0.3.0)
- Bug: two PS4 pads over Bluetooth misbehaved. The real cause: the same physical pad has a different Controlify
  uid and SDL GUID in each client (one HIDAPI GUID `...0000 6800`, one evdev GUID `...1181 0000`, depending on how
  SDL found it), so uid-keyed claim files couldn't stop two clients taking one pad. Pad identity doesn't carry
  between processes.
- Redesign (user's idea): P1 runs a lobby (Lobby + LobbyScreen) and watches every pad (PadEdges; Controlify
  polls inactive pads too). START on a free pad joins (a pad that disconnected first, then a free client P2-P4)
  and is written to mcss-control/slots.
  - P1 publishes each joined player's button presses with timestamps in mcss-control/events. Each guest
    (GuestPad) scores its local pads by matching presses (+-1 within 250 ms), takes the one leading by 2 and
    drops it at -3.
  - Guests write alive-P<n> and left-P<n>. AppRun launches per join generation and never relaunches a guest that
    quit.
- User's rules: P1 is permanent and quitting it ends everything (moving the host over would mean moving the
  world and losing the MSA account). Guests move up when one leaves: the shown number is the place in the slots
  list, put in the window title, so the tiler re-sorts. The lobby can't be left until Player 1's pad is known.
- Bug: under Steam, quitting P1 left the window up. AppRun killed dbus-run-session, which doesn't forward
  signals, so the nested kwin_wayland lived on. `systemctl --user stop` (my test harness) killed the whole cgroup
  and hid it. Fix: track kwin_wayland's own PID (`pgrep -P $SESSION`). The user confirmed the window closes.
- Bug: one START on the Deck joined two players ("Steam Deck" + "Steam Controller"). Steam sets
  SDL_GAMECONTROLLER_ALLOW_STEAM_VIRTUAL_GAMEPAD=1, which lets its virtual pads past the ignore list. Fix: set it
  to 0 for the clients, plus a 28de:11ff filter in PadEdges. Steam's own SDL ignore list (792 entries) includes
  the Deck (28de:1205) and PS4 (054c:09cc), which is why the game only saw virtual pads before.
- To launch the Steam shortcut over SSH in Desktop Mode: read the appid from userdata/*/config/shortcuts.vdf,
  then `steam steam://rungameid/$(( (appid << 32) | 0x02000000 ))`.
- User confirmed it working: launched from Steam, Deck + DualSense.

## 2026-10-24, layout setting
- User asked for horizontal vs vertical, without blocking launch. Added a "Splitscreen" button (top left of
  Player 1's title screen and pause menu) that opens SettingsScreen: Layout and Players (the lobby view). Saved
  in the P1 instance's config/splitscreen.properties.
- The layout reaches the KWin tiler through Player 1's window caption ("P1v ..." = vertical). That needs no new
  IPC, and captionChanged already re-tiles. Layout maths checked in node: V2 = 640x800 x2, V3 = P1 640x800 left
  + 2x 640x400 right.
- Packaging bug: build/libs kept splitscreen-glue-0.1.0.jar after the version bump, and the AppImage shipped both
  (Fabric refuses duplicate mods). The build script now copies only the current version. CI builds were clean.
- Guests now shut down when their player leaves the world, instead of landing on the title screen. To tell that
  apart from the host closing or switching worlds, the host writes mcss-control/world = "open" (SERVER_STARTED)
  and "closed" (SERVER_STOPPING, which runs before players are kicked). A guest disconnected while it still says
  "open" calls mc.stop(). CLIENT_STOPPING writes left-P<n>, so the host frees the place and the rest move up.
- Playtest (Deck + DualSense, launched from Steam): P2 matched its pad in 2 presses (score 2 vs -2), the
  vertical layout toggled live, and P2 left the world -> "left the world, shutting this client down" and the
  host logged "client P2 left (quit)".
- Guests now show WaitingScreen ("Waiting for Player 1's world...", controller status, Cancel = mc.stop())
  in place of TitleScreen and DisconnectedScreen (AFTER_INIT swap), and auto-join from there. Esc/back are
  ignored.
- Desktop Mode: the Deck's D-pad moved P2's menu focus. That was Steam's arrow-key emulation reaching the focused nested window; A/B/X/Y are pad-only, so they did nothing. The tiler now keeps keyboard focus on Player 1's window (focusPlayer1 on tile and on windowActivated).
- 2026-10-25: v0.4.0 released (layout setting, guests close on leaving, waiting screen, P1 keeps keyboard focus, mods refreshed each launch). User playtest: works well.

## 2026-10-25, profiles (v0.5.0)
- Every view starts on ProfileSelectScreen: a card row with Create first, the Microsoft account card (P1 only),
  then the saved profiles in `<prism>/mcss-profiles/*.json`. Profiles in use elsewhere are locked
  (`mcss-control/profile-<name>`, refreshed every second, stale after 10 s).
- CreateProfileScreen: a skin picker (9 built-in skins x wide/slim, plus PNGs from
  ~/Documents/Minecraft Skins, which AppRun mirrors to <prism>/mcss-skins because the flatpak can't see
  Documents) and a name typed on an on-screen keyboard. EditProfileScreen changes the skin or deletes the profile.
- Identity: UserMixin overrides Minecraft.getUser/getGameProfile with an offline profile for the name
  (UUIDUtil.createOfflinePlayerUUID). With host auth off, each world keeps that profile's playerdata. Chat keys go
  to EMPTY_KEY_MANAGER while playing as a profile.
- Skins: PlayerInfoMixin.getSkin returns PlayerSkin.insecure(ResourceTexture(tex, tex), ...). Built-in textures
  are textures/entity/player/<wide|slim>/<name>.png; custom ones are DynamicTextures at mcss:skins/<file>. The
  card and preview are a front view drawn with blit (skin UV regions, outer layer on top).
- The START lobby is no longer a boot gate: the first button on any pad is Player 1, START joins the others,
  and a "press START to join" toast appears every 45 s while P1 is alone. LobbyScreen is now just the Players
  list in Settings.
- spectacle screenshots came back pure white (4 KB) while the screen was fine: the capture itself fails on this
  boot. Ask the user what the screen shows instead.
- User confirmed: works well.
- README screenshots: taken locally under WSLg (the Deck's spectacle captures came back white). Loom's client run
  uses runDir ../.tools/dev/instances/P1/minecraft, so the shared mcss-* folders resolve like a Prism instance.
  With MCSS_PLAYER=1 MCSS_DEV_SHOTS=<dir>, DevShots walks profiles -> create -> settings -> waiting -> world
  (third person front) -> pause menu, saves each via Screenshot.takeScreenshot(gameRenderer.mainRenderTarget()),
  and quits. It also stands in for "P1 has a pad".
  - The first try caught the Mojang loading overlay, so DevShots now waits for gui.overlay() == null.
  - The screenshots exposed real label truncation: the "ABC"/"Del" keys became "AB"/"<", and the "Create
    profile" card became "New profile".

## 2026-10-25, quit prompt, Minecraft watch, RAM cap
- Bug report from another Deck: Player 1 = PS4 and a guest pressed A, which closed everything. In Desktop Mode,
  Steam's desktop layouts (controller_base/desktop_neptune.vdf, desktop_ps4.vdf) send A=Enter, B=Escape,
  Y=Space and the D-pad as arrows to the focused window (Player 1's), so a guest's A could press Player 1's Quit
  Game. Fixes:
  - Under Steam, AppRun uses kwin --x11-display even on Plasma. Steam can see an X11 window and applies the
    game's layout.
  - KeyboardHandlerMixin drops those keys in every view.
  - Quit Game asks first (QuitScreen: No selected, Yes armed after 0.7 s), and Minecraft.stop logs its caller.
  - Locally DevShots pressed Quit and got the prompt. On the Deck the user still quit with no prompt seen,
    probably via the old ConfirmScreen (Yes focused, double A), before QuitScreen existed. Awaiting a retest.
- minecraft-watch.yml: a daily check against Mojang's manifest. scripts/mc-compat.py checks Fabric meta and
  Modrinth deps, pins the versions and writes the report. Then compile + scripts/smoke-test.sh (DevShots under
  xvfb), an issue "Minecraft <v> support" (label minecraft-update), and a PR when everything passes. The smoke
  test also gates releases in release.yml.
- WSL hung during the multi-client split-screen capture (2 clients at -Xmx2G + a Gradle daemon). Local runs now
  go through scripts/capped.sh (a systemd user scope, MemoryMax=3G, swap off). The dev client and dev-split
  heaps are -Xmx1G and Gradle is -Xmx1536m. The full smoke test passes inside the 3 GB cap. 3-4 simultaneous
  clients don't fit locally; split-2p-horizontal.png is the only composite so far.
- Split-screen layout pictures: scripts/deck-split.py runs the installed AppImage on the Deck over SSH with
  MCSS_DEV_SPLIT/MCSS_DEV_PLAYERS (flatpak passes the env through to Java).
  - DevSplit makes the host list the guests in "slots", so AppRun starts them without START presses; it pauses
    Lobby/GuestPad.
  - Every view saves P<k>.png at its real tile size, and the script composes them in the tiler's slots.
  - It adds only the sample profiles the Deck lacks, backs up and restores the layout setting, deletes the
    DevSplit-* worlds, and cleans up afterwards (verified).
- Actions bumped to their Node 24 majors (checkout v7, setup-java v6, setup-python v7, upload-artifact v7,
  setup-gradle v6, action-gh-release v3); the CI run has no annotations.
- minecraft-watch run by hand against 26.4-snapshot-5 opened issue #1: Fabric API is ready; Controlify, YACL and
  Sodium aren't yet; the glue doesn't compile because RenderPipelines and DynamicTexture moved and
  Screen.extractRenderState changed signature.
- Playtest bugs (2026-10-25):
  - "Edit profile" was greyed out on a pad: it was enabled only while a card had focus, so moving down onto it
    disabled it. It now acts on the last card focused.
  - The first letter on the keyboard snapped focus to the skin arrows: the capital-first-letter switch rebuilt
    the screen. The keyboard (now OnScreenKeyboard, shared) relabels its keys in place instead.
  - No keyboard for the world name: A on any vanilla text box now opens TypeScreen (our keyboard plus Space)
    through a mixin on Controlify's ScreenProcessor.tryOpenKeyboard, instead of Controlify's overlay. Chat and
    our own screens are left alone. Steam's keyboard can't help, because its Enter, Space and arrows are dropped.
  - The missing "Edit profile" option was really a missing rename: Edit profile now has the name field (A opens the
    keyboard). Profiles.rename moves players/{data,stats,advancements}/<offline uuid> in every P1 world to the
    new name's UUID (a leftover file there is kept aside as *.before-rename-<ms>). It's refused while a world is
    open or another view uses the profile; renaming your own active profile moves its lock.
  - Pad shortcuts on the keyboard: X = backspace, Y = space (no space in profile names).
  - TypeScreen's Done also fills a rebuilt box at the same position, because screens without their own
    repositionElements (e.g. Direct Connect) rebuild their widgets when shown again.
  - Guests: the waiting screen's Cancel (closed the view) is now Back to profile selection (Profiles.deactivate
    frees the lock; auto-join only runs on WaitingScreen). The guest's profile screen gets Leave instead of a
    greyed Back, so a guest can still close its view.
- Keyboards, round two:
  - Controlify's own keyboard ("Press A to open keyboard") never typed: Minecraft 26.3's Gui.setScreen clears the
    old screen's focus, and Controlify's keyboard is a screen over the one with the text box, so the box lost
    focus and EditBox.charTyped ignored every key. KeyboardOverlayFocusMixin gives the focus back. DevKeys
    (MCSS_DEV_KEYS, one local dev client) checks Direct Connect, Add Server and Create World with Controlify's
    real keyboard, no controller needed: before the fix the value stayed empty, after it the text is typed and
    kept.
  - That made our TypeScreen (and its takeover of A) redundant: removed. Our keyboard is left on New profile only.
  - Steam's keyboard: Enter, Space, arrows and page keys pass while a text box has focus (Escape never does).
  - Sodium's video settings can't be driven by the pad. Controlify's Sodium compat matches Sodium 0.9.3's API, so
    the cause isn't confirmed. fetch-mods took the newest upload, betas included ("latest release" in its comment),
    which put Sodium 0.9.3-beta.1 in; it now prefers releases (Sodium 0.9.2), the version Controlify 3.5.x was
    released against.
  - Backspace (and Enter, Tab, arrows) on Controlify's keyboard did nothing: Controlify sends KeyEvent(scancode,
    0, mods), the pre-26.3 shape, but 26.3's KeyEvent takes the SDL keycode second and EditBox switches on it.
    KeyboardKeyCodeMixin fills in the keycode. DevKeys now also checks Backspace and Left.
  - Sodium: the real cause. Controlify 3.5.3 ships controlify-compat.sodium.mixins.json but doesn't list it in its
    fabric.mod.json, so its Sodium support never loaded and the pad only reached the search box and page buttons.
    We list it in ours. Two more fixes were needed (FocusFix): Controlify focuses the first option directly on
    the screen (not through the list), so arrow navigation restarted from Render Distance every time; and Sodium's
    list doesn't scroll to the focused option. DevSodium (MCSS_DEV_SODIUM) checks it: 20 steps down reach Entity
    Shadows with the list scrolled, and a slider step changes Render Distance. Sodium's widgets only take focus
    after arrow or Tab input (Controlify reports its navigation as KEYBOARD_ARROW), so the test sets that.
- Guests follow Player 1 onto a server (ServerFollow): P1's JOIN on a remote server writes mcss-control/server
  (the address), DISCONNECT deletes it. A waiting guest connects to it (else the LAN port, as before), leaves when
  P1 does, and closes its view when its player leaves by themselves. Refused or kicked: the waiting screen shows the
  reason ("needs online-mode=false") and no retry until P1 is on another server. Tested locally with one dev
  guest (Bob) against dnlwtsn.co.uk:25565 (online-mode=false): joined, followed P1 out, refused by a bad host.
- Instant split on join: AppRun opens a loading window (appimage/loading.qml under the Deck's qml6, else zenity)
  as soon as a guest is launched, titled "P<shown> Loading" (shown = its line in slots), so the tiler splits the
  screen at once. The tiler closes it when that player's game window (same P<n>) appears; AppRun closes it when the
  player leaves before then, after 90 s as a fallback, and on teardown. qml6 loads it on the Deck (checked
  off-screen).
- Player 1 gets the loading tile too ("Minecraft is loading…") from launch until its game window appears
  (fallback close after 180 s). README: instant split, server follow. v0.7.0.
- Self-update (v0.8.0): appimage/mcss-update.py (stdlib) asks GitHub's releases/latest, compares semver with the
  AppImage's VERSION (written by build-appimage from gradle.properties; the release workflow fails a tag that
  doesn't match), downloads the versioned asset with its SHA-256 digest from the API, checks size/digest/ELF, and
  os.replace()s $APPIMAGE. AppRun runs it under Player 1's loading tile (live status via a status file the QML
  polls; QML_XHR_ALLOW_FILE_READ=1), keeps .previous, stop_session(), then exec's the new AppImage (same PID for
  Steam) with MCSS_UPDATED set so it doesn't check again. Off: Splitscreen settings "Updates: Off" (updates=off,
  grepped by AppRun), --no-update, MCSS_NO_UPDATE=1. Unit-tested against a fake API: newer/equal/older/pre-release,
  offline (gives up in 4 s), bad digest (rejected, file untouched), progress.
