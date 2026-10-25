package dev.mcss.glue.mixin;

import dev.mcss.glue.TypeScreen;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A on a text box opens our on-screen keyboard (TypeScreen) instead of Controlify's. */
@Pseudo
@Mixin(targets = "dev.isxander.controlify.screenop.ScreenProcessor", remap = false)
public class ControlifyKeyboardMixin {
	@Inject(method = "tryOpenKeyboard", at = @At("HEAD"), cancellable = true, require = 0)
	private void mcss$ourKeyboard(@Coerce Object controller, GuiEventListener focused, CallbackInfoReturnable<Boolean> cir) {
		if (TypeScreen.open(focused)) cir.setReturnValue(true);
	}
}
