package dev.mcss.glue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Types into another screen's text box (a world's name, a seed, a server address) with the on-screen keyboard.
 * Pressing A on a text box opens this instead of Controlify's keyboard (reported not to open on the Deck); Steam's
 * keyboard can't help either, since its Enter, Space and arrows are dropped (KeyboardHandlerMixin).
 */
public class TypeScreen extends DebouncedScreen {
	private final Screen parent;
	private final EditBox target;
	private EditBox box;
	private OnScreenKeyboard keys;
	private int hintY;

	private final boolean space;

	private TypeScreen(Screen parent, EditBox target, boolean space) {
		super(target.getMessage());
		this.parent = parent;
		this.target = target;
		this.space = space;
	}

	/** Called for the focused element when A is pressed on a screen; true when the keyboard was opened. */
	public static boolean open(GuiEventListener focused) {
		Minecraft mc = Minecraft.getInstance();
		Screen screen = mc.gui.screen();
		// screens with the keyboard built in don't need it; chat keeps Controlify's handling
		if (!Splitscreen.ENABLED || screen == null || screen instanceof CreateProfileScreen || screen instanceof TypeScreen
				|| screen instanceof ChatScreen) return false;
		GuiEventListener f = focused;
		while (!(f instanceof EditBox) && f instanceof ContainerEventHandler c && c.getFocused() != null) f = c.getFocused();
		if (!(f instanceof EditBox target) || !target.active || !target.visible) return false;
		// profile names can't have spaces
		mc.gui.setScreen(new TypeScreen(screen, target, !(screen instanceof EditProfileScreen)));
		return true;
	}

	@Override
	protected void init() {
		int kw = OnScreenKeyboard.WIDTH, x = (width - kw) / 2, y0 = Math.max(4, (height - 170) / 2);
		String typed = box == null ? target.getValue() : box.getValue();
		box = addRenderableWidget(new EditBox(font, x, y0 + 14, kw, 18, title));
		box.setMaxLength(256);
		box.setValue(typed);
		keys = new OnScreenKeyboard(box, space);
		int y = keys.add(b -> addRenderableWidget(b), x, y0 + 38) + 4;
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> done()).bounds(x, y, kw / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
				.bounds(x + kw / 2 + 2, y, kw / 2 - 2, 20).build());
		hintY = y + 26;
		setInitialFocus(keys.first());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.text(font, title.getString(), (width - font.width(title)) / 2, Math.max(4, (height - 170) / 2), 0xFFFFFFFF);
		g.text(font, keys.hint(), (width - font.width(keys.hint())) / 2, hintY, 0xFFA0A0A0);
	}

	private void done() {
		String value = box.getValue();
		target.setValue(value);
		onClose();
		// some screens rebuild their widgets when shown again: fill the new box in the same place
		if (!parent.children().contains(target)) {
			for (GuiEventListener l : parent.children()) {
				if (l instanceof EditBox e && e.getX() == target.getX() && e.getY() == target.getY()) e.setValue(value);
			}
		}
	}

	@Override
	public void tick() {
		keys.tick();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
