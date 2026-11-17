package com.nocturne.client;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Default key binds, as SDL scancodes (what Type.KEYBOARD expects in 26.3).
 * References Mojang's own KEY_* constants so they can never drift out of sync.
 */
public final class KeyCodes {
	private KeyCodes() {
	}

	public static final int KEY_C = InputConstants.KEY_C;
	public static final int KEY_H = InputConstants.KEY_H;
	public static final int KEY_J = InputConstants.KEY_J;
	public static final int KEY_N = InputConstants.KEY_N;
	public static final int KEY_P = InputConstants.KEY_P;
	public static final int KEY_RSHIFT = InputConstants.KEY_RSHIFT;
	public static final int KEY_U = InputConstants.KEY_U;
}
