package com.nocturne.client.config;

/** All persisted client settings. Saved as JSON, loaded on startup. */
public class ClientSettings {
	public double guiScale = 1.0;
	public double opacity = 0.92;
	public boolean animations = true;
	public boolean showFps = true;
	public boolean showPing = true;
	public String theme = "graphite";
	public int customColor = 0xFFB9BEC7;
	/** HUD stack corner: 0 = top-right, 1 = top-left, 2 = bottom-left, 3 = bottom-right. */
	public int hudAnchor = 0;
	public int hudOffsetX = 0;
	public int hudOffsetY = 0;
	/** Zoom magnification level, 2-10. Effective FOV = 70 / zoomLevel. */
	public int zoomLevel = 4;
	public String activeProfile = "Default";
}
