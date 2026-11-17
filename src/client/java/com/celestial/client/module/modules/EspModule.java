package com.celestial.client.module.modules;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.celestial.client.KeyCodes;
import com.celestial.client.module.Category;
import com.celestial.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Entity ESP using the vanilla glowing flag without overwriting existing glow states. */
public class EspModule extends Module {
	private final Set<Entity> changedEntities = Collections.newSetFromMap(new IdentityHashMap<>());

	public EspModule() {
		super("esp", "EntityESP", "Outline entities through walls.", Category.VISUAL, KeyCodes.KEY_J);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) return;
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity != mc.player && entity instanceof LivingEntity && !entity.hasGlowingTag()) {
				entity.setGlowingTag(true);
				changedEntities.add(entity);
			}
		}
	}

	@Override
	protected void onDisable(Minecraft mc) {
		for (Entity entity : changedEntities) {
			if (entity != null) entity.setGlowingTag(false);
		}
		changedEntities.clear();
	}
}
