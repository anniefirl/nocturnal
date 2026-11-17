package com.nocturne.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.nocturne.client.KeyCodes;
import com.nocturne.client.Nocturne;
import com.nocturne.client.NocturneClient;
import com.nocturne.client.config.ClientSettings;
import com.nocturne.client.config.Store;
import com.nocturne.client.config.Theme;
import com.nocturne.client.module.Category;
import com.nocturne.client.module.Module;
import com.nocturne.client.module.ModuleManager;
import com.nocturne.client.profile.ProfileManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Nocturne dashboard. Opens with the configurable Right Shift keybind
 * and behaves as a non-pausing overlay.
 *
 * Tabs: modules (search + toggles), keybinds (click-to-rebind), themes,
 * profiles and settings. Every control writes through to Store/ProfileManager
 * immediately, so nothing is ever lost.
 */
public class NocturneScreen extends Screen {
	public enum Tab {
		MODULES("Modules"),
		KEYBINDS("Keybinds"),
		THEMES("Themes"),
		PROFILES("Profiles"),
		SETTINGS("Settings");

		public final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private static boolean open;
	private static long openStamp;
	private static long closedStamp;
	private static NocturneScreen current;

	private Tab tab = Tab.MODULES;
	private String search = "";
	private String selectedProfile;
	private int scrollFirst;
	private String capturing;

	private final List<NocturneWidget> ui = new ArrayList<>();
	private EditBox searchBox;
	private EditBox hexBox;
	private EditBox profileBox;
	private EditBox renameBox;

	public NocturneScreen() {
		super(Component.literal("Nocturne"));
	}

	public static boolean isOpen() {
		return open;
	}

	/**
	 * True right after the menu closed, so a still-queued Right Shift
	 * press that just closed the menu can't instantly reopen it.
	 */
	public static boolean justClosed() {
		return System.currentTimeMillis() - closedStamp < 400;
	}

	public static float contentAlpha() {
		if (!Nocturne.settings.animations || current == null) {
			return 1.0f;
		}
		long age = System.currentTimeMillis() - openStamp;
		return Math.max(0.0f, Math.min(1.0f, age / 180.0f));
	}

	@Override
	public void added() {
		open = true;
		current = this;
		openStamp = System.currentTimeMillis();
		if (selectedProfile == null) {
			selectedProfile = Nocturne.settings.activeProfile;
		}
	}

	@Override
	public void removed() {
		open = false;
		current = null;
		closedStamp = System.currentTimeMillis();
		safeSave();
	}

	@Override
	public void onClose() {
		safeSave();
	}

	private static void safeSave() {
		try {
			Store.save(Nocturne.settings);
		} catch (Exception ignored) {
		}
		try {
			ProfileManager.save(Nocturne.settings.activeProfile);
		} catch (Exception ignored) {
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Keybind capture takes priority; ESC cancels the capture.
		if (capturing != null) {
			int code = keyCodeOf(event);
			if (code == InputConstants.KEY_ESCAPE) {
				capturing = null;
				layoutNow();
				return true;
			}
			if (code != InputConstants.KEY_UNKNOWN && !isAnyBoxFocused()) {
				applyCapture(code);
				capturing = null;
				layoutNow();
				return true;
			}
			return true;
		}
		if (NocturneClient.isMenuKey(event) && !isAnyBoxFocused()) {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.gui.screen() != null) {
				mc.gui.setScreen(null);
			}
			return true;
		}
		return super.keyPressed(event);
	}

	private static int keyCodeOf(KeyEvent event) {
		try {
			return event.key();
		} catch (Exception ignored) {
			return InputConstants.KEY_UNKNOWN;
		}
	}

	private void applyCapture(int code) {
		try {
			InputConstants.Key key = new InputConstants.Key(InputConstants.Type.KEYBOARD, code);
			if ("menu".equals(capturing)) {
				NocturneClient.menuKeyMapping().setKey(key);
			} else {
				Module module = ModuleManager.byId(capturing);
				if (module != null) {
					module.getKey().setKey(key);
				}
			}
			safeSave();
		} catch (Exception ignored) {
		}
	}

	private boolean isAnyBoxFocused() {
		return (searchBox != null && searchBox.isFocused())
			|| (hexBox != null && hexBox.isFocused())
			|| (profileBox != null && profileBox.isFocused())
			|| (renameBox != null && renameBox.isFocused());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		rebuildAll();
		layoutNow();
	}

	// ---------------- build / layout ----------------

	private void rebuildAll() {
		clearTab();
		switch (tab) {
			case MODULES:
				buildModules();
				break;
			case KEYBINDS:
				buildKeybinds();
				break;
			case THEMES:
				buildThemes();
				break;
			case PROFILES:
				buildProfiles();
				break;
			case SETTINGS:
				buildSettings();
				break;
		}
		// Tab bar is always present.
		for (Tab value : Tab.values()) {
			track(new TabButton(value));
		}
		track(new ScrollButton(true));
		track(new ScrollButton(false));
	}

	private void clearTab() {
		try {
			for (NocturneWidget widget : new ArrayList<>(ui)) {
				try {
					removeWidget(widget);
				} catch (Exception ignored) {
				}
			}
		} catch (Exception ignored) {
		}
		try {
			if (searchBox != null) {
				removeWidget(searchBox);
			}
			if (hexBox != null) {
				removeWidget(hexBox);
			}
			if (profileBox != null) {
				removeWidget(profileBox);
			}
			if (renameBox != null) {
				removeWidget(renameBox);
			}
		} catch (Exception ignored) {
		}
		ui.clear();
		searchBox = null;
		hexBox = null;
		profileBox = null;
		renameBox = null;
		scrollFirst = 0;
	}

	private void track(NocturneWidget widget) {
		ui.add(widget);
		try {
			addWidget(widget);
		} catch (Exception ignored) {
		}
	}

	private void trackBox(EditBox box) {
		try {
			addWidget(box);
		} catch (Exception ignored) {
		}
	}

	private double scale() {
		return Math.max(0.75, Math.min(1.5, Nocturne.settings.guiScale));
	}

	private int winW() {
		return (int) Math.min(this.width - 24, 680 * scale());
	}

	private int winH() {
		return (int) Math.min(this.height - 24, 430 * scale());
	}

	private int winX() {
		return (this.width - winW()) / 2;
	}

	private int winY() {
		return (this.height - winH()) / 2;
	}

	private void layoutNow() {
		if (ui.isEmpty()) {
			return;
		}
		switch (tab) {
			case MODULES:
				layoutModules();
				break;
			case KEYBINDS:
				layoutKeybinds();
				break;
			case THEMES:
				layoutThemes();
				break;
			case PROFILES:
				layoutProfiles();
				break;
			case SETTINGS:
				layoutSettings();
				break;
		}
		layoutChrome();
	}

	private void layoutChrome() {
		double s = scale();
		int tabW = (int) (86 * s);
		int tabH = (int) (24 * s);
		int gap = (int) (8 * s);
		int totalW = Tab.values().length * tabW + (Tab.values().length - 1) * gap;
		int startX = winX() + (winW() - totalW) / 2;
		int tabY = winY() + winH() - tabH - (int) (10 * s);
		for (NocturneWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof TabButton) {
				TabButton button = (TabButton) widget;
				int index = button.tab.ordinal();
				button.setX(startX + index * (tabW + gap));
				button.setY(tabY);
				button.setSize(tabW, tabH);
			} else if (widget instanceof ScrollButton) {
				ScrollButton button = (ScrollButton) widget;
				button.setSize((int) (30 * s), (int) (22 * s));
				button.setX(winX() + winW() - (int) (40 * s));
				button.setY(button.up ? winY() + (int) (56 * s) : winY() + (int) (82 * s));
			}
		}
	}

	// ---------------- modules tab ----------------

	private void buildModules() {
		EditBox box = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 20, Component.literal("Search"));
		box.setHint(Component.literal("Search modules..."));
		box.setMaxLength(32);
		box.setResponder(value -> {
			search = value == null ? "" : value.trim().toLowerCase();
			scrollFirst = 0;
			layoutNow();
		});
		searchBox = box;
		trackBox(box);
		for (Category category : Category.values()) {
			for (Module module : ModuleManager.getModules()) {
				if (module.getCategory() != category) {
					continue;
				}
				if (!search.isEmpty()
					&& !module.getName().toLowerCase().contains(search)
					&& !module.getDescription().toLowerCase().contains(search)) {
					continue;
				}
				track(new ModuleRow(module));
			}
		}
	}

	private void layoutModules() {
		double s = scale();
		int listX = winX() + (int) (24 * s);
		int listW = winW() - (int) (96 * s);
		if (searchBox != null) {
			searchBox.setX(listX);
			searchBox.setY(winY() + (int) (56 * s));
			searchBox.setWidth(listW);
			searchBox.setHeight((int) (20 * s));
		}
		int ry = winY() + (int) (84 * s);
		int rowH = (int) (46 * s);
		int visible = 0;
		List<ModuleRow> rows = new ArrayList<>();
		for (NocturneWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof ModuleRow) {
				rows.add((ModuleRow) widget);
			}
		}
		scrollMax(rows.size());
		for (int i = 0; i < rows.size(); i++) {
			ModuleRow row = rows.get(i);
			if (i < scrollFirst || visible >= 5) {
				row.setX(-10000);
				row.setY(-10000);
				row.setSize(listW, rowH);
			} else {
				row.setX(listX);
				row.setY(ry + visible * (rowH + (int) (8 * s)));
				row.setSize(listW, rowH);
				visible++;
			}
		}
	}

	private int scrollCap = 5;

	private void scrollMax(int total) {
		scrollCap = Math.max(0, total - 5);
		scrollFirst = Math.max(0, Math.min(scrollFirst, scrollCap));
	}

	// ---------------- keybinds tab ----------------

	private void buildKeybinds() {
		track(new KeyRow("menu", "Dashboard Menu", "Open or close the Nocturne dashboard."));
		for (Module module : ModuleManager.getModules()) {
			track(new KeyRow(module.getId(), module.getName(), module.getDescription()));
		}
	}

	private void layoutKeybinds() {
		double s = scale();
		int listX = winX() + (int) (24 * s);
		int listW = winW() - (int) (96 * s);
		int ry = winY() + (int) (60 * s);
		int rowH = (int) (34 * s);
		int visible = 0;
		List<KeyRow> rows = new ArrayList<>();
		for (NocturneWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof KeyRow) {
				rows.add((KeyRow) widget);
			}
		}
		scrollMax(rows.size());
		for (int i = 0; i < rows.size(); i++) {
			KeyRow row = rows.get(i);
			if (i < scrollFirst || visible >= 6) {
				row.setX(-10000);
				row.setY(-10000);
				row.setSize(listW, rowH);
			} else {
				row.setX(listX);
				row.setY(ry + visible * (rowH + (int) (8 * s)));
				row.setSize(listW, rowH);
				visible++;
			}
		}
	}

	// ---------------- themes tab ----------------

	private void buildThemes() {
		for (Theme theme : Theme.PRESETS) {
			track(new ThemeSwatch(theme));
		}
		track(new ThemeSwatch(null));
		EditBox box = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 20, Component.literal("Hex"));
		box.setHint(Component.literal("#B9BEC7"));
		box.setMaxLength(9);
		hexBox = box;
		trackBox(box);
		track(new HexApplyButton());
	}

	private void layoutThemes() {
		double s = scale();
		int listX = winX() + (int) (24 * s);
		int listW = winW() - (int) (96 * s);
		int sw = (listW - (int) (24 * s)) / 3;
		int sh = (int) (64 * s);
		int i = 0;
		for (NocturneWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof ThemeSwatch) {
				int col = i % 3;
				int row = i / 3;
				widget.setX(listX + col * (sw + (int) (12 * s)));
				widget.setY(winY() + (int) (60 * s) + row * (sh + (int) (12 * s)));
				widget.setSize(sw, sh);
				i++;
			}
		}
		if (hexBox != null) {
			hexBox.setX(listX);
			hexBox.setY(winY() + (int) (60 * s) + 2 * (sh + (int) (12 * s)));
			hexBox.setWidth(listW - (int) (110 * s));
			hexBox.setHeight((int) (22 * s));
		}
		for (NocturneWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof HexApplyButton) {
				widget.setX(listX + listW - (int) (100 * s));
				widget.setY(winY() + (int) (60 * s) + 2 * (sh + (int) (12 * s)));
				widget.setSize((int) (100 * s), (int) (22 * s));
			}
		}
	}

	// ---------------- profiles tab ----------------

	private void buildProfiles() {
		EditBox box = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 20, Component.literal("Name"));
		box.setHint(Component.literal("New profile name..."));
		box.setMaxLength(24);
		profileBox = box;
		trackBox(box);
		track(new CreateButton());
		EditBox rename = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 20, Component.literal("Rename"));
		rename.setHint(Component.literal("Rename selected..."));
		rename.setMaxLength(24);
		renameBox = rename;
		trackBox(rename);
		track(new RenameButton());
		refreshProfileRows();
	}

	private void refreshProfileRows() {
		try {
			for (NocturneWidget widget : new ArrayList<>(ui)) {
				if (widget instanceof ProfileRow) {
					try {
						removeWidget(widget);
					} catch (Exception ignored) {
					}
					ui.remove(widget);
				}
			}
		} catch (Exception ignored) {
		}
		for (String name : ProfileManager.list()) {
			track(new ProfileRow(name));
		}
		track(new DeleteButton());
	}

	private void layoutProfiles() {
		double s = scale();
		int listX = winX() + (int) (24 * s);
		int listW = winW() - (int) (96 * s);
		if (profileBox != null) {
			profileBox.setX(listX);
			profileBox.setY(winY() + (int) (56 * s));
			profileBox.setWidth(listW - (int) (110 * s));
			profileBox.setHeight((int) (22 * s));
		}
		if (renameBox != null) {
			renameBox.setX(listX);
			renameBox.setY(winY() + (int) (84 * s));
			renameBox.setWidth(listW - (int) (110 * s));
			renameBox.setHeight((int) (22 * s));
		}
		boolean first = true;
		for (NocturneWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof CreateButton || widget instanceof RenameButton) {
				widget.setX(listX + listW - (int) (100 * s));
				widget.setY(winY() + (int) ((first ? 56 : 84) * s));
				widget.setSize((int) (100 * s), (int) (22 * s));
				first = false;
			}
		}
		int ry = winY() + (int) (114 * s);
		int rowH = (int) (28 * s);
		int visible = 0;
		List<NocturneWidget> rows = new ArrayList<>();
		for (NocturneWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof ProfileRow || widget instanceof DeleteButton) {
				rows.add(widget);
			}
		}
		scrollMax(rows.size());
		for (int i = 0; i < rows.size(); i++) {
			NocturneWidget row = rows.get(i);
			if (i < scrollFirst || visible >= 5) {
				row.setX(-10000);
				row.setY(-10000);
				row.setSize(listW, rowH);
			} else {
				row.setX(listX);
				row.setY(ry + visible * (rowH + (int) (6 * s)));
				row.setSize(listW, rowH);
				visible++;
			}
		}
	}

	// ---------------- settings tab ----------------

	private void buildSettings() {
		ClientSettings settings = Nocturne.settings;
		track(new StepperRow("Interface Scale", () -> String.format("%.2f", settings.guiScale),
			() -> settings.guiScale = round2(Math.max(0.75, settings.guiScale - 0.05)),
			() -> settings.guiScale = round2(Math.min(1.5, settings.guiScale + 0.05))));
		track(new StepperRow("Panel Opacity", () -> Math.round(settings.opacity * 100) + "%",
			() -> settings.opacity = round2(Math.max(0.5, settings.opacity - 0.05)),
			() -> settings.opacity = round2(Math.min(1.0, settings.opacity + 0.05))));
		track(new ToggleRow("Interface Animations", () -> settings.animations, value -> settings.animations = value));
		track(new StepperRow("HUD Corner", () -> hudAnchorName(settings.hudAnchor),
			() -> settings.hudAnchor = (settings.hudAnchor + 3) % 4,
			() -> settings.hudAnchor = (settings.hudAnchor + 1) % 4));
		track(new StepperRow("HUD Offset X", () -> String.valueOf(settings.hudOffsetX),
			() -> settings.hudOffsetX = Math.max(-400, settings.hudOffsetX - 4),
			() -> settings.hudOffsetX = Math.min(400, settings.hudOffsetX + 4)));
		track(new StepperRow("HUD Offset Y", () -> String.valueOf(settings.hudOffsetY),
			() -> settings.hudOffsetY = Math.max(-300, settings.hudOffsetY - 4),
			() -> settings.hudOffsetY = Math.min(300, settings.hudOffsetY + 4)));
		track(new StepperRow("Zoom Magnification", () -> settings.zoomLevel + "x",
			() -> settings.zoomLevel = Math.max(2, settings.zoomLevel - 1),
			() -> settings.zoomLevel = Math.min(10, settings.zoomLevel + 1)));
	}

	private static String hudAnchorName(int anchor) {
		switch (anchor) {
			case 1:
				return "Top Left";
			case 2:
				return "Bottom Left";
			case 3:
				return "Bottom Right";
			default:
				return "Top Right";
		}
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private void layoutSettings() {
		double s = scale();
		int listX = winX() + (int) (24 * s);
		int listW = winW() - (int) (96 * s);
		int ry = winY() + (int) (60 * s);
		for (NocturneWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof StepperRow || widget instanceof ToggleRow) {
				widget.setX(listX);
				widget.setY(ry);
				widget.setSize(listW, (int) (30 * s));
				ry += (int) (36 * s);
			}
		}
	}

	// ---------------- render ----------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		try {
			layoutNow();
		} catch (Exception ignored) {
		}
		try {
			drawBackdrop(graphics);
			drawWindow(graphics);
			drawContent(graphics, mouseX, mouseY, partialTick);
		} catch (Exception ignored) {
		}
		try {
			for (Object child : this.children()) {
				if (child instanceof Renderable) {
					try {
						((Renderable) child).extractRenderState(graphics, mouseX, mouseY, partialTick);
					} catch (Exception ignored) {
					}
				}
			}
		} catch (Exception ignored) {
		}
	}

	private void drawBackdrop(GuiGraphicsExtractor graphics) {
		float a = NocturneWidget.anim() * (float) Nocturne.settings.opacity;
		graphics.fill(0, 0, this.width, this.height, NocturneWidget.withAlpha(0xAA050607, a * 0.6f));
	}

	private void drawWindow(GuiGraphicsExtractor graphics) {
		double s = scale();
		int x = winX();
		int y = winY();
		int w = winW();
		int h = winH();
		float a = NocturneWidget.anim() * (float) Nocturne.settings.opacity;
		graphics.fill(x, y, x + w, y + h, NocturneWidget.withAlpha(0xE0121316, a));
		graphics.fill(x, y, x + w, y + (int) (40 * s), NocturneWidget.withAlpha(0xE01A1B1F, a));
		graphics.fill(x, y + (int) (40 * s), x + w, y + (int) (41 * s), NocturneWidget.withAlpha(0xFF3A3D44, a));
		centered(graphics, "NOCTURNE", x + w / 2, y + (int) (10 * s), 0xFFF2F3F5);
		text(graphics, tab.label, x + (int) (24 * s), y + (int) (46 * s), 0xFF8A8F98);
		text(graphics, "Right Shift: close", x + w - Minecraft.getInstance().font.width("Right Shift: close") - (int) (24 * s),
			y + (int) (46 * s), 0xFF555A62);
	}

	private void centered(GuiGraphicsExtractor g, String string, int cx, int y, int color) {
		g.centeredText(Minecraft.getInstance().font, string, cx, y, NocturneWidget.withAlpha(color, NocturneWidget.anim()));
	}

	private void text(GuiGraphicsExtractor g, String string, int x, int y, int color) {
		g.text(Minecraft.getInstance().font, string, x, y, NocturneWidget.withAlpha(color, NocturneWidget.anim()), true);
	}

	private void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Custom widgets are added via addWidget for events and drawn here explicitly.
		try {
			for (NocturneWidget widget : new ArrayList<>(ui)) {
				try {
					widget.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
				} catch (Exception ignored) {
				}
			}
		} catch (Exception ignored) {
		}
	}

	// ---------------- widget classes ----------------

	private static boolean isOn(String id) {
		try {
			Module module = ModuleManager.byId(id);
			return module != null && module.isEnabled();
		} catch (Exception ignored) {
			return false;
		}
	}

	private static void setMod(String id, boolean value) {
		try {
			Module module = ModuleManager.byId(id);
			if (module != null) {
				module.setEnabled(value);
			}
		} catch (Exception ignored) {
		}
	}

	private class TabButton extends NocturneWidget {
		final Tab tab;

		TabButton(Tab tab) {
			super(0, 0, 10, 10, Component.literal(tab.label));
			this.tab = tab;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			boolean active = NocturneScreen.this.tab == tab;
			boolean hovered = isMouseOver(mx, my);
			panel(g, getX(), getY(), getWidth(), getHeight(), hovered || active);
			if (active) {
				g.fill(getX(), getY() + getHeight() - 2, getX() + getWidth(), getY() + getHeight(), withAlpha(accent(), anim()));
			}
			centeredText(g, tab.label, getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, active ? 0xFFF2F3F5 : 0xFFB9BEC7);
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (NocturneScreen.this.tab != tab) {
				NocturneScreen.this.tab = tab;
				NocturneScreen.this.capturing = null;
				NocturneScreen.this.rebuildAll();
				NocturneScreen.this.layoutNow();
			}
		}
	}

	private class ScrollButton extends NocturneWidget {
		final boolean up;

		ScrollButton(boolean up) {
			super(0, 0, 10, 10, Component.literal(up ? "Up" : "Down"));
			this.up = up;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			panel(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my));
			centeredText(g, up ? "/\\" : "\\/", getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFB9BEC7);
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (up) {
				scrollFirst = Math.max(0, scrollFirst - 1);
			} else {
				scrollFirst = Math.min(scrollCap, scrollFirst + 1);
			}
			layoutNow();
		}
	}

	private class ModuleRow extends NocturneWidget {
		final Module module;

		ModuleRow(Module module) {
			super(0, 0, 10, 10, Component.literal(module.getName()));
			this.module = module;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			boolean on = module.isEnabled();
			panel(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my));
			if (on) {
				g.fill(getX(), getY(), getX() + 2, getY() + getHeight(), withAlpha(accent(), anim()));
			}
			text(g, module.getName(), getX() + 10, getY() + 6, on ? 0xFFF2F3F5 : 0xFFB9BEC7);
			String desc = module.getDescription();
			if (desc.length() > 52) {
				desc = desc.substring(0, 52);
			}
			text(g, desc, getX() + 10, getY() + 20, 0xFF777C85);
			String extra = "";
			if ("zoom".equals(module.getId())) {
				try {
					extra = Nocturne.settings.zoomLevel + "x  ";
				} catch (Exception ignored) {
				}
			}
			text(g, extra + module.getKey().getTranslatedKeyMessage().getString(), getX() + getWidth() - 110,
				getY() + 6, 0xFF555A62);
			drawToggle(g, getX() + getWidth() - 52, getY() + (getHeight() - 16) / 2, 42, 16, on, 1.0f);
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			module.toggle();
			layoutNow();
		}
	}

	private class KeyRow extends NocturneWidget {
		final String bindId;
		final String title;
		final String hint;

		KeyRow(String bindId, String title, String hint) {
			super(0, 0, 10, 10, Component.literal(title));
			this.bindId = bindId;
			this.title = title;
			this.hint = hint;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			boolean active = bindId.equals(capturing);
			panel(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my) || active);
			text(g, title, getX() + 10, getY() + 5, 0xFFF2F3F5);
			String sub = hint;
			if (sub.length() > 48) {
				sub = sub.substring(0, 48);
			}
			text(g, sub, getX() + 10, getY() + 18, 0xFF777C85);
			String label;
			if (active) {
				label = "press key...";
			} else {
				try {
					if ("menu".equals(bindId)) {
						label = NocturneClient.menuKeyMapping().getTranslatedKeyMessage().getString();
					} else {
						Module module = ModuleManager.byId(bindId);
						label = module == null ? "?" : module.getKey().getTranslatedKeyMessage().getString();
					}
				} catch (Exception ignored) {
					label = "?";
				}
			}
			int bw = Math.max(70, Minecraft.getInstance().font.width(label) + 16);
			int bx = getX() + getWidth() - bw - 8;
			int by = getY() + (getHeight() - 20) / 2;
			g.fill(bx, by, bx + bw, by + 20, withAlpha(active ? accent() : 0xFF26272C, anim()));
			g.outline(bx, by, bx + bw, by + 20, withAlpha(0xFF3A3D44, anim()));
			centeredText(g, label, bx + bw / 2, by + 6, active ? 0xFF141518 : 0xFFF2F3F5);
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			capturing = bindId;
			layoutNow();
		}
	}

	private class ThemeSwatch extends NocturneWidget {
		final Theme theme;

		ThemeSwatch(Theme theme) {
			super(0, 0, 10, 10, Component.literal(theme == null ? "Custom" : theme.label));
			this.theme = theme;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			boolean active;
			try {
				active = theme == null ? Nocturne.settings.theme.equalsIgnoreCase("custom")
					: Nocturne.settings.theme.equalsIgnoreCase(theme.id);
			} catch (Exception ignored) {
				active = false;
			}
			panel(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my) || active);
			int preview = theme == null ? Nocturne.settings.customColor : theme.accent;
			g.fill(getX() + 8, getY() + 8, getX() + 28, getY() + getHeight() - 8, withAlpha(preview, anim()));
			text(g, theme == null ? "Custom" : theme.label, getX() + 34, getY() + 8, 0xFFF2F3F5);
			if (theme != null) {
				text(g, String.format("#%06X", theme.accent & 0xFFFFFF), getX() + 34, getY() + 22, 0xFF777C85);
			} else {
				text(g, "Use the hex field below.", getX() + 34, getY() + 22, 0xFF777C85);
			}
			if (active) {
				g.fill(getX(), getY() + getHeight() - 2, getX() + getWidth(), getY() + getHeight(), withAlpha(accent(), anim()));
			}
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (theme == null) {
				Nocturne.settings.theme = "custom";
			} else {
				Nocturne.settings.theme = theme.id;
			}
			Store.applyTheme(Nocturne.settings);
			layoutNow();
		}
	}

	private class HexApplyButton extends NocturneWidget {
		HexApplyButton() {
			super(0, 0, 10, 10, Component.literal("Apply"));
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			panel(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my));
			centeredText(g, "Apply Hex", getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFF2F3F5);
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (hexBox == null) {
				return;
			}
			String raw = hexBox.getValue().trim().replace("#", "");
			try {
				int rgb = Integer.parseInt(raw, 16) & 0xFFFFFF;
				Nocturne.settings.customColor = 0xFF000000 | rgb;
				Nocturne.settings.theme = "custom";
				Store.applyTheme(Nocturne.settings);
				layoutNow();
			} catch (Exception ignored) {
			}
		}
	}

	private class CreateButton extends NocturneWidget {
		CreateButton() {
			super(0, 0, 10, 10, Component.literal("Create"));
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			panel(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my));
			centeredText(g, "Create", getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFF2F3F5);
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (profileBox != null && !profileBox.getValue().trim().isEmpty()) {
				ProfileManager.create(profileBox.getValue());
				profileBox.setValue("");
				refreshProfileRows();
				layoutNow();
			}
		}
	}

	private class RenameButton extends NocturneWidget {
		RenameButton() {
			super(0, 0, 10, 10, Component.literal("Rename"));
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			panel(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my));
			centeredText(g, "Rename", getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFF2F3F5);
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (selectedProfile != null && renameBox != null && !renameBox.getValue().trim().isEmpty()) {
				ProfileManager.rename(selectedProfile, renameBox.getValue(), Nocturne.settings);
				selectedProfile = Nocturne.settings.activeProfile;
				renameBox.setValue("");
				refreshProfileRows();
				layoutNow();
			}
		}
	}

	private class DeleteButton extends NocturneWidget {
		DeleteButton() {
			super(0, 0, 10, 10, Component.literal("Delete selected"));
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			panel(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my));
			centeredText(g, "Delete selected profile", getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFB9BEC7);
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (selectedProfile != null && !"Default".equals(selectedProfile)) {
				ProfileManager.delete(selectedProfile);
				selectedProfile = Nocturne.settings.activeProfile;
				if (!ProfileManager.list().contains(selectedProfile)) {
					selectedProfile = "Default";
					ProfileManager.load("Default", Nocturne.settings);
				}
				refreshProfileRows();
				layoutNow();
			}
		}
	}

	private class ProfileRow extends NocturneWidget {
		final String name;

		ProfileRow(String name) {
			super(0, 0, 10, 10, Component.literal(name));
			this.name = name;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			boolean active = name.equals(Nocturne.settings.activeProfile);
			boolean selected = name.equals(selectedProfile);
			panel(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my) || selected);
			if (active) {
				g.fill(getX(), getY(), getX() + 2, getY() + getHeight(), withAlpha(accent(), anim()));
			}
			text(g, name + (active ? "  [active]" : ""), getX() + 10, getY() + (getHeight() - 8) / 2,
				active ? 0xFFF2F3F5 : 0xFFB9BEC7);
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			selectedProfile = name;
			ProfileManager.load(name, Nocturne.settings);
			layoutNow();
		}
	}

	private interface BoolSupplier {
		boolean get();
	}

	private interface BoolConsumer {
		void set(boolean value);
	}

	private interface TextSupplier {
		String get();
	}

	private interface Stepper {
		void step();
	}

	private class ToggleRow extends NocturneWidget {
		final String label;
		final BoolSupplier getter;
		final BoolConsumer setter;

		ToggleRow(String label, BoolSupplier getter, BoolConsumer setter) {
			super(0, 0, 10, 10, Component.literal(label));
			this.label = label;
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			boolean on = false;
			try {
				on = getter.get();
			} catch (Exception ignored) {
			}
			panel(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my));
			text(g, label, getX() + 10, getY() + (getHeight() - 8) / 2, 0xFFF2F3F5);
			drawToggle(g, getX() + getWidth() - 52, getY() + (getHeight() - 16) / 2, 42, 16, on, 1.0f);
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			boolean next = true;
			try {
				next = !getter.get();
			} catch (Exception ignored) {
			}
			try {
				setter.set(next);
			} catch (Exception ignored) {
			}
			Store.save(Nocturne.settings);
			layoutNow();
		}
	}

	private class StepperRow extends NocturneWidget {
		final String label;
		final TextSupplier getter;
		final Stepper down;
		final Stepper up;

		StepperRow(String label, TextSupplier getter, Stepper down, Stepper up) {
			super(0, 0, 10, 10, Component.literal(label));
			this.label = label;
			this.getter = getter;
			this.down = down;
			this.up = up;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			panel(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my));
			text(g, label, getX() + 10, getY() + (getHeight() - 8) / 2, 0xFFF2F3F5);
			String value = "?";
			try {
				value = getter.get();
			} catch (Exception ignored) {
			}
			String wide = "< " + value + " >";
			text(g, wide, getX() + getWidth() - Minecraft.getInstance().font.width(wide) - 10,
				getY() + (getHeight() - 8) / 2, 0xFFB9BEC7);
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			int third = getWidth() / 3;
			int rel = (int) event.x() - getX();
			try {
				if (rel > third * 2) {
					up.step();
				} else {
					down.step();
				}
			} catch (Exception ignored) {
			}
			Store.save(Nocturne.settings);
			layoutNow();
		}
	}
}
