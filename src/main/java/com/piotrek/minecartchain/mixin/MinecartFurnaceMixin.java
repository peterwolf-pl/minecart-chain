package com.piotrek.minecartchain.mixin;

import com.piotrek.minecartchain.MinecartChainAccess;
import com.piotrek.minecartchain.MinecartLocomotiveAccess;
import com.piotrek.minecartchain.MinecartLocomotiveResources;
import com.piotrek.minecartchain.MinecartTrainLogic;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecartFurnace.class)
public abstract class MinecartFurnaceMixin implements MinecartLocomotiveAccess {
	@Shadow
	private int fuel;

	@Shadow
	public Vec3 push;

	@Shadow
	protected abstract void setHasFuel(boolean hasFuel);

	@Unique
	private int minecartChain$fuelBeforeTick;

	@Inject(method = "tick", at = @At("HEAD"))
	private void minecartChain$captureFuelBeforeTick(final CallbackInfo ci) {
		MinecartFurnace furnace = (MinecartFurnace) (Object) this;
		if (!furnace.level().isClientSide()) {
			this.minecartChain$fuelBeforeTick = this.fuel;
		}
	}

	@Redirect(
		method = "tick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V")
	)
	private void minecartChain$suppressVanillaSmoke(
		final Level level,
		final ParticleOptions particle,
		final double x,
		final double y,
		final double z,
		final double xSpeed,
		final double ySpeed,
		final double zSpeed
	) {
		MinecartFurnace furnace = (MinecartFurnace) (Object) this;
		if (!((MinecartChainAccess) furnace).minecartChain$hasEngineLever()) {
			level.addParticle(particle, x, y, z, xSpeed, ySpeed, zSpeed);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void minecartChain$tickEngine(final CallbackInfo ci) {
		MinecartFurnace furnace = (MinecartFurnace) (Object) this;
		MinecartChainAccess data = (MinecartChainAccess) furnace;
		if (furnace.level().isClientSide() || !data.minecartChain$hasEngineLever()) {
			return;
		}

		this.fuel = Math.max(0, this.minecartChain$fuelBeforeTick);
		Vec3 direction = MinecartTrainLogic.drivingDirection(furnace);
		MinecartTrainLogic.updateLocomotiveYaw(furnace, direction);

		if (data.minecartChain$isEngineActive()) {
			this.push = Vec3.ZERO;
			MinecartTrainLogic.applyTrainBrake(furnace);
			this.setHasFuel(false);
			return;
		}

		if (direction.lengthSqr() <= 1.0E-5D || this.fuel <= 0 || data.minecartChain$getWaterTicks() <= 0) {
			this.push = Vec3.ZERO;
			this.setHasFuel(false);
			return;
		}

		boolean fullThrottle = data.minecartChain$isFullThrottle();
		this.push = MinecartTrainLogic.enginePushVector(direction, fullThrottle);
		MinecartTrainLogic.applyEngineAssist(furnace, direction, fullThrottle);
		MinecartTrainLogic.guidePoweredTrain(furnace, direction);
		MinecartTrainLogic.emitLocomotiveSmoke(furnace);
		this.fuel = Math.max(0, this.fuel - MinecartTrainLogic.locomotiveFuelCost(furnace));
		data.minecartChain$setWaterTicks(data.minecartChain$getWaterTicks() - MinecartTrainLogic.locomotiveWaterCost(furnace));
		this.setHasFuel(true);
	}

	@Override
	public int minecartChain$getLocomotiveFuelTicks() {
		return this.fuel;
	}

	@Override
	public int minecartChain$addLocomotiveFuel(final ItemStack stack) {
		int fuelTicks = this.minecartChain$fuelTicksFor(stack);
		if (fuelTicks <= 0) {
			return 0;
		}

		int acceptedFuelTicks = Math.min(fuelTicks, Math.max(0, MinecartLocomotiveResources.MAX_FUEL_TICKS - this.fuel));
		if (acceptedFuelTicks <= 0) {
			return 0;
		}

		this.fuel += acceptedFuelTicks;
		this.setHasFuel(this.fuel > 0);
		return acceptedFuelTicks;
	}

	@Override
	public boolean minecartChain$canAcceptLocomotiveFuel(final ItemStack stack) {
		return this.fuel < MinecartLocomotiveResources.MAX_FUEL_TICKS && this.minecartChain$fuelTicksFor(stack) > 0;
	}

	@Unique
	private int minecartChain$fuelTicksFor(final ItemStack stack) {
		MinecartFurnace furnace = (MinecartFurnace) (Object) this;
		int burnDuration = furnace.level().fuelValues().burnDuration(stack);
		if (burnDuration <= 0) {
			return 0;
		}

		int coalBurnDuration = Math.max(1, furnace.level().fuelValues().burnDuration(new ItemStack(Items.COAL)));
		long scaledFuelTicks = (long) burnDuration * MinecartLocomotiveResources.COAL_FUEL_TICKS / coalBurnDuration;
		return (int) Math.max(1L, Math.min(MinecartLocomotiveResources.MAX_FUEL_TICKS, scaledFuelTicks));
	}
}
