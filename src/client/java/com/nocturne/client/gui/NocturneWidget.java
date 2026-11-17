package com.nocturne.client.gui;

import com.nocturne.client.Nocturne;
import com.nocturne.client.config.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Base class for all Nocturne interface widgets. Custom-drawn dark visuals
 * with vanilla focus/click/sound plumbing inherited from AbstractWidget.
 */
public abstract class NocturneWidget extends AbstractWidget {
	protected NocturneWidget(int x, int y, int width, int height, Component message) {
		super(x, y, width, height, message);
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		drawWidget(graphics, mouseX, mouseY, partialTick);
	}

	protected abstract void drawWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		handleClick(event);
	}

	protected abstract void handleClick(MouseButtonEvent event);

	@Override
	public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}

	@Override
	public void setX(int x) {
		super.setX(x);
		syncRect();
	}

	@Override
	public void setY(int y) {
		super.setY(y);
		syncRect();
	}

	@Override
	public void setWidth(int width) {
		super.setWidth(width);
		syncRect();
	}

	@Override
	public void setHeight(int height) {
		super.setHeight(height);
		syncRect();
	}

	@Override
	public void setSize(int width, int height) {
		super.setSize(width);
		super.setHeight(height);
		syncRect();
	}

	private boolean syncingRect;

	private void syncRect() {
		// Vanilla setRectangle() itself calls back into setSize()/setX(),
		// so an unguarded sync recurses forever (StackOverflowError).
		if (syncingRect) {
			return;
		}
		syncingRect = true;
		try {
			setRectangle(getX(), getY(), getWidth(), getHeight());
		} catch (Exception ignored) {
		} finally {
			syncingRect = false;
		}
	}

	/**
	 * Click/hover hit-test straight from the live widget bounds. Vanilla's
	 * cached ScreenRectangle can go stale after programmatic moves, which
	 * made buttons look alive but never fire.
	 */
	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= getX() && mouseX <= getX() + getWidth()
			&& mouseY >= getY() && mouseY <= getY() + getHeight();
	}

	// ---------- shared drawing helpers ----------

	protected static float anim() {
		return NocturneScreen.contentAlpha();
	}

	protected static int accent() {
		return Theme.current.accent;
	}

	/** ARGB color with alpha multiplied (0..1). */
	protected static int withAlpha(int argb, float mul) {
		int a = (int) (((argb >>> 24) & 0xFF) * mul);
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	protected void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered) {
		float a = anim() * (float) Nocturne.settings.opacity;
		g.fill(x, y, x + w, y + h, withAlpha(0xE0141518, a));
		g.outline(x, y, x + w, y + h, withAlpha(hovered ? accent() : 0xFF3A3D44, a));
	}

	protected void text(GuiGraphicsExtractor g, String string, int x, int y, int color) {
		g.text(Minecraft.getInstance().font, string, x, y, withAlpha(color, anim()), true);
	}

	protected void centeredText(GuiGraphicsExtractor g, String string, int centerX, int y, int color) {
		g.centeredText(Minecraft.getInstance().font, string, centerX, y, withAlpha(color, anim()));
	}

	/** Shared toggle-pill drawing (flat gray track, white knob). */
	protected static void drawToggle(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean on, float t) {
		float a = anim();
		int track = on ? accent() : 0xFF3A3D44;
		g.fill(x, y, x + w, y + h, withAlpha(track & 0x00FFFFFF | (on ? 0xCC000000 : 0xFF000000), a));
		g.outline(x, y, x + w, y + h, withAlpha(on ? accent() : 0xFF55555F, a));
		int knob = h - 4;
		int travel = w - knob - 4;
		int from = on ? x + 2 : x + 2 + travel;
		int to = on ? x + 2 + travel : x + 2;
		int kx = (int) (from + (to - from) * t);
		g.fill(kx, y + 2, kx + 2 + knob, y + 2 + knob, withAlpha(0xFFFFFFFF, a));
	}
}
