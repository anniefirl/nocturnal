package com.nocturne.client.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Grayscale accent themes. The whole GUI reads {@link #current} every frame,
 * so switching themes updates the interface immediately.
 */
public class Theme {
	public final String id;
	public final String label;
	public final int accent;
	public final int secondary;

	public static final List<Theme> PRESETS = new ArrayList<>();
	public static Theme current;

	static {
		PRESETS.add(new Theme("graphite", "Graphite", 0xFFB9BEC7, 0xFF5B6068));
		PRESETS.add(new Theme("onyx", "Onyx", 0xFFE8E8EC, 0xFF8A8F98));
		PRESETS.add(new Theme("slate", "Slate", 0xFFAEB6C2, 0xFF565C66));
		PRESETS.add(new Theme("ash", "Ash", 0xFFD7DADE, 0xFF777C85));
		current = PRESETS.get(0);
	}

	public Theme(String id, String label, int accent, int secondary) {
		this.id = id;
		this.label = label;
		this.accent = accent;
		this.secondary = secondary;
	}

	public static Theme byId(String id) {
		for (Theme theme : PRESETS) {
			if (theme.id.equalsIgnoreCase(id)) {
				return theme;
			}
		}
		return PRESETS.get(0);
	}

	/** Darker shade of the accent, for gradients and borders. */
	public int deep() {
		int r = (int) (((accent >> 16) & 0xFF) * 0.55);
		int g = (int) (((accent >> 8) & 0xFF) * 0.55);
		int b = (int) ((accent & 0xFF) * 0.55);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
