package com.nocturne.client.module.modules;

import com.nocturne.client.KeyCodes;
import com.nocturne.client.module.Category;
import com.nocturne.client.module.Module;

/** HUD overlay module: shows server ping. Rendering lives in NocturneClient. */
public class PingModule extends Module {
	public PingModule() {
		super("ping", "Ping Counter", "Display your latency to the server in milliseconds.", Category.HUD, KeyCodes.KEY_N);
	}
}
