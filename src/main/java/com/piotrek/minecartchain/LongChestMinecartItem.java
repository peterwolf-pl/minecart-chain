package com.piotrek.minecartchain;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

public class LongChestMinecartItem extends LongMinecartItem {
	public LongChestMinecartItem(final Properties properties) { super(properties); }

	@Override protected EntityType<? extends AbstractMinecart> cartType() {
		return MinecartChainMod.LONG_CHEST_MINECART_ENTITY.get();
	}
}
