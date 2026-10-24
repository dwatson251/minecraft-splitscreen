package dev.mcss.glue;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.config.settings.profile.ProfileSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Gives every splitscreen client its own controller, auto-detected: each client claims one connected pad that no
 * other running client has claimed, and keeps it while it stays connected. A pad plugged in later goes to the
 * lowest-numbered player still without one; if a player's pad disconnects, they take the next free one.
 *
 * Under Steam, AppRun hides Steam Input's virtual pads from the game (they all look alike, as "Steam Controller",
 * so the Deck couldn't be told apart and players ended up on the wrong pad); the clients read the real devices.
 *
 * Claims are small files next to the instances ("<prism>/mcss-pads/P<n>": timestamp + pad uid), refreshed every
 * second; a claim older than STALE_MS belongs to a client that quit. When two clients want the same pad, the lower
 * player number wins. First choice for player n is pad n-1 in a fixed order (a Steam Deck's own controls first, so
 * the Deck player is P1), then any free pad. Controlify keeps reading the pad while the window is unfocused.
 * Only loaded when Controlify is present.
 */
final class ControlifyPin {
	private static final long STALE_MS = 5000;
	private static final Path CLAIMS = Splitscreen.SHARED.resolve("mcss-pads");

	private static String claimedUid;
	private static int ticks;
	private static int lastCount = -1;
	private static boolean toldToConnect;

	private ControlifyPin() {}

	private static boolean failed;

	static void tick() {
		if (ticks++ % 20 != 0) return;  // once a second is plenty for hot-plug
		try {
			update();
		} catch (RuntimeException e) {
			// never take the game down over controller bookkeeping; Controlify still works on its own
			if (!failed) SplitscreenGlue.LOG.error("[mcss] controller assignment failed", e);
			failed = true;
		}
	}

	private static void update() {
		Controlify c = Controlify.instance();
		// before Controlify has discovered controllers there's no manager and no active profile yet
		if (c.getControllerManager().isEmpty()) return;
		// out-of-focus input: only one tile has focus. Mixed input: Steam turns Deck controls into keyboard/mouse
		// events as well, and switching input mode on every press made Controlify reset menu focus to the first button
		var global = c.config().getSettings().globalSettings();
		if (!global.outOfFocusInput || !global.mixedInput) {
			global.outOfFocusInput = true;
			global.mixedInput = true;
			c.config().markDirty();
		}
		List<ControllerEntity> pads = c.getControllerManager().map(m -> m.getConnectedControllers()).orElse(List.of())
				.stream().sorted(ORDER).toList();
		if (pads.size() != lastCount) {
			lastCount = pads.size();
			SplitscreenGlue.LOG.info("[mcss] controllers: {}", pads.stream()
					.map(p -> p.name() + " uid=" + p.uid() + " id=" + p.info().ucid() + " guid=" + p.guid()
							+ " hid=" + p.info().hid().map(Object::toString).orElse("-")).toList());
		}

		ControllerEntity want = choose(pads, readClaims());
		writeClaim(want == null ? "" : want.uid());
		if (want == null) {
			if (claimedUid != null) SplitscreenGlue.LOG.info("[mcss] P{} lost its controller", Splitscreen.PLAYER);
			claimedUid = null;
			// Controlify picks a pad by itself at startup (often one another player owns): drop it
			if (c.getCurrentController().isPresent()) {
				SplitscreenGlue.LOG.info("[mcss] P{} has no free controller, releasing {}", Splitscreen.PLAYER,
						c.getCurrentController().get().name());
				c.setCurrentController(null, false);
			}
			ProfileSettings profile = c.config().getActiveProfile();
			if (profile.controllerUid != null) {
				profile.controllerUid = null;
				c.config().markDirty();
			}
			if (!toldToConnect) {
				toldToConnect = true;
				SystemToast.add(Minecraft.getInstance().gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
						Component.literal("Player " + Splitscreen.PLAYER), Component.literal("Connect a controller to play"));
			}
			return;
		}
		toldToConnect = false;
		claimedUid = want.uid();
		if (c.getCurrentController().orElse(null) == want) return;
		SplitscreenGlue.LOG.info("[mcss] P{} takes {} ({})", Splitscreen.PLAYER, want.name(), want.uid());
		ProfileSettings profile = c.config().getActiveProfile();
		if (!Objects.equals(profile.controllerUid, want.uid())) {
			profile.controllerUid = want.uid();
			c.config().markDirty();
		}
		c.setCurrentController(want, true);
	}

	/** The pad this client should use, or null when every connected pad belongs to someone else. */
	private static ControllerEntity choose(List<ControllerEntity> pads, Map<String, Integer> others) {
		// keep the current pad unless a lower-numbered player claims it too
		for (ControllerEntity p : pads) {
			if (p.uid().equals(claimedUid) && others.getOrDefault(p.uid(), Integer.MAX_VALUE) > Splitscreen.PLAYER) {
				return p;
			}
		}
		int preferred = Splitscreen.PAD;
		if (preferred < pads.size() && !others.containsKey(pads.get(preferred).uid())) return pads.get(preferred);
		// lower-numbered players still waiting for a pad get first pick of the free ones
		int waitingBefore = 0;
		for (int n = 1; n < Splitscreen.PLAYER; n++) {
			if ("".equals(readClaim(n))) waitingBefore++;
		}
		for (ControllerEntity p : pads) {
			if (others.containsKey(p.uid())) continue;
			if (waitingBefore-- > 0) continue;
			return p;
		}
		return null;
	}

	/** Fresh claims of the other running clients: pad uid -> player number. */
	private static Map<String, Integer> readClaims() {
		Map<String, Integer> out = new HashMap<>();
		for (int n = 1; n <= 4; n++) {
			if (n == Splitscreen.PLAYER) continue;
			String uid = readClaim(n);
			if (uid != null && !uid.isEmpty()) out.merge(uid, n, Math::min);
		}
		return out;
	}

	/** Player n's claimed pad uid, "" if it runs without a pad, null if it isn't running. */
	private static String readClaim(int n) {
		try {
			String[] parts = Files.readString(CLAIMS.resolve("P" + n), StandardCharsets.UTF_8).split(" ", 2);
			if (System.currentTimeMillis() - Long.parseLong(parts[0].trim()) > STALE_MS) return null;
			return parts.length > 1 ? parts[1].trim() : "";
		} catch (IOException | NumberFormatException e) {
			return null;
		}
	}

	private static void writeClaim(String uid) {
		try {
			Files.createDirectories(CLAIMS);
			Path tmp = CLAIMS.resolve("P" + Splitscreen.PLAYER + ".tmp");
			Files.writeString(tmp, System.currentTimeMillis() + " " + uid, StandardCharsets.UTF_8);
			Files.move(tmp, CLAIMS.resolve("P" + Splitscreen.PLAYER), StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			SplitscreenGlue.LOG.warn("[mcss] can't write pad claim: {}", e.toString());
		}
	}

	// a Steam Deck's own controls first; then SDL discovery order (the UniqueControllerID's toString ends with the
	// SDL joystick instance id, which grows as pads are found)
	private static final Comparator<ControllerEntity> ORDER = Comparator
			.comparing((ControllerEntity p) -> !p.name().contains("Steam Deck"))
			.thenComparingLong(p -> trailingNumber(String.valueOf(p.info().ucid())))
			.thenComparing(ControllerEntity::uid);

	private static long trailingNumber(String s) {
		int i = s.length();
		while (i > 0 && Character.isDigit(s.charAt(i - 1))) i--;
		return i == s.length() ? Long.MAX_VALUE : Long.parseLong(s.substring(i));
	}
}
