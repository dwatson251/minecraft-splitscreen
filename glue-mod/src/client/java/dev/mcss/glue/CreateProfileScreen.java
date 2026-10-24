package dev.mcss.glue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Create a profile: pick a skin, type a name on the on-screen keyboard (pad-friendly; a real keyboard works too,
 * and the Steam keyboard isn't needed), Create. The new profile is chosen straight away.
 */
public class CreateProfileScreen extends SkinScreen {
	private static final String[] ROWS = {"1234567890", "QWERTYUIOP", "ASDFGHJKL_", "^ZXCVBNM<"};

	private final ProfileSelectScreen parent;
	private EditBox name;
	private Button create;
	private boolean shift = true;
	private String problem = "";

	public CreateProfileScreen(ProfileSelectScreen parent) {
		super(Component.literal("New profile"), Skins.DEFAULT, false);
		this.parent = parent;
	}

	@Override
	protected void init() {
		int kw = 10 * 18 - 2, left = 100, gap = 12;
		int x0 = (width - (left + gap + kw)) / 2, y0 = Math.max(4, (height - 170) / 2);
		addPicker(x0, y0 + 14, left);

		int kx = x0 + left + gap;
		String typed = name == null ? "" : name.getValue();
		name = addRenderableWidget(new EditBox(font, kx, y0 + 14, kw, 18, Component.literal("Name")));
		name.setMaxLength(16);
		name.setHint(Component.literal("Name"));
		name.setValue(typed);
		name.setResponder(this::typed);
		int ky = y0 + 38;
		Button first = null;
		for (int r = 0; r < ROWS.length; r++) {
			for (int c = 0; c < ROWS[r].length(); c++) {
				char key = ROWS[r].charAt(c);
				Button b = addRenderableWidget(Button.builder(keyLabel(key), btn -> press(key))
						.bounds(kx + c * 18, ky + r * 18, 16, 16).build());
				if (first == null) first = b;
			}
		}
		create = addRenderableWidget(Button.builder(Component.literal("Create"), b -> create())
				.bounds(kx, ky + 4 * 18 + 4, kw / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
				.bounds(kx + kw / 2 + 2, ky + 4 * 18 + 4, kw / 2 - 2, 20).build());
		typed(name.getValue());
		setInitialFocus(first);
	}

	private Component keyLabel(char key) {
		if (key == '^') return Component.literal(shift ? "ABC" : "abc");
		if (key == '<') return Component.literal("Del");
		return Component.literal(String.valueOf(shift ? key : Character.toLowerCase(key)));
	}

	private void press(char key) {
		if (key == '^') {
			shift = !shift;
			rebuildWidgets();
			return;
		}
		if (key == '<') {
			name.deleteChars(-1);
		} else {
			name.insertText(String.valueOf(shift ? key : Character.toLowerCase(key)));
			// like a phone keyboard: capital first letter, then lower case
			if (shift && name.getValue().length() == 1) {
				shift = false;
				rebuildWidgets();
			}
		}
	}

	/** Keeps only valid characters (a real keyboard can type anything) and updates the Create button. */
	private void typed(String value) {
		String clean = value.replaceAll("[^A-Za-z0-9_]", "");
		if (!clean.equals(value)) {
			name.setValue(clean);
			return;
		}
		String why = Profiles.nameProblem(clean);
		problem = clean.isEmpty() ? "Type a name" : why == null ? "" : why;
		if (create != null) create.active = why == null;
	}

	private void create() {
		String n = name.getValue();
		if (Profiles.nameProblem(n) != null) return;
		Profiles.Profile p = new Profiles.Profile(n, skin(), slim);
		Profiles.save(p);
		parent.choose(p);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.text(font, title.getString(), (width - font.width(title)) / 2, Math.max(4, (height - 170) / 2), 0xFFFFFFFF);
		if (!problem.isEmpty() && create != null) {
			g.text(font, problem, create.getX(), create.getY() + 24, 0xFFFF8080);
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
