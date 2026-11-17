package com.nocturne.client.module.modules;

import com.nocturne.client.KeyCodes;
import com.nocturne.client.Nocturne;
import com.nocturne.client.module.Category;
import com.nocturne.client.module.Module;
import net.minecraft.client.Minecraft;

public class ZoomModule extends Module {
	private int previousFov = 70;

	public ZoomModule() {
		super("zoom", "Zoom", "Narrow the field of view for a closer look. Adjust the magnification in Settings.", Category.UTILITY, KeyCodes.KEY_C);
	}

	/** Target FOV derived from the zoom level setting (70 / level, min 7). */
	public static int targetFov() {
		int level = 4;
		try {
			level = Nocturne.settings.zoomLevel;
		} catch (Exception ignored) {
		}
		return Math.max(7, 70 / Math.max(2, Math.min(10, level)));
	}

	@Override
	protected void onEnable(Minecraft mc) {
		try {
			previousFov = mc.options.fov().get();
			mc.options.fov().set(targetFov());
		} catch (Exception ignored) {
		}
	}

	@Override
	protected void onDisable(Minecraft mc) {
		try {
			mc.options.fov().set(previousFov);
		} catch (Exception ignored) {
		}
	}

	@Override
	public void onTick(Minecraft mc) {
		// Re-apply if the player changed the zoom level mid-zoom.
		try {
			int want = targetFov();
			if (mc.options.fov().get() != want) {
				mc.options.fov().set(want);
			}
		} catch (Exception ignored) {
		}
	}
}
