package dev.mcss.glue;

import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The host's "Press START to play" screen: who has joined, with which controller. Joining itself happens in Lobby
 * (START on any free pad, here or later in game); this screen only shows it, and Player 1 continues to the title
 * screen with A on Continue. Until Player 1's controller is known it can't be left (no Esc, no back button), so
 * the game never reaches the menus without knowing whose pad drives the host's view.
 */
public class LobbyScreen extends DebouncedScreen {
	private final Screen parent;
	private String shown = "";
	private Button cont;

	public LobbyScreen(Screen parent) {
		super(Component.literal("Splitscreen"));
		this.parent = parent;
	}

	private static String state() {
		StringBuilder b = new StringBuilder();
		for (Map.Entry<Integer, Lobby.Slot> e : Lobby.ordered()) {
			b.append(e.getKey()).append(e.getValue().pad == null ? "-" : e.getValue().padName).append(';');
		}
		return b.toString();
	}

	@Override
	protected void init() {
		shown = state();
		int y = height / 2 - 70;
		center(Component.literal("Press START to play"), y);
		List<Map.Entry<Integer, Lobby.Slot>> players = Lobby.ordered();
		for (int n = 1; n <= 4; n++) {
			Lobby.Slot s = n <= players.size() ? players.get(n - 1).getValue() : null;
			String line = s == null ? "Player " + n + ":  press START"
					: s.pad == null ? "Player " + n + ":  controller disconnected, press START"
					: "Player " + n + ":  " + s.padName;
			center(Component.literal(line), y + 24 + (n - 1) * 14);
		}
		boolean ready = Lobby.hostReady();
		center(Component.literal(ready ? "More players can press START at any time, also later in game"
				: "The first controller to press START is Player 1"), y + 88);
		cont = addRenderableWidget(Button.builder(Component.literal("Continue"), b -> onClose())
				.bounds(width / 2 - 50, y + 108, 100, 20).build());
		cont.active = ready;
		setInitialFocus();
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
	protected void setInitialFocus() {
		if (cont != null && cont.active) setInitialFocus(cont);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return Lobby.hostReady();
	}

	@Override
	public void onClose() {
		if (Lobby.hostReady()) minecraft.gui.setScreen(parent);
	}
}
