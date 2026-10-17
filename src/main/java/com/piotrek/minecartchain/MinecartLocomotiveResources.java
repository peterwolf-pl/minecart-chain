package com.piotrek.minecartchain;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class MinecartLocomotiveResources {
	public static final int COAL_FUEL_TICKS = 3600;
	public static final int MAX_FUEL_TICKS = 32000;
	public static final int WATER_TICKS_PER_BUCKET = 12000;
	public static final int MAX_WATER_TICKS = 32000;

	private MinecartLocomotiveResources() {
	}

	public static boolean isCookingFuel(final Level level, final ItemStack stack) {
		return level != null && level.fuelValues().isFuel(stack);
	}

	public static int locomotiveFuelTicks(final ServerLevel level, final ItemStack stack) {
		int burnDuration = level.fuelValues().burnDuration(stack);
		if (burnDuration <= 0) {
			return 0;
		}

		int coalBurnDuration = Math.max(1, level.fuelValues().burnDuration(new ItemStack(Items.COAL)));
		long scaledFuelTicks = (long) burnDuration * COAL_FUEL_TICKS / coalBurnDuration;
		return (int) Math.max(1L, Math.min(MAX_FUEL_TICKS, scaledFuelTicks));
	}
}
