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
 * keeps each profile's inventory, and it's the name tag. Names are fixed once created, because worlds save player
 * data under the name. Player 1 can also play as its Microsoft account (ACCOUNT).
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
		if (name.length() < 3) return "At least 3 letters";
		if (!NAME.matcher(name).matches()) return "Letters, numbers and _ only";
		if (byName(name) != null || name.equalsIgnoreCase(accountName)) return "That name is taken";
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
