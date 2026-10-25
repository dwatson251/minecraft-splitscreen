package dev.mcss.glue;

import dev.mcss.glue.mixin.DisconnectedScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/**
 * Guests follow Player 1 onto a multiplayer server, as they follow a LAN world. Player 1 publishes the address it's
 * on ("server" in mcss-control); a waiting guest connects to it, and leaves again when Player 1 does.
 *
 * Guests play offline profiles, so only servers with online-mode=false let them in. When a server refuses a guest at
 * login, the waiting screen says why, and that guest doesn't retry until Player 1 leaves that server (and comes back,
 * or moves to another). A guest kicked while playing (a server restart, say) rejoins by itself a few seconds later.
 *
 * Every view connects from the same address, and servers limit connections per address (Bukkit's
 * connection-throttle, 4 s by default; Velocity's login-ratelimit). So guests make their first attempt a few
 * seconds apart, and a "throttled" refusal isn't final: that guest waits and tries again, backing off up to 30 s.
 */
final class ServerFollow {
	/** The server a guest is connecting to or playing on (null: none, or Player 1's LAN world). */
	private static volatile String joining;
	private static volatile String refused;
	private static volatile Component refusedReason;
	private static volatile long disconnectedAt, kickedAt;
	/** Throttled: no attempt before this; how many times in a row. */
	private static volatile long retryAt;
	private static volatile int throttled;
	/** When this guest first saw Player 1 on the current server (the staggered first attempt counts from it). */
	private static volatile String seen;
	/** In the world on the server it connected to (a kick then isn't a refusal). */
	private static volatile boolean playing;
	private static volatile long seenAt;
	private static final long STAGGER_MS = 4500;

	private ServerFollow() {}

	/** The address Player 1 is playing on, or null. */
	static String hostServer() {
		String s = Splitscreen.read("server");
		return s == null || s.isBlank() ? null : s.trim();
	}

	/** Host: joined a world (a remote server, or its own one). */
	static void hostJoined(Minecraft mc) {
		ServerData data = mc.getCurrentServer();
		if (!mc.isLocalServer() && data != null) {
			Splitscreen.write("server", data.ip);
			SplitscreenGlue.LOG.info("[mcss] P1 is on server {}: guests follow", data.ip);
		} else {
			Splitscreen.delete("server");
		}
	}

	static void hostLeft() {
		Splitscreen.delete("server");
	}

	/** Guest, waiting: the server to join now, or null (none, refused, or not this guest's turn yet). */
	static String toJoin() {
		String s = hostServer();
		if (s == null) {
			// Player 1 left: a refusal was for that visit only (the server may be fixed when Player 1 is back)
			refused = null;
			refusedReason = null;
			seen = null;
			return null;
		}
		if (s.equals(refused)) return null;
		long now = System.currentTimeMillis();
		if (!s.equals(seen)) {
			seen = s;
			seenAt = now;
			throttled = 0;
			retryAt = 0;
		}
		// Players 2, 3, 4 try 0, 4.5 and 9 s after seeing the server, so they don't trip the per-address limit
		if (now < seenAt + (Splitscreen.PLAYER - 2) * STAGGER_MS || now < retryAt) return null;
		return s;
	}

	/** Guest: the server asked it to wait (throttled) and it will try again by itself. */
	static boolean retrying() {
		return retryAt > System.currentTimeMillis();
	}

	/** Guest: joined a world; a server that let it in resets the back-off. */
	static void guestJoined() {
		throttled = 0;
		retryAt = 0;
		playing = joining != null;
	}

	static void connect(Minecraft mc, String address) {
		joining = address;
		playing = false;
		SplitscreenGlue.LOG.info("[mcss] following Player 1 to {}", address);
		ServerData data = new ServerData("Player 1's server", address, ServerData.Type.OTHER);
		ConnectScreen.startConnecting(mc.gui.screen(), mc, ServerAddress.parseString(address), data, false, null);
	}

	static void connectingToLan() {
		joining = null;
	}

	static void guestDisconnected() {
		disconnectedAt = System.currentTimeMillis();
	}

	/** Guest: a "disconnected" screen: the server refused or kicked this guest. */
	static void guestRefused(DisconnectedScreen screen) {
		kickedAt = System.currentTimeMillis();
		if (joining == null) return;
		Component reason = ((DisconnectedScreenAccessor) screen).mcss$details().reason();
		String text = reason.getString().toLowerCase(java.util.Locale.ROOT);
		if (playing) {
			// kicked while playing (a server restart, say): not a refusal; rejoin once Player 1 is back on it
			SplitscreenGlue.LOG.info("[mcss] {} disconnected P{} while playing ({}), rejoining", joining, Splitscreen.PLAYER,
					reason.getString());
			playing = false;
			retryAt = System.currentTimeMillis() + 5_000L + (Splitscreen.PLAYER - 2) * 1_500L;
			joining = null;
			return;
		}
		if (text.contains("throttled") || text.contains("too fast")) {
			// Bukkit "Connection throttled! Please wait before reconnecting.", Velocity "logging in too fast"
			throttled++;
			retryAt = System.currentTimeMillis() + Math.min(30_000, 5_000L * throttled) + (Splitscreen.PLAYER - 2) * 1_500L;
			SplitscreenGlue.LOG.info("[mcss] {} throttled P{} ({} in a row), trying again in {} s", joining, Splitscreen.PLAYER,
					throttled, (retryAt - System.currentTimeMillis()) / 1000);
			joining = null;
			return;
		}
		refused = joining;
		refusedReason = reason;
		SplitscreenGlue.LOG.info("[mcss] {} refused P{}: {}", refused, Splitscreen.PLAYER, refusedReason.getString());
		joining = null;
	}

	/** Guest, after a disconnect: true when this player left Player 1's server themselves (not kicked). */
	static boolean leftServer() {
		String was = joining;
		joining = null;
		return was != null && was.equals(hostServer()) && kickedAt < disconnectedAt;
	}

	/** Guest, every tick: leave the server when Player 1 has. */
	static void tick(Minecraft mc) {
		String on = joining;
		if (on == null || mc.getConnection() == null || on.equals(hostServer())) return;
		SplitscreenGlue.LOG.info("[mcss] Player 1 left {}, leaving too", on);
		joining = null;
		mc.disconnectFromWorld(Component.literal("Player 1 left the server"));
		mc.gui.setScreen(new WaitingScreen());
	}

	/** Why Player 1's current server refused this guest, or null. */
	static Component refusal() {
		String s = hostServer();
		return s != null && s.equals(refused) ? refusedReason : null;
	}
}
