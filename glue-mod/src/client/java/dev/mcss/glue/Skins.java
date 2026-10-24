package dev.mcss.glue;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Skins a profile can wear: Minecraft's 9 built-in ones ("builtin:steve"), and 64x64 PNGs the player dropped into
 * ~/Documents/Minecraft Skins ("file:name.png", copied to <prism>/mcss-skins by AppRun because the Prism flatpak
 * can't see Documents). Custom skins are loaded once into a DynamicTexture.
 */
public final class Skins {
	public static final String DEFAULT = "builtin:steve";
	private static final List<String> BUILTIN = List.of("steve", "alex", "ari", "efe", "kai", "makena", "noor",
			"sunny", "zuri");
	private static final Path DIR = Splitscreen.SHARED.resolve("mcss-skins");
	private static final Map<String, Identifier> LOADED = new HashMap<>();

	private Skins() {}

	/** Every skin to choose from: built-in first, then the custom ones. */
	public static List<String> choices() {
		List<String> out = new ArrayList<>();
		for (String b : BUILTIN) out.add("builtin:" + b);
		try (DirectoryStream<Path> files = Files.newDirectoryStream(DIR, "*.png")) {
			List<String> custom = new ArrayList<>();
			for (Path f : files) custom.add("file:" + f.getFileName());
			custom.sort(String.CASE_INSENSITIVE_ORDER);
			out.addAll(custom);
		} catch (IOException ignored) {
			// no custom skins
		}
		return out;
	}

	public static String label(String skin) {
		String n = skin.substring(skin.indexOf(':') + 1);
		if (skin.startsWith("file:")) n = n.replaceAll("(?i)\\.png$", "");
		return n.isEmpty() ? n : Character.toUpperCase(n.charAt(0)) + n.substring(1);
	}

	public static PlayerSkin skin(Profiles.Profile p) {
		return skin(p.skin(), p.slim());
	}

	public static PlayerSkin skin(String skin, boolean slim) {
		PlayerModelType model = slim ? PlayerModelType.SLIM : PlayerModelType.WIDE;
		Identifier tex = texture(skin, slim);
		return PlayerSkin.insecure(new ClientAsset.ResourceTexture(tex, tex), null, null, model);
	}

	/** The texture to draw for a skin (custom ones are uploaded on first use; unknown ones fall back to Steve). */
	public static Identifier texture(String skin, boolean slim) {
		if (skin.startsWith("file:")) {
			Identifier id = custom(skin.substring(5));
			if (id != null) return id;
			skin = DEFAULT;
		}
		String name = skin.startsWith("builtin:") && BUILTIN.contains(skin.substring(8)) ? skin.substring(8) : "steve";
		return Identifier.withDefaultNamespace("textures/entity/player/" + (slim ? "slim/" : "wide/") + name + ".png");
	}

	private static Identifier custom(String file) {
		if (LOADED.containsKey(file)) return LOADED.get(file);
		Identifier id = null;
		try (InputStream in = Files.newInputStream(DIR.resolve(file))) {
			NativeImage img = NativeImage.read(in);
			if (img.getWidth() != 64 || img.getHeight() != 64) {
				SplitscreenGlue.LOG.warn("[mcss] skin {} is {}x{}, needs 64x64", file, img.getWidth(), img.getHeight());
				img.close();
			} else {
				id = Identifier.fromNamespaceAndPath("mcss", "skins/" + file.toLowerCase(Locale.ROOT)
						.replaceAll("[^a-z0-9_.-]", "_"));
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "mcss skin " + file, img));
			}
		} catch (IOException e) {
			SplitscreenGlue.LOG.warn("[mcss] can't load skin {}: {}", file, e.toString());
		}
		LOADED.put(file, id);
		return id;
	}

	/** A front view of the whole skin, 16x32 texels at the given scale (with the outer layer on top). */
	public static void drawBody(GuiGraphicsExtractor g, String skin, boolean slim, int x, int y, int s) {
		drawBody(g, texture(skin, slim), slim, x, y, s);
	}

	public static void drawBody(GuiGraphicsExtractor g, Identifier t, boolean slim, int x, int y, int s) {
		int aw = slim ? 3 : 4;
		for (int layer = 0; layer < 2; layer++) {
			int o = layer * 16;  // outer layer rows sit 16 texels below the inner ones (head: 32 to the right)
			part(g, t, x + 4 * s, y, layer == 0 ? 8 : 40, 8, 8, 8, s);                       // head / hat
			part(g, t, x + 4 * s, y + 8 * s, 20, 20 + o, 8, 12, s);                         // body / jacket
			part(g, t, x + (4 - aw) * s, y + 8 * s, 44, 20 + o, aw, 12, s);                 // right arm
			part(g, t, x + 12 * s, y + 8 * s, layer == 0 ? 36 : 52, 52, aw, 12, s);        // left arm
			part(g, t, x + 4 * s, y + 20 * s, 4, 20 + o, 4, 12, s);                         // right leg
			part(g, t, x + 8 * s, y + 20 * s, layer == 0 ? 20 : 4, 52, 4, 12, s);          // left leg
		}
	}

	private static void part(GuiGraphicsExtractor g, Identifier t, int x, int y, int u, int v, int w, int h, int s) {
		g.blit(RenderPipelines.GUI_TEXTURED, t, x, y, u, v, w * s, h * s, w, h, 64, 64);
	}
}
