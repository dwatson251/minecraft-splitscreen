package dev.mcss.glue;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.Screenshot;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * README screenshots, for development only: with MCSS_DEV_SHOTS=<folder> set, Player 1 walks through its screens
 * by itself, saves a PNG of each, and quits. It also stands in for "Player 1 has a controller" (Lobby.hostReady),
 * since a dev run usually has no pads. Never active in normal play.
 */
final class DevShots {
	static final String DIR = System.getenv("MCSS_DEV_SHOTS");
	static final boolean ACTIVE = DIR != null && !DIR.isBlank();

	private static int step, wait = 60;
	private static Screen title;
	private static ProfileSelectScreen select;

	private DevShots() {}

	static void tick(Minecraft mc) {
		if (!ACTIVE || --wait > 0) return;
		wait = 40;
		Screen s = mc.gui.screen();
		switch (step) {
			case 0 -> {  // the profile row comes up by itself on the first title screen, once loading has faded out
				if (!(s instanceof ProfileSelectScreen p) || mc.gui.overlay() != null) {
					wait = 20;
					return;
				}
				if (select == null) {
					select = p;
					wait = 60;
					return;
				}
				select = p;
				shot(mc, "profiles");
			}
			case 1 -> {
				CreateProfileScreen c = new CreateProfileScreen(select);
				mc.gui.setScreen(c);
				c.devName("Steve_Fan");
			}
			case 2 -> shot(mc, "create-profile");
			case 3 -> {
				Profiles.activate(Profiles.byName("Alice"));
				title = new TitleScreen();
				mc.gui.setScreen(new SettingsScreen(title));
			}
			case 4 -> shot(mc, "settings");
			case 5 -> {  // what a guest sees: its own profile
				Splitscreen.shown = 2;
				Profiles.activate(Profiles.byName("Kai"));
				mc.gui.setScreen(new WaitingScreen());
			}
			case 6 -> shot(mc, "waiting");
			case 7 -> {
				Splitscreen.shown = 1;
				Profiles.activate(Profiles.byName("Alice"));
				LevelSettings settings = new LevelSettings("Screenshots", GameType.CREATIVE,
						new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
				mc.createWorldOpenFlows().createFreshLevel("Screenshots-" + System.currentTimeMillis(), settings,
						new WorldOptions(2026L, true, false), WorldPresets::createNormalWorldDimensions, title);
			}
			case 8 -> {  // wait for the world, then look at the player from the front
				if (mc.player == null || mc.level == null) {
					wait = 20;
					return;
				}
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.player.setXRot(10);
				wait = 200;  // chunks
			}
			case 9 -> shot(mc, "in-game");
			case 10 -> mc.gui.setScreen(new PauseScreen(true));
			case 11 -> shot(mc, "pause-menu");
			default -> {
				SplitscreenGlue.LOG.info("[mcss] dev screenshots done");
				mc.stop();
				return;
			}
		}
		step++;
	}

	private static void shot(Minecraft mc, String name) {
		Path out = Path.of(DIR, name + ".png");
		Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), (NativeImage img) -> {
			try (img) {
				Files.createDirectories(out.getParent());
				img.writeToFile(out);
				SplitscreenGlue.LOG.info("[mcss] dev screenshot {}", out);
			} catch (IOException e) {
				SplitscreenGlue.LOG.warn("[mcss] dev screenshot {} failed: {}", out, e.toString());
			}
		});
	}
}
