package com.piotrek.minecartchain;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class LongMinecartEntity extends Minecart {
	private float wheelRotation;
	private float previousWheelRotation;

	public LongMinecartEntity(final EntityType<? extends LongMinecartEntity> entityType, final Level level) {
		super(entityType, level);
	}

	public float getWheelRotation(final float partialTick) {
		return Mth.lerp(partialTick, this.previousWheelRotation, this.wheelRotation);
	}

	@Override
	public void tick() {
		this.previousWheelRotation = this.wheelRotation;
		super.tick();

		SteamLocomotiveEntity locomotive = MinecartTrainLogic.connectedSteamLocomotive(this);
		Vec3 movement = locomotive != null
			? MinecartTrainLogic.horizontalMovement(locomotive)
			: MinecartTrainLogic.horizontalMovement(this);
		double distance = movement.length();
		if (locomotive == null && distance <= 0.0015D) {
			double dx = this.getX() - this.xOld;
			double dz = this.getZ() - this.zOld;
			distance = Math.sqrt(dx * dx + dz * dz);
			movement = new Vec3(dx, 0.0D, dz);
		}
		if (distance > 0.0015D) {
			float direction;
			if (locomotive != null) {
				direction = locomotive.isReverser() ? 1.0F : -1.0F;
			} else {
				double yawRadians = Math.toRadians(this.getYRot());
				Vec3 facing = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
				direction = movement.dot(facing) >= 0.0D ? -1.0F : 1.0F;
			}
			this.wheelRotation += (float) (direction * distance / 0.15625D);
		}
	}

	@Override
	protected Item getDropItem() {
		return MinecartChainMod.LONG_MINECART_ITEM;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(MinecartChainMod.LONG_MINECART_ITEM);
	}
}
