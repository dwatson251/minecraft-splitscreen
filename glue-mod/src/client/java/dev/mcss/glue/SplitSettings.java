package dev.mcss.glue;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Player 1's splitscreen settings, kept in the host instance's config folder so they last between sessions. The
 * layout reaches the KWin tiler through Player 1's window title ("P1v ..." = vertical); "updates=off" is read by
 * AppRun, which skips its self-update at launch.
 */
final class SplitSettings {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("splitscreen.properties");
	private static final Properties PROPS = load();
	private static boolean vertical = "vertical".equals(PROPS.getProperty("layout"));
	private static boolean updates = !"off".equals(PROPS.getProperty("updates"));

	private SplitSettings() {}

	/** Vertical: side by side (2 players), Player 1 down the left (3). Horizontal: stacked, Player 1 on top. */
	static boolean vertical() {
		return vertical;
	}

	static void setVertical(boolean v) {
		vertical = v;
		save();
	}

	/** Look for a newer version on GitHub at launch (AppRun) and offer it (UpdateScreen). */
	static boolean updates() {
		return updates;
	}

	static void setUpdates(boolean on) {
		updates = on;
		save();
	}

	private static void save() {
		// one key per line, no comment: AppRun greps for "updates=off"
		Properties p = new Properties();
		p.setProperty("layout", vertical ? "vertical" : "horizontal");
		p.setProperty("updates", updates ? "on" : "off");
		try (Writer w = Files.newBufferedWriter(FILE)) {
			for (String key : p.stringPropertyNames()) w.write(key + "=" + p.getProperty(key) + "\n");
		} catch (IOException e) {
			SplitscreenGlue.LOG.warn("[mcss] can't save {}: {}", FILE, e.toString());
		}
	}

	private static Properties load() {
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(FILE)) {
			p.load(r);
		} catch (IOException e) {
			// no settings yet: defaults
		}
		return p;
	}
}
