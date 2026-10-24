package dev.mcss.glue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

/**
 * This client's place in the splitscreen session, read once from the environment.
 * MCSS_PLAYER  1..4; unset = taken from Prism Launcher's INST_ID when that is P1..P4 (the instances mcss-setup.sh
 *              creates), otherwise 0 = glue disabled (the client behaves like vanilla)
 * MCSS_ROLE    host (runs the "press START" lobby, shares whatever world it opens on LAN) or guest (joins it);
 *              default host for P1
 * MCSS_PORT    LAN port the host publishes on and guests join (default 25599)
 *
 * The clients share state through files in CONTROL, under Prism Launcher's data folder (AppRun empties it at start):
 *   slots       "<player> <generation>" per joined player, host first, then guests in join order (that order is
 *               the shown player number); written by the host's Lobby. AppRun starts and stops P2-P4 to match
 *               (a new generation means a fresh join)
 *   events      recent button presses of every joined player, written by the host, matched by GuestPad
 *   alive-P<n>  a guest's heartbeat; left-P<n>  a guest quit
 */
public final class Splitscreen {
	public static final int PLAYER = intEnv("MCSS_PLAYER", prismInstancePlayer());
	public static final boolean ENABLED = PLAYER > 0;
	public static final boolean HOST = "host".equals(env("MCSS_ROLE", PLAYER == 1 ? "host" : "guest"));
	public static final int PORT = intEnv("MCSS_PORT", 25599);
	// game dir is <prism>/instances/P<n>/minecraft
	public static final Path SHARED = FabricLoader.getInstance().getGameDir().toAbsolutePath().getParent().getParent()
			.getParent();
	public static final Path CONTROL = SHARED.resolve("mcss-control");

	/**
	 * The number players see ("Player 2", window title "P2 ..." that the tiler orders by). PLAYER is which client
	 * this is (instance P<n>) and never changes; the shown number is the place in the host's slots list (host first,
	 * then guests by join time), so when a guest leaves the ones after it move up and new joins go last.
	 */
	public static volatile int shown = PLAYER;

	/** "P2 ", or for Player 1 "P1 " / "P1v " (the layout the tiler should use: v = vertical). */
	public static String titlePrefix() {
		return "P" + shown + (HOST && SplitSettings.vertical() ? "v" : "") + " ";
	}

	private Splitscreen() {}

	/** The file's text, or null if it doesn't exist (or can't be read). */
	public static String read(String name) {
		try {
			return Files.readString(CONTROL.resolve(name), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return null;
		}
	}

	/** Replaces the file in one step, so readers in other clients never see half of it. */
	public static void write(String name, String text) {
		try {
			Files.createDirectories(CONTROL);
			Path tmp = CONTROL.resolve(name + ".tmp-P" + PLAYER);
			Files.writeString(tmp, text, StandardCharsets.UTF_8);
			Files.move(tmp, CONTROL.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			SplitscreenGlue.LOG.warn("[mcss] can't write {}: {}", name, e.toString());
		}
	}

	public static void delete(String name) {
		try {
			Files.deleteIfExists(CONTROL.resolve(name));
		} catch (IOException ignored) {
		}
	}

	public static long modified(String name) {
		try {
			return Files.getLastModifiedTime(CONTROL.resolve(name)).toMillis();
		} catch (IOException e) {
			return 0;
		}
	}

	// Prism Launcher 11 can't be relied on for per-instance Env (it drops JSON it can't parse), but it always
	// exports the instance folder name as INST_ID
	private static int prismInstancePlayer() {
		String id = env("INST_ID", "");
		return id.matches("P[1-4]") ? id.charAt(1) - '0' : 0;
	}

	private static String env(String key, String def) {
		String v = System.getenv(key);
		return v == null || v.isBlank() ? def : v.trim();
	}

	private static int intEnv(String key, int def) {
		try {
			return Integer.parseInt(env(key, Integer.toString(def)));
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
