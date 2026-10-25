package dev.mcss.glue.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Controlify's on-screen keyboard sends Backspace, Enter, Tab and the arrows to a text box as (SDL scancode, 0,
 * modifiers), the pre-26.3 shape. Minecraft 26.3's KeyEvent has the SDL keycode second, and EditBox reads only that,
 * so those keys did nothing. This fills in the keycode.
 */
@Pseudo
@Mixin(targets = "dev.isxander.controlify.screenop.compat.vanilla.EditBoxComponentProcessor$EditBoxInputTarget", remap = false)
public class KeyboardKeyCodeMixin {
	@Shadow
	@Final
	private EditBox editBox;

	@Inject(method = "acceptKeyCode", at = @At("HEAD"), cancellable = true, require = 0)
	private void mcss$withKeycode(int scancode, int keycode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (keycode != 0) return;
		editBox.keyPressed(new KeyEvent(scancode, mcss$keycode(scancode), modifiers));
		cir.setReturnValue(true);
	}

	/** SDL_GetKeyFromScancode for the keys Controlify sends: a few are ASCII, the rest are the scancode | 1 << 30. */
	@Unique
	private static int mcss$keycode(int scancode) {
		return switch (scancode) {
			case 40 -> '\r';
			case 41 -> 27;
			case 42 -> '\b';
			case 43 -> '\t';
			case 44 -> ' ';
			case 76 -> 127;
			default -> scancode | 0x40000000;
		};
	}
}
