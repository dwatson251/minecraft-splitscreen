package dev.mcss.glue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

/** Change a profile's skin, or delete it. The name stays: worlds keep the profile's inventory under it. */
public class EditProfileScreen extends SkinScreen {
	private final ProfileSelectScreen parent;
	private final Profiles.Profile profile;

	public EditProfileScreen(ProfileSelectScreen parent, Profiles.Profile profile) {
		super(Component.literal(profile.name()), profile.skin(), profile.slim());
		this.parent = parent;
		this.profile = profile;
	}

	@Override
	protected void init() {
		int w = 120, y0 = Math.max(4, (height - 190) / 2), x0 = (width - w) / 2;
		int y = addPicker(x0, y0 + 14, w);
		Button save = addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
			Profiles.save(new Profiles.Profile(profile.name(), skin(), slim));
			onClose();
		}).bounds(x0, y, w, 20).build());
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

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.text(font, title.getString(), (width - font.width(title)) / 2, Math.max(4, (height - 190) / 2), 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
