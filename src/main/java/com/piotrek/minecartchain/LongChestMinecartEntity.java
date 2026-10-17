package com.piotrek.minecartchain;

import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Two-block cargo wagon with a persistent, fully accessible 54-slot inventory. */
public class LongChestMinecartEntity extends AbstractMinecartContainer {
	private NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);
	private float wheelRotation;
	private float previousWheelRotation;

	public LongChestMinecartEntity(final EntityType<? extends LongChestMinecartEntity> type, final Level level) {
		super(type, level);
	}

	@Override public int getContainerSize() { return 54; }
	@Override public NonNullList<ItemStack> getItemStacks() { return this.items; }
	@Override public void clearItemStacks() { this.items = NonNullList.withSize(54, ItemStack.EMPTY); }
	@Override protected Item getDropItem() { return MinecartChainMod.LONG_CHEST_MINECART_ITEM.get(); }
	@Override public ItemStack getPickResult() { return new ItemStack(this.getDropItem()); }
	@Override public AbstractContainerMenu createMenu(final int id, final Inventory inventory) {
		return ChestMenu.sixRows(id, inventory, this);
	}
	@Override public Component getDisplayName() {
		return this.hasCustomName() ? this.getCustomName()
			: Component.translatable("container.minecart_chain.long_chest_minecart");
	}

	public float getWheelRotation(final float partialTick) {
		return Mth.lerp(partialTick, this.previousWheelRotation, this.wheelRotation);
	}

	@Override public void tick() {
		this.previousWheelRotation = this.wheelRotation;
		super.tick();
		SteamLocomotiveEntity locomotive = MinecartTrainLogic.connectedSteamLocomotive(this);
		Vec3 movement = MinecartTrainLogic.horizontalMovement(locomotive != null ? locomotive : this);
		double distance = movement.length();
		if (locomotive == null && distance <= 0.0015D) {
			movement = new Vec3(this.getX() - this.xOld, 0.0D, this.getZ() - this.zOld);
			distance = movement.length();
		}
		if (distance > 0.0015D) {
			double yaw = Math.toRadians(this.getYRot());
			float sign = locomotive != null ? (locomotive.isReverser() ? 1.0F : -1.0F)
				: movement.dot(new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw))) >= 0.0D ? -1.0F : 1.0F;
			this.wheelRotation += (float) (sign * distance / 0.15625D);
		}
	}
}
