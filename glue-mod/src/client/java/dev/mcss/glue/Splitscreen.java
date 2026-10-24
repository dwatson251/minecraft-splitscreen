package dev.mcss.glue;

/**
 * This client's place in the splitscreen session, read once from the environment.
 * MCSS_PLAYER  1..4; unset = taken from Prism Launcher's INST_ID when that is P1..P4 (the instances mcss-setup.sh
 *              creates), otherwise 0 = glue disabled (the client behaves like vanilla)
 * MCSS_PAD     controller this client tries first, in ControlifyPin order (default PLAYER-1); any free pad otherwise
 * MCSS_ROLE    host (opens the world and shares it on LAN) or guest (joins it); default host for P1
 * MCSS_PORT    LAN port the host publishes on and guests join (default 25599)
 * MCSS_WORLD   save folder the host opens or creates (default "Splitscreen")
 */
public final class Splitscreen {
	public static final int PLAYER = intEnv("MCSS_PLAYER", prismInstancePlayer());
	public static final boolean ENABLED = PLAYER > 0;
	public static final int PAD = intEnv("MCSS_PAD", Math.max(0, PLAYER - 1));
	public static final boolean HOST = "host".equals(env("MCSS_ROLE", PLAYER == 1 ? "host" : "guest"));
	public static final int PORT = intEnv("MCSS_PORT", 25599);
	public static final String WORLD = env("MCSS_WORLD", "Splitscreen");

	private Splitscreen() {}

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
