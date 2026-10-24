package dev.mcss.glue;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A screen whose focus moves at most one step per DEBOUNCE_MS. In Desktop Mode one press can arrive twice
 * (Controlify reading the pad, plus Steam's arrow-key emulation), and a stick pushed hard repeats.
 */
abstract class DebouncedScreen extends Screen {
	private static final long DEBOUNCE_MS = 200;
	private long lastMove;

	DebouncedScreen(Component title) {
		super(title);
	}

	@Override
	protected void changeFocus(ComponentPath path) {
		long now = System.currentTimeMillis();
		if (now - lastMove < DEBOUNCE_MS) return;
		lastMove = now;
		super.changeFocus(path);
	}
}
