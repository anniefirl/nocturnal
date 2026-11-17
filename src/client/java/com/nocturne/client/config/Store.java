package com.nocturne.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads/saves settings. Called on init, on every toggle, and whenever a
 * setting changes — so nothing is ever lost.
 */
public final class Store {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Store() {
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("nocturne");
	}

	public static Path settingsFile() {
		return dir().resolve("nocturne.json");
	}

	public static Path profilesDir() {
		return dir().resolve("profiles");
	}

	public static void load(ClientSettings settings) {
		try {
			Files.createDirectories(dir());
			Files.createDirectories(profilesDir());
		} catch (IOException ignored) {
		}
		Path file = settingsFile();
		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				ClientSettings loaded = GSON.fromJson(reader, ClientSettings.class);
				if (loaded != null) {
					settings.guiScale = clamp(loaded.guiScale, 0.75, 1.5);
					settings.opacity = clamp(loaded.opacity, 0.5, 1.0);
					settings.animations = loaded.animations;
					settings.showFps = loaded.showFps;
					settings.showPing = loaded.showPing;
					settings.theme = loaded.theme != null ? loaded.theme : "graphite";
					settings.customColor = loaded.customColor;
					settings.hudAnchor = Math.max(0, Math.min(3, loaded.hudAnchor));
					settings.hudOffsetX = Math.max(-400, Math.min(400, loaded.hudOffsetX));
					settings.hudOffsetY = Math.max(-300, Math.min(300, loaded.hudOffsetY));
					settings.zoomLevel = Math.max(2, Math.min(10, loaded.zoomLevel));
					settings.activeProfile = loaded.activeProfile != null ? loaded.activeProfile : "Default";
				}
			} catch (Exception ignored) {
			}
		}
		applyThemeQuiet(settings);
	}

	public static void save(ClientSettings settings) {
		try {
			Files.createDirectories(dir());
			try (Writer writer = Files.newBufferedWriter(settingsFile(), StandardCharsets.UTF_8)) {
				GSON.toJson(settings, writer);
			}
		} catch (IOException ignored) {
		}
	}

	public static void applyTheme(ClientSettings settings) {
		applyThemeQuiet(settings);
		save(settings);
	}

	private static void applyThemeQuiet(ClientSettings settings) {
		if (settings.theme.equalsIgnoreCase("custom")) {
			Theme.current = new Theme("custom", "Custom", settings.customColor, 0xFF5B6068);
		} else {
			Theme.current = Theme.byId(settings.theme);
		}
	}

	private static double clamp(double value, double min, double max) {
		if (Double.isNaN(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}
}
