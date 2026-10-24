package dev.mcss.glue.mixin;

import dev.mcss.glue.Profiles;
import dev.mcss.glue.Skins;
import dev.mcss.glue.Splitscreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players named after a profile wear its skin, in every view (all views read the same profile files). */
@Mixin(PlayerInfo.class)
public class PlayerInfoMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void mcss$profileSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		if (!Splitscreen.ENABLED) return;
		Profiles.Profile p = Profiles.byName(((PlayerInfo) (Object) this).getProfile().name());
		if (p != null) cir.setReturnValue(Skins.skin(p));
	}
}
