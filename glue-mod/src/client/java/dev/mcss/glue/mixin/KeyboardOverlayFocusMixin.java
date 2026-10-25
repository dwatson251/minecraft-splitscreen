package dev.mcss.glue.mixin;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Controlify's on-screen keyboard is a screen of its own over the one with the text box. Minecraft 26.3 clears the
 * old screen's focus when switching screens, so the text box lost focus as the keyboard opened and ignored every
 * key typed on it. This remembers what had focus and gives it back once the keyboard screen is up.
 */
@Pseudo
@Mixin(targets = "dev.isxander.controlify.screenop.keyboard.KeyboardOverlayScreen", remap = false)
public class KeyboardOverlayFocusMixin {
	@Shadow
	@Final
	private Screen backgroundScreen;

	@Unique
	private GuiEventListener mcss$typingInto;

	@Inject(method = "<init>", at = @At("RETURN"), require = 0)
	private void mcss$remember(CallbackInfo ci) {
		// still focused here: the switch to the keyboard screen (which clears it) comes after
		mcss$typingInto = backgroundScreen.getFocused();
	}

	@Inject(method = {"init", "tick"}, at = @At("TAIL"), require = 0)
	private void mcss$refocus(CallbackInfo ci) {
		if (mcss$typingInto != null && backgroundScreen.getFocused() != mcss$typingInto) backgroundScreen.setFocused(mcss$typingInto);
	}
}
