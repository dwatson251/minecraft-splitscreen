package dev.mcss.glue;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.input.ControllerStateView;
import dev.isxander.controlify.controller.input.GamepadInputs;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Button presses and releases of every connected pad, polled once per client tick. Controlify keeps polling pads
 * that aren't its current controller, so this sees all of them. Pad identities (ControllerEntity, uid) are only
 * meaningful inside this process: the same physical pad can get a different uid and GUID in another client.
 */
final class PadEdges {
	record Edge(ControllerEntity pad, String button, boolean down, long time) {}

	static final List<Identifier> BUTTONS = List.of(GamepadInputs.SOUTH_BUTTON, GamepadInputs.EAST_BUTTON,
			GamepadInputs.WEST_BUTTON, GamepadInputs.NORTH_BUTTON, GamepadInputs.LEFT_SHOULDER_BUTTON,
			GamepadInputs.RIGHT_SHOULDER_BUTTON, GamepadInputs.LEFT_STICK_BUTTON, GamepadInputs.RIGHT_STICK_BUTTON,
			GamepadInputs.BACK_BUTTON, GamepadInputs.START_BUTTON, GamepadInputs.DPAD_UP_BUTTON,
			GamepadInputs.DPAD_DOWN_BUTTON, GamepadInputs.DPAD_LEFT_BUTTON, GamepadInputs.DPAD_RIGHT_BUTTON);
	static final String START = GamepadInputs.START_BUTTON.getPath();

	private final Map<ControllerEntity, Set<String>> held = new IdentityHashMap<>();
	private List<ControllerEntity> pads = List.of();
	private int lastCount = -1;

	/**
	 * Steam Input's virtual pad (Valve 28de:11ff) mirrors a real one, so seeing both would make one press count
	 * twice. AppRun already hides it from SDL; this is the fallback if a Steam client still lets it through.
	 */
	private static boolean steamVirtual(ControllerEntity pad) {
		String hid = pad.info().hid().map(Object::toString).orElse("");
		return hid.contains("VID=0x28de") && hid.contains("PID=0x11ff");
	}

	/** Connected pads; null before Controlify has discovered controllers. */
	List<ControllerEntity> pads() {
		return pads;
	}

	/** This tick's edges, or null while Controlify isn't ready. */
	List<Edge> poll() {
		Controlify c = Controlify.instance();
		if (c.getControllerManager().isEmpty()) return null;
		// out-of-focus input: only one tile has focus. Mixed input: Steam turns Deck controls into keyboard/mouse
		// events as well, and switching input mode on every press made Controlify reset menu focus
		var global = c.config().getSettings().globalSettings();
		if (!global.outOfFocusInput || !global.mixedInput) {
			global.outOfFocusInput = true;
			global.mixedInput = true;
			c.config().markDirty();
		}
		pads = c.getControllerManager().get().getConnectedControllers().stream().filter(p -> !steamVirtual(p)).toList();
		int all = c.getControllerManager().get().getConnectedControllers().size();
		if (all != lastCount) {
			lastCount = all;
			SplitscreenGlue.LOG.info("[mcss] controllers: {}", c.getControllerManager().get().getConnectedControllers()
					.stream().map(p -> p.name() + (steamVirtual(p) ? " (Steam virtual, ignored)" : "") + " uid=" + p.uid()
							+ " " + p.info().hid().map(h -> h.toString().replaceAll(", path=[^\\]]*", "")).orElse("no hid"))
					.toList());
		}
		long now = System.currentTimeMillis();
		List<Edge> out = new ArrayList<>();
		held.keySet().retainAll(new java.util.HashSet<>(pads));
		for (ControllerEntity pad : pads) {
			if (pad.input().isEmpty()) continue;
			ControllerStateView state = pad.input().get().stateNow();
			Set<String> was = held.computeIfAbsent(pad, p -> new java.util.HashSet<>());
			for (Identifier b : BUTTONS) {
				boolean down = state.isButtonDown(b);
				if (down != was.contains(b.getPath())) {
					out.add(new Edge(pad, b.getPath(), down, now));
					if (down) was.add(b.getPath());
					else was.remove(b.getPath());
				}
			}
		}
		return out;
	}
}
