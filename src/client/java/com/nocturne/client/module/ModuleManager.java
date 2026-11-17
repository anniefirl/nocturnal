package com.nocturne.client.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.nocturne.client.module.modules.CpsModule;
import com.nocturne.client.module.modules.FpsModule;
import com.nocturne.client.module.modules.FullbrightModule;
import com.nocturne.client.module.modules.HudModule;
import com.nocturne.client.module.modules.PingModule;
import com.nocturne.client.module.modules.ZoomModule;
import net.minecraft.client.Minecraft;

public final class ModuleManager {
	private static final List<Module> MODULES = new ArrayList<>();

	private ModuleManager() {
	}

	public static void init() {
		MODULES.clear();
		add(FpsModule::new);
		add(PingModule::new);
		add(CpsModule::new);
		add(HudModule::new);
		add(FullbrightModule::new);
		add(ZoomModule::new);
		// First run: keep the HUD the settings describe. Returning players
		// keep whatever their profile saved, since load() overrides these.
		setInitial("fps", com.nocturne.client.Nocturne.settings.showFps);
		setInitial("ping", com.nocturne.client.Nocturne.settings.showPing);
		setInitial("hud", true);
	}

	/** One broken module must never take down the whole menu. */
	private static void add(Supplier<Module> factory) {
		try {
			MODULES.add(factory.get());
		} catch (Exception e) {
			System.out.println("[Nocturne] skipping broken module: " + e);
		}
	}

	public static void setInitial(String id, boolean value) {
		Module module = byId(id);
		if (module != null) {
			module.setEnabled(value, false);
		}
	}

	public static List<Module> getModules() {
		return Collections.unmodifiableList(MODULES);
	}

	public static Module byId(String id) {
		for (Module module : MODULES) {
			if (module.getId().equals(id)) {
				return module;
			}
		}
		return null;
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		for (Module module : MODULES) {
			module.handleKey();
			if (module.isEnabled()) {
				try {
					module.onTick(mc);
				} catch (Exception ignored) {
				}
			}
		}
	}
}
