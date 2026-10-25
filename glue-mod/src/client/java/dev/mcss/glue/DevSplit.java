package dev.mcss.glue;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * README split-screen shots, for development only (scripts/dev-split.py): with MCSS_DEV_SPLIT=<folder> and
 * MCSS_DEV_PLAYERS=<n>, client P<k> plays as a fixed profile; Player 1 creates a world, waits for everyone, gathers
 * them on a small cleared patch facing each other at noon, and then every client saves its own view as P<k>.png and
 * quits. The script composes the views in the tiler's layout. Never active in normal play.
 */
final class DevSplit {
	static final String DIR = System.getenv("MCSS_DEV_SPLIT");
	static final boolean ACTIVE = DIR != null && !DIR.isBlank();
	private static final int PLAYERS = Integer.parseInt(System.getenv().getOrDefault("MCSS_DEV_PLAYERS", "2"));
	private static final List<String> NAMES = List.of("Alice", "Bob", "Kai", "Zuri");

	private static boolean worldAsked, gathered, shot;
	private static int ticks, waitTicks;

	private DevSplit() {}

	static void start() {
		if (ACTIVE) Profiles.activate(Profiles.byName(NAMES.get(Splitscreen.PLAYER - 1)));
	}

	static void tick(Minecraft mc) {
		if (!ACTIVE) return;
		ticks++;
		// the first title screen can come up before start() picked the profile: move past the profile row
		if (mc.gui.screen() instanceof ProfileSelectScreen && Profiles.active() != null) {
			mc.gui.setScreen(Splitscreen.HOST ? new TitleScreen() : new WaitingScreen());
		}
		if (Splitscreen.HOST) host(mc);
		if (!shot && Splitscreen.read("shoot") != null && mc.player != null && --waitTicks < -20) {
			shot = true;
			Path out = Path.of(DIR, "P" + Splitscreen.PLAYER + ".png");
			Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), (NativeImage img) -> {
				try (img) {
					Files.createDirectories(out.getParent());
					img.writeToFile(out);
				} catch (IOException e) {
					SplitscreenGlue.LOG.warn("[mcss] dev split shot failed: {}", e.toString());
				}
				Splitscreen.write("shot-P" + Splitscreen.PLAYER, "1");
				SplitscreenGlue.LOG.info("[mcss] dev split shot {}", out);
			});
		}
		if (shot && Splitscreen.read("shot-P" + Splitscreen.PLAYER) != null) {
			// guests leave at once; the host waits for everyone's picture, then closes the world
			boolean all = true;
			for (int n = 1; n <= PLAYERS; n++) all &= Splitscreen.read("shot-P" + n) != null;
			if (!Splitscreen.HOST || all) mc.stop();
		}
	}

	private static void host(Minecraft mc) {
		if (!worldAsked && mc.gui.screen() instanceof TitleScreen title) {
			worldAsked = true;
			LevelSettings settings = new LevelSettings("Splitscreen", GameType.CREATIVE,
					new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
			mc.createWorldOpenFlows().createFreshLevel("DevSplit-" + System.currentTimeMillis(), settings,
					new WorldOptions(2026L, false, false), WorldPresets::createNormalWorldDimensions, title);
		}
		IntegratedServer server = mc.getSingleplayerServer();
		if (gathered || server == null || server.getPlayerCount() < PLAYERS || ticks % 20 != 0) return;
		gathered = true;
		server.execute(() -> {
			ServerPlayer host = server.getPlayerList().getPlayers().stream()
					.filter(p -> p.getGameProfile().name().equals(NAMES.get(0))).findFirst().orElse(null);
			if (host == null) return;
			// ground level (ignoring leaves) at the host's spot; clear a patch, trees included, and stand everyone
			// on it 3.5 blocks from the middle, eyes level with each other
			int x = host.getBlockX(), z = host.getBlockZ();
			int y = server.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			var src = server.createCommandSourceStack().withSuppressedOutput();
			var cmd = server.getCommands();
			cmd.performPrefixedCommand(src, "time set noon");
			cmd.performPrefixedCommand(src, "weather clear");
			cmd.performPrefixedCommand(src, "clear @a");
			cmd.performPrefixedCommand(src, String.format("fill %d %d %d %d %d %d minecraft:grass_block", x - 8, y - 1, z - 8, x + 8, y - 1, z + 8));
			cmd.performPrefixedCommand(src, String.format("fill %d %d %d %d %d %d minecraft:air", x - 8, y, z - 8, x + 8, y + 12, z + 8));
			for (int i = 0; i < PLAYERS; i++) {
				double a = Math.PI * 2 * i / PLAYERS + Math.PI / 4;
				double px = x + 0.5 + Math.cos(a) * 3.5, pz = z + 0.5 + Math.sin(a) * 3.5;
				cmd.performPrefixedCommand(src, String.format(java.util.Locale.ROOT, "tp %s %.2f %d %.2f facing %.2f %.2f %.2f",
						NAMES.get(i), px, y, pz, x + 0.5, y + 1.2, z + 0.5));
			}
		});
		// chunks render and the "joined" chat lines fade (10 s) before the picture
		waitTicks = 0;
		new Thread(() -> {
			try {
				Thread.sleep(13_000);
			} catch (InterruptedException ignored) {
			}
			// empty hands for the picture (items can arrive after the first clear), then everyone shoots
			server.execute(() -> server.getCommands().performPrefixedCommand(
					server.createCommandSourceStack().withSuppressedOutput(), "clear @a"));
			try {
				Thread.sleep(1500);
			} catch (InterruptedException ignored) {
			}
			Splitscreen.write("shoot", "1");
		}, "mcss-devsplit").start();
	}
}
