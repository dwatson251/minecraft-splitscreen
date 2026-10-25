package dev.mcss.glue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;

/**
 * Pad navigation fixes for Sodium's video settings (Controlify's Sodium support, which we load ourselves: see
 * fabric.mod.json).
 * <ul>
 * <li>Controlify focuses the first option straight on the screen, although it lives in the scrolling list, so arrow
 * navigation always restarted from Render Distance. When a screen's focus isn't its own child, this routes it
 * through the container that holds it.</li>
 * <li>Sodium's list doesn't scroll to the focused option, so navigation walked off the bottom. This scrolls the list
 * (its ScrollbarWidget, by reflection: the glue doesn't compile against Sodium) to keep the option in view.</li>
 * </ul>
 */
final class FocusFix {
	private static Class<?> scrollable;
	private static Field scrollbar;
	private static Method scroll;
	private static boolean sodiumMissing;

	private FocusFix() {}

	static void tick(Minecraft mc) {
		Screen s = mc.gui.screen();
		if (s == null) return;
		GuiEventListener focused = s.getFocused();
		if (focused == null) return;
		if (!s.children().contains(focused)) {
			for (GuiEventListener child : s.children()) {
				if (child instanceof ContainerEventHandler c && c.children().contains(focused)) {
					s.setFocused(child);
					c.setFocused(focused);
					return;
				}
			}
			return;
		}
		if (focused instanceof ContainerEventHandler list && list.getFocused() != null) keepInView(list, list.getFocused());
	}

	private static void keepInView(ContainerEventHandler list, GuiEventListener option) {
		if (!isSodiumScrollable(list)) return;
		ScreenRectangle view = list.getRectangle(), r = option.getRectangle();
		int by = r.bottom() > view.bottom() ? r.bottom() - view.bottom() : r.top() < view.top() ? r.top() - view.top() : 0;
		if (by == 0) return;
		try {
			scroll.invoke(scrollbar.get(list), by);
		} catch (ReflectiveOperationException e) {
			SplitscreenGlue.LOG.warn("[mcss] can't scroll Sodium's list: {}", e.toString());
			sodiumMissing = true;
		}
	}

	private static boolean isSodiumScrollable(Object list) {
		if (sodiumMissing) return false;
		try {
			if (scrollable == null) {
				scrollable = Class.forName("net.caffeinemc.mods.sodium.client.gui.options.control.AbstractScrollable");
				scrollbar = scrollable.getDeclaredField("scrollbar");
				scrollbar.setAccessible(true);
				scroll = scrollbar.getType().getMethod("scroll", int.class);
			}
		} catch (ReflectiveOperationException e) {
			sodiumMissing = true;  // no Sodium (or it changed): nothing to fix
			return false;
		}
		return scrollable.isInstance(list);
	}
}
