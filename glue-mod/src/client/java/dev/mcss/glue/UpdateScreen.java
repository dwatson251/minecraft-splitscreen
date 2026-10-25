package dev.mcss.glue;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Player 1's update offer. AppRun looks for a newer GitHub release in the background and leaves it in
 * mcss-control/update-available ("<tag> <url> <size> <sha256>"). Once Player 1 is on the title screen, this shows the
 * installed and the new version: "Update and Restart" asks AppRun to install it (update-request) and shows its
 * progress (update-status: "Downloading v… NN%", "ready", "failed: <why>"); "Continue without update" goes on. A
 * failed update is reported here and the game carries on. After a restart that didn't work, AppRun leaves the
 * reason in update-error, shown here once.
 */
public class UpdateScreen extends DebouncedScreen {
	private enum Mode { OFFER, PROGRESS, FAILED, ERROR }

	private static boolean offered;
	private final Screen parent;
	private final String tag;
	private Mode mode;
	private String message = "";
	private String shown = "";

	private UpdateScreen(Screen parent, Mode mode, String tag, String message) {
		super(Component.literal("Update"));
		this.parent = parent;
		this.mode = mode;
		this.tag = tag;
		this.message = message;
	}

	static UpdateScreen offer(Screen parent, String tag) {
		return new UpdateScreen(parent, Mode.OFFER, tag, "");
	}

	static UpdateScreen error(Screen parent, String message) {
		return new UpdateScreen(parent, Mode.ERROR, "", message);
	}

	/** The installed version: the glue mod's, which every AppImage builds with its own VERSION. */
	static String installed() {
		return FabricLoader.getInstance().getModContainer("splitscreen-glue")
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}

	/** Host, every tick: once Player 1 can answer (title screen, profile, controller), show what AppRun left. */
	static void tick(Minecraft mc) {
		if (offered || !(mc.gui.screen() instanceof TitleScreen title) || Profiles.active() == null || !Lobby.hostReady()) return;
		String error = Splitscreen.read("update-error");
		if (error != null) {
			offered = true;
			Splitscreen.delete("update-error");
			mc.gui.setScreen(new UpdateScreen(title, Mode.ERROR, "", error.trim()));
			return;
		}
		String available = Splitscreen.read("update-available");
		if (available == null || available.isBlank()) return;
		offered = true;
		String tag = available.trim().split("\\s+")[0];
		SplitscreenGlue.LOG.info("[mcss] offering the update to {}", tag);
		mc.gui.setScreen(new UpdateScreen(title, Mode.OFFER, tag, ""));
	}

	@Override
	protected void init() {
		int y = height / 2 - 50;
		String head = switch (mode) {
			case OFFER -> "A new version of Minecraft Splitscreen is available";
			case PROGRESS -> "Updating Minecraft Splitscreen";
			case FAILED -> "The update didn't work";
			case ERROR -> "The update didn't work";
		};
		center(Component.literal(head), y, 0xFFFFFFFF);
		if (mode == Mode.OFFER || mode == Mode.PROGRESS) {
			center(Component.literal("Installed: v" + installed()), y + 20, 0xFFA0A0A0);
			center(Component.literal("New: " + tag), y + 32, 0xFF80FF80);
		}
		if (mode == Mode.OFFER) {
			Button update = addRenderableWidget(Button.builder(Component.literal("Update and Restart"), b -> requestUpdate()).bounds(width / 2 - 100, y + 56, 200, 20).build());
			addRenderableWidget(Button.builder(Component.literal("Continue without update"), b -> onClose())
					.bounds(width / 2 - 100, y + 80, 200, 20).build());
			setInitialFocus(update);
		} else if (mode == Mode.PROGRESS) {
			center(Component.literal(message), y + 56, 0xFFFFFFFF);
		} else {
			center(Component.literal(font.plainSubstrByWidth(message, width - 16)), y + 20, 0xFFFF8080);
			center(Component.literal("You can carry on playing v" + installed() + "."), y + 32, 0xFFA0A0A0);
			Button ok = addRenderableWidget(Button.builder(Component.literal("Continue"), b -> onClose())
					.bounds(width / 2 - 100, y + 56, 200, 20).build());
			setInitialFocus(ok);
		}
	}

	/** "Update and Restart": AppRun installs it and restarts; progress and errors come back in update-status. */
	void requestUpdate() {
		SplitscreenGlue.LOG.info("[mcss] Player 1 chose to update to {}", tag);
		Splitscreen.delete("update-status");
		Splitscreen.write("update-request", tag);
		mode = Mode.PROGRESS;
		message = "Starting the download...";
		rebuildWidgets();
	}

	private void center(Component text, int y, int color) {
		Component coloured = text.copy().withColor(color & 0xFFFFFF);
		int w = font.width(coloured);
		addRenderableWidget(new StringWidget((width - w) / 2, y, w, 12, coloured, font));
	}

	@Override
	public void tick() {
		if (mode != Mode.PROGRESS) return;
		String status = Splitscreen.read("update-status");
		if (status == null) return;
		status = status.trim();
		if (status.equals(shown)) return;
		shown = status;
		if (status.startsWith("failed:")) {
			mode = Mode.FAILED;
			message = "Couldn't update to " + tag + ": " + status.substring("failed:".length()).trim();
			SplitscreenGlue.LOG.info("[mcss] {}", message);
		} else {
			message = status.equals("ready") ? "Restarting..." : status;
		}
		rebuildWidgets();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return mode != Mode.PROGRESS;  // no backing out half way: AppRun is replacing the file
	}

	@Override
	public void onClose() {
		if (mode != Mode.PROGRESS) minecraft.gui.setScreen(parent);
	}
}
