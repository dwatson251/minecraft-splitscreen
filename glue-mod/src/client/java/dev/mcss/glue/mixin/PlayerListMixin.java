package dev.mcss.glue.mixin;

import dev.mcss.glue.Splitscreen;
import java.util.UUID;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A world remembers one "singleplayer UUID" (whoever hosted it last), and the host, whatever its name, loads that
 * UUID's player data. Hosting as a profile after the Microsoft account (or the other way round, or as another profile)
 * would hand over the previous host's inventory and position. Without it the host loads the data saved under its own
 * UUID, which is what the guests already get.
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {
	@Redirect(method = "loadPlayerData", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/storage/WorldData;getSinglePlayerUUID()Ljava/util/UUID;"))
	private UUID mcss$ownPlayerData(WorldData data) {
		return Splitscreen.ENABLED ? null : data.getSinglePlayerUUID();
	}
}
