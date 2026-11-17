package com.nocturne.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nocturne.client.config.Theme;
import com.nocturne.client.gui.NocturneScreen;
import com.nocturne.client.module.Module;
import com.nocturne.client.module.ModuleManager;
import com.nocturne.client.module.modules.CpsModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;

public class NocturneClient implements ClientModInitializer {
	private static KeyMapping menuKey;

	public static String extraKeyName() {
		try {
			if (menuKey == null || menuKey.isUnbound()) {
				return "NONE";
			}
			return menuKey.getTranslatedKeyMessage().getString();
		} catch (Exception e) {
			return "?";
		}
	}

	public static KeyMapping menuKeyMapping() {
		return menuKey;
	}

	/** True when the pressed key matches the dashboard toggle bind (Right Shift by default). */
	public static boolean isMenuKey(KeyEvent event) {
		try {
			return menuKey != null && event != null && menuKey.matches(event);
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public void onInitializeClient() {
		Nocturne.init();

		// Dashboard toggle. Right Shift by default, rebindable under
		// Options > Controls > Nocturne. ESC is left fully vanilla.
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.nocturne.menu",
			InputConstants.Type.KEYBOARD,
			KeyCodes.KEY_RSHIFT,
			KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				return;
			}
			while (menuKey.consumeClick()) {
				// Open-only: closing is handled inside NocturneScreen.keyPressed
				// (Right Shift / ESC), which is focus-aware.
				if (NocturneScreen.isOpen() || NocturneScreen.justClosed()) {
					continue;
				}
				Screen current = null;
				try {
					current = client.gui.screen();
				} catch (Exception ignored) {
				}
				if (current == null) {
					client.gui.setScreen(new NocturneScreen());
				}
			}
			ModuleManager.tick(client);
		});

		HudElementRegistry.addLast(Identifier.withDefaultNamespace("nocturne_hud"), (graphics, deltaTracker) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.level == null) {
				return;
			}
			if (!isHudOn("hud")) {
				return;
			}
			java.util.List<String> lines = new java.util.ArrayList<>();
			boolean showFps = isHudOn("fps");
			boolean showPing = isHudOn("ping");
			boolean showCps = isHudOn("cps");
			StringBuilder first = new StringBuilder();
			if (showFps) {
				first.append("FPS: ").append(mc.getFps());
			}
			if (showFps && showPing) {
				first.append(" | ");
			}
			if (showPing) {
				first.append("Ping: ").append(pingText(mc));
			}
			if (first.length() > 0) {
				lines.add(first.toString());
			}
			if (showCps) {
				String cps;
				try {
					cps = "CPS: " + CpsModule.getLeftCps() + " | " + CpsModule.getRightCps();
				} catch (Exception ignored) {
					cps = "CPS: --";
				}
				lines.add(cps);
			}
			if (lines.isEmpty()) {
				return;
			}
			int anchor = 0;
			int offX = 0;
			int offY = 0;
			int underline = 0xFF5B6068;
			try {
				anchor = Nocturne.settings.hudAnchor;
				offX = Nocturne.settings.hudOffsetX;
				offY = Nocturne.settings.hudOffsetY;
				if (Theme.current != null) {
					underline = Theme.current.secondary;
				}
			} catch (Exception ignored) {
			}
			boolean right = anchor == 0 || anchor == 3;
			int screenW = 854;
			int screenH = 480;
			try {
				screenW = mc.getWindow().getGuiScaledWidth();
				screenH = mc.getWindow().getGuiScaledHeight();
			} catch (Exception ignored) {
			}
			int count = lines.size();
			for (int i = 0; i < count; i++) {
				String line = lines.get(i);
				int width = 0;
				try {
					width = mc.font.width(line);
				} catch (Exception ignored) {
					width = line.length() * 6;
				}
				int x;
				int y;
				if (anchor <= 1) {
					y = 3 + offY + i * 16;
				} else {
					y = screenH - 3 - 14 - offY - (count - 1 - i) * 16;
				}
				if (right) {
					x = screenW - 6 - offX - width - 4;
				} else {
					x = 6 + offX;
				}
				graphics.fill(x - 4, y, x + width + 4, y + 14, 0x80000000);
				graphics.fill(x - 4, y + 13, x + width + 4, y + 14, underline);
				graphics.text(mc.font, line, x, y + 3, 0xFFF2F3F5, true);
			}
		});
	}

	private static boolean isHudOn(String id) {
		try {
			Module module = ModuleManager.byId(id);
			return module != null && module.isEnabled();
		} catch (Exception ignored) {
			return false;
		}
	}

	private static String pingText(Minecraft mc) {
		try {
			if (mc.getConnection() != null && mc.player != null) {
				PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
				if (info != null) {
					return info.getLatency() + "ms";
				}
			}
			// Singleplayer: no remote latency available, so report 0ms
			// instead of "--" (which looks like a broken display).
			if (mc.hasSingleplayerServer()) {
				return "0ms";
			}
		} catch (Exception ignored) {
		}
		return "--";
	}
}
