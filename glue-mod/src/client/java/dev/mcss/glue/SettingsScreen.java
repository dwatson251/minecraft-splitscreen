package dev.mcss.glue;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** "Splitscreen settings", Player 1 only: screen layout, and the players list (the START lobby). */
public class SettingsScreen extends DebouncedScreen {
	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Splitscreen settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int y = height / 2 - 50;
		int tw = font.width(title);
		addRenderableWidget(new StringWidget((width - tw) / 2, y, tw, 12, title, font));
		Button layout = addRenderableWidget(Button.builder(layoutLabel(), b -> {
			SplitSettings.setVertical(!SplitSettings.vertical());
			b.setMessage(layoutLabel());
			SplitscreenGlue.LOG.info("[mcss] layout: {}", SplitSettings.vertical() ? "vertical" : "horizontal");
			minecraft.updateTitle();  // the tiler reads the layout from Player 1's title and re-tiles at once
		}).bounds(width / 2 - 100, y + 24, 200, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Players (" + Lobby.SLOTS.size() + ")"),
				b -> minecraft.gui.setScreen(new LobbyScreen(this))).bounds(width / 2 - 100, y + 48, 200, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(width / 2 - 100, y + 84, 200, 20).build());
		setInitialFocus(layout);
	}

	private static Component layoutLabel() {
		return Component.literal("Layout: " + (SplitSettings.vertical() ? "Vertical (side by side)" : "Horizontal (stacked)"));
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
