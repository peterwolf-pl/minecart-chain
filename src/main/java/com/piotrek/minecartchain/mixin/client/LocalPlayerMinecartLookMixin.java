package com.piotrek.minecartchain.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMinecartLookMixin {
	private static final float MIN_TURN = 0.05F;
	private static final float MAX_TURN = 80.0F;

	@Unique
	private int minecartChain$lookVehicleId = -1;

	@Unique
	private float minecartChain$lookVehicleHeading;

	@Inject(method = "rideTick", at = @At("TAIL"))
	private void minecartChain$followMinecartTurn(final CallbackInfo ci) {
		LocalPlayer player = (LocalPlayer) (Object) this;
		if (!(player.getVehicle() instanceof AbstractMinecart minecart)) {
			this.minecartChain$lookVehicleId = -1;
			return;
		}

		// Splinecart owns passenger yaw (it forces 90 and rotates the camera by the track basis).
		// Copying facingYaw here turns the rider 90° off the cab on east-west track ties.
		if (minecart instanceof com.piotrek.minecartchain.SteamLocomotiveEntity locomotive && locomotive.isOnMountedTrack()) {
			this.minecartChain$lookVehicleId = -1;
			return;
		}

		float heading = minecartChain$vehicleHeading(minecart);
		if (minecart.getId() != this.minecartChain$lookVehicleId) {
			this.minecartChain$lookVehicleId = minecart.getId();
			this.minecartChain$lookVehicleHeading = heading;
			return;
		}

		float delta = Mth.wrapDegrees(heading - this.minecartChain$lookVehicleHeading);
		this.minecartChain$lookVehicleHeading = heading;
		if (Math.abs(delta) > 90.0F) {
			delta -= Math.copySign(180.0F, delta);
		}
		if (Math.abs(delta) < MIN_TURN || Math.abs(delta) > MAX_TURN) {
			return;
		}

		player.setYRot(player.getYRot() + delta);
		player.yRotO += delta;
		player.setYHeadRot(player.getYHeadRot() + delta);
		player.yHeadRotO += delta;
		player.setYBodyRot(player.yBodyRot + delta);
		player.yBodyRotO += delta;
	}

	@Unique
	private static float minecartChain$vehicleHeading(final AbstractMinecart minecart) {
		if (minecart instanceof com.piotrek.minecartchain.SteamLocomotiveEntity locomotive) {
			return locomotive.getFacingYaw();
		}
		Vec3 movement = minecart.getDeltaMovement().horizontal();
		if (movement.lengthSqr() > 1.0E-5D) {
			return (float) (Mth.atan2(movement.z, movement.x) * Mth.RAD_TO_DEG);
		}
		return minecart.getYRot();
	}
}
