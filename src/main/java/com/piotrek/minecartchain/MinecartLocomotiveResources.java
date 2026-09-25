package com.piotrek.minecartchain;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

public final class MinecartLocomotiveResources {
	public static final int COAL_FUEL_TICKS = 3600;
	public static final int MAX_FUEL_TICKS = 32000;
	public static final int WATER_TICKS_PER_BUCKET = 12000;
	public static final int MAX_WATER_TICKS = 32000;

	private MinecartLocomotiveResources() {
	}

	public static boolean isCookingFuel(final ItemStack stack) {
		return stack.has(DataComponents.COOKING_FUEL);
	}

	public static int locomotiveFuelTicks(final ServerLevel level, final ItemStack stack) {
		int burnDuration = cookingBurnDuration(level, stack);
		if (burnDuration <= 0) {
			return 0;
		}

		int coalBurnDuration = Math.max(1, cookingBurnDuration(level, new ItemStack(Items.COAL)));
		long scaledFuelTicks = (long) burnDuration * COAL_FUEL_TICKS / coalBurnDuration;
		return (int) Math.max(1L, Math.min(MAX_FUEL_TICKS, scaledFuelTicks));
	}

	private static int cookingBurnDuration(final ServerLevel level, final ItemStack stack) {
		LootParams params = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
		LootContext context = new LootContext.Builder(params).create(Optional.empty());
		return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
	}
}
