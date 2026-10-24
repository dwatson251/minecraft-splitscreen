// Tiles Minecraft windows into a splitscreen grid, ordered by the "P<n> " caption prefix the glue mod sets.
// 1 window: fullscreen. 2: top/bottom. 3: P1 across the top, P2/P3 below. 4: quadrants.
// Anything else (Prism Launcher progress or error dialogs) is kept above the game and centred.

function playerIndex(w) {
    var m = /^P(\d+) /.exec(w.caption);
    return m ? parseInt(m[1], 10) : -1;
}

function slots(n, a) {
    var hw = Math.floor(a.width / 2), hh = Math.floor(a.height / 2);
    var x = a.x, y = a.y, W = a.width, H = a.height;
    if (n <= 1) return [{x: x, y: y, width: W, height: H}];
    if (n == 2) return [{x: x, y: y, width: W, height: hh}, {x: x, y: y + hh, width: W, height: H - hh}];
    var q = [{x: x, y: y, width: hw, height: hh}, {x: x + hw, y: y, width: W - hw, height: hh},
             {x: x, y: y + hh, width: hw, height: H - hh}, {x: x + hw, y: y + hh, width: W - hw, height: H - hh}];
    if (n == 3) return [{x: x, y: y, width: W, height: hh}, q[2], q[3]];
    return q;
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
    wins.sort(function (a, b) { return playerIndex(a) - playerIndex(b); });
    var s = slots(wins.length, area);
    for (var i = 0; i < wins.length && i < s.length; i++) {
        var w = wins[i];
        w.noBorder = true;
        if (w.fullScreen) w.fullScreen = false;
        if (w.maximizable) w.setMaximize(false, false);
        w.frameGeometry = s[i];
    }
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
workspace.windowRemoved.connect(tile);
workspace.virtualScreenGeometryChanged.connect(tile);
workspace.windowList().forEach(watch);
tile();
