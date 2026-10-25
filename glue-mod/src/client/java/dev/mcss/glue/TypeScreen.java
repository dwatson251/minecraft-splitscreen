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

	private TypeScreen(Screen parent, EditBox target) {
		super(target.getMessage());
		this.parent = parent;
		this.target = target;
	}

	/** Called for the focused element when A is pressed on a screen; true when the keyboard was opened. */
	public static boolean open(GuiEventListener focused) {
		Minecraft mc = Minecraft.getInstance();
		Screen screen = mc.gui.screen();
		// our own screens have their keyboard already; chat keeps Controlify's handling
		if (!Splitscreen.ENABLED || screen == null || screen instanceof DebouncedScreen || screen instanceof ChatScreen) return false;
		GuiEventListener f = focused;
		while (!(f instanceof EditBox) && f instanceof ContainerEventHandler c && c.getFocused() != null) f = c.getFocused();
		if (!(f instanceof EditBox target) || !target.active || !target.visible) return false;
		mc.gui.setScreen(new TypeScreen(screen, target));
		return true;
	}

	@Override
	protected void init() {
		int kw = OnScreenKeyboard.WIDTH, x = (width - kw) / 2, y0 = Math.max(4, (height - 170) / 2);
		String typed = box == null ? target.getValue() : box.getValue();
		box = addRenderableWidget(new EditBox(font, x, y0 + 14, kw, 18, title));
		box.setMaxLength(256);
		box.setValue(typed);
		OnScreenKeyboard keys = new OnScreenKeyboard(box, true);
		int y = keys.add(b -> addRenderableWidget(b), x, y0 + 38) + 4;
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
			target.setValue(box.getValue());
			onClose();
		}).bounds(x, y, kw / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
				.bounds(x + kw / 2 + 2, y, kw / 2 - 2, 20).build());
		setInitialFocus(keys.first());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.text(font, title.getString(), (width - font.width(title)) / 2, Math.max(4, (height - 170) / 2), 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
