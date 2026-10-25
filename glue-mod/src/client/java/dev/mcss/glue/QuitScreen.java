package dev.mcss.glue;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Player 1's "Quit?": quitting ends the game for every player. No is selected, and for the first ARM_MS nothing
 * responds, so a double press (or one press arriving twice) on Quit Game can't fall through to Yes unseen.
 */
public class QuitScreen extends DebouncedScreen {
	private static final long ARM_MS = 700;
	private final Screen parent;
	private final long opened = System.currentTimeMillis();
	private Button yes;

	public QuitScreen(Screen parent) {
		super(Component.literal("Quit?"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int y = height / 2 - 30;
		center(title, y);
		center(Component.literal("This ends the game for every player."), y + 14);
		yes = addRenderableWidget(Button.builder(Component.literal("Yes, quit"), b -> {
			if (System.currentTimeMillis() - opened < ARM_MS) return;
			SplitscreenGlue.LOG.info("[mcss] Player 1 confirmed Quit Game");
			minecraft.stop();
		}).bounds(width / 2 - 104, y + 40, 100, 20).build());
		Button no = addRenderableWidget(Button.builder(Component.literal("No"), b -> onClose())
				.bounds(width / 2 + 4, y + 40, 100, 20).build());
		setInitialFocus(no);
	}

	private void center(Component text, int y) {
		int w = font.width(text);
		addRenderableWidget(new StringWidget((width - w) / 2, y, w, 12, text, font));
	}

	@Override
	public void tick() {
		if (yes != null) yes.active = System.currentTimeMillis() - opened >= ARM_MS;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
