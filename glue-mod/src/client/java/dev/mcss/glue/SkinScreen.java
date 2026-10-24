package dev.mcss.glue;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A screen with a skin picker: a big front view, < > through the skins, and a classic/slim arms toggle. */
abstract class SkinScreen extends DebouncedScreen {
	protected List<String> skins = List.of(Skins.DEFAULT);
	protected int skinIndex;
	protected boolean slim;
	private int previewX, previewY, previewScale, labelY;
	private Button arms;

	SkinScreen(Component title, String skin, boolean slim) {
		super(title);
		this.skins = Skins.choices();
		this.skinIndex = Math.max(0, skins.indexOf(skin));
		this.slim = slim;
	}

	protected String skin() {
		return skins.get(skinIndex);
	}

	/** Lays the picker out in a column of the given width, starting at y; returns the y below it. */
	protected int addPicker(int x, int y, int w) {
		previewScale = Math.max(1, Math.min(3, (height - 120) / 32));
		previewX = x + (w - 16 * previewScale) / 2;
		previewY = y;
		int row = y + 32 * previewScale + 6;
		labelY = row + 6;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> step(-1)).bounds(x, row, 20, 20).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> step(1)).bounds(x + w - 20, row, 20, 20).build());
		arms = addRenderableWidget(Button.builder(armsLabel(), b -> {
			slim = !slim;
			b.setMessage(armsLabel());
		}).bounds(x, row + 24, w, 20).build());
		return row + 48;
	}

	private void step(int d) {
		skinIndex = Math.floorMod(skinIndex + d, skins.size());
	}

	private Component armsLabel() {
		return Component.literal("Arms: " + (slim ? "Slim" : "Classic"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		Skins.drawBody(g, skin(), slim, previewX, previewY, previewScale);
		String label = Skins.label(skin());
		int cx = previewX + 8 * previewScale;
		g.text(font, label, cx - font.width(label) / 2, labelY, 0xFFFFFFFF);
	}
}
