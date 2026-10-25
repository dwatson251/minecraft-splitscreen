// Tiles Minecraft windows into a splitscreen grid, ordered by the "P<n> " caption prefix the glue mod sets.
// Player 1's caption also carries the layout picked in its Splitscreen settings: "P1 " horizontal, "P1v " vertical.
// Horizontal: 2 = top/bottom, 3 = P1 across the top with P2/P3 below. Vertical: 2 = side by side, 3 = P1 down the
// left with P2/P3 stacked on the right. 1 = fullscreen, 4 = quadrants either way.
// Anything else (Prism Launcher progress or error dialogs) is kept above the game and centred.
// A joining player's loading window ("P<n> Loading", AppRun) holds their tile until their game window appears; then
// it's closed.
// Keyboard focus always stays on Player 1's view: in Desktop Mode Steam also sends the Deck's D-pad as arrow keys,
// which must not move another player's menus. Guests read their pads directly and don't need focus.

function playerIndex(w) {
    var m = /^P(\d+)v? /.exec(w.caption);
    return m ? parseInt(m[1], 10) : -1;
}

function isLoading(w) {
    return /^P\d+ Loading$/.test(w.caption);
}

function vertical(wins) {
    return wins.some(function (w) { return /^P1v /.test(w.caption); });
}

function slots(n, a, vert) {
    var hw = Math.floor(a.width / 2), hh = Math.floor(a.height / 2);
    var x = a.x, y = a.y, W = a.width, H = a.height;
    if (n <= 1) return [{x: x, y: y, width: W, height: H}];
    if (n == 2 && vert) return [{x: x, y: y, width: hw, height: H}, {x: x + hw, y: y, width: W - hw, height: H}];
    if (n == 2) return [{x: x, y: y, width: W, height: hh}, {x: x, y: y + hh, width: W, height: H - hh}];
    var q = [{x: x, y: y, width: hw, height: hh}, {x: x + hw, y: y, width: W - hw, height: hh},
             {x: x, y: y + hh, width: hw, height: H - hh}, {x: x + hw, y: y + hh, width: W - hw, height: H - hh}];
    if (n == 3 && vert) return [{x: x, y: y, width: hw, height: H}, q[1], q[3]];
    if (n == 3) return [{x: x, y: y, width: W, height: hh}, q[2], q[3]];
    return q;
}

function focusPlayer1(wins) {
    var p1 = wins.filter(function (w) { return playerIndex(w) == 1; })[0];
    if (p1 && workspace.activeWindow !== p1) workspace.activeWindow = p1;
}

var busy = false;
function tile() {
    if (busy) return;
    busy = true;
    var area = workspace.virtualScreenGeometry;
    var all = workspace.windowList().filter(function (w) { return !w.deleted && (w.normalWindow || w.dialog); });
    all.forEach(function (w) {
        if (playerIndex(w) >= 0) return;
        w.keepAbove = true;
        var g = w.frameGeometry;
        w.frameGeometry = {x: area.x + Math.max(0, Math.floor((area.width - g.width) / 2)),
                           y: area.y + Math.max(0, Math.floor((area.height - g.height) / 2)),
                           width: Math.min(g.width, area.width), height: Math.min(g.height, area.height)};
    });
    var wins = all.filter(function (w) { return playerIndex(w) >= 0; });
    // a game window has replaced a loading one: close the loading window and leave it out
    var games = {};
    wins.forEach(function (w) { if (!isLoading(w)) games[playerIndex(w)] = true; });
    wins = wins.filter(function (w) {
        if (isLoading(w) && games[playerIndex(w)]) {
            w.closeWindow();
            return false;
        }
        return true;
    });
    wins.sort(function (a, b) { return playerIndex(a) - playerIndex(b); });
    var s = slots(wins.length, area, vertical(wins));
    for (var i = 0; i < wins.length && i < s.length; i++) {
        var w = wins[i];
        w.noBorder = true;
        if (w.fullScreen) w.fullScreen = false;
        if (w.maximizable) w.setMaximize(false, false);
        w.frameGeometry = s[i];
    }
    focusPlayer1(wins);
    console.log("mcss-tile: " + wins.map(function (w, i) {
        var g = w.frameGeometry;
        return w.caption + "@" + g.x + "," + g.y + " " + g.width + "x" + g.height;
    }).join(" | "));
    busy = false;
}

function watch(w) {
    w.captionChanged.connect(tile);
    w.fullScreenChanged.connect(tile);
    w.frameGeometryChanged.connect(function () {
        // Fight clients (Minecraft restores its own window size) without recursing.
        if (!busy) tile();
    });
}

workspace.windowAdded.connect(function (w) { watch(w); tile(); });
workspace.windowActivated.connect(function (w) {
    // another player's view (or a click) took focus: hand it back to Player 1
    if (w && playerIndex(w) > 1) focusPlayer1(workspace.windowList());
});
workspace.windowRemoved.connect(tile);
workspace.virtualScreenGeometryChanged.connect(tile);
workspace.windowList().forEach(watch);
tile();
