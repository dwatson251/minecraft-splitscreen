package dev.mcss.glue;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Development only: with MCSS_DEV_UPDATE=<folder>, Player 1 walks through UpdateScreen's states (offer, "Update and
 * Restart", download progress, a failed download, the error after a failed restart), playing AppRun's side by
 * writing update-status itself, and saves a picture of each in the folder. Then it quits.
 */
final class DevUpdate {
	static final String DIR = System.getenv("MCSS_DEV_UPDATE");
	static final boolean ACTIVE = DIR != null && !DIR.isBlank();
	private static int step, wait = 400;
	private static UpdateScreen screen;

	private DevUpdate() {}

	static void tick(Minecraft mc) {
		if (!ACTIVE || mc.gui.overlay() != null || --wait > 0) return;
		wait = 30;
		switch (step++) {
			case 0 -> mc.gui.setScreen(screen = UpdateScreen.offer(new TitleScreen(), "v0.8.1"));
			case 1 -> shot(mc, "update-offer");
			case 2 -> {
				screen.requestUpdate();
				SplitscreenGlue.LOG.info("[mcss] devupdate request file: {}", Splitscreen.read("update-request"));
			}
			case 3 -> Splitscreen.write("update-status", "Downloading v0.8.1... 42%");
			case 4 -> shot(mc, "update-progress");
			case 5 -> Splitscreen.write("update-status", "failed: SHA-256 doesn't match");
			case 6 -> shot(mc, "update-failed");
			case 7 -> mc.gui.setScreen(UpdateScreen.error(new TitleScreen(), "Version v0.8.1 didn't start, so this is v0.8.0 again."));
			case 8 -> shot(mc, "update-error");
			default -> mc.stop();
		}
	}

	private static void shot(Minecraft mc, String name) {
		Path out = Path.of(DIR, name + ".png");
		Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), (NativeImage img) -> {
			try (img) {
				Files.createDirectories(out.getParent());
				img.writeToFile(out);
			} catch (IOException e) {
				SplitscreenGlue.LOG.warn("[mcss] dev update shot failed: {}", e.toString());
			}
		});
	}
}
