package dev.mcss.glue.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mcss.glue.Splitscreen;
import java.util.Set;
import net.minecraft.client.KeyboardHandler;
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
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	@Unique
	private static final Set<Integer> MCSS$STEAM_KEYS = Set.of(InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER,
			InputConstants.KEY_ESCAPE, InputConstants.KEY_SPACE, InputConstants.KEY_UP, InputConstants.KEY_DOWN,
			InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT, InputConstants.KEY_PAGEUP, InputConstants.KEY_PAGEDOWN);

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void mcss$dropSteamKeys(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (Splitscreen.ENABLED && MCSS$STEAM_KEYS.contains(event.input())) ci.cancel();
	}
}
