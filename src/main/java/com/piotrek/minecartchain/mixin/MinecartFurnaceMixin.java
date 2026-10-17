package com.piotrek.minecartchain.mixin;

import com.piotrek.minecartchain.MinecartChainAccess;
import com.piotrek.minecartchain.MinecartChainConfig;
import com.piotrek.minecartchain.MinecartChainMod;
import com.piotrek.minecartchain.MinecartLocomotiveAccess;
import com.piotrek.minecartchain.MinecartLocomotiveResources;
import com.piotrek.minecartchain.MinecartTrainLogic;
import com.piotrek.minecartchain.RadioLocomotiveCompat;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

	@Inject(method = "getDropItem", at = @At("HEAD"), cancellable = true)
	private void minecartChain$overrideRadioLocomotiveDrop(final CallbackInfoReturnable<Item> cir) {
		if (((MinecartChainAccess) this).minecartChain$isRadioLocomotive()) {
			cir.setReturnValue(MinecartChainMod.RADIO_LOCOMOTIVE_ITEM);
		}
	}

	@Inject(method = "getPickResult", at = @At("HEAD"), cancellable = true)
	private void minecartChain$overrideRadioLocomotivePick(final CallbackInfoReturnable<ItemStack> cir) {
		if (((MinecartChainAccess) this).minecartChain$isRadioLocomotive()) {
			cir.setReturnValue(new ItemStack(MinecartChainMod.RADIO_LOCOMOTIVE_ITEM));
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
		Vec3 engineFacing = MinecartTrainLogic.engineDirection(furnace);
		MinecartTrainLogic.updateLocomotiveYaw(furnace, engineFacing);

		if (data.minecartChain$isRadioLocomotive()) {
			this.minecartChain$tickRadioLocomotive(furnace, data, engineFacing);
			return;
		}

		Vec3 direction = MinecartTrainLogic.drivingDirection(furnace);

		if (data.minecartChain$isEngineActive()) {
			this.push = Vec3.ZERO;
			MinecartTrainLogic.applyTrainBrake(furnace);
			this.setHasFuel(false);
			return;
		}

		boolean fuelMissing = MinecartChainConfig.requireFuel && this.fuel <= 0;
		boolean waterMissing = MinecartChainConfig.requireWater && data.minecartChain$getWaterTicks() <= 0;
		if (direction.lengthSqr() <= 1.0E-5D || fuelMissing || waterMissing) {
			this.push = Vec3.ZERO;
			this.setHasFuel(false);
			return;
		}

		boolean fullThrottle = data.minecartChain$isFullThrottle();
		this.push = MinecartTrainLogic.usesMountedTrack(furnace)
			? Vec3.ZERO
			: MinecartTrainLogic.enginePushVector(direction, fullThrottle);
		MinecartTrainLogic.applyEngineAssist(furnace, direction, fullThrottle);
		MinecartTrainLogic.guidePoweredTrain(furnace, direction);
		MinecartTrainLogic.emitLocomotiveSmoke(furnace);
		int trainSize = MinecartTrainLogic.connectedTrainSize(furnace);
		if (MinecartChainConfig.requireFuel) {
			this.fuel = Math.max(0, this.fuel - MinecartTrainLogic.locomotiveFuelCost(trainSize));
		}
		if (MinecartChainConfig.requireWater) {
			data.minecartChain$setWaterTicks(data.minecartChain$getWaterTicks() - MinecartTrainLogic.locomotiveWaterCost(trainSize));
		}
		this.setHasFuel(true);
	}

	@Unique
	private void minecartChain$tickRadioLocomotive(
		final MinecartFurnace furnace,
		final MinecartChainAccess data,
		final Vec3 engineFacing
	) {
		int throttleCh = data.minecartChain$getThrottleChannel();
		int reverseCh = data.minecartChain$getReverseChannel();

		int throttlePower = RadioLocomotiveCompat.getChannelPower(throttleCh);
		int reversePower = RadioLocomotiveCompat.getChannelPower(reverseCh);

		data.minecartChain$setReceivedThrottlePower(throttlePower);
		data.minecartChain$setReceivedReversePower(reversePower);

		if (MinecartChainConfig.layoutMode) {
			// --- MODEL RAILWAY LAYOUT MODE (DEFAULT) ---
			// Channel 2: Coupler / Uncoupler
			// High (> 0) -> couple wagon within 2 blocks
			// Low (== 0) -> uncouple wagons
			if (furnace.level() instanceof ServerLevel serverLevel) {
				if (reversePower > 0) {
					minecartChain$coupleNearestWagon(serverLevel, furnace, data);
				} else {
					minecartChain$uncoupleWagons(serverLevel, furnace, data);
				}
			}

			// Safety arming check: newly placed radio locomotive is DISARMED (braked) by default!
			// Prevents runaway train on placement, giving the player time to configure jumpers safely.
			// It requires the throttle to FIRST be brought to 50% (neutral stop: signal 7 or 8) to arm!
			boolean isArmed = data.minecartChain$isArmed();
			if (!isArmed) {
				if (throttlePower == 7 || throttlePower == 8) {
					data.minecartChain$setArmed(true);
					furnace.level().playSound(
						null, furnace.getX(), furnace.getY(), furnace.getZ(),
						SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0F, 1.2F
					);
				}
				this.push = Vec3.ZERO;
				MinecartTrainLogic.applyTrainBrake(furnace);
				this.setHasFuel(false);
				return;
			}

			// Channel 1: Potentiometer Speed & Direction
			// 50% (signal 7 or 8, godzina 12:00): ZATRZYMANIE (STOP)!
			// < 50% (signal 0..6, godzina 7:30): RUCH DO TYŁU (REVERSE)!
			// > 50% (signal 9..15, godzina 5:30): RUCH DO PRZODU (FORWARD)!
			if (throttlePower == 7 || throttlePower == 8) {
				this.push = Vec3.ZERO;
				MinecartTrainLogic.applyTrainBrake(furnace);
				this.setHasFuel(false);
				data.minecartChain$setReversePending(false);
				return;
			}

			boolean desiredReverse = (throttlePower < 7);
			boolean currentlyReversed = data.minecartChain$isReversed();
			boolean stopped = MinecartTrainLogic.isTrainStopped(furnace)
				|| furnace.getDeltaMovement().horizontalDistanceSqr() <= 0.0025D;

			if (desiredReverse != currentlyReversed) {
				if (stopped) {
					data.minecartChain$setReversed(desiredReverse);
					data.minecartChain$setReversePending(false);
					if (furnace.tickCount > 10) {
						furnace.level().playSound(
							null, furnace.getX(), furnace.getY(), furnace.getZ(),
							SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.9F, desiredReverse ? 0.75F : 0.6F
						);
					}
				} else {
					data.minecartChain$setReversePending(true);
					this.push = Vec3.ZERO;
					MinecartTrainLogic.applyTrainBrake(furnace);
					this.setHasFuel(false);
					return;
				}
			} else {
				data.minecartChain$setReversePending(false);
			}

			double throttle;
			if (desiredReverse) {
				// Reverse movement: scaled from signal 0..6 (at 0 / 7:30 -> full reverse speed)
				throttle = 0.2D + 0.55D * ((7.0D - throttlePower) / 7.0D);
			} else {
				// Forward movement: scaled from signal 9..15 (at 15 / 5:30 -> full forward speed)
				throttle = 0.2D + 0.8D * ((throttlePower - 8.0D) / 7.0D);
			}

			Vec3 direction = MinecartTrainLogic.drivingDirection(furnace);

			boolean fuelMissing = MinecartChainConfig.requireFuel && this.fuel <= 0;
			boolean waterMissing = MinecartChainConfig.requireWater && data.minecartChain$getWaterTicks() <= 0;
			if (fuelMissing || waterMissing) {
				this.push = Vec3.ZERO;
				MinecartTrainLogic.applyTrainBrake(furnace);
				this.setHasFuel(false);
				return;
			}

			boolean fullThrottle = throttle > 0.6D;
			data.minecartChain$setFullThrottle(fullThrottle);

			this.push = MinecartTrainLogic.usesMountedTrack(furnace)
				? Vec3.ZERO
				: MinecartTrainLogic.enginePushVector(direction, throttle);
			MinecartTrainLogic.applyEngineAssist(furnace, direction, throttle);
			MinecartTrainLogic.guidePoweredTrain(furnace, direction);
			MinecartTrainLogic.emitLocomotiveSmoke(furnace, throttle);

			int trainSize = MinecartTrainLogic.connectedTrainSize(furnace);
			int baseFuel = MinecartTrainLogic.locomotiveFuelCost(trainSize);
			int baseWater = MinecartTrainLogic.locomotiveWaterCost(trainSize);
			if (MinecartChainConfig.requireFuel) {
				int fuelCost = Math.max(1, (int) Math.round(baseFuel * throttle));
				this.fuel = Math.max(0, this.fuel - fuelCost);
			}
			if (MinecartChainConfig.requireWater) {
				int waterCost = Math.max(1, (int) Math.round(baseWater * throttle));
				data.minecartChain$setWaterTicks(data.minecartChain$getWaterTicks() - waterCost);
			}
			this.setHasFuel(true);
			return;
		}

		// --- CLASSIC MODE ---
		// Handle reverse switching: REVERS MOZNA WLACZYC TYLKO GDY STOI
		boolean desiredReverse = reversePower > 0;
		boolean currentlyReversed = data.minecartChain$isReversed();
		boolean stopped = MinecartTrainLogic.isTrainStopped(furnace);

		if (desiredReverse != currentlyReversed) {
			if (stopped) {
				data.minecartChain$setReversed(desiredReverse);
				data.minecartChain$setReversePending(false);
				furnace.level().playSound(
					null, furnace.getX(), furnace.getY(), furnace.getZ(),
					SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.9F, desiredReverse ? 0.75F : 0.6F
				);
			} else {
				data.minecartChain$setReversePending(true);
				this.push = Vec3.ZERO;
				MinecartTrainLogic.applyTrainBrake(furnace);
				this.setHasFuel(false);
				return;
			}
		} else {
			data.minecartChain$setReversePending(false);
		}

		Vec3 direction = MinecartTrainLogic.drivingDirection(furnace);

		boolean fuelMissing = MinecartChainConfig.requireFuel && this.fuel <= 0;
		boolean waterMissing = MinecartChainConfig.requireWater && data.minecartChain$getWaterTicks() <= 0;
		if (throttlePower <= 0 || fuelMissing || waterMissing) {
			this.push = Vec3.ZERO;
			MinecartTrainLogic.applyTrainBrake(furnace);
			this.setHasFuel(false);
			return;
		}

		double throttle = (double) throttlePower / 15.0D;
		boolean fullThrottle = throttle > 0.6D;
		data.minecartChain$setFullThrottle(fullThrottle);

		this.push = MinecartTrainLogic.usesMountedTrack(furnace)
			? Vec3.ZERO
			: MinecartTrainLogic.enginePushVector(direction, throttle);
		MinecartTrainLogic.applyEngineAssist(furnace, direction, throttle);
		MinecartTrainLogic.guidePoweredTrain(furnace, direction);
		MinecartTrainLogic.emitLocomotiveSmoke(furnace, throttle);

		int trainSize = MinecartTrainLogic.connectedTrainSize(furnace);
		int baseFuel = MinecartTrainLogic.locomotiveFuelCost(trainSize);
		int baseWater = MinecartTrainLogic.locomotiveWaterCost(trainSize);
		if (MinecartChainConfig.requireFuel) {
			int fuelCost = Math.max(1, (int) Math.round(baseFuel * throttle));
			this.fuel = Math.max(0, this.fuel - fuelCost);
		}
		if (MinecartChainConfig.requireWater) {
			int waterCost = Math.max(1, (int) Math.round(baseWater * throttle));
			data.minecartChain$setWaterTicks(data.minecartChain$getWaterTicks() - waterCost);
		}
		this.setHasFuel(true);
	}

	@Unique
	private void minecartChain$coupleNearestWagon(
		final ServerLevel level, final MinecartFurnace furnace, final MinecartChainAccess data
	) {
		if (!data.minecartChain$canAcceptLink()) {
			return;
		}

		double maxDist = 2.0D;
		double maxDistSqr = maxDist * maxDist;
		Vec3 trackDir = MinecartTrainLogic.engineDirection(furnace);
		Vec3 trackDirNorm = trackDir.lengthSqr() > 1.0E-5D ? trackDir.normalize() : Vec3.ZERO;

		net.minecraft.world.entity.vehicle.minecart.AbstractMinecart closest = null;
		double closestDistSqr = maxDistSqr;

		for (net.minecraft.world.entity.Entity entity : level.getEntities(furnace, furnace.getBoundingBox().inflate(maxDist))) {
			if (entity instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart cart && !cart.isRemoved() && cart != furnace) {
				MinecartChainAccess cartData = (MinecartChainAccess) cart;
				if (data.minecartChain$hasLink(cart.getUUID()) || !cartData.minecartChain$canAcceptLink()) {
					continue;
				}

				Vec3 toCart = cart.position().subtract(furnace.position()).horizontal();
				// Filter out wagons on adjacent/parallel tracks:
				if (trackDirNorm.lengthSqr() > 0.5D) {
					double along = toCart.dot(trackDirNorm);
					Vec3 perpendicular = toCart.subtract(trackDirNorm.scale(along));
					if (perpendicular.lengthSqr() > 0.36D) { // > 0.6 blocks to the side
						continue;
					}
				}

				double dSqr = furnace.distanceToSqr(cart);
				if (dSqr <= closestDistSqr) {
					closest = cart;
					closestDistSqr = dSqr;
				}
			}
		}

		if (closest != null) {
			data.minecartChain$addLink(closest.getUUID());
			((MinecartChainAccess) closest).minecartChain$addLink(furnace.getUUID());
			furnace.level().playSound(null, furnace.getX(), furnace.getY(), furnace.getZ(),
				SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
	}

	@Unique
	private void minecartChain$uncoupleWagons(
		final ServerLevel level, final MinecartFurnace furnace, final MinecartChainAccess data
	) {
		if (data.minecartChain$linkCount() <= 0) {
			return;
		}

		data.minecartChain$getFirstLink().ifPresent(id -> {
			net.minecraft.world.entity.Entity e = level.getEntity(id);
			if (e instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart cart) {
				((MinecartChainAccess) cart).minecartChain$removeLink(furnace.getUUID());
			}
		});
		data.minecartChain$getSecondLink().ifPresent(id -> {
			net.minecraft.world.entity.Entity e = level.getEntity(id);
			if (e instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart cart) {
				((MinecartChainAccess) cart).minecartChain$removeLink(furnace.getUUID());
			}
		});
		data.minecartChain$clearLinks();
		furnace.level().playSound(null, furnace.getX(), furnace.getY(), furnace.getZ(),
			SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
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
		if (!(furnace.level() instanceof ServerLevel serverLevel)) {
			return 0;
		}

		return MinecartLocomotiveResources.locomotiveFuelTicks(serverLevel, stack);
	}
}
