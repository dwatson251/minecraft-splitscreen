package dev.mcss.glue;

import dev.isxander.controlify.screenop.ComponentProcessorProvider;
import dev.isxander.controlify.screenop.ScreenProcessorProvider;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Development only: with MCSS_DEV_SODIUM set, Player 1 opens Sodium's video settings and drives it the way
 * Controlify's D-pad does (focus navigation down, then a slider step), logging ("[mcss] devsodium") the focused
 * option, its value and the list's scroll. Sodium is reached by reflection: the glue doesn't compile against it.
 */
final class DevSodium {
	static final boolean ACTIVE = System.getenv("MCSS_DEV_SODIUM") != null;
	private static int step, wait = 400;

	private DevSodium() {}

	static void tick(Minecraft mc) {
		if (!ACTIVE || mc.gui.overlay() != null || --wait > 0) return;
		wait = 20;
		try {
			Screen s = mc.gui.screen();
			if (step == 0) {
				Class<?> c = Class.forName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen");
				mc.gui.setScreen((Screen) c.getMethod("createScreen", Screen.class).invoke(null, new TitleScreen()));
			} else if (step == 1) {
				SplitscreenGlue.LOG.info("[mcss] devsodium screen {}, Controlify processor {}", s.getClass().getSimpleName(),
						((ScreenProcessorProvider) s).screenProcessor().getClass().getSimpleName());
				log(s, "opened");
				if (s.getFocused() instanceof ContainerEventHandler list) {
					int i = 0;
					for (GuiEventListener c : list.children()) {
						if (i++ > 7) break;
						SplitscreenGlue.LOG.info("[mcss] devsodium child {} {} {} focused={}", i, c.getClass().getSimpleName(),
								c.getRectangle(), c.isFocused());
					}
				}
			} else if (step <= 21) {
				// Controlify's D-pad: ScreenProcessor.handleComponentNavigation -> nextFocusPath(ArrowNavigation) -> changeFocus,
				// reported as arrow-key input (Sodium's widgets only take focus after keyboard-style input)
				mc.setLastInputType(net.minecraft.client.InputType.KEYBOARD_ARROW);
				ComponentPath path = s.nextFocusPath(new FocusNavigationEvent.ArrowNavigation(ScreenDirection.DOWN));
				if (path == null) {
					SplitscreenGlue.LOG.info("[mcss] devsodium down {}: no path", step - 1);
				} else {
					SplitscreenGlue.LOG.info("[mcss] devsodium down {}: path {}", step - 1, describe(path));
					s.clearFocus();
					path.applyFocus(true);
				}
				log(s, "down " + (step - 1));
			} else if (step == 22) {
				GuiEventListener leaf = leaf(s);
				Object p = ComponentProcessorProvider.provide(leaf);
				SplitscreenGlue.LOG.info("[mcss] devsodium focused processor {}", p.getClass().getSimpleName());
				Method inc = findMethod(p.getClass(), "incrementSlider", boolean.class);
				if (inc != null) {
					inc.invoke(p, true);
					log(s, "slider +1");
				}
			} else {
				SplitscreenGlue.LOG.info("[mcss] devsodium done");
				mc.stop();
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			SplitscreenGlue.LOG.warn("[mcss] devsodium step {} failed", step, e);
			mc.stop();
		}
		step++;
	}

	private static void log(Screen s, String when) throws ReflectiveOperationException {
		GuiEventListener leaf = leaf(s);
		String option = "-", value = "-", scroll = "-";
		Method getOption = leaf == null ? null : findMethod(leaf.getClass(), "getOption");
		if (getOption != null) {
			Object o = getOption.invoke(leaf);
			option = o.getClass().getMethod("getName").invoke(o).toString();
			Method v = findMethod(o.getClass(), "getValidatedValue");
			if (v != null) value = String.valueOf(v.invoke(o));
		}
		// the scrolling list: the innermost focused container with getScrollAmount
		GuiEventListener f = s;
		while (f instanceof ContainerEventHandler c && c.getFocused() != null) {
			Method m = findMethod(f.getClass(), "getScrollAmount");
			if (m != null) scroll = String.valueOf(m.invoke(f));
			f = c.getFocused();
		}
		SplitscreenGlue.LOG.info("[mcss] devsodium {}: focused {} option '{}' = {}, scroll {}", when,
				leaf == null ? null : leaf.getClass().getSimpleName(), option, value, scroll);
	}

	private static String describe(ComponentPath path) {
		StringBuilder b = new StringBuilder();
		while (path != null) {
			GuiEventListener c = path.component();
			b.append(c.getClass().getSimpleName()).append(c.getRectangle()).append(" > ");
			path = path instanceof ComponentPath.Path p ? p.childPath() : null;
		}
		return b.toString();
	}

	private static GuiEventListener leaf(Screen s) {
		GuiEventListener f = s.getFocused();
		while (f instanceof ContainerEventHandler c && c.getFocused() != null) f = c.getFocused();
		return f;
	}

	private static Method findMethod(Class<?> c, String name, Class<?>... args) {
		for (; c != null; c = c.getSuperclass()) {
			try {
				Method m = c.getDeclaredMethod(name, args);
				m.setAccessible(true);  // Sodium's control elements are package-private classes
				return m;
			} catch (NoSuchMethodException ignored) {
				// try the superclass
			}
		}
		return null;
	}
}
