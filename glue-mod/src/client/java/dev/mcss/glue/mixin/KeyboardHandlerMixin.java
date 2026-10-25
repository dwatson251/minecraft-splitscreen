package dev.mcss.glue.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mcss.glue.Splitscreen;
import java.util.Set;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops the keys Steam's desktop controller layouts produce (Deck and PS4: A = Enter, B = Escape, Y = Space, D-pad =
 * arrows, page keys). In Desktop Mode they reach whichever view has keyboard focus, so a guest pressing A could
 * activate Player 1's selected button (Quit Game ended the session). Every player is read as a controller anyway,
 * and Controlify's on-screen keyboard doesn't come through here, so typing letters still works.
 *
 * While a text field has focus they go through (except Escape), so a keyboard, including Steam's on-screen one, can
 * type spaces, move the cursor and press Enter there.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	@Unique
	private static final Set<Integer> MCSS$STEAM_KEYS = Set.of(InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER,
			InputConstants.KEY_ESCAPE, InputConstants.KEY_SPACE, InputConstants.KEY_UP, InputConstants.KEY_DOWN,
			InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT, InputConstants.KEY_PAGEUP, InputConstants.KEY_PAGEDOWN);

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void mcss$dropSteamKeys(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (!Splitscreen.ENABLED || !MCSS$STEAM_KEYS.contains(event.input())) return;
		if (event.input() != InputConstants.KEY_ESCAPE && mcss$typing()) return;
		ci.cancel();
	}

	@Unique
	private static boolean mcss$typing() {
		GuiEventListener f = Minecraft.getInstance().gui.screen();
		while (!(f instanceof EditBox) && f instanceof ContainerEventHandler c && c.getFocused() != null) f = c.getFocused();
		return f instanceof EditBox box && box.isFocused();
	}
}
