package dev.mcss.glue;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.controller.ControllerEntity;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Joining, run by the host client (P1), which sees every pad. The first controller to press anything is Player 1
 * (the host's own view). After that, START on a pad nobody owns makes it a player: first it goes to a player whose
 * pad disconnected, otherwise to a free guest client (P2-P4). While Player 1 is alone a toast says so now and then. Each join is written to the slots file, and AppRun starts that player's
 * window. A guest that quits frees its client and its pad, and the players after it move up (ordered()). Player 1
 * never leaves: quitting the host ends the whole session.
 *
 * Guest windows can't be told which pad is theirs by any id (ids differ between processes), so the host publishes
 * every joined player's button presses with timestamps (events file) and each guest picks the local pad whose
 * presses match (GuestPad).
 */
final class Lobby {
	static final class Slot {
		final long gen;
		ControllerEntity pad;
		String padName;

		Slot(long gen, ControllerEntity pad) {
			this.gen = gen;
			this.pad = pad;
			this.padName = pad.name();
		}
	}

	private static final int EVENTS_KEPT = 64;
	private static final long START_TIMEOUT_MS = 180_000;  // a guest window that never comes up
	private static final long ALIVE_TIMEOUT_MS = 20_000;   // a guest that stopped without saying so
	private static final int JOIN_HINT_TICKS = 45 * 20;

	private static final PadEdges EDGES = new PadEdges();
	static final Map<Integer, Slot> SLOTS = new TreeMap<>();
	private static final Deque<String> EVENTS = new ArrayDeque<>();
	private static int ticks;

	private Lobby() {}

	static void tick() {
		List<PadEdges.Edge> edges = EDGES.poll();
		if (edges == null) return;
		boolean changed = false;

		// pads that went away leave their player waiting for a reconnect (START on any free pad)
		for (Map.Entry<Integer, Slot> e : SLOTS.entrySet()) {
			Slot s = e.getValue();
			if (s.pad != null && !EDGES.pads().contains(s.pad)) {
				SplitscreenGlue.LOG.info("[mcss] client P{} lost its controller ({})", e.getKey(), s.padName);
				s.pad = null;
				toast("Player " + shownNumber(e.getKey()), "Controller disconnected: press START to reconnect");
			}
		}

		boolean published = false;
		for (PadEdges.Edge edge : edges) {
			Integer owner = ownerOf(edge.pad());
			// Player 1 takes the first controller that presses anything; everyone else joins with START
			if (owner == null && edge.down() && (PadEdges.START.equals(edge.button()) || !hostReady())) {
				owner = join(edge.pad());
				changed |= owner != null;
			}
			if (owner != null) {
				EVENTS.addLast(edge.time() + " " + owner + " " + edge.button() + " " + (edge.down() ? 1 : 0));
				if (EVENTS.size() > EVENTS_KEPT) EVENTS.removeFirst();
				published = true;
			}
		}
		if (published) Splitscreen.write("events", String.join("\n", EVENTS) + "\n");

		if (ticks++ % 20 == 0) changed |= dropLeftGuests();
		// while Player 1 plays alone, remind them that others can join
		if (SLOTS.size() == 1 && hostReady() && ticks % JOIN_HINT_TICKS == JOIN_HINT_TICKS / 2) {
			toast("Player 2", "Press START on a controller to join");
		}
		if (changed) writeSlots();
		applyHostPad();
	}

	private static Integer ownerOf(ControllerEntity pad) {
		for (Map.Entry<Integer, Slot> e : SLOTS.entrySet()) {
			if (e.getValue().pad == pad) return e.getKey();
		}
		return null;
	}

	private static Integer join(ControllerEntity pad) {
		for (Map.Entry<Integer, Slot> e : SLOTS.entrySet()) {
			if (e.getValue().pad == null) {
				e.getValue().pad = pad;
				e.getValue().padName = pad.name();
				SplitscreenGlue.LOG.info("[mcss] client P{} reconnected with {} ({})", e.getKey(), pad.name(), pad.uid());
				toast("Player " + shownNumber(e.getKey()), "Controller reconnected: " + pad.name());
				return e.getKey();
			}
		}
		for (int n = 1; n <= 4; n++) {
			if (!SLOTS.containsKey(n)) {
				SLOTS.put(n, new Slot(System.currentTimeMillis(), pad));
				SplitscreenGlue.LOG.info("[mcss] client P{} joined as player {} with {} ({})", n, SLOTS.size(), pad.name(),
						pad.uid());
				if (n > 1) toast("Player " + SLOTS.size() + " joined", "Their view is starting");
				return n;
			}
		}
		return null;  // four players already
	}

	/** Guests that quit (left-P<n>), never came up, or stopped answering give their number back. */
	private static boolean dropLeftGuests() {
		boolean changed = false;
		long now = System.currentTimeMillis();
		for (var it = SLOTS.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			int n = e.getKey();
			if (n == 1) continue;
			String alive = Splitscreen.read("alive-P" + n);
			long seen = 0;
			try {
				if (alive != null) seen = Long.parseLong(alive.trim());
			} catch (NumberFormatException ignored) {
			}
			boolean left = Splitscreen.read("left-P" + n) != null && Splitscreen.modified("left-P" + n) >= e.getValue().gen;
			boolean neverCame = seen < e.getValue().gen && now - e.getValue().gen > START_TIMEOUT_MS;
			boolean stopped = seen >= e.getValue().gen && now - seen > ALIVE_TIMEOUT_MS;
			if (left || neverCame || stopped) {
				SplitscreenGlue.LOG.info("[mcss] client P{} left ({})", n, left ? "quit" : neverCame ? "never started" : "stopped");
				Splitscreen.delete("left-P" + n);
				it.remove();
				changed = true;
			}
		}
		return changed;
	}

	/** True once Player 1 (the host's own view) has a controller. */
	static boolean hostReady() {
		if (DevShots.ACTIVE || DevSplit.ACTIVE) return true;
		Slot p1 = SLOTS.get(1);
		return p1 != null && p1.pad != null;
	}

	/** Joined clients in player order: the host first, then guests by join time. */
	static List<Map.Entry<Integer, Slot>> ordered() {
		return SLOTS.entrySet().stream().sorted(java.util.Comparator
				.comparing((Map.Entry<Integer, Slot> e) -> e.getKey() != 1).thenComparingLong(e -> e.getValue().gen)).toList();
	}

	static int shownNumber(int client) {
		List<Map.Entry<Integer, Slot>> o = ordered();
		for (int i = 0; i < o.size(); i++) {
			if (o.get(i).getKey() == client) return i + 1;
		}
		return client;
	}

	private static void writeSlots() {
		Splitscreen.write("slots", ordered().stream().map(e -> e.getKey() + " " + e.getValue().gen)
				.collect(Collectors.joining("\n")) + "\n");
	}

	/** The host's own view follows Player 1's pad, and no other. */
	private static void applyHostPad() {
		Controlify c = Controlify.instance();
		Slot p1 = SLOTS.get(1);
		ControllerEntity want = p1 == null ? null : p1.pad;
		if (c.getCurrentController().orElse(null) != want) c.setCurrentController(want, false);
	}

	static void toast(String title, String text) {
		if (DevSplit.ACTIVE) return;  // keep the dev split-screen pictures clean
		SystemToast.addOrUpdate(Minecraft.getInstance().gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				Component.literal(title), Component.literal(text));
	}
}
