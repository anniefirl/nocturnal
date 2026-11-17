package com.nocturne.client.module.modules;

import com.nocturne.client.KeyCodes;
import com.nocturne.client.module.Category;
import com.nocturne.client.module.Module;
import net.minecraft.client.Minecraft;

public class FullbrightModule extends Module {
	private double previousGamma = 1.0;

	public FullbrightModule() {
		super("fullbright", "Fullbright", "Maximum brightness everywhere, even in caves.", Category.UTILITY, KeyCodes.KEY_P);
	}

	@Override
	protected void onEnable(Minecraft mc) {
		try {
			previousGamma = mc.options.gamma().get();
			mc.options.gamma().set(16.0);
		} catch (Exception ignored) {
		}
	}

	@Override
	protected void onDisable(Minecraft mc) {
		try {
			mc.options.gamma().set(previousGamma);
		} catch (Exception ignored) {
		}
	}
}
