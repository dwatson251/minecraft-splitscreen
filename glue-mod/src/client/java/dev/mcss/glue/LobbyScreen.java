package dev.mcss.glue;

import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The "Players" list in Splitscreen settings: who has joined, with which controller. Joining itself happens in
 * Lobby (START on any free pad, at any time); this screen only shows it.
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
		center(Component.literal("Players"), y);
		List<Map.Entry<Integer, Lobby.Slot>> players = Lobby.ordered();
		for (int n = 1; n <= 4; n++) {
			Lobby.Slot s = n <= players.size() ? players.get(n - 1).getValue() : null;
			String line = s == null ? "Player " + n + ":  press START"
					: s.pad == null ? "Player " + n + ":  controller disconnected, press START"
					: "Player " + n + ":  " + s.padName;
			center(Component.literal(line), y + 24 + (n - 1) * 14);
		}
		boolean ready = Lobby.hostReady();
		center(Component.literal(ready ? "More players can press START on a controller at any time"
				: "Press any button on Player 1's controller"), y + 88);
		cont = addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
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
