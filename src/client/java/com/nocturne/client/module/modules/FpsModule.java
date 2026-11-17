package com.nocturne.client.module.modules;

import com.nocturne.client.KeyCodes;
import com.nocturne.client.module.Category;
import com.nocturne.client.module.Module;

/** HUD overlay module: shows FPS. Rendering lives in NocturneClient. */
public class FpsModule extends Module {
	public FpsModule() {
		super("fps", "FPS Counter", "Display your frames per second in-game.", Category.HUD, KeyCodes.KEY_H);
	}
}
