package com.nocturne.mixin;

import com.nocturne.client.gui.NocturneScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla main menu with the Nocturne dashboard: dark backdrop,
 * brand header, and working buttons for singleplayer, multiplayer, options
 * and the client dashboard.
 */
@Mixin(net.minecraft.client.gui.screens.TitleScreen.class)
public class TitleScreenMixin {
	@Shadow
	protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget);

	private boolean nocturne$styled;

	@Inject(method = "init", at = @At("TAIL"), require = 0)
	private void nocturne$dashboard(CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		if (nocturne$styled) {
			return;
		}
		nocturne$styled = true;
		try {
			for (Object child : new java.util.ArrayList<>(self.children())) {
				if (child instanceof AbstractWidget && !(child instanceof Button && ((Button) child).getMessage().getString().startsWith("Nocturne"))) {
					((AbstractWidget) child).visible = false;
				}
			}
		} catch (Exception ignored) {
		}
		try {
			int cx = self.width / 2;
			int y0 = self.height / 2 - 30;
			addRenderableWidget(Button.builder(Component.literal("Singleplayer"), button -> {
				Minecraft mc = Minecraft.getInstance();
				mc.setScreenAndShow(new SelectWorldScreen(self));
			}).bounds(cx - 150, y0, 145, 24).build());
			addRenderableWidget(Button.builder(Component.literal("Multiplayer"), button -> {
				Minecraft mc = Minecraft.getInstance();
				mc.setScreenAndShow(new JoinMultiplayerScreen(self));
			}).bounds(cx + 5, y0, 145, 24).build());
			addRenderableWidget(Button.builder(Component.literal("Options"), button -> {
				Minecraft mc = Minecraft.getInstance();
				mc.setScreenAndShow(new OptionsScreen(self, mc.options));
			}).bounds(cx - 150, y0 + 30, 145, 24).build());
			addRenderableWidget(Button.builder(Component.literal("Nocturne"), button -> {
				Minecraft mc = Minecraft.getInstance();
				mc.setScreenAndShow(new NocturneScreen());
			}).bounds(cx + 5, y0 + 30, 145, 24).build());
			addRenderableWidget(Button.builder(Component.literal("Quit Game"), button -> {
				Minecraft.getInstance().stop();
			}).bounds(cx - 100, y0 + 64, 200, 22).build());
		} catch (Exception ignored) {
		}
	}

	@Inject(method = "render", at = @At("HEAD"), require = 0)
	private void nocturne$backdrop(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		try {
			Minecraft mc = Minecraft.getInstance();
			graphics.fill(0, 0, self.width, self.height, 0xF20B0C0E);
			graphics.centeredText(mc.font, Component.literal("NOCTURNE"), self.width / 2, self.height / 2 - 90, 0xFFF2F3F5);
			graphics.centeredText(mc.font, Component.literal("black-and-gray utility client  \u00B7  " + com.nocturne.client.Nocturne.VERSION),
				self.width / 2, self.height / 2 - 72, 0xFF777C85);
			graphics.centeredText(mc.font, Component.literal("Right Shift opens the dashboard in-game"),
				self.width / 2, self.height / 2 + 100, 0xFF555A62);
		} catch (Exception ignored) {
		}
	}
}
