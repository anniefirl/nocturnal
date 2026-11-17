package com.nocturne.client.profile;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nocturne.client.Nocturne;
import com.nocturne.client.config.ClientSettings;
import com.nocturne.client.config.Store;
import com.nocturne.client.module.Module;
import com.nocturne.client.module.ModuleManager;

/**
 * Named configuration profiles. Each profile stores module states plus the
 * HUD, zoom and theme settings, so switching profiles swaps the whole setup.
 */
public final class ProfileManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private ProfileManager() {
	}

	/** Snapshot written to each profile file. */
	public static class ProfileData {
		public Map<String, Boolean> modules = new HashMap<>();
		public String theme = "graphite";
		public int customColor = 0xFFB9BEC7;
		public int hudAnchor = 0;
		public int hudOffsetX = 0;
		public int hudOffsetY = 0;
		public int zoomLevel = 4;
		public double guiScale = 1.0;
		public double opacity = 0.92;
		public boolean animations = true;
	}

	public static List<String> list() {
		List<String> names = new ArrayList<>();
		try {
			Files.createDirectories(Store.profilesDir());
			try (Stream<Path> stream = Files.list(Store.profilesDir())) {
				stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
					String name = p.getFileName().toString();
					names.add(name.substring(0, name.length() - 5));
				});
			}
		} catch (IOException ignored) {
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	public static void ensureDefault(ClientSettings settings) {
		try {
			Files.createDirectories(Store.profilesDir());
			Path file = Store.profilesDir().resolve("Default.json");
			if (!Files.isRegularFile(file)) {
				save("Default");
			}
		} catch (Exception ignored) {
		}
		if (settings.activeProfile == null || settings.activeProfile.isEmpty()) {
			settings.activeProfile = "Default";
		}
	}

	public static void save(String name) {
		if (name == null || name.trim().isEmpty()) {
			return;
		}
		try {
			Files.createDirectories(Store.profilesDir());
			ProfileData data = new ProfileData();
			for (Module module : ModuleManager.getModules()) {
				data.modules.put(module.getId(), module.isEnabled());
			}
			ClientSettings settings = Nocturne.settings;
			data.theme = settings.theme;
			data.customColor = settings.customColor;
			data.hudAnchor = settings.hudAnchor;
			data.hudOffsetX = settings.hudOffsetX;
			data.hudOffsetY = settings.hudOffsetY;
			data.zoomLevel = settings.zoomLevel;
			data.guiScale = settings.guiScale;
			data.opacity = settings.opacity;
			data.animations = settings.animations;
			try (Writer writer = Files.newBufferedWriter(Store.profilesDir().resolve(name + ".json"), StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
		} catch (Exception ignored) {
		}
	}

	public static void load(String name, ClientSettings settings) {
		if (name == null || name.trim().isEmpty()) {
			return;
		}
		Path file = Store.profilesDir().resolve(name + ".json");
		if (!Files.isRegularFile(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			ProfileData data = GSON.fromJson(reader, ProfileData.class);
			if (data == null) {
				return;
			}
			Nocturne.setQuiet(true);
			try {
				if (data.modules != null) {
					for (Map.Entry<String, Boolean> entry : data.modules.entrySet()) {
						Module module = ModuleManager.byId(entry.getKey());
						if (module != null && entry.getValue() != null) {
							module.setEnabled(entry.getValue(), false);
						}
					}
				}
				if (data.theme != null) {
					settings.theme = data.theme;
				}
				settings.customColor = data.customColor;
				settings.hudAnchor = Math.max(0, Math.min(3, data.hudAnchor));
				settings.hudOffsetX = Math.max(-400, Math.min(400, data.hudOffsetX));
				settings.hudOffsetY = Math.max(-300, Math.min(300, data.hudOffsetY));
				settings.zoomLevel = Math.max(2, Math.min(10, data.zoomLevel));
				settings.guiScale = Math.max(0.75, Math.min(1.5, data.guiScale));
				settings.opacity = Math.max(0.5, Math.min(1.0, data.opacity));
				settings.animations = data.animations;
				Store.applyTheme(settings);
			} finally {
				Nocturne.setQuiet(false);
			}
			settings.activeProfile = name;
			Store.save(settings);
		} catch (Exception ignored) {
		}
	}

	public static void create(String name) {
		if (name == null || name.trim().isEmpty() || list().contains(name.trim())) {
			return;
		}
		save(name.trim());
	}

	public static void delete(String name) {
		if (name == null || name.equals("Default")) {
			return;
		}
		try {
			Files.deleteIfExists(Store.profilesDir().resolve(name + ".json"));
		} catch (IOException ignored) {
		}
	}

	public static void rename(String oldName, String newName, ClientSettings settings) {
		if (oldName == null || newName == null || newName.trim().isEmpty() || list().contains(newName.trim())) {
			return;
		}
		Path from = Store.profilesDir().resolve(oldName + ".json");
		Path to = Store.profilesDir().resolve(newName.trim() + ".json");
		try {
			Files.move(from, to);
		} catch (IOException ignored) {
			return;
		}
		if (oldName.equals(settings.activeProfile)) {
			settings.activeProfile = newName.trim();
			Store.save(settings);
		}
	}
}
