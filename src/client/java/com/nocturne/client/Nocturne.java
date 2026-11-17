package com.nocturne.client;

import com.nocturne.client.config.ClientSettings;
import com.nocturne.client.config.Store;
import com.nocturne.client.module.Module;
import com.nocturne.client.module.ModuleManager;
import com.nocturne.client.profile.ProfileManager;

/** Global client state: settings, persistence glue, bulk-load guard. */
public final class Nocturne {
	public static final ClientSettings settings = new ClientSettings();
	public static final String VERSION = "v1.0";
	public static final String BUILD_DATE = "2026.11.17";

	private static boolean quiet;

	private Nocturne() {
	}

	public static void init() {
		Store.load(settings);
		ModuleManager.init();
		ProfileManager.ensureDefault(settings);
		setQuiet(true);
		try {
			ProfileManager.load(settings.activeProfile, settings);
		} finally {
			setQuiet(false);
		}
	}

	public static void onModulesChanged() {
		syncHudFlags();
		if (!quiet) {
			try {
				ProfileManager.save(settings.activeProfile);
			} catch (Exception ignored) {
			}
		}
	}

	/** Keeps the legacy showFps/showPing flags in step with the counter modules. */
	private static void syncHudFlags() {
		Module fps = ModuleManager.byId("fps");
		if (fps != null) {
			settings.showFps = fps.isEnabled();
		}
		Module ping = ModuleManager.byId("ping");
		if (ping != null) {
			settings.showPing = ping.isEnabled();
		}
	}

	public static void setQuiet(boolean value) {
		quiet = value;
	}
}
