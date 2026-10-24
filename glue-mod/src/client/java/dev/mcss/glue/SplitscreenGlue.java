package dev.mcss.glue;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;

/**
 * Host: opens (or creates) the shared world from the title screen and publishes it on LAN with authentication off,
 * so offline-named guests can join. Guest: whenever it sits on the title screen, waits for the host's port and
 * joins. Every client: no pause on focus loss (only one tile has focus), guests muted so audio isn't doubled.
 */
public class SplitscreenGlue implements ClientModInitializer {
	public static final Logger LOG = LogUtils.getLogger();
	private static final boolean CONTROLIFY = FabricLoader.getInstance().isModLoaded("controlify");

	private boolean worldRequested;
	private volatile boolean probing;

	@Override
	public void onInitializeClient() {
		if (!Splitscreen.ENABLED) return;
		LOG.info("[mcss] P{} pad {} {} port {}", Splitscreen.PLAYER, Splitscreen.PAD,
				Splitscreen.HOST ? "host" : "guest", Splitscreen.PORT);
		ClientLifecycleEvents.CLIENT_STARTED.register(this::setOptions);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void setOptions(Minecraft mc) {
		mc.options.pauseOnLostFocus = false;
		mc.options.onboardAccessibility = false;
		mc.options.skipMultiplayerWarning = true;
		mc.options.joinedFirstServer = true;
		if (!Splitscreen.HOST) mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
		mc.options.save();
		mc.updateTitle();
	}

	private void tick(Minecraft mc) {
		if (CONTROLIFY) ControlifyPin.tick();
		if (Splitscreen.HOST) tickHost(mc);
		else tickGuest(mc);
	}

	private void tickHost(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null && mc.player != null && !server.isPublished()) {
			server.setUsesAuthentication(false);
			boolean ok = server.publishServer(MinecraftServer.MultiplayerScope.LAN, true, Splitscreen.PORT);
			LOG.info("[mcss] published '{}' on port {}: {}", Splitscreen.WORLD, Splitscreen.PORT, ok);
			// publishing can fail if the port is taken; don't retry every tick
			if (!ok) server.setUsesAuthentication(true);
		}
		if (!worldRequested && mc.gui.screen() instanceof TitleScreen) {
			worldRequested = true;
			if (mc.getLevelSource().levelExists(Splitscreen.WORLD)) {
				LOG.info("[mcss] opening world '{}'", Splitscreen.WORLD);
				mc.createWorldOpenFlows().openWorld(Splitscreen.WORLD, () -> mc.gui.setScreen(new TitleScreen()));
			} else {
				LOG.info("[mcss] creating world '{}'", Splitscreen.WORLD);
				LevelSettings settings = new LevelSettings(Splitscreen.WORLD, GameType.SURVIVAL,
						new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true,
						WorldDataConfiguration.DEFAULT);
				mc.createWorldOpenFlows().createFreshLevel(Splitscreen.WORLD, settings,
						WorldOptions.defaultWithRandomSeed(), WorldPresets::createNormalWorldDimensions, new TitleScreen());
			}
		}
	}

	private void tickGuest(Minecraft mc) {
		if (!(mc.gui.screen() instanceof TitleScreen) || probing) return;
		probing = true;
		Thread t = new Thread(() -> {
			while (!portOpen()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					return;
				}
			}
			mc.execute(() -> {
				probing = false;
				if (!(mc.gui.screen() instanceof TitleScreen)) return;
				LOG.info("[mcss] joining 127.0.0.1:{}", Splitscreen.PORT);
				ServerData data = new ServerData("Splitscreen", "127.0.0.1:" + Splitscreen.PORT, ServerData.Type.LAN);
				ConnectScreen.startConnecting(mc.gui.screen(), mc, new ServerAddress("127.0.0.1", Splitscreen.PORT), data,
						false, null);
			});
		}, "mcss-join");
		t.setDaemon(true);
		t.start();
	}

	private static boolean portOpen() {
		try (Socket s = new Socket()) {
			s.connect(new InetSocketAddress("127.0.0.1", Splitscreen.PORT), 500);
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
