package dev.mcss.glue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

/**
 * Change a profile's name or skin, or delete it. A rename moves the profile's inventories in Player 1's worlds along
 * with it (Profiles.rename). A on the name opens the on-screen keyboard (TypeScreen).
 */
public class EditProfileScreen extends SkinScreen {
	private final ProfileSelectScreen parent;
	private final Profiles.Profile profile;
	private EditBox name;
	private String problem = "";

	public EditProfileScreen(ProfileSelectScreen parent, Profiles.Profile profile) {
		super(Component.literal(profile.name()), profile.skin(), profile.slim());
		this.parent = parent;
		this.profile = profile;
	}

	@Override
	protected void init() {
		int w = 120, y0 = Math.max(4, (height - 224) / 2), x0 = (width - w) / 2;
		int y = addPicker(x0, y0 + 14, w);
		y += 12;
		String typed = name == null ? profile.name() : name.getValue();
		name = addRenderableWidget(new EditBox(font, x0, y, w, 18, Component.literal("Name")));
		name.setMaxLength(16);
		name.setValue(typed);
		name.setResponder(v -> {
			// only valid name characters (a real keyboard can type anything)
			String clean = v.replaceAll("[^A-Za-z0-9_]", "");
			if (!clean.equals(v)) name.setValue(clean);
			problem = "";
		});
		y += 22;
		Button save = addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(x0, y, w, 20).build());
		boolean inUse = Profiles.inUseElsewhere(profile) || Profiles.active() == profile
				|| (Profiles.active() != null && Profiles.active().name().equals(profile.name()));
		Button delete = addRenderableWidget(Button.builder(Component.literal(inUse ? "Delete (in use)" : "Delete"),
				b -> minecraft.gui.setScreen(new ConfirmScreen(yes -> {
					if (yes) Profiles.delete(profile);
					onClose();
				}, Component.literal("Delete " + profile.name() + "?"),
						Component.literal("Its inventories stay in the worlds, but the profile is gone.")))).bounds(x0, y + 24, w, 20).build());
		delete.active = !inUse;
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(x0, y + 48, w, 20).build());
		setInitialFocus(save);
	}

	private void save() {
		String why = Profiles.rename(profile, name.getValue());
		if (why != null) {
			problem = why;
			return;
		}
		Profiles.save(new Profiles.Profile(name.getValue(), skin(), slim));
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.text(font, title.getString(), (width - font.width(title)) / 2, Math.max(4, (height - 224) / 2), 0xFFFFFFFF);
		String above = problem.isEmpty() ? "Name (A to change)" : problem;
		g.text(font, above, (width - font.width(above)) / 2, name.getY() - 10, problem.isEmpty() ? 0xFFA0A0A0 : 0xFFFF8080);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
