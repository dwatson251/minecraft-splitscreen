package dev.mcss.glue.mixin;

import com.mojang.authlib.GameProfile;
import dev.mcss.glue.Profiles;
import dev.mcss.glue.Splitscreen;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.core.UUIDUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Plays under the chosen profile's name: the LAN login (guests) and the integrated server's host player (P1) both
 * read the name and UUID from here, and the host runs without authentication, so the server's offline UUID for the
 * name matches ours and each world keeps that profile's inventory. Chat isn't signed with the Microsoft account's
 * key when P1 plays as a profile.
 */
@Mixin(Minecraft.class)
public class UserMixin {
	@Unique
	private User mcss$user;

	@Inject(method = "getUser", at = @At("RETURN"), cancellable = true)
	private void mcss$profileUser(CallbackInfoReturnable<User> cir) {
		if (!Splitscreen.ENABLED) return;
		String name = Profiles.overrideName();
		if (name == null) {
			Profiles.noteAccount(cir.getReturnValue().getName());
			return;
		}
		if (mcss$user == null || !mcss$user.getName().equals(name)) {
			mcss$user = new User(name, UUIDUtil.createOfflinePlayerUUID(name), "0", Optional.empty(), Optional.empty());
		}
		cir.setReturnValue(mcss$user);
	}

	@Inject(method = "getGameProfile", at = @At("RETURN"), cancellable = true)
	private void mcss$profileGameProfile(CallbackInfoReturnable<GameProfile> cir) {
		if (!Splitscreen.ENABLED) return;
		String name = Profiles.overrideName();
		if (name == null) Profiles.noteAccountProfile(cir.getReturnValue());
		else cir.setReturnValue(UUIDUtil.createOfflineProfile(name));
	}

	@Inject(method = "getProfileKeyPairManager", at = @At("RETURN"), cancellable = true)
	private void mcss$noAccountKeys(CallbackInfoReturnable<ProfileKeyPairManager> cir) {
		if (Splitscreen.ENABLED && Profiles.overrideName() != null) cir.setReturnValue(ProfileKeyPairManager.EMPTY_KEY_MANAGER);
	}
}
