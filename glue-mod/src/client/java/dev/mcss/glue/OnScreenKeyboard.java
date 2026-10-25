package dev.mcss.glue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * The on-screen keyboard: rows of plain buttons (so pad navigation works) that type into an EditBox. Shift and the
 * phone-style "capital first letter" only relabel the keys, so the key you're on keeps focus. On the pad, X deletes
 * and Y types a space (where spaces are allowed); the screen calls tick().
 */
final class OnScreenKeyboard {
	static final int WIDTH = 10 * 18 - 2;
	private static final String[] ROWS = {"1234567890", "QWERTYUIOP", "ASDFGHJKL_", "^ZXCVBNM<"};

	private final EditBox target;
	private final boolean space;
	private final List<Button> keys = new ArrayList<>();
	private final List<Character> chars = new ArrayList<>();
	private boolean shift = true;
	private Button first;
	private int faceHeld = -1;

	/** space: adds a Space bar (free text such as world names; profile names can't have spaces). */
	OnScreenKeyboard(EditBox target, boolean space) {
		this.target = target;
		this.space = space;
	}

	/** Adds the keys with their top-left corner at x, y; returns the y below them. */
	int add(UnaryOperator<Button> add, int x, int y) {
		keys.clear();
		chars.clear();
		first = null;
		for (int r = 0; r < ROWS.length; r++) {
			for (int c = 0; c < ROWS[r].length(); c++) {
				char key = ROWS[r].charAt(c);
				Button b = add.apply(Button.builder(label(key), btn -> press(key)).bounds(x + c * 18, y + r * 18, 16, 16).build());
				keys.add(b);
				chars.add(key);
				if (first == null) first = b;
			}
		}
		int below = y + ROWS.length * 18;
		if (space) {
			add.apply(Button.builder(Component.literal("Space"), btn -> press(' ')).bounds(x, below, WIDTH, 16).build());
			below += 18;
		}
		return below;
	}

	/** The pad shortcuts' hint, for under the keyboard. */
	String hint() {
		return space ? "X: delete   Y: space" : "X: delete";
	}

	/** Pad shortcuts: X = backspace, Y = space, on the press (not while held). */
	void tick() {
		if (!SplitscreenGlue.CONTROLIFY) return;
		int now = PadEdges.currentFaceButtons();
		// ignore buttons already held when the keyboard opened
		int pressed = faceHeld < 0 ? 0 : now & ~faceHeld;
		faceHeld = now;
		if ((pressed & 1) != 0) press('<');
		if ((pressed & 2) != 0 && space) press(' ');
	}

	Button first() {
		return first;
	}

	private Component label(char key) {
		// keys are 16 px wide: two characters at most
		if (key == '^') return Component.literal(shift ? "AB" : "ab");
		if (key == '<') return Component.literal("<");
		return Component.literal(String.valueOf(shift ? key : Character.toLowerCase(key)));
	}

	private void setShift(boolean on) {
		shift = on;
		for (int i = 0; i < keys.size(); i++) keys.get(i).setMessage(label(chars.get(i)));
	}

	private void press(char key) {
		if (key == '^') {
			setShift(!shift);
		} else if (key == '<') {
			target.deleteChars(-1);
		} else {
			target.insertText(String.valueOf(shift ? key : Character.toLowerCase(key)));
			// like a phone keyboard: capital first letter, then lower case
			if (shift && target.getValue().length() == 1) setShift(false);
		}
	}
}
