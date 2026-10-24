package dev.mcss.glue.mixin;

import dev.mcss.glue.Splitscreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "P2 Minecraft 26.3": the KWin tiler orders windows by this prefix (the shown player number). Player 1's title also
 * carries the layout: "P1v ..." when vertical.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void mcss$playerTitle(CallbackInfoReturnable<String> cir) {
		if (Splitscreen.ENABLED) cir.setReturnValue(Splitscreen.titlePrefix() + cir.getReturnValue());
	}
}
