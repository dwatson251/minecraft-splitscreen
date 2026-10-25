package dev.mcss.glue;

import dev.isxander.controlify.screenop.ComponentProcessorProvider;
import dev.isxander.controlify.screenop.keyboard.ComponentKeyboardBehaviour;
import dev.isxander.controlify.screenop.keyboard.InputTarget;
import dev.isxander.controlify.screenop.keyboard.KeyboardOverlayScreen;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

/**
 * Development only: with MCSS_DEV_KEYS set, Player 1 opens screens with text boxes, opens Controlify's on-screen
 * keyboard on each box the way pressing A does, types with it (letters, Backspace, Left), closes it, and logs
 * ("[mcss] devkeys") whether the box kept focus and the text. Then it quits. No controller needed: Controlify's keyboard doesn't use one to open.
 */
final class DevKeys {
	static final boolean ACTIVE = System.getenv("MCSS_DEV_KEYS") != null;
	private static final String TYPED = "Hi there";

	private static final List<Consumer<Minecraft>> OPEN = List.of(
			mc -> mc.gui.setScreen(new DirectJoinServerScreen(new TitleScreen(), b -> {}, new ServerData("", "", ServerData.Type.OTHER))),
			mc -> mc.gui.setScreen(new ManageServerScreen(new TitleScreen(), Component.literal("Add Server"), b -> {},
					new ServerData("", "", ServerData.Type.OTHER))),
			mc -> CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen())));

	private static int index, phase, wait = 400;
	private static Screen screen;
	private static EditBox box;
	private static InputTarget target;

	private DevKeys() {}

	static void tick(Minecraft mc) {
		if (!ACTIVE || mc.gui.overlay() != null || --wait > 0) return;
		wait = 40;
		if (index >= OPEN.size()) {
			SplitscreenGlue.LOG.info("[mcss] devkeys done");
			mc.stop();
			return;
		}
		switch (phase++) {
			case 0 -> OPEN.get(index).accept(mc);
			case 1 -> {
				screen = mc.gui.screen();
				box = find(screen);
				if (box == null) {
					SplitscreenGlue.LOG.info("[mcss] devkeys {}: no text box", name());
					next();
					return;
				}
				target = openKeyboard(mc, screen, box);
				if (target == null) {
					next();
					return;
				}
				log(mc, "keyboard open");
			}
			case 2 -> {
				log(mc, "before typing");
				for (char c : TYPED.toCharArray()) target.acceptChar(c, 0);
				log(mc, "typed");
				// Backspace and Left as Controlify's keyboard sends them (KeyWidget: scancode, 0, 0)
				target.acceptKeyCode(42, 0, 0);
				log(mc, "after Backspace");
				target.acceptKeyCode(80, 0, 0);
				target.acceptChar('X', 0);
				log(mc, "after Left, X");
			}
			case 3 -> {
				log(mc, "a second later");
				mc.gui.screen().onClose();
			}
			case 4 -> {
				EditBox now = find(mc.gui.screen());
				SplitscreenGlue.LOG.info("[mcss] devkeys {}: after closing the keyboard: screen {}, same box {}, value '{}'",
						name(), mc.gui.screen() == null ? null : mc.gui.screen().getClass().getSimpleName(), now == box,
						now == null ? null : now.getValue());
				next();
			}
			default -> next();
		}
	}

	/**
	 * Opens Controlify's on-screen keyboard on a screen's text box, as pressing A on it does
	 * (ScreenProcessor.tryOpenKeyboard); returns what it types into, or null. DevShots uses it too.
	 */
	static InputTarget openKeyboard(Minecraft mc, Screen screen, EditBox box) {
		screen.setFocused(box);
		var behaviour = ComponentProcessorProvider.provide(box).getKeyboardBehaviour(null, null);
		if (!(behaviour instanceof ComponentKeyboardBehaviour.Handled h)) {
			SplitscreenGlue.LOG.info("[mcss] devkeys {}: keyboard behaviour {}", screen.getClass().getSimpleName(), behaviour);
			return null;
		}
		mc.gui.setScreen(new KeyboardOverlayScreen(screen, h.layout(), h.inputTarget(), h.positioner()));
		return h.inputTarget();
	}

	private static String name() {
		return screen == null ? "#" + index : screen.getClass().getSimpleName();
	}

	private static void log(Minecraft mc, String when) {
		SplitscreenGlue.LOG.info("[mcss] devkeys {}: {}: screen {}, box focused {}, parent focus {}, value '{}'", name(), when,
				mc.gui.screen() == null ? null : mc.gui.screen().getClass().getSimpleName(), box.isFocused(),
				screen.getFocused() == null ? null : screen.getFocused().getClass().getSimpleName(), box.getValue());
	}

	private static void next() {
		index++;
		phase = 0;
		screen = null;
		box = null;
		target = null;
	}

	static EditBox find(GuiEventListener l) {
		if (l instanceof EditBox e) return e;
		if (l instanceof ContainerEventHandler c) {
			for (GuiEventListener child : c.children()) {
				EditBox e = find(child);
				if (e != null) return e;
			}
		}
		return null;
	}
}
