package dev.mcss.glue;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;

/**
 * What a guest (Players 2-4) shows instead of the title screen or the "disconnected" screen: it waits for Player
 * 1's world (or server) and joins it by itself (SplitscreenGlue.tickGuest, ServerFollow); if a server refused this
 * player, it says why. Back (or the controller's back button) returns to
 * profile selection, where Leave closes this view; a guest never lands in the menus.
 */
public class WaitingScreen extends DebouncedScreen {
	private String shown = "";
	private Button back;

	public WaitingScreen() {
		super(Component.literal("Waiting for Player 1's world"));
	}

	private static String state() {
		Component refusal = ServerFollow.refusal();
		return Splitscreen.shown + "|" + GuestPad.controllerName() + "|" + ServerFollow.hostServer() + "|"
				+ (refusal == null ? "" : refusal.getString());
	}

	@Override
	protected void init() {
		shown = state();
		int y = height / 2 - 40;
		Profiles.Profile p = Profiles.active();
		center(Component.literal("Player " + Splitscreen.shown + (p == null ? "" : ": " + Profiles.displayName(p))), y);
		String server = ServerFollow.hostServer();
		Component refusal = ServerFollow.refusal();
		if (refusal != null) {
			// guests play offline profiles: only servers with online-mode=false take them
			y -= 24;
			center(Component.literal(server + " didn't let this player in:"), y + 16);
			center(Component.literal(font.plainSubstrByWidth(refusal.getString(), width - 16)), y + 28);
			center(Component.literal("Guests need a server with online-mode=false."), y + 40);
			y += 24;
		} else {
			center(Component.literal(server == null ? "Waiting for Player 1's world..." : "Joining Player 1 on " + server + "..."), y + 16);
		}
		String pad = GuestPad.controllerName();
		center(Component.literal(pad == null ? "Press any button on your controller" : "Controller: " + pad), y + 36);
		back = addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
				.bounds(width / 2 - 50, y + 60, 100, 20).build());
		setInitialFocus(back);
	}

	private void center(Component text, int y) {
		int w = font.width(text);
		addRenderableWidget(new StringWidget((width - w) / 2, y, w, 12, text, font));
	}

	@Override
	public void tick() {
		if (!state().equals(shown)) rebuildWidgets();
	}

	@Override
	public void onClose() {
		Profiles.deactivate();
		minecraft.updateTitle();
		minecraft.gui.setScreen(new ProfileSelectScreen(null));
	}
}
