package dev.mcss.glue;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;

/**
 * What a guest (Players 2-4) shows instead of the title screen or the "disconnected" screen: it waits for Player
 * 1's world and joins it by itself (SplitscreenGlue.tickGuest). Back (or the controller's back button) returns to
 * profile selection, where Leave closes this view; a guest never lands in the menus.
 */
public class WaitingScreen extends DebouncedScreen {
	private String shown = "";
	private Button back;

	public WaitingScreen() {
		super(Component.literal("Waiting for Player 1's world"));
	}

	private static String state() {
		return Splitscreen.shown + "|" + GuestPad.controllerName();
	}

	@Override
	protected void init() {
		shown = state();
		int y = height / 2 - 40;
		Profiles.Profile p = Profiles.active();
		center(Component.literal("Player " + Splitscreen.shown + (p == null ? "" : ": " + Profiles.displayName(p))), y);
		center(Component.literal("Waiting for Player 1's world..."), y + 16);
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
