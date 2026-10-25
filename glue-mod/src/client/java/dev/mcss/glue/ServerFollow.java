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
 * Guests play offline profiles, so only servers with online-mode=false let them in. When a server refuses (or kicks)
 * a guest, the waiting screen says why, and that guest doesn't retry until Player 1 moves to another server.
 */
final class ServerFollow {
	/** The server a guest is connecting to or playing on (null: none, or Player 1's LAN world). */
	private static volatile String joining;
	private static volatile String refused;
	private static volatile Component refusedReason;
	private static volatile long disconnectedAt, kickedAt;

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

	/** Guest, waiting: the server to join now, or null (none, or it refused this guest). */
	static String toJoin() {
		String s = hostServer();
		return s == null || s.equals(refused) ? null : s;
	}

	static void connect(Minecraft mc, String address) {
		joining = address;
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
		refused = joining;
		refusedReason = ((DisconnectedScreenAccessor) screen).mcss$details().reason();
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
