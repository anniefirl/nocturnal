package com.nocturne.client.module.modules;

import com.nocturne.client.KeyCodes;
import com.nocturne.client.module.Category;
import com.nocturne.client.module.Module;

/** Master switch for the whole HUD overlay (FPS, ping, CPS lines). */
public class HudModule extends Module {
	public HudModule() {
		super("hud", "HUD Overlay", "Master switch for all HUD counter lines.", Category.HUD, KeyCodes.KEY_J);
	}
}
