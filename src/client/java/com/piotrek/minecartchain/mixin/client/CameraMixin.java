package com.piotrek.minecartchain.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
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

	@Unique
	private float minecartChain$lean;

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void minecartChain$leanFromMinecart(final float partialTicks, final CallbackInfo ci) {
		if (!(this.entity instanceof LocalPlayer player) || !(player.getVehicle() instanceof AbstractMinecart)) {
			this.minecartChain$lean = 0.0F;
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.options.getCameraType().isFirstPerson()) {
			this.minecartChain$lean = 0.0F;
			return;
		}

		int targetLean = 0;
		if (minecraft.options.keyLeft.isDown()) {
			targetLean--;
		}
		if (minecraft.options.keyRight.isDown()) {
			targetLean++;
		}

		this.minecartChain$lean += (targetLean - this.minecartChain$lean) * LEAN_SMOOTHING;
		if (Math.abs(this.minecartChain$lean) < 0.01F) {
			return;
		}

		this.move(0.0F, Math.abs(this.minecartChain$lean) * LEAN_RAISE, this.minecartChain$lean * LEAN_OFFSET);
	}
}
