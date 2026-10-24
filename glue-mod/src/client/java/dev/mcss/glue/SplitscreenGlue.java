package dev.mcss.glue;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;

/**
 * Host: shows "Press START to play" (LobbyScreen) until Player 1's controller is known, adds a "Splitscreen"
 * settings button to its title screen and pause menu, and publishes whatever world it opens on LAN with authentication off, so offline-named guests can
 * join. Guest: never shows the menus; it waits on WaitingScreen for the host's port and joins; leaving the world
 * while the host's is still open shuts the guest down (its player has left). Every client: no pause
 * on focus loss (only one tile has focus), guests muted so audio isn't doubled. Controllers: Lobby (host) and
 * GuestPad (guests).
 */
public class SplitscreenGlue implements ClientModInitializer {
	public static final Logger LOG = LogUtils.getLogger();
	private static final boolean CONTROLIFY = FabricLoader.getInstance().isModLoaded("controlify");

	private boolean padsFailed;
	private volatile boolean probing;
	private volatile long leaveCheckAt;

	@Override
	public void onInitializeClient() {
		if (!Splitscreen.ENABLED) return;
		LOG.info("[mcss] P{} {} port {}", Splitscreen.PLAYER,
				Splitscreen.HOST ? "host" : "guest", Splitscreen.PORT);
		ClientLifecycleEvents.CLIENT_STARTED.register(this::setOptions);
		if (!Splitscreen.HOST && CONTROLIFY) ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> GuestPad.leave());
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		if (Splitscreen.HOST) {
			// "world" tells guests whether a disconnect was them leaving (world still open: shut that guest down) or
			// the host closing the world (they wait and rejoin). SERVER_STOPPING runs before players are kicked.
			ServerLifecycleEvents.SERVER_STARTED.register(server -> Splitscreen.write("world", "open"));
			ServerLifecycleEvents.SERVER_STOPPING.register(server -> Splitscreen.write("world", "closed"));
		} else {
			ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> leaveCheckAt = System.currentTimeMillis() + 300);
			// guests never show the menus: the title screen and "disconnected" become "Waiting for Player 1's world"
			ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
				if (screen instanceof TitleScreen || screen instanceof DisconnectedScreen) {
					mc.execute(() -> {
						if (mc.gui.screen() == screen) mc.gui.setScreen(new WaitingScreen());
					});
				}
			});
		}
		if (Splitscreen.HOST) {
			ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
				if (screen instanceof PauseScreen) Screens.getWidgets(screen).add(settingsButton(mc, screen));
				if (screen instanceof TitleScreen title) {
					// no menus before Player 1's controller is known: first boot, or Player 1's pad went away
					if (CONTROLIFY && !Lobby.hostReady()) mc.execute(() -> {
						if (mc.gui.screen() == title) mc.gui.setScreen(new LobbyScreen(title));
					});
					Screens.getWidgets(screen).add(settingsButton(mc, screen));
				}
			});
		}
	}

	/** Top-left "Splitscreen" button on Player 1's title screen and pause menu: layout and players. */
	private static Button settingsButton(Minecraft mc, Screen screen) {
		return Button.builder(Component.literal("Splitscreen"), b -> mc.gui.setScreen(new SettingsScreen(screen)))
				.bounds(4, 4, 100, 20).build();
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
		if (CONTROLIFY) {
			try {
				if (Splitscreen.HOST) Lobby.tick();
				else GuestPad.tick();
			} catch (RuntimeException e) {
				// never take the game down over controller bookkeeping
				if (!padsFailed) LOG.error("[mcss] controller handling failed", e);
				padsFailed = true;
			}
		}
		if (Splitscreen.HOST) tickHost(mc);
		else tickGuest(mc);
	}

	private void tickHost(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null && mc.player != null && !server.isPublished()) {
			server.setUsesAuthentication(false);
			boolean ok = server.publishServer(MinecraftServer.MultiplayerScope.LAN, true, Splitscreen.PORT);
			LOG.info("[mcss] published '{}' on port {}: {}", server.getWorldData().getLevelName(), Splitscreen.PORT, ok);
			// publishing can fail if the port is taken; don't retry every tick
			if (!ok) server.setUsesAuthentication(true);
		}
	}

	private void tickGuest(Minecraft mc) {
		if (leaveCheckAt != 0 && System.currentTimeMillis() >= leaveCheckAt) {
			leaveCheckAt = 0;
			String world = Splitscreen.read("world");
			if (world != null && world.trim().equals("open")) {
				// the host's world is still up, so this player left it: close this view instead of showing a menu
				LOG.info("[mcss] P{} left the world, shutting this client down", Splitscreen.PLAYER);
				mc.stop();
				return;
			}
			LOG.info("[mcss] host closed the world, waiting to rejoin");
		}
		if (!(mc.gui.screen() instanceof WaitingScreen) || probing) return;
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
				if (!(mc.gui.screen() instanceof WaitingScreen)) return;
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
