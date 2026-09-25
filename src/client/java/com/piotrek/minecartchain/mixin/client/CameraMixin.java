package com.piotrek.minecartchain.mixin.client;

import com.piotrek.minecartchain.SteamLocomotiveEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, priority = 1500)
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

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void minecartChain$handleCamera(final float partialTicks, final CallbackInfo ci) {
		if (!(this.entity instanceof LocalPlayer player) || !(player.getVehicle() instanceof AbstractMinecart)) {
			this.minecartChain$lean = 0.0F;
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (player.getVehicle() instanceof SteamLocomotiveEntity locomotive) {
			Vec3 forward = locomotive.getVisualForward();
			Vec3 rear = forward.scale(-1.0D);
			Vec3 locoPos = locomotive.getPosition(partialTicks);

			if (minecraft.options.getCameraType().isFirstPerson()) {
				// Handle A/D lean
				int targetLean = 0;
				if (minecraft.options.keyLeft.isDown()) {
					targetLean--;
				}
				if (minecraft.options.keyRight.isDown()) {
					targetLean++;
				}
				this.minecartChain$lean += (targetLean - this.minecartChain$lean) * LEAN_SMOOTHING;

				// Place eyes inside the driver's cab, looking out through the front opening and side windows
				this.setPosition(locoPos.add(rear.scale(0.80D)).add(0.0D, 1.55D, 0.0D));

				if (Math.abs(this.minecartChain$lean) >= 0.01F) {
					this.move(0.0F, Math.abs(this.minecartChain$lean) * LEAN_RAISE, this.minecartChain$lean * LEAN_OFFSET);
				}
			} else {
				// Third person (F5) - anchor high above the locomotive to bypass cab clipping and Splinecart eye override
				this.minecartChain$lean = 0.0F;
				this.setPosition(locoPos.add(0.0D, 1.8D, 0.0D));

				if (minecraft.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK) {
					// Orbit safely behind the locomotive
					this.move(-6.2F, 0.8F, 0.0F);
				} else if (minecraft.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_FRONT) {
					// Orbit safely in front of the locomotive
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
		}
	}
}
