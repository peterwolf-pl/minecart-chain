package com.piotrek.minecartchain;

import net.minecraft.world.item.ItemStack;

public interface MinecartLocomotiveAccess {
	int minecartChain$getLocomotiveFuelTicks();

	int minecartChain$addLocomotiveFuel(ItemStack stack);

	boolean minecartChain$canAcceptLocomotiveFuel(ItemStack stack);
}
