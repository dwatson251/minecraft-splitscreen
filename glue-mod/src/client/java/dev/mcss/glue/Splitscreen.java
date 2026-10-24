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
 * MCSS_PAD     controller this client tries first, in ControlifyPin order (default PLAYER-1); any free pad otherwise
 * MCSS_ROLE    host (shares whatever world it opens on LAN, picks the player count) or guest (joins it); default
 *              host for P1
 * MCSS_PORT    LAN port the host publishes on and guests join (default 25599)
 *
 * The clients share state through files in Prism Launcher's data folder (SHARED): the player count the host picked
 * (mcss-control/players, read by AppRun to start and stop clients) and controller claims (mcss-pads/).
 */
public final class Splitscreen {
	public static final int PLAYER = intEnv("MCSS_PLAYER", prismInstancePlayer());
	public static final boolean ENABLED = PLAYER > 0;
	public static final int PAD = intEnv("MCSS_PAD", Math.max(0, PLAYER - 1));
	public static final boolean HOST = "host".equals(env("MCSS_ROLE", PLAYER == 1 ? "host" : "guest"));
	public static final int PORT = intEnv("MCSS_PORT", 25599);
	// game dir is <prism>/instances/P<n>/minecraft
	public static final Path SHARED = FabricLoader.getInstance().getGameDir().toAbsolutePath().getParent().getParent()
			.getParent();
	private static final Path PLAYERS_FILE = SHARED.resolve("mcss-control").resolve("players");

	private Splitscreen() {}

	/** The player count the host picked this session, or 0 before it has picked (AppRun clears it at start). */
	public static int players() {
		try {
			return Integer.parseInt(Files.readString(PLAYERS_FILE, StandardCharsets.UTF_8).trim());
		} catch (IOException | NumberFormatException e) {
			return 0;
		}
	}

	public static void setPlayers(int n) {
		try {
			Files.createDirectories(PLAYERS_FILE.getParent());
			Path tmp = PLAYERS_FILE.resolveSibling("players.tmp");
			Files.writeString(tmp, Integer.toString(n), StandardCharsets.UTF_8);
			Files.move(tmp, PLAYERS_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			SplitscreenGlue.LOG.warn("[mcss] can't write player count: {}", e.toString());
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
