package com.piotrek.minecartchain;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class SteamLocomotiveEntity extends AbstractMinecart implements MinecartLocomotiveAccess {
	public static final double MAX_SPEED = 0.65D;
	public static final double ACCELERATION = 0.035D;
	public static final double BRAKE_FORCE = 0.18D;

	private static final EntityDataAccessor<Float> THROTTLE =
		SynchedEntityData.defineId(SteamLocomotiveEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> REVERSER =
		SynchedEntityData.defineId(SteamLocomotiveEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> FUEL_TICKS =
		SynchedEntityData.defineId(SteamLocomotiveEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> WATER_TICKS =
		SynchedEntityData.defineId(SteamLocomotiveEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> COUPLER_ENGAGED =
		SynchedEntityData.defineId(SteamLocomotiveEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> FACING_YAW =
		SynchedEntityData.defineId(SteamLocomotiveEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> HAS_FACING_YAW =
		SynchedEntityData.defineId(SteamLocomotiveEntity.class, EntityDataSerializers.BOOLEAN);

	private float wheelRotation = 0.0F;
	private float prevWheelRotation = 0.0F;
	private Vec3 lastTravelDir = Vec3.ZERO;
	private boolean lastReverserApplied;
	private boolean lastReverserInitialized;
	private boolean wasMoving;
	private int stopSteamPuffTicks;

	public SteamLocomotiveEntity(final EntityType<? extends SteamLocomotiveEntity> entityType, final Level level) {
		super(entityType, level);
	}

	public SteamLocomotiveEntity(
		final EntityType<? extends SteamLocomotiveEntity> entityType,
		final Level level,
		final double x,
		final double y,
		final double z
	) {
		super(entityType, level, x, y, z);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(THROTTLE, 0.0F);
		builder.define(REVERSER, false);
		builder.define(FUEL_TICKS, 0);
		builder.define(WATER_TICKS, 0);
		builder.define(COUPLER_ENGAGED, false);
		builder.define(FACING_YAW, 0.0F);
		builder.define(HAS_FACING_YAW, false);
	}

	@Override
	public EntityDimensions getDimensions(final Pose pose) {
		return EntityDimensions.scalable(1.2F, 1.85F);
	}

	public float getThrottle() {
		return this.entityData.get(THROTTLE);
	}

	public void setThrottle(final float throttle) {
		this.entityData.set(THROTTLE, Mth.clamp(throttle, -1.0F, 1.0F));
	}

	public boolean isReverser() {
		return ((MinecartChainAccess) this).minecartChain$isReversed();
	}

	public void setReverser(final boolean reverser) {
		((MinecartChainAccess) this).minecartChain$setReversed(reverser);
		this.entityData.set(REVERSER, reverser);
	}

	public int getFuelTicks() {
		return this.entityData.get(FUEL_TICKS);
	}

	public void setFuelTicks(final int ticks) {
		this.entityData.set(FUEL_TICKS, Math.max(0, Math.min(ticks, MinecartLocomotiveResources.MAX_FUEL_TICKS)));
	}

	public int getWaterTicks() {
		return this.entityData.get(WATER_TICKS);
	}

	public void setWaterTicks(final int ticks) {
		this.entityData.set(WATER_TICKS, Math.max(0, Math.min(ticks, MinecartLocomotiveResources.MAX_WATER_TICKS)));
	}

	public boolean isCouplerEngaged() {
		return this.entityData.get(COUPLER_ENGAGED);
	}

	public void setCouplerEngaged(final boolean engaged) {
		this.entityData.set(COUPLER_ENGAGED, engaged);
	}

	public float getFacingYaw() {
		if (this.entityData.get(HAS_FACING_YAW)) {
			return this.entityData.get(FACING_YAW);
		}
		return this.getYRot();
	}

	public void setFacingYaw(final float yaw) {
		this.entityData.set(FACING_YAW, yaw);
		this.entityData.set(HAS_FACING_YAW, true);
	}

	public boolean isOnMountedTrack() {
		return MountedTrackCompat.isMounted(this);
	}

	public Vec3 getVisualForward() {
		double yawRad = Math.toRadians(this.getFacingYaw());
		return new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
	}

	public float getWheelRotation(final float partialTick) {
		return Mth.lerp(partialTick, this.prevWheelRotation, this.wheelRotation);
	}

	public void handleDriverInput(final float throttle, final boolean reverser, final boolean toggleCoupler) {
		this.setThrottle(throttle);
		this.setReverser(reverser);
		if (toggleCoupler && this.level() instanceof ServerLevel serverLevel) {
			this.toggleCoupler(serverLevel);
		}
	}

	public void handleDriverInput(final float throttle, final boolean reverser) {
		this.handleDriverInput(throttle, reverser, false);
	}

	public void toggleCoupler(final ServerLevel serverLevel) {
		MinecartChainAccess data = (MinecartChainAccess) this;
		if (this.isCouplerEngaged() || data.minecartChain$linkCount() > 0) {
			// Uncouple
			data.minecartChain$getFirstLink().ifPresent(uuid -> {
				Entity e = serverLevel.getEntity(uuid);
				if (e instanceof AbstractMinecart cart) {
					((MinecartChainAccess) cart).minecartChain$removeLink(this.getUUID());
				}
			});
			data.minecartChain$getSecondLink().ifPresent(uuid -> {
				Entity e = serverLevel.getEntity(uuid);
				if (e instanceof AbstractMinecart cart) {
					((MinecartChainAccess) cart).minecartChain$removeLink(this.getUUID());
				}
			});
			data.minecartChain$clearLinks();
			this.setCouplerEngaged(false);
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
			if (this.getFirstPassenger() instanceof Player player) {
				player.sendOverlayMessage(Component.literal("Coupler: Disconnected"));
			}
			return;
		}

		// Search for nearest AbstractMinecart behind the locomotive along the track within 2.5 blocks.
		double searchRadius = 2.5D;
		double searchRadiusSqr = searchRadius * searchRadius;
		double yawRad = Math.toRadians(this.getYRot());
		Vec3 facing = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
		Vec3 rearDir = facing.reverse();

		AbstractMinecart closest = null;
		double closestDistSqr = searchRadiusSqr;

		for (Entity entity : serverLevel.getEntities(this, this.getBoundingBox().inflate(searchRadius))) {
			if (entity instanceof AbstractMinecart cart && !cart.isRemoved() && cart != this) {
				MinecartChainAccess cartData = (MinecartChainAccess) cart;
				if (data.minecartChain$hasLink(cart.getUUID()) || !cartData.minecartChain$canAcceptLink()) {
					continue;
				}
				Vec3 toCart = cart.position().subtract(this.position()).horizontal();
				// Verify the cart is behind the locomotive (or close enough)
				if (toCart.dot(rearDir) > -0.2D || toCart.lengthSqr() < 1.5D) {
					double dSqr = this.distanceToSqr(cart);
					if (dSqr < closestDistSqr) {
						closest = cart;
						closestDistSqr = dSqr;
					}
				}
			}
		}

		if (closest != null) {
			data.minecartChain$addLink(closest.getUUID());
			((MinecartChainAccess) closest).minecartChain$addLink(this.getUUID());
			this.setCouplerEngaged(true);
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
			if (this.getFirstPassenger() instanceof Player player) {
				player.sendOverlayMessage(Component.literal("Coupler: Connected to wagon"));
			}
		} else {
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 0.6F);
			if (this.getFirstPassenger() instanceof Player player) {
				player.sendOverlayMessage(Component.literal("Coupler: No wagon in reach"));
			}
		}
	}

	@Override
	protected double getMaxSpeed(final ServerLevel level) {
		return MAX_SPEED;
	}

	@Override
	public InteractionResult interact(final Player player, final InteractionHand hand, final Vec3 hitLocation) {
		ItemStack stack = player.getItemInHand(hand);

		if (player.isSecondaryUseActive()) {
			if (stack.isEmpty()) {
				if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
					this.toggleCoupler(serverLevel);
				}
				return InteractionResult.SUCCESS;
			}
			if (stack.is(Items.WATER_BUCKET)) {
				if (this.getWaterTicks() < MinecartLocomotiveResources.MAX_WATER_TICKS) {
					if (!this.level().isClientSide()) {
						int added = Math.min(
							MinecartLocomotiveResources.WATER_TICKS_PER_BUCKET,
							MinecartLocomotiveResources.MAX_WATER_TICKS - this.getWaterTicks()
						);
						this.setWaterTicks(this.getWaterTicks() + added);
						if (!player.hasInfiniteMaterials()) {
							player.setItemInHand(hand, new ItemStack(Items.BUCKET));
						}
						this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.9F, 1.0F);
						player.sendOverlayMessage(Component.literal("Locomotive water: " + this.getWaterTicks() + " ticks"));
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}

			if (MinecartLocomotiveResources.isCookingFuel(stack)) {
				int burnDuration = this.level() instanceof ServerLevel sl ? MinecartLocomotiveResources.locomotiveFuelTicks(sl, stack) : 0;
				if (burnDuration > 0 && this.getFuelTicks() < MinecartLocomotiveResources.MAX_FUEL_TICKS) {
					if (!this.level().isClientSide()) {
						int added = Math.min(burnDuration, MinecartLocomotiveResources.MAX_FUEL_TICKS - this.getFuelTicks());
						this.setFuelTicks(this.getFuelTicks() + added);
						if (!player.hasInfiniteMaterials()) {
							var remainderTpl = stack.getCraftingRemainder();
							stack.shrink(1);
							if (remainderTpl != null) {
								ItemStack remainder = remainderTpl.create();
								if (!remainder.isEmpty()) {
									player.getInventory().add(remainder);
								}
							}
						}
						this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 0.8F, 1.15F);
						player.sendOverlayMessage(Component.literal("Locomotive fuel: " + this.getFuelTicks() + " ticks"));
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}

			return InteractionResult.PASS;
		}

		if (!this.isVehicle()) {
			if (!this.level().isClientSide()) {
				return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
			}
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	protected void addPassenger(final Entity passenger) {
		super.addPassenger(passenger);
		if (passenger instanceof Player) {
			passenger.setPose(Pose.SITTING);
			float initialYaw = this.getFacingYaw();
			passenger.setYRot(initialYaw);
			passenger.yRotO = initialYaw;
			passenger.setYHeadRot(initialYaw);
			passenger.setYBodyRot(initialYaw);
		}
	}

	@Override
	protected void removePassenger(final Entity passenger) {
		super.removePassenger(passenger);
		if (passenger instanceof Player && passenger.getPose() == Pose.SITTING) {
			passenger.setPose(Pose.STANDING);
		}
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(final Entity passenger, final EntityDimensions dimensions, final float partialTick) {
		if (this.isOnMountedTrack()) {
			// In Splinecart track follower space, cab is always at local Z = -0.85D
			return new Vec3(0.0D, 0.10D, -0.85D);
		}
		Vec3 forward = this.getVisualForward();
		Vec3 rear = forward.scale(-1.0D);
		return new Vec3(rear.x * 0.85D, 0.10D, rear.z * 0.85D);
	}

	@Override
	protected void positionRider(final Entity passenger, final Entity.MoveFunction moveFunction) {
		if (passenger instanceof Player) {
			passenger.setPose(Pose.SITTING);
		}

		if (this.isOnMountedTrack()) {
			// Keep player strictly in the cab (Z = -0.85D) on switches, ties, and curves.
			moveFunction.accept(passenger, this.getX(), this.getY() + 0.10D, this.getZ() - 0.85D);
			passenger.setYBodyRot(90.0F);
			return;
		}

		Vec3 forward = this.getVisualForward();
		Vec3 rear = forward.scale(-1.0D);
		double riderX = this.getX() + rear.x * 0.85D;
		double riderY = this.getY() + 0.10D;
		double riderZ = this.getZ() + rear.z * 0.85D;
		moveFunction.accept(passenger, riderX, riderY, riderZ);

		passenger.setYBodyRot(this.getFacingYaw());
	}

	@Override
	public Vec3 getDismountLocationForPassenger(final LivingEntity passenger) {
		Direction direction = this.getDirection();
		Direction[] sides = { direction.getClockWise(), direction.getCounterClockWise(), direction.getOpposite() };
		for (Direction side : sides) {
			Vec3 safePos = DismountHelper.findSafeDismountLocation(
				passenger.getType(),
				this.level(),
				this.blockPosition().relative(side),
				false
			);
			if (safePos != null) {
				return safePos;
			}
		}
		return super.getDismountLocationForPassenger(passenger);
	}

	@Override
	protected Item getDropItem() {
		Item item = BuiltInRegistries.ITEM.getValue(MinecartChainMod.id("steam_locomotive"));
		return item != Items.AIR ? item : Items.MINECART;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(this.getDropItem());
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putFloat("Throttle", this.getThrottle());
		output.putBoolean("Reverser", this.isReverser());
		if (this.entityData.get(HAS_FACING_YAW)) {
			output.putFloat("FacingYaw", this.getFacingYaw());
			output.putBoolean("HasFacingYaw", true);
		}
		output.putInt("FuelTicks", this.getFuelTicks());
		output.putInt("WaterTicks", this.getWaterTicks());
		output.putBoolean("CouplerEngaged", this.isCouplerEngaged());
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setThrottle(input.getFloatOr("Throttle", 0.0F));
		this.setReverser(input.getBooleanOr("Reverser", false));
		if (input.getBooleanOr("HasFacingYaw", false)) {
			this.setFacingYaw(input.getFloatOr("FacingYaw", 0.0F));
		}
		this.setFuelTicks(input.getIntOr("FuelTicks", 0));
		this.setWaterTicks(input.getIntOr("WaterTicks", 0));
		this.setCouplerEngaged(input.getBooleanOr("CouplerEngaged", false));
	}

	private void applyFacingYaw(final float yaw) {
		this.setYRot(yaw);
		this.yRotO = yaw;
		this.setYHeadRot(yaw);
		this.setYBodyRot(yaw);
	}

	@Override
	public void tick() {
		if (!this.entityData.get(HAS_FACING_YAW)) {
			MinecartChainAccess access = (MinecartChainAccess) this;
			if (access.minecartChain$hasLocomotiveYaw()) {
				this.setFacingYaw(access.minecartChain$getLocomotiveYaw());
			} else if (!MountedTrackCompat.isMounted(this)) {
				this.setFacingYaw(this.getYRot());
			}
		}

		float forwardYaw = this.getFacingYaw();

		super.tick();

		// Keep visual heading independent of vanilla minecart yaw and Splinecart's passenger.setYRot(90).
		this.applyFacingYaw(forwardYaw);

		Vec3 movement = MinecartTrainLogic.horizontalMovement(this);
		double hSpeed = movement.length();
		this.prevWheelRotation = this.wheelRotation;
		if (hSpeed > 0.0015D) {
			// Wheel animation is relative to locomotive heading:
			// Driving forward always rolls forward, driving reverse rolls backward.
			float rotDelta = (float) ((this.isReverser() ? 1.0F : -1.0F) * (hSpeed / 0.25D));
			this.wheelRotation += rotDelta;
		}

		Entity rider = this.getFirstPassenger();
		boolean hasPlayerRider = rider instanceof Player;
		float throttle = this.getThrottle();

		if (this.level() instanceof ServerLevel serverLevel) {
			boolean hasLinks = ((MinecartChainAccess) this).minecartChain$linkCount() > 0;
			if (this.entityData.get(COUPLER_ENGAGED) != hasLinks) {
				this.setCouplerEngaged(hasLinks);
			}

			boolean isMoving = hSpeed > 0.015D;
			if (this.wasMoving && !isMoving) {
				this.stopSteamPuffTicks = 10;
				serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.45F, 1.25F);
			}
			this.wasMoving = isMoving;

			boolean infinite = rider instanceof Player player && player.hasInfiniteMaterials();
			if (throttle > 0.0F && hasPlayerRider && !infinite) {
				if (this.getFuelTicks() > 0 && this.getWaterTicks() > 0) {
					this.setFuelTicks(this.getFuelTicks() - 1);
					if (this.tickCount % 2 == 0) {
						this.setWaterTicks(this.getWaterTicks() - 1);
					}
				}
			}

			boolean hasSteam = (this.getFuelTicks() > 0 && this.getWaterTicks() > 0) || infinite;

			if (hasPlayerRider && throttle > 0.0F && hasSteam) {
				driveForward(serverLevel, throttle);
			} else if (throttle < 0.0F) {
				applyBrake(serverLevel, Math.abs(throttle));
				if (hSpeed <= 0.005D || throttle <= -0.7F) {
					MinecartTrainLogic.applyTrainBrake(this);
				}
			} else if (throttle == 0.0F && hSpeed < 0.001D) {
				MinecartTrainLogic.setHorizontalMovement(this, Vec3.ZERO);
			}

			emitParticles(serverLevel, throttle, hasSteam, hSpeed);
			playSounds(serverLevel, throttle, hasSteam);
		}

		this.applyFacingYaw(this.getFacingYaw());
	}

	@Override
	public boolean isRideable() {
		return true;
	}

	private Vec3 getTrackLine(final ServerLevel serverLevel) {
		if (MountedTrackCompat.isMounted(this)) {
			Vec3 splineDir = MountedTrackCompat.trackDirection(this);
			if (splineDir.lengthSqr() > 1.0E-5D) {
				return splineDir.normalize();
			}
		}

		Vec3 externalDir = MinecartTrainLogic.externalTrackDirection(this);
		if (externalDir.lengthSqr() > 1.0E-5D) {
			return externalDir.normalize();
		}

		// When already rolling at speed on rails, actual velocity is the smoothest rail tangent:
		Vec3 currentMovement = this.getDeltaMovement().horizontal();
		if (currentMovement.lengthSqr() > 0.0025D) {
			return currentMovement.normalize();
		}

		BlockPos railPos = this.getCurrentBlockPosOrRailBelow();
		BlockState railState = serverLevel.getBlockState(railPos);
		if (!BaseRailBlock.isRail(railState)) {
			railPos = railPos.below();
			railState = serverLevel.getBlockState(railPos);
		}

		if (BaseRailBlock.isRail(railState) && railState.getBlock() instanceof BaseRailBlock railBlock) {
			RailShape shape = railState.getValue(railBlock.getShapeProperty());
			Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
			Vec3 exit1 = new Vec3(exits.getFirst().getX(), 0.0D, exits.getFirst().getZ()).normalize();
			Vec3 exit2 = new Vec3(exits.getSecond().getX(), 0.0D, exits.getSecond().getZ()).normalize();
			double yawRad = Math.toRadians(this.getFacingYaw());
			Vec3 facing = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
			return facing.dot(exit1) >= facing.dot(exit2) ? exit1 : exit2;
		}

		double rad = Math.toRadians(this.getFacingYaw());
		return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
	}

	private void driveForward(final ServerLevel serverLevel, final float throttle) {
		if (MountedTrackCompat.isMounted(this)) {
			this.driveOnMountedTrack(throttle);
			return;
		}

		Vec3 trackLine = getTrackLine(serverLevel);
		double yawRad = Math.toRadians(this.getFacingYaw());
		Vec3 facing = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));

		if (trackLine.dot(facing) < 0.0D) {
			trackLine = trackLine.reverse();
		}

		Vec3 driveDirection = this.isReverser() ? trackLine.reverse() : trackLine;
		this.applyDriveVelocity(driveDirection, throttle);

		this.lastTravelDir = driveDirection;

		// Smooth heading update along curves and straight tracks:
		Vec3 velocity = this.getDeltaMovement().horizontal();
		Vec3 headingRef = velocity.lengthSqr() > 0.0025D ? velocity.normalize() : trackLine;
		if (headingRef.lengthSqr() > 1.0E-4D && !MinecartTrainLogic.isTrainStopped(this)) {
			Vec3 engineFront = this.isReverser() ? headingRef.reverse() : headingRef;
			float targetYaw = (float) (Mth.atan2(-engineFront.x, engineFront.z) * Mth.RAD_TO_DEG);
			// Prevent sudden 180-degree yaw flips caused by bounce/deceleration rebound
			if (Mth.degreesDifferenceAbs(this.getFacingYaw(), targetYaw) <= 110.0F) {
				float newYaw = Mth.approachDegrees(this.getFacingYaw(), targetYaw, 25.0F);
				this.setFacingYaw(newYaw);
				this.applyFacingYaw(newYaw);
				MinecartChainAccess access = (MinecartChainAccess) this;
				access.minecartChain$setLocomotiveYaw(newYaw);
				access.minecartChain$setHasLocomotiveYaw(true);
			}
		}

		guideCoupledWagons(serverLevel);
	}

	/**
	 * On Splinecart, the positive tangent of a stretch can flip at a switch.
	 * Keep pushing along the current world-space travel, like a furnace locomotive.
	 */
	private void driveOnMountedTrack(final float throttle) {
		if (!this.lastReverserInitialized) {
			this.lastReverserApplied = this.isReverser();
			this.lastReverserInitialized = true;
		}

		Vec3 current = MinecartTrainLogic.horizontalMovement(this);
		Vec3 travel;
		boolean reverseToggled = this.isReverser() != this.lastReverserApplied;
		this.lastReverserApplied = this.isReverser();

		if (current.lengthSqr() > 0.0004D) {
			travel = current.normalize();
			if (reverseToggled) {
				travel = travel.reverse();
			}
			this.lastTravelDir = travel;
		} else if (this.lastTravelDir.lengthSqr() > 1.0E-8D) {
			travel = this.lastTravelDir;
			if (reverseToggled) {
				travel = travel.reverse();
				this.lastTravelDir = travel;
			}
		} else {
			double yawRad = Math.toRadians(this.getFacingYaw());
			Vec3 facing = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
			Vec3 splineDir = MountedTrackCompat.trackDirection(this);
			if (splineDir.lengthSqr() > 1.0E-8D) {
				travel = facing.dot(splineDir) >= 0.0D ? splineDir.normalize() : splineDir.normalize().reverse();
			} else {
				travel = facing;
			}
			if (this.isReverser()) {
				travel = travel.reverse();
			}
			this.lastTravelDir = travel;
		}

		this.applyDriveVelocity(travel, throttle);

		Vec3 currentMovement = MinecartTrainLogic.horizontalMovement(this);
		if (currentMovement.lengthSqr() > 0.0025D) {
			Vec3 front = this.isReverser() ? currentMovement.normalize().reverse() : currentMovement.normalize();
			float targetYaw = (float) (Mth.atan2(-front.x, front.z) * Mth.RAD_TO_DEG);
			float newYaw = Mth.approachDegrees(this.getFacingYaw(), targetYaw, 25.0F);
			this.setFacingYaw(newYaw);
			this.applyFacingYaw(newYaw);
		}
	}

	private void applyDriveVelocity(final Vec3 driveDirection, final float throttle) {
		if (driveDirection.lengthSqr() <= 1.0E-8D) {
			return;
		}
		double pushSpeed = MAX_SPEED * throttle;
		Vec3 currentVelocity = MinecartTrainLogic.horizontalMovement(this);
		Vec3 targetVelocity = driveDirection.normalize().scale(pushSpeed);
		double accel = ACCELERATION * throttle;

		BlockPos railPos = this.getCurrentBlockPosOrRailBelow();
		BlockState railState = this.level().getBlockState(railPos);
		boolean isSlope = false;
		if (BaseRailBlock.isRail(railState) && railState.getBlock() instanceof BaseRailBlock railBlock) {
			isSlope = railState.getValue(railBlock.getShapeProperty()).isSlope();
		}

		double targetY = isSlope ? currentVelocity.y : Math.min(0.0D, currentVelocity.y);

		Vec3 newVelocity = new Vec3(
			Mth.approach((float) currentVelocity.x, (float) targetVelocity.x, (float) accel),
			targetY,
			Mth.approach((float) currentVelocity.z, (float) targetVelocity.z, (float) accel)
		);
		MinecartTrainLogic.setHorizontalMovement(this, newVelocity);
	}

	private void guideCoupledWagons(final ServerLevel serverLevel) {
		if (MountedTrackCompat.isMounted(this)) {
			return;
		}
		MinecartTrainLogic.guidePoweredTrain(this, this.getDeltaMovement());
	}

	private void applyBrake(final ServerLevel serverLevel, final float brakeIntensity) {
		Vec3 currentMovement = MinecartTrainLogic.horizontalMovement(this);
		double speedSqr = currentMovement.lengthSqr();
		boolean stopCompletely = speedSqr < 0.005D || brakeIntensity >= 0.7F;
		if (stopCompletely) {
			MinecartTrainLogic.applyTrainBrake(this);
		} else {
			double damping = Math.min(1.0D, 0.35D + 0.65D * brakeIntensity);
			double factor = Math.max(0.0D, 1.0D - damping);
			MinecartTrainLogic.setHorizontalMovement(this, currentMovement.scale(factor));
			for (AbstractMinecart follower : MinecartTrainLogic.orderedTrain(serverLevel, this)) {
				if (follower != this) {
					Vec3 fv = MinecartTrainLogic.horizontalMovement(follower);
					MinecartTrainLogic.setHorizontalMovement(follower, fv.scale(factor));
				}
			}
		}
	}

	@Override
	public void activateMinecart(final ServerLevel serverLevel, final int x, final int y, final int z, final boolean powered) {
		if (this.getThrottle() < 0.0F) {
			return; // Lock movement completely when braking
		}
		super.activateMinecart(serverLevel, x, y, z, powered);
	}

	private void emitParticles(final ServerLevel serverLevel, final float throttle, final boolean hasSteam, final double hSpeed) {
		Vec3 forward = this.getVisualForward();
		Vec3 right = new Vec3(-forward.z, 0.0D, forward.x).normalize();
		Vec3 left = right.scale(-1.0D);

		// Chimney top: exactly at the rim of the smokestack (1.0 block forward, 1.98 blocks above base)
		Vec3 chimneyPos = this.position().add(forward.scale(1.0D)).add(0.0D, 1.98D, 0.0D);

		// 1. Wheel side steam burst upon stopping ("po zatrzymaniu po bokach kół ma buchać biała para - kilka białych chmurek")
		if (this.stopSteamPuffTicks > 0) {
			this.stopSteamPuffTicks--;
			double[] wheelOffsets = { 0.9D, 0.0D, -0.7D };
			for (double along : wheelOffsets) {
				Vec3 wheelCenter = this.position().add(forward.scale(along)).add(0.0D, 0.22D, 0.0D);
				Vec3 leftPos = wheelCenter.add(left.scale(0.55D));
				Vec3 rightPos = wheelCenter.add(right.scale(0.55D));

				serverLevel.sendParticles(
					ParticleTypes.CLOUD,
					leftPos.x, leftPos.y, leftPos.z,
					1,
					0.05D, 0.03D, 0.05D,
					0.035D
				);
				serverLevel.sendParticles(
					ParticleTypes.CLOUD,
					rightPos.x, rightPos.y, rightPos.z,
					1,
					0.05D, 0.03D, 0.05D,
					0.035D
				);
			}
		}

		// 2. Chimney smoke:
		// "zatrzymana lokomotywa ma delikatnie kopcic na bardzo ciemno szary. im szybciej jedzie tym bardziej jasny dym z komina"
		boolean isStopped = hSpeed <= 0.005D && throttle <= 0.0F;

		if (isStopped) {
			// Delikatne kopcenie na bardzo ciemnoszary dym podczas postoju
			if (hasSteam && this.getFuelTicks() > 0 && this.tickCount % 6 == 0) {
				serverLevel.sendParticles(
					ParticleTypes.LARGE_SMOKE,
					chimneyPos.x,
					chimneyPos.y,
					chimneyPos.z,
					1,
					0.02D,
					0.04D,
					0.02D,
					0.008D
				);
			}
		} else if (hasSteam) {
			// W ruchu: im szybciej jedzie, tym jaśniejszy i gęstszy dym z komina
			double speedFactor = Mth.clamp(hSpeed / MAX_SPEED, 0.0D, 1.0D);
			double intensity = Math.max(speedFactor, Math.max(0.0D, (double) throttle));

			int interval = Math.max(1, (int) Math.round(7.0D - 5.0D * intensity));
			if (this.tickCount % interval == 0) {
				double upwardSpeed = 0.03D + 0.08D * intensity;

				if (intensity < 0.35D) {
					// Niska prędkość / ruszanie: ciemnoszary dym węglowy
					serverLevel.sendParticles(
						ParticleTypes.LARGE_SMOKE,
						chimneyPos.x, chimneyPos.y, chimneyPos.z,
						1, 0.03D, 0.05D, 0.03D, upwardSpeed * 0.7D
					);
					serverLevel.sendParticles(
						ParticleTypes.SMOKE,
						chimneyPos.x, chimneyPos.y, chimneyPos.z,
						1, 0.02D, 0.04D, 0.02D, upwardSpeed * 0.5D
					);
				} else if (intensity < 0.70D) {
					// Średnia prędkość: mieszanka szarego i jasnego dymu/pary
					serverLevel.sendParticles(
						ParticleTypes.SMOKE,
						chimneyPos.x, chimneyPos.y, chimneyPos.z,
						1, 0.03D, 0.06D, 0.03D, upwardSpeed * 0.8D
					);
					serverLevel.sendParticles(
						ParticleTypes.CAMPFIRE_COSY_SMOKE,
						chimneyPos.x, chimneyPos.y, chimneyPos.z,
						1, 0.04D, 0.08D, 0.04D, upwardSpeed
					);
				} else {
					// Duża prędkość: jasny, biały dym i kłęby pary
					serverLevel.sendParticles(
						ParticleTypes.CAMPFIRE_COSY_SMOKE,
						chimneyPos.x, chimneyPos.y, chimneyPos.z,
						2, 0.05D, 0.1D, 0.05D, upwardSpeed
					);
					serverLevel.sendParticles(
						ParticleTypes.CLOUD,
						chimneyPos.x, chimneyPos.y + 0.05D, chimneyPos.z,
						1, 0.04D, 0.08D, 0.04D, upwardSpeed * 0.9D
					);
				}
			}
		}
	}

	private void playSounds(final ServerLevel serverLevel, final float throttle, final boolean hasSteam) {
		if (throttle > 0.0F && hasSteam) {
			int interval = Math.max(3, (int) (16 - (throttle * 12)));
			if (this.tickCount % interval == 0) {
				float pitch = 0.7F + (throttle * 0.5F) + (this.random.nextFloat() * 0.15F);
				serverLevel.playSound(
					null,
					this.getX(),
					this.getY(),
					this.getZ(),
					SoundEvents.FIRE_EXTINGUISH,
					SoundSource.NEUTRAL,
					0.35F,
					pitch
				);
			}
		} else if (throttle < 0.0F && this.getDeltaMovement().horizontalDistanceSqr() > 0.01D && this.tickCount % 12 == 0) {
			serverLevel.playSound(
				null,
				this.getX(),
				this.getY(),
				this.getZ(),
				SoundEvents.FIRE_EXTINGUISH,
				SoundSource.NEUTRAL,
				0.25F,
				0.5F + (this.random.nextFloat() * 0.2F)
			);
		}
	}

	@Override
	public int minecartChain$getLocomotiveFuelTicks() {
		return this.getFuelTicks();
	}

	@Override
	public int minecartChain$addLocomotiveFuel(final ItemStack stack) {
		if (!MinecartLocomotiveResources.isCookingFuel(stack)) {
			return 0;
		}
		int burnDuration = this.level() instanceof ServerLevel sl ? MinecartLocomotiveResources.locomotiveFuelTicks(sl, stack) : 0;
		int accepted = Math.min(burnDuration, MinecartLocomotiveResources.MAX_FUEL_TICKS - this.getFuelTicks());
		if (accepted > 0) {
			this.setFuelTicks(this.getFuelTicks() + accepted);
		}
		return accepted;
	}

	@Override
	public boolean minecartChain$canAcceptLocomotiveFuel(final ItemStack stack) {
		return MinecartLocomotiveResources.isCookingFuel(stack) && this.getFuelTicks() < MinecartLocomotiveResources.MAX_FUEL_TICKS;
	}
}
