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
	private final ProfileSelectScreen parent;
	private EditBox name;
	private Button create;
	private String problem = "";
	private String pendingDevName;

	public CreateProfileScreen(ProfileSelectScreen parent) {
		super(Component.literal("New profile"), Skins.DEFAULT, false);
		this.parent = parent;
	}

	@Override
	protected void init() {
		int kw = OnScreenKeyboard.WIDTH, left = 100, gap = 12;
		int x0 = (width - (left + gap + kw)) / 2, y0 = Math.max(4, (height - 170) / 2);
		addPicker(x0, y0 + 14, left);

		int kx = x0 + left + gap;
		String typed = name == null ? "" : name.getValue();
		name = addRenderableWidget(new EditBox(font, kx, y0 + 14, kw, 18, Component.literal("Name")));
		name.setMaxLength(16);
		name.setHint(Component.literal("Name"));
		name.setValue(pendingDevName != null ? pendingDevName : typed);
		name.setResponder(this::typed);
		int ky = y0 + 38;
		OnScreenKeyboard keys = new OnScreenKeyboard(name, false);
		keys.add(b -> addRenderableWidget(b), kx, ky);
		create = addRenderableWidget(Button.builder(Component.literal("Create"), b -> create())
				.bounds(kx, ky + 4 * 18 + 4, kw / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
				.bounds(kx + kw / 2 + 2, ky + 4 * 18 + 4, kw / 2 - 2, 20).build());
		typed(name.getValue());
		setInitialFocus(keys.first());
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

	/** DevShots: show a typed name. */
	void devName(String n) {
		if (name != null) name.setValue(n);
		pendingDevName = n;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
