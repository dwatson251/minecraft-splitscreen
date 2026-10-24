package dev.mcss.glue;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.controller.ControllerEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * A guest finds its own pad by its player's button presses. The host publishes them with timestamps (events file);
 * every client reads the same hardware at the same moment, so the local pad that pressed the same buttons at the
 * same times is the one. Each published press scores +1 for every local pad with a matching press within
 * MATCH_MS and -1 for the others. A pad is taken once it leads by LEAD points, and dropped if it falls to LOSE
 * (wrong pick, or the pad reconnected under another identity), when matching starts over.
 */
final class GuestPad {
	private record Remote(long time, String button, boolean down) {}

	private static final long MATCH_MS = 250;
	private static final long SETTLE_MS = 400;  // wait this long before judging a press: our own poll may lag
	private static final long KEEP_MS = 3000;
	private static final int LEAD = 2, LOSE = -3, CAP = 6;

	private static final PadEdges EDGES = new PadEdges();
	private static final Map<ControllerEntity, Deque<PadEdges.Edge>> LOCAL = new IdentityHashMap<>();
	private static final Map<ControllerEntity, Integer> SCORE = new IdentityHashMap<>();
	private static final List<Remote> PENDING = new ArrayList<>();
	private static ControllerEntity mine;
	private static long lastEventsFile, lastRemote = System.currentTimeMillis();
	private static int ticks;
	private static boolean asked;

	private GuestPad() {}

	static void tick() {
		if (ticks++ % 20 == 0) {
			Splitscreen.write("alive-P" + Splitscreen.PLAYER, Long.toString(System.currentTimeMillis()));
			updateShownNumber();
		}
		List<PadEdges.Edge> edges = EDGES.poll();
		if (edges == null) return;
		long now = System.currentTimeMillis();

		LOCAL.keySet().retainAll(new java.util.HashSet<>(EDGES.pads()));
		SCORE.keySet().retainAll(LOCAL.keySet());
		for (PadEdges.Edge e : edges) LOCAL.computeIfAbsent(e.pad(), p -> new ArrayDeque<>()).addLast(e);
		for (Deque<PadEdges.Edge> q : LOCAL.values()) {
			while (!q.isEmpty() && now - q.peekFirst().time() > KEEP_MS) q.removeFirst();
		}
		readRemote();

		for (var it = PENDING.iterator(); it.hasNext(); ) {
			Remote r = it.next();
			if (now - r.time() < SETTLE_MS) continue;
			it.remove();
			for (ControllerEntity pad : EDGES.pads()) {
				boolean hit = LOCAL.getOrDefault(pad, new ArrayDeque<>()).stream().anyMatch(e -> e.button().equals(r.button())
						&& e.down() == r.down() && Math.abs(e.time() - r.time()) <= MATCH_MS);
				SCORE.merge(pad, hit ? 1 : -1, (a, b) -> Math.max(-CAP, Math.min(CAP, a + b)));
			}
		}

		if (mine != null && (!EDGES.pads().contains(mine) || SCORE.getOrDefault(mine, 0) <= LOSE)) {
			SplitscreenGlue.LOG.info("[mcss] P{} lets go of {} ({})", Splitscreen.PLAYER, mine.name(),
					EDGES.pads().contains(mine) ? "presses stopped matching" : "disconnected");
			mine = null;
			SCORE.clear();
		}
		if (mine == null) pick();
		Controlify c = Controlify.instance();
		if (c.getCurrentController().orElse(null) != mine) c.setCurrentController(mine, false);
		if (mine == null && !asked) {
			asked = true;
			Lobby.toast("Player " + Splitscreen.shown, "Press any button on your controller");
		}
	}

	private static void pick() {
		ControllerEntity best = null;
		int bestScore = Integer.MIN_VALUE, second = Integer.MIN_VALUE;
		for (Map.Entry<ControllerEntity, Integer> e : SCORE.entrySet()) {
			if (e.getValue() > bestScore) {
				second = bestScore;
				bestScore = e.getValue();
				best = e.getKey();
			} else if (e.getValue() > second) {
				second = e.getValue();
			}
		}
		if (best != null && bestScore >= LEAD && (second == Integer.MIN_VALUE || bestScore - second >= LEAD)) {
			mine = best;
			asked = false;
			SplitscreenGlue.LOG.info("[mcss] P{} found its controller: {} ({}), score {} vs {}", Splitscreen.PLAYER,
					best.name(), best.uid(), bestScore, second == Integer.MIN_VALUE ? "-" : second);
			Lobby.toast("Player " + Splitscreen.shown, "Controller found: " + best.name());
		}
	}

	/** New presses of this player from the host's events file ("<time> <player> <button> <1|0>" per line). */
	private static void readRemote() {
		long mod = Splitscreen.modified("events");
		if (mod == lastEventsFile) return;
		lastEventsFile = mod;
		String text = Splitscreen.read("events");
		if (text == null) return;
		long newest = lastRemote;
		for (String line : text.split("\n")) {
			String[] f = line.trim().split(" ");
			if (f.length != 4 || !f[1].equals(Integer.toString(Splitscreen.PLAYER))) continue;
			try {
				long t = Long.parseLong(f[0]);
				if (t <= lastRemote) continue;
				PENDING.add(new Remote(t, f[2], f[3].equals("1")));
				newest = Math.max(newest, t);
			} catch (NumberFormatException ignored) {
			}
		}
		lastRemote = newest;
	}

	/** Our place in the host's slots list is the number players see; retitle the window when it changes. */
	private static void updateShownNumber() {
		String slots = Splitscreen.read("slots");
		if (slots == null) return;
		String[] lines = slots.trim().split("\n");
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].startsWith(Splitscreen.PLAYER + " ")) {
				if (Splitscreen.shown != i + 1) {
					SplitscreenGlue.LOG.info("[mcss] client P{} is now player {}", Splitscreen.PLAYER, i + 1);
					Splitscreen.shown = i + 1;
					Minecraft.getInstance().updateTitle();
				}
				return;
			}
		}
	}

	/** Called when the guest shuts down: the host frees this player number. */
	static void leave() {
		Splitscreen.write("left-P" + Splitscreen.PLAYER, Long.toString(System.currentTimeMillis()));
	}
}
