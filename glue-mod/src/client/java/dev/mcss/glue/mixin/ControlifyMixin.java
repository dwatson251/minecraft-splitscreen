package dev.mcss.glue.mixin;

import dev.mcss.glue.Splitscreen;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Controlify makes any other pad that gives input the current one, and opens a setup wizard for new pads. In
 * splitscreen every client sees every pad, so both would steal other players' controllers.
 */
@Pseudo
@Mixin(targets = "dev.isxander.controlify.Controlify", remap = false)
public class ControlifyMixin {
	@Inject(method = "tickInactiveController", at = @At("HEAD"), cancellable = true, require = 0)
	private void mcss$noAutoSwitch(CallbackInfo ci) {
		if (Splitscreen.ENABLED) ci.cancel();
	}

	@Inject(method = "onControllerAdded", at = @At("HEAD"), cancellable = true, require = 0)
	private void mcss$noWizard(CallbackInfo ci) {
		if (Splitscreen.ENABLED) ci.cancel();
	}
}
