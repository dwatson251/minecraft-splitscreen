package dev.mcss.glue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * "Choose your profile": every view shows this before anything else (Player 1 before the main menu, guests before
 * they wait for the world). One row of cards, the chosen one sliding to the middle: Create profile first, then
 * Player 1's Microsoft account (Player 1 only), then the saved profiles. A profile another view is using is
 * greyed out and can't be picked. Edit (below the row) changes a profile's skin or deletes it.
 */
public class ProfileSelectScreen extends DebouncedScreen {
	private static final Profiles.Profile CREATE = new Profiles.Profile("+", "", false);
	private static final int CARD_W = 72, CARD_H = 104, GAP = 8;

	private final Screen parent;
	private final List<Card> cards = new ArrayList<>();
	private Button edit;
	private Card lastCard;
	private float scroll = Float.NaN;
	private String shown = "";
	private int keepIndex = -1;
	private static java.util.function.Supplier<net.minecraft.world.entity.player.PlayerSkin> accountSkin;

	/** parent: where Back goes for Player 1 changing profile later; null when a profile must be chosen. */
	public ProfileSelectScreen(Screen parent) {
		super(Component.literal("Choose your profile"));
		this.parent = parent;
	}

	private static List<Profiles.Profile> entries() {
		List<Profiles.Profile> out = new ArrayList<>();
		out.add(CREATE);
		if (Splitscreen.HOST) out.add(Profiles.ACCOUNT);
		out.addAll(Profiles.all());
		return out;
	}

	private static String state() {
		StringBuilder b = new StringBuilder(Splitscreen.HOST ? Lobby.hostReady() + "" : GuestPad.controllerName() + "");
		for (Profiles.Profile p : entries()) b.append('|').append(Profiles.displayName(p)).append(p.skin()).append(p.slim())
				.append(Profiles.inUseElsewhere(p));
		return b.toString();
	}

	@Override
	protected void init() {
		shown = state();
		cards.clear();
		List<Profiles.Profile> list = entries();
		int y = Math.max(28, (height - CARD_H) / 2 - 12);
		for (Profiles.Profile p : list) cards.add(addRenderableWidget(new Card(p, y)));
		edit = addRenderableWidget(Button.builder(Component.literal("Edit profile"), b -> {
			// the card stays chosen while the pad is down on this button
			Card c = lastCard;
			if (c != null && isProfile(c.profile)) minecraft.gui.setScreen(new EditProfileScreen(this, c.profile));
		}).bounds(width / 2 - 104, y + CARD_H + 8, 100, 20).build());
		Button back = addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
				.bounds(width / 2 + 4, y + CARD_H + 8, 100, 20).build());
		back.active = canLeave();

		// focus: the card we were on, else the profile in use, else the first saved profile, else Create
		int idx = keepIndex;
		if (idx < 0 || idx >= cards.size()) {
			idx = 0;
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i) == Profiles.active() || (Profiles.active() != null && isProfile(list.get(i))
						&& list.get(i).name().equals(Profiles.active().name()))) idx = i;
			}
			if (idx == 0 && Profiles.active() == null && list.size() > (Splitscreen.HOST ? 2 : 1)) idx = Splitscreen.HOST ? 2 : 1;
		}
		setInitialFocus(cards.get(idx));
		lastCard = cards.get(idx);
		if (Float.isNaN(scroll)) scroll = idx * (CARD_W + GAP);
	}

	private static boolean isProfile(Profiles.Profile p) {
		return p != CREATE && p != Profiles.ACCOUNT;
	}

	private Card focusedCard() {
		GuiEventListener f = getFocused();
		return f instanceof Card c ? c : null;
	}

	private boolean canLeave() {
		return parent != null && Profiles.active() != null;
	}

	@Override
	public void tick() {
		if (!state().equals(shown)) {
			Card c = focusedCard();
			keepIndex = c == null ? -1 : cards.indexOf(c);
			rebuildWidgets();
		}
	}

	/** A card was pressed (or a profile was just created). */
	void choose(Profiles.Profile p) {
		if (p == CREATE) {
			minecraft.gui.setScreen(new CreateProfileScreen(this));
			return;
		}
		if (Splitscreen.HOST && !Lobby.hostReady()) {
			Lobby.toast("Player 1", "Press a button on your controller first");
			minecraft.gui.setScreen(this);
			return;
		}
		if (!Profiles.activate(p)) {
			Lobby.toast(Profiles.displayName(p), "Another player is using this profile");
			minecraft.gui.setScreen(this);
			return;
		}
		minecraft.updateTitle();
		if (!Splitscreen.HOST) minecraft.gui.setScreen(new WaitingScreen());
		else minecraft.gui.setScreen(parent != null ? parent : new TitleScreen());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// slide so the focused card sits in the middle; cards past the edges simply run off the screen
		Card f = focusedCard();
		if (f != null) lastCard = f;
		if (f != null) {
			float target = cards.indexOf(f) * (CARD_W + GAP);
			scroll += (target - scroll) * Math.min(1f, 0.25f * Math.max(1f, partialTick + 1f));
			if (Math.abs(target - scroll) < 0.5f) scroll = target;
		}
		for (int i = 0; i < cards.size(); i++) {
			cards.get(i).setX(Math.round(width / 2f - CARD_W / 2f + i * (CARD_W + GAP) - scroll));
		}
		edit.active = lastCard != null && isProfile(lastCard.profile);
		super.extractRenderState(g, mouseX, mouseY, partialTick);

		int top = cards.isEmpty() ? 20 : cards.get(0).getY() - 26;
		String head = Splitscreen.HOST ? "Player 1: choose your profile" : "Player " + Splitscreen.shown + ": choose your profile";
		g.text(font, head, (width - font.width(head)) / 2, top, 0xFFFFFFFF);
		String hint = null;
		if (Splitscreen.HOST && !Lobby.hostReady()) hint = "Press any button on your controller";
		else if (!Splitscreen.HOST && GuestPad.controllerName() == null) hint = "Press any button on your controller";
		if (hint != null) g.text(font, hint, (width - font.width(hint)) / 2, top + 12, 0xFFFFFF80);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return canLeave();
	}

	@Override
	public void onClose() {
		if (canLeave()) minecraft.gui.setScreen(parent);
	}

	private final class Card extends AbstractButton {
		final Profiles.Profile profile;

		Card(Profiles.Profile profile, int y) {
			super(0, y, CARD_W, CARD_H, Component.literal(profile == CREATE ? "New profile" : Profiles.displayName(profile)));
			this.profile = profile;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			choose(profile);
		}

		@Override
		protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			boolean busy = isProfile(profile) || profile == Profiles.ACCOUNT ? Profiles.inUseElsewhere(profile) : false;
			boolean focused = isHoveredOrFocused();
			int x = getX(), y = getY();
			g.fill(x, y, x + width, y + height, focused ? 0xC0505A70 : 0x90202530);
			if (focused) {
				g.fill(x, y, x + width, y + 1, 0xFFFFFFFF);
				g.fill(x, y + height - 1, x + width, y + height, 0xFFFFFFFF);
				g.fill(x, y, x + 1, y + height, 0xFFFFFFFF);
				g.fill(x + width - 1, y, x + width, y + height, 0xFFFFFFFF);
			}
			String name = getMessage().getString();
			if (profile == CREATE) {
				String plus = "+";
				g.text(font, plus, x + (width - font.width(plus)) / 2, y + 36, 0xFFFFFFFF);
			} else if (profile == Profiles.ACCOUNT) {
				Identifier tex = accountTexture();
				Skins.drawBody(g, tex, false, x + (width - 32) / 2, y + 6, 2);
			} else {
				Skins.drawBody(g, profile.skin(), profile.slim(), x + (width - 32) / 2, y + 6, 2);
			}
			String shownName = font.width(name) > width - 4 ? font.plainSubstrByWidth(name, width - 8) + ".." : name;
			int color = busy ? 0xFF808080 : 0xFFFFFFFF;
			g.text(font, shownName, x + (width - font.width(shownName)) / 2, y + height - 26, color);
			String sub = profile == Profiles.ACCOUNT ? "Microsoft" : busy ? "In use" : null;
			if (sub != null) g.text(font, sub, x + (width - font.width(sub)) / 2, y + height - 14, busy ? 0xFFFF8080 : 0xFFA0A0A0);
			if (busy) g.fill(x, y, x + width, y + height, 0x80000000);
		}

		private Identifier accountTexture() {
			var profile = Profiles.accountProfile();
			if (profile == null) return Skins.texture(Skins.DEFAULT, false);
			if (accountSkin == null) accountSkin = minecraft.getSkinManager().createLookup(profile, false);
			return accountSkin.get().body().texturePath();
		}
	}
}
