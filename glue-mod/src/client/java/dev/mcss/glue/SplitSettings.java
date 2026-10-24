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
 * layout reaches the KWin tiler through Player 1's window title ("P1v ..." = vertical).
 */
final class SplitSettings {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("splitscreen.properties");
	private static boolean vertical = load();

	private SplitSettings() {}

	/** Vertical: side by side (2 players), Player 1 down the left (3). Horizontal: stacked, Player 1 on top. */
	static boolean vertical() {
		return vertical;
	}

	static void setVertical(boolean v) {
		vertical = v;
		Properties p = new Properties();
		p.setProperty("layout", v ? "vertical" : "horizontal");
		try (Writer w = Files.newBufferedWriter(FILE)) {
			p.store(w, "Splitscreen settings (Player 1)");
		} catch (IOException e) {
			SplitscreenGlue.LOG.warn("[mcss] can't save {}: {}", FILE, e.toString());
		}
	}

	private static boolean load() {
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(FILE)) {
			p.load(r);
		} catch (IOException e) {
			return false;
		}
		return "vertical".equals(p.getProperty("layout"));
	}
}
