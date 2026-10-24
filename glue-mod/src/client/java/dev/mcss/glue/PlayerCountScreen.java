package dev.mcss.glue;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * P1 picks how many players share the screen (1-4). The choice goes to a file AppRun watches; it starts or stops
 * the other clients to match. Plain vanilla buttons, so Controlify's controller navigation works on it.
 *
 * In Desktop Mode one press can arrive twice (Controlify reading the pad, plus Steam's arrow-key emulation), and a
 * stick pushed hard repeats, so focus moves are debounced: one step per DEBOUNCE_MS.
 */
public class PlayerCountScreen extends Screen {
	private static final long DEBOUNCE_MS = 200;

	private final Screen parent;
	private Button preferred;
	private long lastMove;

	public PlayerCountScreen(Screen parent) {
		super(Component.literal("Splitscreen"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int current = Splitscreen.players();
		int y = height / 2 - 30;
		Component question = Component.literal("How many players?");
		int tw = font.width(question);
		addRenderableWidget(new StringWidget((width - tw) / 2, y, tw, 20, question, font));
		int bw = 40, gap = 8, x0 = (width - (4 * bw + 3 * gap)) / 2;
		int want = current == 0 ? 2 : current;
		// the preferred button goes in first: anything that focuses "the first widget" lands on it. Arrow and
		// controller navigation go by position, so the order doesn't show
		preferred = addRenderableWidget(button(want, x0, y + 26, bw, gap));
		for (int n = 1; n <= 4; n++) {
			if (n != want) addRenderableWidget(button(n, x0, y + 26, bw, gap));
		}
		setInitialFocus();
	}

	private Button button(int n, int x0, int y, int bw, int gap) {
		return Button.builder(Component.literal(Integer.toString(n)), btn -> pick(n))
				.bounds(x0 + (n - 1) * (bw + gap), y, bw, 20).build();
	}

	@Override
	protected void changeFocus(ComponentPath path) {
		long now = System.currentTimeMillis();
		if (now - lastMove < DEBOUNCE_MS) return;
		lastMove = now;
		super.changeFocus(path);
	}

	// also what Controlify calls when it (re)enters controller mode: land on the current count, not on "1"
	@Override
	protected void setInitialFocus() {
		if (preferred != null) setInitialFocus(preferred);
		else super.setInitialFocus();
	}

	private void pick(int n) {
		SplitscreenGlue.LOG.info("[mcss] host picked {} players", n);
		Splitscreen.setPlayers(n);
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void onClose() {
		// backing out keeps the current count, or 1 player if none was picked yet, so AppRun isn't left waiting
		if (Splitscreen.players() == 0) Splitscreen.setPlayers(1);
		minecraft.gui.setScreen(parent);
	}
}
