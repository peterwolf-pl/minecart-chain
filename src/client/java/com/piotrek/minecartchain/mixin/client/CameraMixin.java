package com.piotrek.minecartchain.mixin.client;

import com.piotrek.minecartchain.SteamLocomotiveEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, priority = 2000)
public abstract class CameraMixin {
	@Unique
	private static final float LEAN_OFFSET = 0.7F;
	@Unique
	private static final float LEAN_RAISE = 0.06F;
	@Unique
	private static final float LEAN_SMOOTHING = 0.35F;

	@Shadow
	private Entity entity;

	@Shadow
	protected abstract void move(float forwards, float up, float right);

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Unique
	private float minecartChain$lean;

	@Unique
	private float minecartChain$cabYaw;

	@Unique
	private boolean minecartChain$hasCabYaw;

	@Inject(method = "setup", at = @At("TAIL"))
	private void minecartChain$handleCamera(
		final net.minecraft.world.level.Level level,
		final Entity entity,
		final boolean detached,
		final boolean mirrored,
		final float partialTicks,
		final CallbackInfo ci
	) {
		if (!(this.entity instanceof LocalPlayer player) || !(player.getVehicle() instanceof AbstractMinecart)) {
			this.minecartChain$lean = 0.0F;
			this.minecartChain$hasCabYaw = false;
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (player.getVehicle() instanceof SteamLocomotiveEntity locomotive) {
			Vec3 forward = minecartChain$smoothedForward(locomotive);
			Vec3 rear = forward.scale(-1.0D);
			Vec3 locoPos = locomotive.getPosition(partialTicks);

			if (minecraft.options.getCameraType().isFirstPerson()) {
				int targetLean = 0;
				if (minecraft.options.keyLeft.isDown()) {
					targetLean--;
				}
				if (minecraft.options.keyRight.isDown()) {
					targetLean++;
				}
				this.minecartChain$lean += (targetLean - this.minecartChain$lean) * LEAN_SMOOTHING;

				// Same cab seat as before: 0.80 behind the boiler, eyes at window height.
				// Yaw is smoothed so a curve does not snap this offset between packets.
				this.setPosition(locoPos.add(rear.scale(0.80D)).add(0.0D, 1.55D, 0.0D));

				if (Math.abs(this.minecartChain$lean) >= 0.01F) {
					this.move(0.0F, Math.abs(this.minecartChain$lean) * LEAN_RAISE, this.minecartChain$lean * LEAN_OFFSET);
				}
			} else {
				this.minecartChain$lean = 0.0F;
				this.setPosition(locoPos.add(0.0D, 1.8D, 0.0D));

				if (minecraft.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK) {
					this.move(-6.2F, 0.8F, 0.0F);
				} else if (minecraft.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_FRONT) {
					this.move(6.2F, 0.8F, 0.0F);
				}
			}
			return;
		}

		// Generic minecart lean in first person
		if (minecraft.options.getCameraType().isFirstPerson()) {
			int targetLean = 0;
			if (minecraft.options.keyLeft.isDown()) {
				targetLean--;
			}
			if (minecraft.options.keyRight.isDown()) {
				targetLean++;
			}
			this.minecartChain$lean += (targetLean - this.minecartChain$lean) * LEAN_SMOOTHING;
			if (Math.abs(this.minecartChain$lean) >= 0.01F) {
				this.move(0.0F, Math.abs(this.minecartChain$lean) * LEAN_RAISE, this.minecartChain$lean * LEAN_OFFSET);
			}
		} else {
			this.minecartChain$lean = 0.0F;
			this.minecartChain$hasCabYaw = false;
		}
	}

	@Unique
	private Vec3 minecartChain$smoothedForward(final SteamLocomotiveEntity locomotive) {
		float target = locomotive.getFacingYaw();
		if (!this.minecartChain$hasCabYaw) {
			this.minecartChain$cabYaw = target;
			this.minecartChain$hasCabYaw = true;
		} else {
			float diff = Mth.wrapDegrees(target - this.minecartChain$cabYaw);
			if (Math.abs(diff) > 110.0F) {
				this.minecartChain$cabYaw = target;
			} else {
				float dt = Minecraft.getInstance().getDeltaTracker().getRealtimeDeltaTicks();
				float alpha = 1.0F - (float) Math.exp(-Math.max(0.0F, dt) * 8.0F);
				this.minecartChain$cabYaw = Mth.wrapDegrees(this.minecartChain$cabYaw + diff * alpha);
			}
		}
		double yawRad = Math.toRadians(this.minecartChain$cabYaw);
		return new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
	}
}
