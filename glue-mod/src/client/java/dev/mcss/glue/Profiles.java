package dev.mcss.glue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Player profiles: a name and a skin, shared by every view (files in <prism>/mcss-profiles, kept between sessions).
 * The chosen profile becomes this client's identity (UserMixin): its name is what the LAN world sees, so each world
 * keeps each profile's inventory, and it's the name tag. Worlds save player data under the name's offline UUID, so
 * a rename moves those files in Player 1's worlds along with it. Player 1 can also play as its Microsoft account
 * (ACCOUNT).
 *
 * A profile in use is locked for this session (mcss-control/profile-<name>, refreshed every second; stale after
 * LOCK_STALE_MS), so two views can't pick the same one.
 */
public final class Profiles {
	public record Profile(String name, String skin, boolean slim) {}

	/** Player 1's "play as my Microsoft account" card. */
	public static final Profile ACCOUNT = new Profile("", "", false);

	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final long LOCK_STALE_MS = 10_000;
	private static final Path DIR = Splitscreen.SHARED.resolve("mcss-profiles");
	private static final Gson GSON = new Gson();

	private static List<Profile> cache = List.of();
	private static long cacheStamp = -1;
	private static volatile Profile active;
	/** The launcher account's name, captured by UserMixin before any profile is applied. */
	private static volatile String accountName = "";
	private static int ticks;

	private Profiles() {}

	private static volatile com.mojang.authlib.GameProfile accountProfile;

	public static void noteAccount(String name) {
		if (name != null && !name.isEmpty()) accountName = name;
	}

	public static void noteAccountProfile(com.mojang.authlib.GameProfile profile) {
		if (profile != null) accountProfile = profile;
	}

	/** The Microsoft account's own game profile (for its skin on the account card). */
	public static com.mojang.authlib.GameProfile accountProfile() {
		return accountProfile;
	}

	public static Profile active() {
		return active;
	}

	/** The name this client plays under: the profile's, or null while none is chosen / playing as the account. */
	public static String overrideName() {
		Profile p = active;
		return p == null || p == ACCOUNT ? null : p.name();
	}

	public static String displayName(Profile p) {
		return p == ACCOUNT ? accountName : p.name();
	}

	/** All saved profiles, by name. Re-read when the folder changes (another view may have created one). */
	public static synchronized List<Profile> all() {
		long stamp = stamp();
		if (stamp != cacheStamp) {
			cacheStamp = stamp;
			List<Profile> out = new ArrayList<>();
			try (DirectoryStream<Path> files = Files.newDirectoryStream(DIR, "*.json")) {
				for (Path f : files) {
					try {
						JsonObject o = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
						String name = o.get("name").getAsString();
						if (NAME.matcher(name).matches()) {
							out.add(new Profile(name, o.has("skin") ? o.get("skin").getAsString() : Skins.DEFAULT,
									o.has("model") && o.get("model").getAsString().equals("slim")));
						}
					} catch (RuntimeException | IOException e) {
						SplitscreenGlue.LOG.warn("[mcss] bad profile {}: {}", f.getFileName(), e.toString());
					}
				}
			} catch (IOException ignored) {
				// no profiles yet
			}
			out.sort(Comparator.comparing(p -> p.name().toLowerCase(Locale.ROOT)));
			cache = List.copyOf(out);
		}
		return cache;
	}

	public static Profile byName(String name) {
		if (name == null) return null;
		for (Profile p : all()) {
			if (p.name().equalsIgnoreCase(name)) return p;
		}
		return null;
	}

	/** Why a new profile name can't be used, or null if it can. */
	public static String nameProblem(String name) {
		return nameProblem(name, null);
	}

	/** As above, for renaming `self` (which may keep its name in another case). */
	public static String nameProblem(String name, Profile self) {
		if (name.length() < 3) return "At least 3 letters";
		if (!NAME.matcher(name).matches()) return "Letters, numbers and _ only";
		Profile taken = byName(name);
		if ((taken != null && (self == null || !taken.name().equals(self.name()))) || name.equalsIgnoreCase(accountName)) {
			return "That name is taken";
		}
		return null;
	}

	/** Player data folders inside a world, current (26.x) and older layouts. */
	private static final String[] PLAYER_DIRS = {"players/data", "players/stats", "players/advancements", "playerdata",
			"stats", "advancements"};
	private static final String[] PLAYER_EXTS = {".dat", ".dat_old", ".json"};

	/**
	 * Renames a profile and moves its player data in every one of Player 1's worlds to the new name's UUID, so
	 * inventories, stats and advancements follow. Not while a world is open (the server would write the old file
	 * back) or while another view plays as it. Returns why it can't, or null when done.
	 */
	public static synchronized String rename(Profile p, String newName) {
		if (p.name().equals(newName)) return null;
		String why = nameProblem(newName, p);
		if (why != null) return why;
		if (inUseElsewhere(p)) return "Another player is using this profile";
		String world = Splitscreen.read("world");
		if (world != null && world.trim().equals("open")) return "Not while a world is open";

		String from = net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(p.name()).toString();
		String to = net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(newName).toString();
		int moved = 0;
		try (DirectoryStream<Path> worlds = Files.newDirectoryStream(Splitscreen.SHARED.resolve("instances/P1/minecraft/saves"))) {
			for (Path w : worlds) {
				for (String dir : PLAYER_DIRS) {
					for (String ext : PLAYER_EXTS) {
						Path src = w.resolve(dir).resolve(from + ext);
						if (!Files.isRegularFile(src)) continue;
						Path dst = w.resolve(dir).resolve(to + ext);
						// a deleted profile that had this name may have left data: keep it beside, never overwrite
						if (Files.exists(dst)) Files.move(dst, dst.resolveSibling(to + ext + ".before-rename-" + System.currentTimeMillis()));
						Files.move(src, dst);
						moved++;
					}
				}
			}
		} catch (IOException e) {
			SplitscreenGlue.LOG.warn("[mcss] rename {} -> {}: moving world data failed: {}", p.name(), newName, e.toString());
			return "Couldn't move the world data";
		}

		boolean mine = active != null && active != ACCOUNT && active.name().equals(p.name());
		if (mine) release();
		Profile renamed = new Profile(newName, p.skin(), p.slim());
		save(renamed);
		delete(p);
		if (mine) activate(renamed);
		SplitscreenGlue.LOG.info("[mcss] renamed profile {} -> {} ({} world files moved)", p.name(), newName, moved);
		return null;
	}

	public static void save(Profile p) {
		JsonObject o = new JsonObject();
		o.addProperty("name", p.name());
		o.addProperty("skin", p.skin());
		o.addProperty("model", p.slim() ? "slim" : "wide");
		try {
			Files.createDirectories(DIR);
			Path tmp = DIR.resolve(p.name() + ".json.tmp-P" + Splitscreen.PLAYER);
			Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
			Files.move(tmp, DIR.resolve(p.name() + ".json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
					java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			SplitscreenGlue.LOG.info("[mcss] saved profile {} ({}, {})", p.name(), p.skin(), p.slim() ? "slim" : "wide");
		} catch (IOException e) {
			SplitscreenGlue.LOG.warn("[mcss] can't save profile {}: {}", p.name(), e.toString());
		}
		if (active != null && active != ACCOUNT && active.name().equals(p.name())) active = p;
		cacheStamp = -1;
	}

	public static void delete(Profile p) {
		try {
			Files.deleteIfExists(DIR.resolve(p.name() + ".json"));
			SplitscreenGlue.LOG.info("[mcss] deleted profile {}", p.name());
		} catch (IOException e) {
			SplitscreenGlue.LOG.warn("[mcss] can't delete profile {}: {}", p.name(), e.toString());
		}
		cacheStamp = -1;
	}

	/** True when another running view has this profile (by name). */
	public static boolean inUseElsewhere(Profile p) {
		String lock = lockName(displayName(p));
		String owner = Splitscreen.read(lock);
		if (owner == null || owner.trim().equals("P" + Splitscreen.PLAYER)) return false;
		return System.currentTimeMillis() - Splitscreen.modified(lock) < LOCK_STALE_MS;
	}

	/** Plays as this profile from now on (outside a world only). False if another view took it. */
	public static boolean activate(Profile p) {
		if (inUseElsewhere(p)) return false;
		release();
		Splitscreen.write(lockName(displayName(p)), "P" + Splitscreen.PLAYER);
		active = p;
		SplitscreenGlue.LOG.info("[mcss] P{} plays as {}", Splitscreen.PLAYER, p == ACCOUNT ? "the Microsoft account "
				+ accountName : "profile " + p.name());
		return true;
	}

	/** Stops playing as any profile (a guest going back from the waiting screen), freeing its lock. */
	public static void deactivate() {
		release();
		active = null;
		SplitscreenGlue.LOG.info("[mcss] P{} has no profile again", Splitscreen.PLAYER);
	}

	private static void release() {
		Profile p = active;
		if (p == null) return;
		String lock = lockName(displayName(p));
		String owner = Splitscreen.read(lock);
		if (owner != null && owner.trim().equals("P" + Splitscreen.PLAYER)) Splitscreen.delete(lock);
	}

	/** Keeps this view's lock fresh. */
	static void tick() {
		if (ticks++ % 20 != 0 || active == null) return;
		Splitscreen.write(lockName(displayName(active)), "P" + Splitscreen.PLAYER);
	}

	private static String lockName(String name) {
		return "profile-" + name.toLowerCase(Locale.ROOT);
	}

	private static long stamp() {
		try {
			long s = Files.getLastModifiedTime(DIR).toMillis();
			try (DirectoryStream<Path> files = Files.newDirectoryStream(DIR, "*.json")) {
				for (Path f : files) s = 31 * s + Files.getLastModifiedTime(f).toMillis();
			}
			return s;
		} catch (IOException e) {
			return 0;
		}
	}
}
