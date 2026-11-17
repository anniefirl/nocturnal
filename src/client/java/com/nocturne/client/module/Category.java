package com.nocturne.client.module;

/** Module categories shown in the dashboard. */
public enum Category {
	HUD("HUD"),
	UTILITY("Utility");

	public final String label;

	Category(String label) {
		this.label = label;
	}
}
