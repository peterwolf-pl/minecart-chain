package com.piotrek.minecartchain;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
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
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
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
	private static final double STOP_SPEED = 0.003D;

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
	private static final EntityDataAccessor<Float> ENGINE_LOAD =
		SynchedEntityData.defineId(SteamLocomotiveEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> WAGONS_COUNT =
		SynchedEntityData.defineId(SteamLocomotiveEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> TRACK_GRADE =
		SynchedEntityData.defineId(SteamLocomotiveEntity.class, EntityDataSerializers.FLOAT);

	private float wheelRotation = 0.0F;
	private float prevWheelRotation = 0.0F;
	private Vec3 lastTravelDir = Vec3.ZERO;
	private final Map<UUID, Vec3> followerTrackFronts = new HashMap<>();
	private boolean lastReverserApplied;
	private boolean lastReverserInitialized;
	private boolean wasMoving;
	private int stopSteamPuffTicks;

	// Permanent tender (locomotive-with-tender pair)
	private Optional<UUID> permanentTenderId = Optional.empty();

	// Breadcrumb trail for hinge kinematics (positions the locomotive traversed)
	private static final int MAX_BREADCRUMBS = 4096;
	public static final double HINGE_DISTANCE = 3.00D;
	private static final double TENDER_WAGON_DISTANCE = 2.50D;
	private static final double BUFFER_GAP = 0.40D;
	private static final double BUFFER_SOLID = 0.05D;
	private static final double BUFFER_STIFFNESS = 0.55D;
	private static final double BUFFER_DAMPING = 0.85D;
	private static final double TENDER_WAGON_TOLERANCE = 0.25D;
	private static final double COUPLE_REACH = 4.0D;
	private static final double CURVE_RESISTANCE_STEP = 0.0001D;
	private static final double MAX_CURVE_RESISTANCE = 0.0005D;
	private final Deque<Vec3> breadcrumbs = new ArrayDeque<>();

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
		builder.define(ENGINE_LOAD, 0.0F);
		builder.define(WAGONS_COUNT, 0);
		builder.define(TRACK_GRADE, 0.0F);
	}

	@Override
	public EntityDimensions getDimensions(final Pose pose) {
		return EntityDimensions.scalable(1.2F, 1.85F);
	}

	public float getThrottle() {
		return this.entityData.get(THROTTLE);
	}

	public float getEngineLoad() {
		return this.entityData.get(ENGINE_LOAD);
	}

	public void setEngineLoad(final float load) {
		this.entityData.set(ENGINE_LOAD, Mth.clamp(load, 0.0F, 1.0F));
	}

	public int getWagonsCount() {
		return this.entityData.get(WAGONS_COUNT);
	}

	public void setWagonsCount(final int count) {
		this.entityData.set(WAGONS_COUNT, Math.max(0, count));
	}

	public float getTrackGrade() {
		return this.entityData.get(TRACK_GRADE);
	}

	public void setTrackGrade(final float grade) {
		this.entityData.set(TRACK_GRADE, Mth.clamp(grade, -1.0F, 1.0F));
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

	// --- Permanent tender API ---

	public Optional<UUID> getPermanentTenderId() {
		return this.permanentTenderId;
	}

	public boolean hasPermanentTender() {
		return this.permanentTenderId.isPresent();
	}

	public void setPermanentTender(final UUID id) {
		this.permanentTenderId = Optional.of(id);
	}

	@Override
	public void destroy(final ServerLevel level, final net.minecraft.world.damagesource.DamageSource damageSource) {
		super.destroy(level, damageSource);
		if (this.hasPermanentTender()) {
			CoalTenderEntity tender = this.findPermanentTender(level);
			if (tender != null && !tender.isRemoved()) {
				tender.chestVehicleDestroyed(damageSource, level, tender);
				tender.discard();
			}
		}
	}

	/**
	 * Called by the tender when it is destroyed first, so locomotive can do a clean drop.
	 */
	public void destroyAsPermanentTender(final ServerLevel level, final net.minecraft.world.damagesource.DamageSource damageSource) {
		if (!this.isRemoved()) {
			this.destroy(level, damageSource);
		}
	}

	public void handleDriverInput(
		final float throttle,
		final boolean reverser,
		final boolean coupleNext,
		final boolean uncoupleLast
	) {
		if (!((MinecartChainAccess) this).minecartChain$isRadioLocomotive()) {
			this.setThrottle(throttle);
			this.setReverser(reverser);
		}
		if (this.level() instanceof ServerLevel serverLevel) {
			if (uncoupleLast) {
				this.uncoupleLastWagon(serverLevel);
			} else if (coupleNext) {
				this.coupleNextWagon(serverLevel);
			}
		}
	}

	public void handleDriverInput(final float throttle, final boolean reverser) {
		this.handleDriverInput(throttle, reverser, false, false);
	}

	private void coupleNextWagon(final ServerLevel serverLevel) {
		List<AbstractMinecart> train = MinecartTrainLogic.orderedTrain(serverLevel, this);
		AbstractMinecart tail = train.get(train.size() - 1);
		MinecartChainAccess tailData = (MinecartChainAccess) tail;
		if (!tailData.minecartChain$canAcceptLink()) {
			return;
		}

		// A new wagon may be at most 4 blocks from the current tail, never from the locomotive.
		boolean hasWagon = train.size() > (this.hasPermanentTender() ? 2 : 1);
		double yawRadians = Math.toRadians(tail.getYRot());
		Vec3 rearDirection = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians)).reverse();
		Set<UUID> trainIds = new HashSet<>();
		for (AbstractMinecart cart : train) {
			trainIds.add(cart.getUUID());
		}

		AbstractMinecart closest = null;
		double closestDistance = COUPLE_REACH * COUPLE_REACH;
		for (Entity entity : serverLevel.getEntities(tail, tail.getBoundingBox().inflate(COUPLE_REACH))) {
			if (!(entity instanceof AbstractMinecart candidate)
				|| candidate.isRemoved()
				|| trainIds.contains(candidate.getUUID())) {
				continue;
			}
			MinecartChainAccess candidateData = (MinecartChainAccess) candidate;
			if (!candidateData.minecartChain$canAcceptLink()) {
				continue;
			}
			Vec3 toCandidate = candidate.position().subtract(tail.position()).horizontal();
			if (toCandidate.lengthSqr() > COUPLE_REACH * COUPLE_REACH) {
				continue;
			}
			if (hasWagon && toCandidate.dot(rearDirection) <= 0.0D) {
				continue;
			}
			if (!hasWagon && toCandidate.dot(rearDirection) <= -0.2D && toCandidate.lengthSqr() >= 1.5D) {
				continue;
			}
			double distance = toCandidate.lengthSqr();
			if (distance < closestDistance) {
				closest = candidate;
				closestDistance = distance;
			}
		}

		Player rider = this.getFirstPassenger() instanceof Player player ? player : null;
		if (closest == null) {
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 0.6F);
			if (rider != null) {
				rider.sendOverlayMessage(Component.literal("Coupler: No next wagon in reach"));
			}
			return;
		}

		tailData.minecartChain$addLink(closest.getUUID());
		((MinecartChainAccess) closest).minecartChain$addLink(tail.getUUID());
		this.setCouplerEngaged(true);
		serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
		if (rider != null) {
			rider.sendOverlayMessage(Component.literal("Coupler: Added wagon " + (train.size() - (this.hasPermanentTender() ? 1 : 0))));
		}
	}

	private void uncoupleLastWagon(final ServerLevel serverLevel) {
		List<AbstractMinecart> train = MinecartTrainLogic.orderedTrain(serverLevel, this);
		int protectedCount = this.hasPermanentTender() ? 2 : 1;
		Player rider = this.getFirstPassenger() instanceof Player player ? player : null;
		if (train.size() <= protectedCount) {
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 0.6F);
			if (rider != null) {
				rider.sendOverlayMessage(Component.literal("Coupler: No wagon to disconnect"));
			}
			return;
		}

		AbstractMinecart last = train.get(train.size() - 1);
		AbstractMinecart previous = train.get(train.size() - 2);
		((MinecartChainAccess) last).minecartChain$removeLink(previous.getUUID());
		((MinecartChainAccess) previous).minecartChain$removeLink(last.getUUID());
		this.setCouplerEngaged(train.size() - 1 > protectedCount);
		serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
		if (rider != null) {
			rider.sendOverlayMessage(Component.literal("Coupler: Disconnected last wagon"));
		}
	}

	public void toggleCoupler(final ServerLevel serverLevel) {
		MinecartChainAccess data = (MinecartChainAccess) this;

		// If this locomotive has a permanent tender, the C key manages the wagon BEHIND the tender
		if (this.hasPermanentTender()) {
			CoalTenderEntity tender = this.findPermanentTender(serverLevel);
			if (tender == null) {
				return;
			}
			MinecartChainAccess tenderData = (MinecartChainAccess) tender;

			// Find any link on the tender that is NOT this locomotive (i.e. wagon behind tender)
			UUID wagonUUID = null;
			Optional<UUID> link1 = tenderData.minecartChain$getFirstLink();
			Optional<UUID> link2 = tenderData.minecartChain$getSecondLink();
			if (link1.isPresent() && !link1.get().equals(this.getUUID())) {
				wagonUUID = link1.get();
			} else if (link2.isPresent() && !link2.get().equals(this.getUUID())) {
				wagonUUID = link2.get();
			}

			if (wagonUUID != null) {
				// Wagon is coupled to tender – uncouple it
				Entity wagonEntity = serverLevel.getEntity(wagonUUID);
				if (wagonEntity instanceof AbstractMinecart wagon) {
					((MinecartChainAccess) wagon).minecartChain$removeLink(tender.getUUID());
				}
				tenderData.minecartChain$removeLink(wagonUUID);
				serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
				this.setCouplerEngaged(false);
				if (this.getFirstPassenger() instanceof Player player) {
					player.sendOverlayMessage(Component.literal("Coupler: Disconnected wagon behind tender"));
				}
			} else {
				// Search behind the tender, the current tail, within 4 blocks.
				double searchRadius = COUPLE_REACH;
				double yawRad = Math.toRadians(tender.getYRot());
				Vec3 tenderFacing = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
				Vec3 rearDir = tenderFacing.reverse();

				AbstractMinecart closest = null;
				double closestDistSqr = searchRadius * searchRadius;

				for (Entity entity : serverLevel.getEntities(tender, tender.getBoundingBox().inflate(searchRadius))) {
					if (entity instanceof AbstractMinecart cart
						&& !cart.isRemoved()
						&& cart != this
						&& cart != tender) {
						MinecartChainAccess cartData = (MinecartChainAccess) cart;
						if (tenderData.minecartChain$hasLink(cart.getUUID()) || !cartData.minecartChain$canAcceptLink()) {
							continue;
						}
						Vec3 toCart = cart.position().subtract(tender.position()).horizontal();
						if (toCart.lengthSqr() <= COUPLE_REACH * COUPLE_REACH
							&& (toCart.dot(rearDir) > -0.2D || toCart.lengthSqr() < 1.5D)) {
							double dSqr = toCart.lengthSqr();
							if (dSqr < closestDistSqr) {
								closest = cart;
								closestDistSqr = dSqr;
							}
						}
					}
				}

				if (closest != null) {
					tenderData.minecartChain$addLink(closest.getUUID());
					((MinecartChainAccess) closest).minecartChain$addLink(tender.getUUID());
					this.setCouplerEngaged(true);
					serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
					if (this.getFirstPassenger() instanceof Player player) {
						player.sendOverlayMessage(Component.literal("Coupler: Connected wagon to tender"));
					}
				} else {
					serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 0.6F);
					if (this.getFirstPassenger() instanceof Player player) {
						player.sendOverlayMessage(Component.literal("Coupler: No wagon in reach"));
					}
				}
			}
			return;
		}

		// Standard coupler behavior (no permanent tender)
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

		// The locomotive is the tail until a wagon is attached. Reach is 4 blocks.
		double searchRadius = COUPLE_REACH;
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
				// Verify the cart is behind the locomotive (or close enough) and within 4 blocks.
				if (toCart.lengthSqr() <= COUPLE_REACH * COUPLE_REACH
					&& (toCart.dot(rearDir) > -0.2D || toCart.lengthSqr() < 1.5D)) {
					double dSqr = toCart.lengthSqr();
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

		MinecartChainAccess chainData = (MinecartChainAccess) this;
		if (chainData.minecartChain$isRadioLocomotive() && (player.isSecondaryUseActive() || stack.isEmpty())) {
			if (!this.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
				RadioLocomotiveMenu.open(serverPlayer, this);
			}
			return InteractionResult.SUCCESS;
		}

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
		if (this.hasPermanentTender()) {
			return MinecartChainMod.STEAM_LOCOMOTIVE_WITH_TENDER_ITEM;
		}
		if (((MinecartChainAccess) this).minecartChain$isRadioLocomotive()) {
			Item radioItem = BuiltInRegistries.ITEM.getValue(MinecartChainMod.id("radio_steam_locomotive"));
			if (radioItem != Items.AIR) {
				return radioItem;
			}
		}
		Item item = BuiltInRegistries.ITEM.getValue(MinecartChainMod.id("steam_locomotive"));
		return item != Items.AIR ? item : Items.MINECART;
	}

	@Override
	public ItemStack getPickResult() {
		if (this.hasPermanentTender()) {
			return new ItemStack(MinecartChainMod.STEAM_LOCOMOTIVE_WITH_TENDER_ITEM);
		}
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
		this.permanentTenderId.ifPresent(id -> output.putString("PermanentTender", id.toString()));
		if (!this.breadcrumbs.isEmpty()) {
			output.store("Breadcrumbs", Vec3.CODEC.listOf(), new ArrayList<>(this.breadcrumbs));
		}
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
		String tenderIdStr = input.getStringOr("PermanentTender", "");
		if (!tenderIdStr.isBlank()) {
			try {
				this.permanentTenderId = Optional.of(UUID.fromString(tenderIdStr));
			} catch (IllegalArgumentException ignored) {
				this.permanentTenderId = Optional.empty();
			}
		}
		input.read("Breadcrumbs", Vec3.CODEC.listOf()).ifPresent(list -> {
			this.breadcrumbs.clear();
			this.breadcrumbs.addAll(list);
		});
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
			boolean hasLinks;
			if (this.hasPermanentTender()) {
				CoalTenderEntity tender = this.findPermanentTender(serverLevel);
				if (tender != null) {
					MinecartChainAccess tenderData = (MinecartChainAccess) tender;
					hasLinks = tenderData.minecartChain$getFirstLink().filter(id -> !id.equals(this.getUUID())).isPresent()
						|| tenderData.minecartChain$getSecondLink().filter(id -> !id.equals(this.getUUID())).isPresent();
				} else {
					hasLinks = false;
				}
			} else {
				hasLinks = ((MinecartChainAccess) this).minecartChain$linkCount() > 0;
			}
			if (this.entityData.get(COUPLER_ENGAGED) != hasLinks) {
				this.setCouplerEngaged(hasLinks);
			}

			boolean isMoving = hSpeed > 0.015D;
			if (this.wasMoving && !isMoving) {
				this.stopSteamPuffTicks = 10;
				serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.45F, 1.25F);
			}
			this.wasMoving = isMoving;

			// Update breadcrumb trail for hinge kinematics
			this.updateBreadcrumbs();

			boolean infinite = rider instanceof Player player && player.hasInfiniteMaterials();
			if (((MinecartChainAccess) this).minecartChain$isRadioLocomotive()) {
				this.tickRadioControl(serverLevel);
			} else {
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
				}
				// Idle provides no traction; rail friction slows the train naturally.

				emitParticles(serverLevel, throttle, hasSteam, hSpeed);
				playSounds(serverLevel, throttle, hasSteam);
			}

			// Always guide permanent tender along hinge path and guide wagons behind tender
			if (this.hasPermanentTender()) {
				UUID tenderId = this.permanentTenderId.get();
				MinecartChainAccess locoData = (MinecartChainAccess) this;
				if (!locoData.minecartChain$hasLink(tenderId)) {
					locoData.minecartChain$addLink(tenderId);
				}

				CoalTenderEntity tender = this.findPermanentTender(serverLevel);
				if (tender != null) {
					if (!tender.hasPermanentLocomotive() || !tender.getPermanentLocomotiveId().filter(id -> id.equals(this.getUUID())).isPresent()) {
						tender.setPermanentLocomotive(this.getUUID());
					}
					MinecartChainAccess tenderData = (MinecartChainAccess) tender;
					if (!tenderData.minecartChain$hasLink(this.getUUID())) {
						tenderData.minecartChain$addLink(this.getUUID());
					}
					// A spline tender must retain its follower, just like the other wagons.
				}
				// positionPermanentTender also places every loaded wagon behind the
				// tender on this same centreline. Heading comes from the rail, never
				// from the wagons' drag, including while coasting through a curve.
				if (throttle <= 0.0F) {
					this.followRailHeading(serverLevel);
				}
				Vec3 travel = this.trackTravel(serverLevel);
				double signedBeforeWagons = MinecartTrainLogic.horizontalMovement(this).dot(travel);
				this.positionPermanentTender(serverLevel);
				this.absorbWagonCurveResistance(travel, signedBeforeWagons);
			} else if (hSpeed > 0.005D) {
				guideCoupledWagons(serverLevel);
			}
			this.applyConsistBuffers(serverLevel);
			this.updateTelemetry(serverLevel, hSpeed, throttle);
		}

		if (this.level() instanceof ServerLevel serverLevel) {
			this.pushPlayersAside(serverLevel);
		}
		this.applyFacingYaw(this.getFacingYaw());
	}

	@Override
	public boolean isPushable() {
		// A player walking into the locomotive must not change its rail speed.
		return false;
	}

	public double getTrackGrade(final ServerLevel serverLevel, final Vec3 travelDir) {
		Vec3 travelNorm = travelDir.lengthSqr() > 1.0E-6D ? travelDir.normalize() : this.getVisualForward();

		// 1. Splinecart mounted tracks (trackDirection provides the 3D tangent vector):
		if (MountedTrackCompat.isMounted(this)) {
			Vec3 track3D = MountedTrackCompat.trackDirection(this);
			if (track3D.lengthSqr() > 1.0E-6D) {
				double sign = travelNorm.dot(track3D.horizontal()) >= 0.0D ? 1.0D : -1.0D;
				// Incline along the 3D spline tangent: positive = uphill, negative = downhill
				return Mth.clamp(track3D.y * sign * 2.0D, -1.0D, 1.0D);
			}
		}

		// 2. Vanilla Minecraft rail blocks:
		BlockPos railPos = this.getCurrentBlockPosOrRailBelow();
		BlockState railState = serverLevel.getBlockState(railPos);
		if (!BaseRailBlock.isRail(railState)) {
			railPos = railPos.below();
			railState = serverLevel.getBlockState(railPos);
		}

		if (BaseRailBlock.isRail(railState) && railState.getBlock() instanceof BaseRailBlock railBlock) {
			RailShape shape = railState.getValue(railBlock.getShapeProperty());
			Vec3 ascentDir = switch (shape) {
				case ASCENDING_NORTH -> new Vec3(0.0D, 0.0D, -1.0D);
				case ASCENDING_SOUTH -> new Vec3(0.0D, 0.0D, 1.0D);
				case ASCENDING_EAST -> new Vec3(1.0D, 0.0D, 0.0D);
				case ASCENDING_WEST -> new Vec3(-1.0D, 0.0D, 0.0D);
				default -> Vec3.ZERO;
			};
			if (ascentDir.lengthSqr() > 0.5D) {
				// Dot product with travel: +1.0 = driving uphill, -1.0 = driving downhill
				return travelNorm.dot(ascentDir);
			}
		}

		// 3. Dynamic motion fallback:
		double dy = this.getY() - this.yOld;
		double hDist = this.getDeltaMovement().horizontalDistance();
		if (Math.abs(dy) > 0.005D && hDist > 0.01D) {
			return Mth.clamp((dy / hDist) * 1.5D, -1.0D, 1.0D);
		}

		return 0.0D;
	}

	private void updateTelemetry(final ServerLevel serverLevel, final double hSpeed, final float throttle) {
		List<AbstractMinecart> train = MinecartTrainLogic.orderedTrain(serverLevel, this);
		int wagonCount = Math.max(0, train.size() - (this.hasPermanentTender() ? 2 : 1));
		this.setWagonsCount(wagonCount);

		Vec3 travel = this.establishedTravel();
		double grade = this.getTrackGrade(serverLevel, travel);
		this.setTrackGrade((float) grade);

		// 1. Base rolling and internal static friction resistance:
		// Bare locomotive: ~7-8%
		double baseResistance = 0.07D;

		// 2. Mass resistance from connected consist and cargo:
		// Each additional car adds ~3-4% breakaway resistance:
		double mass = this.trainMass(serverLevel);
		double massFactor = (mass - 2.0D) * 0.028D;

		// Grade effect:
		// UPHILL (grade > 0): gravity acts against movement, increasing engine load.
		// DOWNHILL (grade < 0): gravity assists movement, pushing the train down and relieving load!
		double gradeEffect;
		if (grade > 0.0D) {
			gradeEffect = grade * (0.20D + Math.min(0.18D, massFactor * 0.6D));
		} else {
			gradeEffect = grade * (0.16D + Math.min(0.12D, massFactor * 0.4D));
		}

		// Curve drag:
		double curveDrag = this.wagonCurveDrag(travel);
		double curveResistance = Math.min(0.06D, curveDrag * 20.0D);

		// Static breakaway resistance on stop:
		double staticBreakaway = baseResistance + massFactor + gradeEffect + curveResistance;

		// Dynamic rolling resistance in motion:
		// Static stiction vanishes gradually as train gains speed up to cruising range (0.35 blocks/tick = ~25 km/h):
		double motionFactor = Mth.clamp(hSpeed / 0.35D, 0.0D, 1.0D);
		double dynamicRolling = 0.03D + (massFactor * 0.20D) + gradeEffect + curveResistance;

		double currentRequiredLoad = Mth.lerp(motionFactor, staticBreakaway, dynamicRolling);
		float targetLoad = (float) Mth.clamp(currentRequiredLoad, 0.02D, 0.90D);

		// Smooth, heavy mechanical needle filtering (0.025F per tick):
		float currentLoad = this.getEngineLoad();
		this.setEngineLoad(Mth.approach(currentLoad, targetLoad, 0.025F));
	}

	private void pushPlayersAside(final ServerLevel level) {
		if (MinecartTrainLogic.horizontalMovement(this).lengthSqr() < 0.0025D) {
			return;
		}
		Vec3 forward = this.getVisualForward();
		Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
		for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(0.15D))) {
			if (player.isSpectator() || player.isCreative() || this.hasPassenger(player)) {
				continue;
			}
			Vec3 offset = player.position().subtract(this.position());
			Vec3 sideways = right.scale(offset.dot(right) < 0.0D ? -1.0D : 1.0D);
			player.hurtServer(level, this.damageSources().generic(), 2.0F);
			player.push(sideways.x * 0.8D, 0.2D, sideways.z * 0.8D);
		}
	}

	@Override
	public boolean isRideable() {
		return true;
	}

	private void tickRadioControl(final ServerLevel serverLevel) {
		MinecartChainAccess data = (MinecartChainAccess) this;
		int throttleCh = data.minecartChain$getThrottleChannel();
		int reverseCh = data.minecartChain$getReverseChannel();

		int throttlePower = RadioLocomotiveCompat.getChannelPower(throttleCh);
		int reversePower = RadioLocomotiveCompat.getChannelPower(reverseCh);

		data.minecartChain$setReceivedThrottlePower(throttlePower);
		data.minecartChain$setReceivedReversePower(reversePower);

		double hSpeed = MinecartTrainLogic.horizontalMovement(this).length();

		if (MinecartChainConfig.layoutMode) {
			if (reversePower > 0) {
				if (!this.isCouplerEngaged() && data.minecartChain$linkCount() == 0) {
					this.toggleCoupler(serverLevel);
				}
			} else {
				if (this.isCouplerEngaged() || data.minecartChain$linkCount() > 0) {
					this.toggleCoupler(serverLevel);
				}
			}

			boolean isArmed = data.minecartChain$isArmed();
			if (!isArmed) {
				if (throttlePower == 7 || throttlePower == 8) {
					data.minecartChain$setArmed(true);
					serverLevel.playSound(
						null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0F, 1.2F
					);
				}
				this.setThrottle(-1.0F);
				MinecartTrainLogic.applyTrainBrake(this);
				emitParticles(serverLevel, -1.0F, false, 0.0D);
				return;
			}

			if (throttlePower == 7 || throttlePower == 8) {
				this.setThrottle(-1.0F);
				MinecartTrainLogic.applyTrainBrake(this);
				data.minecartChain$setReversePending(false);
				boolean hasSteam = (this.getFuelTicks() > 0 && this.getWaterTicks() > 0) || !MinecartChainConfig.requireFuel;
				emitParticles(serverLevel, -1.0F, hasSteam, 0.0D);
				return;
			}

			boolean desiredReverse = (throttlePower < 7);
			boolean currentlyReversed = this.isReverser();
			boolean stopped = MinecartTrainLogic.isTrainStopped(this) || hSpeed <= 0.005D;

			if (desiredReverse != currentlyReversed) {
				if (stopped) {
					this.setReverser(desiredReverse);
					data.minecartChain$setReversePending(false);
					serverLevel.playSound(
						null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.9F, desiredReverse ? 0.75F : 0.6F
					);
				} else {
					data.minecartChain$setReversePending(true);
					this.setThrottle(-1.0F);
					MinecartTrainLogic.applyTrainBrake(this);
					boolean hasSteam = (this.getFuelTicks() > 0 && this.getWaterTicks() > 0) || !MinecartChainConfig.requireFuel;
					emitParticles(serverLevel, -1.0F, hasSteam, 0.0D);
					return;
				}
			} else {
				data.minecartChain$setReversePending(false);
			}

			double calcThrottle;
			if (desiredReverse) {
				calcThrottle = 0.2D + 0.65D * ((7.0D - throttlePower) / 7.0D);
			} else {
				calcThrottle = 0.2D + 0.8D * ((throttlePower - 8.0D) / 7.0D);
			}

			float activeThrottle = (float) Mth.clamp(calcThrottle, 0.0D, 1.0D);
			this.setThrottle(activeThrottle);

			boolean fuelMissing = MinecartChainConfig.requireFuel && this.getFuelTicks() <= 0;
			boolean waterMissing = MinecartChainConfig.requireWater && this.getWaterTicks() <= 0;
			if (fuelMissing || waterMissing) {
				this.setThrottle(-1.0F);
				MinecartTrainLogic.applyTrainBrake(this);
				emitParticles(serverLevel, -1.0F, false, 0.0D);
				return;
			}

			if (MinecartChainConfig.requireFuel) {
				this.setFuelTicks(Math.max(0, this.getFuelTicks() - 1));
			}
			if (MinecartChainConfig.requireWater && this.tickCount % 2 == 0) {
				this.setWaterTicks(Math.max(0, this.getWaterTicks() - 1));
			}

			driveForward(serverLevel, activeThrottle);
			emitParticles(serverLevel, activeThrottle, true, hSpeed);
			playSounds(serverLevel, activeThrottle, true);
			return;
		}

		// Classic mode
		boolean desiredReverse = reversePower > 0;
		boolean currentlyReversed = this.isReverser();
		boolean stopped = MinecartTrainLogic.isTrainStopped(this) || hSpeed <= 0.005D;

		if (desiredReverse != currentlyReversed) {
			if (stopped) {
				this.setReverser(desiredReverse);
				data.minecartChain$setReversePending(false);
				serverLevel.playSound(
					null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.9F, desiredReverse ? 0.75F : 0.6F
				);
			} else {
				data.minecartChain$setReversePending(true);
				this.setThrottle(-1.0F);
				MinecartTrainLogic.applyTrainBrake(this);
				boolean hasSteam = (this.getFuelTicks() > 0 && this.getWaterTicks() > 0) || !MinecartChainConfig.requireFuel;
				emitParticles(serverLevel, -1.0F, hasSteam, 0.0D);
				return;
			}
		} else {
			data.minecartChain$setReversePending(false);
		}

		if (throttlePower <= 0) {
			this.setThrottle(-1.0F);
			MinecartTrainLogic.applyTrainBrake(this);
			boolean hasSteam = (this.getFuelTicks() > 0 && this.getWaterTicks() > 0) || !MinecartChainConfig.requireFuel;
			emitParticles(serverLevel, -1.0F, hasSteam, 0.0D);
			return;
		}

		float activeThrottle = (float) (throttlePower / 15.0D);
		this.setThrottle(activeThrottle);

		boolean fuelMissing = MinecartChainConfig.requireFuel && this.getFuelTicks() <= 0;
		boolean waterMissing = MinecartChainConfig.requireWater && this.getWaterTicks() <= 0;
		if (fuelMissing || waterMissing) {
			this.setThrottle(-1.0F);
			MinecartTrainLogic.applyTrainBrake(this);
			emitParticles(serverLevel, -1.0F, false, 0.0D);
			return;
		}

		if (MinecartChainConfig.requireFuel) {
			this.setFuelTicks(Math.max(0, this.getFuelTicks() - 1));
		}
		if (MinecartChainConfig.requireWater && this.tickCount % 2 == 0) {
			this.setWaterTicks(Math.max(0, this.getWaterTicks() - 1));
		}

		driveForward(serverLevel, activeThrottle);
		emitParticles(serverLevel, activeThrottle, true, hSpeed);
		playSounds(serverLevel, activeThrottle, true);
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

		// A tender consist must not treat wagon-drag velocity as the rail tangent.
		// That sample is often sideways or backward on a curve and would turn the cab.
		if (!this.hasPermanentTender()) {
			Vec3 currentMovement = this.getDeltaMovement().horizontal();
			if (currentMovement.lengthSqr() > 0.0025D) {
				return currentMovement.normalize();
			}
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
		// Track tangent (aligned to the existing cab), never the sign of
		// velocity: rolling downhill backwards must not rotate the engine.
		Vec3 headingRef = trackLine;
		if (headingRef.lengthSqr() > 1.0E-4D && !MinecartTrainLogic.isTrainStopped(this)) {
			Vec3 engineFront = headingRef;
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
		Vec3 splineDir = MountedTrackCompat.trackDirection(this);
		if (splineDir.lengthSqr() <= 1.0E-8D) {
			splineDir = this.getVisualForward();
		} else {
			splineDir = splineDir.normalize();
		}

		Vec3 currentMovement = MinecartTrainLogic.horizontalMovement(this);
		// Uphill stalls can make the entire consist roll back. Traction must still
		// point where the engine faces, not in the direction of that rollback.
		Vec3 forward = this.getVisualForward();
		Vec3 travelDir = forward.dot(splineDir) >= 0.0D ? splineDir : splineDir.reverse();
		if (this.isReverser()) {
			travelDir = travelDir.reverse();
		}
		this.lastTravelDir = travelDir;

		double currentSpeed = currentMovement.dot(travelDir);
		boolean isMoving = Math.abs(currentSpeed) > 0.012D;
		float breakawayThreshold = this.getEngineLoad();
		double grade = this.getTrackGrade(this.level() instanceof ServerLevel sl ? sl : null, travelDir);

		// If stopped at a standstill, throttle must exceed breakaway resistance to start:
		if (!isMoving && throttle <= breakawayThreshold && grade >= -0.1D) {
			return;
		}

		// Drive dynamics based on net surplus above breakaway resistance:
		double surplus = Math.max(0.0D, (double) throttle - (double) breakawayThreshold);
		double maxSurplus = Math.max(0.05D, 1.0D - (double) breakawayThreshold);
		double surplusRatio = Mth.clamp(surplus / maxSurplus, 0.0D, 1.0D);

		// When barely exceeding load threshold, starting crawl is extremely slow and heavy:
		double speedTransition = Mth.clamp(Math.abs(currentSpeed) / 0.12D, 0.0D, 1.0D);
		double crawlScale = Math.max(0.06D, surplusRatio * surplusRatio);
		double powerScale = Mth.lerp(speedTransition, crawlScale, Math.max(0.20D, Math.sqrt(surplusRatio)));

		// Once moving, throttle dictates cruising speed; easing off allows smooth coasting:
		double targetSpeed = MAX_SPEED * (double) throttle * (isMoving ? 1.0D : powerScale);
		if (grade < -0.15D && throttle <= 0.01F) {
			// Downhill gravity rolls train forward freely even on zero throttle:
			targetSpeed = Math.min(MAX_SPEED * 0.7D, -grade * 0.28D);
		} else if (grade > 0.15D && throttle < breakawayThreshold) {
			// Uphill slows cruising speed if power is insufficient:
			targetSpeed = Math.max(0.0D, targetSpeed - grade * 0.12D);
		}

		double accel = this.tractionAcceleration(throttle) * (isMoving ? Math.max(0.35D, powerScale) : Math.max(0.06D, powerScale));
		double newSpeed;
		if (currentSpeed < targetSpeed) {
			newSpeed = Math.min(targetSpeed, currentSpeed + accel);
		} else {
			// Coasting down with consist inertia (never an abrupt stop):
			double inertia = Math.max(2.0D, this.trainMass(this.level() instanceof ServerLevel sl ? sl : null));
			double coastDecel = 0.003D / (inertia * 0.5D);
			if (grade > 0.15D) {
				coastDecel += grade * 0.006D; // Uphill decelerates coasting faster
			}
			newSpeed = Math.max(targetSpeed, currentSpeed - coastDecel);
		}

		// Keep the signed velocity: clamping it to zero would stop a train
		// instantly the moment it starts sliding backwards on a slope.
		Vec3 newMovement = travelDir.scale(newSpeed);
		MinecartTrainLogic.setHorizontalMovement(this, newMovement);

		// Do not copy the locomotive's world-space vector to each wagon: on
		// curves its projection onto a wagon's local rail may be zero. The
		// follower guides below match signed speed along each wagon's own track.
		if (!this.hasPermanentTender() && this.level() instanceof ServerLevel serverLevel) {
			this.guideCoupledWagons(serverLevel);
		}

		Vec3 finalMovement = MinecartTrainLogic.horizontalMovement(this);
		if (finalMovement.lengthSqr() > 0.0025D) {
			// Track tangent changes through curves; velocity reversals do not turn
			// the locomotive around or swap the order of the wagons.
			Vec3 front = this.isReverser() ? travelDir.reverse() : travelDir;
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

		Vec3 currentVelocity = MinecartTrainLogic.horizontalMovement(this);
		double currentDot = currentVelocity.dot(driveDirection.normalize());
		boolean isMoving = Math.abs(currentDot) > 0.012D;
		float breakawayThreshold = this.getEngineLoad();
		ServerLevel serverLevel = this.level() instanceof ServerLevel sl ? sl : null;
		double grade = serverLevel != null ? this.getTrackGrade(serverLevel, driveDirection) : 0.0D;

		BlockPos railPos = this.getCurrentBlockPosOrRailBelow();
		BlockState railState = this.level().getBlockState(railPos);
		boolean isSlope = false;
		if (BaseRailBlock.isRail(railState) && railState.getBlock() instanceof BaseRailBlock railBlock) {
			isSlope = railState.getValue(railBlock.getShapeProperty()).isSlope();
		}
		double targetY = isSlope ? currentVelocity.y : Math.min(0.0D, currentVelocity.y);

		// If stopped at a standstill, throttle must exceed breakaway resistance (unless steep downhill):
		if (!isMoving && throttle <= breakawayThreshold && grade >= -0.1D) {
			MinecartTrainLogic.setHorizontalMovement(this, new Vec3(0.0D, targetY, 0.0D));
			return;
		}

		// Drive dynamics based on net surplus above breakaway resistance:
		double surplus = Math.max(0.0D, (double) throttle - (double) breakawayThreshold);
		double maxSurplus = Math.max(0.05D, 1.0D - (double) breakawayThreshold);
		double surplusRatio = Mth.clamp(surplus / maxSurplus, 0.0D, 1.0D);

		// When barely exceeding load threshold, starting crawl is extremely slow and heavy:
		double speedTransition = Mth.clamp(Math.abs(currentDot) / 0.12D, 0.0D, 1.0D);
		double crawlScale = Math.max(0.06D, surplusRatio * surplusRatio);
		double powerScale = Mth.lerp(speedTransition, crawlScale, Math.max(0.20D, Math.sqrt(surplusRatio)));

		// Once moving, target speed is proportional to throttle:
		double targetSpeed = MAX_SPEED * (double) throttle * (isMoving ? 1.0D : powerScale);
		if (grade < -0.15D && throttle <= 0.01F) {
			// Downhill gravity rolls train forward freely even on zero throttle:
			targetSpeed = Math.min(MAX_SPEED * 0.7D, -grade * 0.28D);
		} else if (grade > 0.15D && throttle < breakawayThreshold) {
			// Uphill slows cruising speed if power is insufficient:
			targetSpeed = Math.max(0.0D, targetSpeed - grade * 0.12D);
		}

		Vec3 targetVelocity = driveDirection.normalize().scale(targetSpeed);
		double accel = this.tractionAcceleration(throttle) * (isMoving ? Math.max(0.35D, powerScale) : Math.max(0.06D, powerScale));

		Vec3 newVelocity;
		if (currentDot < targetSpeed) {
			// Accelerating towards target speed:
			newVelocity = new Vec3(
				Mth.approach((float) currentVelocity.x, (float) targetVelocity.x, (float) accel),
				targetY,
				Mth.approach((float) currentVelocity.z, (float) targetVelocity.z, (float) accel)
			);
		} else {
			// Coasting down towards target speed with train inertia (never an instant stop):
			double inertia = Math.max(2.0D, this.trainMass(serverLevel));
			double coastDecel = 0.003D / (inertia * 0.5D);
			if (grade > 0.15D) {
				coastDecel += grade * 0.006D;
			}
			newVelocity = new Vec3(
				Mth.approach((float) currentVelocity.x, (float) targetVelocity.x, (float) coastDecel),
				targetY,
				Mth.approach((float) currentVelocity.z, (float) targetVelocity.z, (float) coastDecel)
			);
		}
		MinecartTrainLogic.setHorizontalMovement(this, newVelocity);
	}

	private void guideCoupledWagons(final ServerLevel serverLevel) {
		if (this.hasPermanentTender()) {
			return; // The tender path pass positions the complete consist.
		}
		if (this.breadcrumbs.size() >= 2) {
			this.positionTenderWagons(serverLevel, this, this.breadcrumbs.toArray(new Vec3[0]),
				MinecartTrainLogic.horizontalMovement(this), 0.0D);
		}
	}

	public void initBreadcrumbs(final Vec3 rearPoint, final Vec3 frontPoint) {
		this.breadcrumbs.clear();
		this.breadcrumbs.addFirst(frontPoint);
		int steps = 8;
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			this.breadcrumbs.addLast(frontPoint.lerp(rearPoint, t));
		}
	}

	/**
	 * Find the permanent tender entity in the server level.
	 */
	private CoalTenderEntity findPermanentTender(final ServerLevel serverLevel) {
		if (this.permanentTenderId.isEmpty()) {
			return null;
		}
		Entity entity = serverLevel.getEntity(this.permanentTenderId.get());
		if (entity instanceof CoalTenderEntity tender && !tender.isRemoved()) {
			return tender;
		}
		return null;
	}

	/**
	 * Record the current locomotive position in the breadcrumb trail.
	 * Keeps entries to cover at least HINGE_DISTANCE + 1.5 blocks behind locomotive.
	 */
	private void updateBreadcrumbs() {
		Vec3 pos = this.position();
		Vec3 delta = this.breadcrumbs.isEmpty() ? Vec3.ZERO : pos.subtract(this.breadcrumbs.peekFirst());
		Vec3 travel = this.establishedTravel();
		Vec3 horizontalDelta = delta.horizontal();
		double alongTravel = horizontalDelta.dot(travel);
		double horizontalSpeed = horizontalDelta.length();
		// Facing lags the rail on a curve, so a tiny negative dot with the cab is not
		// a reversal. Only a real rollback may shift the path instead of extending it.
		boolean rollingBack = !this.isReverser() && horizontalSpeed > 1.0E-4D && alongTravel < -0.25D * horizontalSpeed;
		if (!this.breadcrumbs.isEmpty() && (this.isReverser() || rollingBack)) {
			// The tender is behind the cab even if gravity makes the whole train
			// roll back with the reverser still set to forward. Preserve the
			// existing order instead of recording downhill samples as new "front".
			// Translate every tick, including movements smaller than 0.04 blocks.
			if (delta.lengthSqr() > 1.0E-10D) {
				List<Vec3> translated = new ArrayList<>(this.breadcrumbs.size());
				for (Vec3 point : this.breadcrumbs) {
					translated.add(point.add(delta));
				}
				this.breadcrumbs.clear();
				this.breadcrumbs.addAll(translated);
			}
			return;
		}

		if (!this.breadcrumbs.isEmpty()) {
			Vec3 lastSample = this.breadcrumbs.size() > 1
				? this.breadcrumbs.stream().skip(1).findFirst().orElse(this.breadcrumbs.peekFirst())
				: this.breadcrumbs.peekFirst();
			if (lastSample.distanceTo(pos) < 0.04D) {
				// Keep the head exact, but measure the sampling threshold from the
				// previous retained sample so slow trains still build a complete path.
				this.breadcrumbs.pollFirst();
				this.breadcrumbs.addFirst(pos);
				return;
			}
		}
		this.breadcrumbs.addFirst(pos);
		while (this.breadcrumbs.size() > MAX_BREADCRUMBS) {
			this.breadcrumbs.pollLast();
		}
	}

	/**
	 * Position the permanently attached tender at arc length HINGE_DISTANCE behind the locomotive
	 * by sampling the breadcrumb trail.
	 */
	void positionPermanentTender(final ServerLevel serverLevel) {
		CoalTenderEntity tender = this.findPermanentTender(serverLevel);
		if (tender == null) {
			return;
		}

		if (this.breadcrumbs.isEmpty()) {
			this.initBreadcrumbs(tender.position(), this.position());
		}

		if (this.breadcrumbs.size() < 2) {
			// Not enough data yet, use simple offset
			double yawRad = Math.toRadians(this.getFacingYaw());
			Vec3 facing = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
			Vec3 tenderPos = this.position().add(facing.scale(-HINGE_DISTANCE));
			tender.setPos(tenderPos.x, this.getY(), tenderPos.z);
			tender.setYRot(this.getFacingYaw());
			tender.setYHeadRot(this.getFacingYaw());
			tender.setYBodyRot(this.getFacingYaw());
			tender.yRotO = this.getFacingYaw();
			return;
		}

		// Sample the very same centreline as the wagons, at a fixed hinge
		// offset. No spring, interpolation target or independent tender physics.
		Vec3[] crumbs = this.breadcrumbs.toArray(new Vec3[0]);
		double hingeDistance = HINGE_DISTANCE;
		PathPoint hinge = this.pointAlongBreadcrumbs(crumbs, hingeDistance);
		if (hinge == null) {
			return;
		}
		Vec3 tenderPos = hinge.position();
		if (MountedTrackCompat.isMounted(tender)) {
			// Use the same follower velocity correction as the grey wagons. Never
			// teleport a passenger independently of its spline/interpolation basis.
			Vec3 trainMovement = MinecartTrainLogic.horizontalMovement(this);
			this.guideMountedWagon(tender, tenderPos, trainMovement, hinge.forwardTangent());
			this.positionTenderWagons(serverLevel, tender, crumbs, trainMovement, hingeDistance);
			return;
		}
		// The model is two blocks long: orient its body from its rear axle to
		// its front axle, not from a single short spline sample at the centre.
		PathPoint front = this.pointAlongBreadcrumbs(crumbs, hingeDistance - 1.0D);
		PathPoint rear = this.pointAlongBreadcrumbs(crumbs, hingeDistance + 1.0D);
		Vec3 axleDirection = front != null && rear != null
			? front.position().subtract(rear.position()) : Vec3.ZERO;
		Vec3 forwardTangent = axleDirection.lengthSqr() > 1.0E-8D
			? axleDirection.normalize() : hinge.forwardTangent();
		tender.setPos(tenderPos.x, tenderPos.y, tenderPos.z);

		// Set tender yaw and pitch from the two-block body on the 3D track.
		if (forwardTangent != null && forwardTangent.horizontalDistanceSqr() > 1.0E-6D) {
			float tenderYaw = (float) (Mth.atan2(-forwardTangent.x, forwardTangent.z) * Mth.RAD_TO_DEG);
			double horiz = forwardTangent.horizontalDistance();
			float tenderPitch = (float) (Mth.atan2(forwardTangent.y, horiz) * Mth.RAD_TO_DEG);
			tender.setYRot(tenderYaw);
			tender.yRotO = tenderYaw;
			tender.setYHeadRot(tenderYaw);
			tender.setYBodyRot(tenderYaw);
			tender.setHingePitch(tenderPitch);
		}

		// The tender is a kinematic hinge, not a second powered minecart.
		// Its position comes exclusively from the locomotive's path; giving it
		// locomotive velocity makes its vanilla tick move it a second time.
		Vec3 locoMovement = MinecartTrainLogic.horizontalMovement(this);
		tender.setDeltaMovement(Vec3.ZERO);
		this.positionTenderWagons(serverLevel, tender, crumbs, locoMovement, hingeDistance);
	}

	/**
	 * Place the complete chain behind the permanent tender on the locomotive's
	 * centreline. This prevents later wagons from cutting tight corners or being
	 * pushed off vanilla rails. Splinecart passengers remain mounted and follow
	 * their spline, with velocity corrections preserving spacing.
	 */
	private void positionTenderWagons(
		final ServerLevel serverLevel,
		final AbstractMinecart tender,
		final Vec3[] crumbs,
		final Vec3 trainMovement,
		final double startDistance
	) {
		AbstractMinecart previous = tender;
		UUID previousId = this.getUUID();
		Set<UUID> visited = new HashSet<>();
		visited.add(this.getUUID());
		visited.add(tender.getUUID());
		double pathDistance = startDistance;

		for (int wagonIndex = 0; wagonIndex < 31; wagonIndex++) {
			MinecartChainAccess previousData = (MinecartChainAccess) previous;
			UUID blockedId = previousId;
			UUID nextId = previousData.minecartChain$getFirstLink()
				.filter(id -> !id.equals(blockedId) && !visited.contains(id))
				.orElseGet(() -> previousData.minecartChain$getSecondLink()
					.filter(id -> !id.equals(blockedId) && !visited.contains(id)).orElse(null));
			if (nextId == null) {
				break;
			}
			Entity entity = serverLevel.getEntity(nextId);
			if (!(entity instanceof AbstractMinecart wagon) || wagon.isRemoved()) {
				break;
			}
			visited.add(nextId);
			// Keep arc-length spacing. Chord-based clearance grows without bound on
			// hairpins and jumps wagons across the bend or into another track section.
			pathDistance += bufferRest(previous, wagon);

			PathPoint wagonPoint = this.pointAlongBreadcrumbs(crumbs, pathDistance);
			if (wagonPoint == null) {
				break;
			}
			Vec3 position = wagonPoint.position();
			if (MountedTrackCompat.isMounted(wagon)) {
				// Do not teleport a Splinecart passenger away from its follower. Match
				// locomotive speed and correct progress toward the exact breadcrumb
				// target; using centre-to-centre distance fails on tight curves.
				this.guideMountedWagon(wagon, position, trainMovement, wagonPoint.forwardTangent());
			} else {
				wagon.setPos(position.x, position.y, position.z);
				Vec3 tangent = this.bodyTangent(crumbs, pathDistance, wagon, wagonPoint.forwardTangent());
				if (tangent.horizontalDistanceSqr() > 1.0E-6D) {
					float yaw = (float) (Mth.atan2(-tangent.x, tangent.z) * Mth.RAD_TO_DEG);
					float pitch = (float) (Mth.atan2(tangent.y, tangent.horizontalDistance()) * Mth.RAD_TO_DEG);
					wagon.setYRot(yaw);
					wagon.yRotO = yaw;
					wagon.setXRot(pitch);
					wagon.xRotO = pitch;
				}
				// Positioned by the consist path; vanilla must not move it a second
				// time using the locomotive's world vector (which cuts across bends).
				wagon.setDeltaMovement(Vec3.ZERO);
			}

			previousId = previous.getUUID();
			previous = wagon;
		}
		this.followerTrackFronts.keySet().retainAll(visited);
	}

	/** Half-length of the body, including buffer beams, so centres cannot overlap. */
	public static double bodyHalfLength(final AbstractMinecart cart) {
		if (cart instanceof SteamLocomotiveEntity) {
			return 1.50D;
		}
		if (cart instanceof CoalTenderEntity || cart instanceof LongMinecartEntity || cart instanceof LongChestMinecartEntity) {
			return 1.10D;
		}
		return 0.55D;
	}

	public static double bufferRest(final AbstractMinecart ahead, final AbstractMinecart behind) {
		return bodyHalfLength(ahead) + bodyHalfLength(behind) + BUFFER_GAP;
	}

	public static double bufferSolid(final AbstractMinecart ahead, final AbstractMinecart behind) {
		return bodyHalfLength(ahead) + bodyHalfLength(behind) + BUFFER_SOLID;
	}

	/** Orient a long body between its axle samples, not a single corner tangent. */
	private Vec3 bodyTangent(final Vec3[] path, final double distance,
		final AbstractMinecart wagon, final Vec3 fallback) {
		double axleOffset = bodyHalfLength(wagon) * 0.7D;
		PathPoint front = this.pointAlongBreadcrumbs(path, Math.max(0.0D, distance - axleOffset));
		PathPoint rear = this.pointAlongBreadcrumbs(path, distance + axleOffset);
		if (front == null || rear == null) {
			return fallback;
		}
		Vec3 axis = front.position().subtract(rear.position());
		return axis.lengthSqr() > 1.0E-8D ? axis.normalize() : fallback;
	}

	private void applyConsistBuffers(final ServerLevel serverLevel) {
		if (this.breadcrumbs.size() >= 2) {
			// Path-based spacing is authoritative. A radial buffer impulse cuts
			// across a hairpin and mistakes adjacent track legs for overlap.
			return;
		}
		List<AbstractMinecart> consist = new ArrayList<>();
		consist.add(this);
		if (this.hasPermanentTender()) {
			CoalTenderEntity tender = this.findPermanentTender(serverLevel);
			if (tender != null) {
				consist.add(tender);
				AbstractMinecart previous = tender;
				UUID previousId = this.getUUID();
				Set<UUID> visited = new HashSet<>();
				visited.add(this.getUUID());
				visited.add(tender.getUUID());
				for (int wagonIndex = 0; wagonIndex < 31; wagonIndex++) {
					MinecartChainAccess previousData = (MinecartChainAccess) previous;
					UUID blockedId = previousId;
					UUID nextId = previousData.minecartChain$getFirstLink()
						.filter(id -> !id.equals(blockedId) && !visited.contains(id))
						.orElseGet(() -> previousData.minecartChain$getSecondLink()
							.filter(id -> !id.equals(blockedId) && !visited.contains(id)).orElse(null));
					if (nextId == null) {
						break;
					}
					Entity entity = serverLevel.getEntity(nextId);
					if (!(entity instanceof AbstractMinecart wagon) || wagon.isRemoved()) {
						break;
					}
					visited.add(nextId);
					consist.add(wagon);
					previousId = previous.getUUID();
					previous = wagon;
				}
			}
		} else {
			consist = MinecartTrainLogic.orderedTrain(serverLevel, this);
		}
		for (int i = 0; i < consist.size() - 1; i++) {
			this.applyBuffer(consist.get(i), consist.get(i + 1));
		}
	}

	/**
	 * Buffers between the locomotive, tender and wagons. Compression slows the
	 * vehicle behind to the one in front and, when reversing, pushes it back
	 * with the consist. The locomotive is only slowed along its travel axis.
	 */
	private void applyBuffer(final AbstractMinecart ahead, final AbstractMinecart behind) {
		Vec3 delta = ahead.position().subtract(behind.position()).horizontal();
		double distance = delta.length();
		double solid = bufferSolid(ahead, behind);
		double rest = bufferRest(ahead, behind);
		if (distance >= rest) {
			return;
		}
		Vec3 towardAhead = distance > 1.0E-4D ? delta.scale(1.0D / distance) : this.getVisualForward();
		if (distance < solid && !MountedTrackCompat.isMounted(behind)) {
			Vec3 shift = towardAhead.scale(-(solid - distance));
			behind.setPos(behind.getX() + shift.x, behind.getY(), behind.getZ() + shift.z);
			distance = solid;
		}

		// Buffer coordinates point toward the physical front, even in reverse.
		Vec3 aheadAxis = this.trackAxis(ahead, towardAhead);
		Vec3 behindAxis = this.trackAxis(behind, towardAhead);
		double aheadSigned = MinecartTrainLogic.horizontalMovement(ahead).dot(aheadAxis);
		double behindSigned = MinecartTrainLogic.horizontalMovement(behind).dot(behindAxis);
		double closing = behindSigned - aheadSigned;
		double compression = Math.max(0.0D, rest - distance);
		if (compression <= 0.0D && closing <= 0.0D) {
			return;
		}
		double impulse = Mth.clamp(compression * BUFFER_STIFFNESS + Math.max(0.0D, closing) * BUFFER_DAMPING, 0.0D, 0.28D);
		double separation = distance < solid ? 0.08D + (solid - distance) : compression * 0.2D;
		double target = aheadSigned - separation;
		if (aheadSigned >= 0.0D) {
			// Braking: absorb the wagon's inertia. Only a small rebound, never a launch backward.
			target = Math.max(target, -0.04D);
		} else {
			// Reversing into the buffer: the vehicle behind must move back with the one in front.
			target = Math.min(aheadSigned, behindSigned) - separation;
		}
		if (closing > 0.0D || distance < solid) {
			MinecartTrainLogic.setHorizontalMovement(behind, behindAxis.scale(target));
		}
		if (ahead == this && aheadSigned < 0.0D && closing > 0.0D) {
			double kept = Math.min(0.0D, aheadSigned + impulse);
			MinecartTrainLogic.setHorizontalMovement(this, aheadAxis.scale(kept));
		}
	}

	private Vec3 trackAxis(final AbstractMinecart cart, final Vec3 travel) {
		Vec3 track = MountedTrackCompat.trackDirection(cart);
		if (track.lengthSqr() <= 1.0E-8D) {
			return travel.lengthSqr() > 1.0E-8D ? travel.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
		}
		track = track.normalize();
		return track.dot(travel) >= 0.0D ? track : track.reverse();
	}

	private void guideMountedWagon(
		final AbstractMinecart wagon, final Vec3 target, final Vec3 trainMovement, final Vec3 forwardTangent
	) {
		Vec3 targetError = target.subtract(wagon.position());
		Vec3 localTrack = MountedTrackCompat.trackDirection(wagon);
		Vec3 corrected = trainMovement;
		if (localTrack.lengthSqr() > 1.0E-8D && forwardTangent != null
			&& forwardTangent.horizontalDistanceSqr() > 1.0E-8D) {
			// On a hairpin the target tangent can oppose the wagon's current one.
			// Transport its previous physical front along the local track instead
			// of flipping it to face a distant breadcrumb on the other leg.
			Vec3 forward = this.followerTrackFronts.getOrDefault(wagon.getUUID(),
				forwardTangent.horizontal().normalize());
			Vec3 localForward = localTrack.dot(forward) >= 0.0D ? localTrack : localTrack.reverse();
			this.followerTrackFronts.put(wagon.getUUID(), localForward);
			// Match the signed velocity of the locomotive, including rollback;
			// do not project its global vector onto curved wagon tracks.
			// localForward is the physical front, not the selected travel direction.
			// Using establishedTravel made reverse speed positive and sent the tender
			// forward through the locomotive. Preserve signed speed in the cab frame.
			Vec3 locomotiveFront = this.trackAxis(this, this.getVisualForward());
			double signedSpeed = Math.copySign(trainMovement.length(), trainMovement.dot(locomotiveFront));
			double along = targetError.dot(localForward);
			// The permanent tender shares locomotive speed immediately, including
			// the first acceleration tick. Buffers handle compression separately.
			boolean permanentTender = wagon instanceof CoalTenderEntity tender
				&& tender.getPermanentLocomotiveId().filter(this.getUUID()::equals).isPresent();
			if (!permanentTender && Math.abs(along) > TENDER_WAGON_TOLERANCE) {
				double adjusted = signedSpeed + Mth.clamp(along * 0.12D, -0.08D, 0.08D);
				// Spacing correction works in both gears without reversing a follower
				// against the locomotive or starting a stopped consist.
				signedSpeed = signedSpeed > 0.0D ? Math.max(0.0D, adjusted)
					: signedSpeed < 0.0D ? Math.min(0.0D, adjusted) : 0.0D;
			}
			corrected = localForward.scale(signedSpeed);
		}
		MinecartTrainLogic.setHorizontalMovement(wagon, corrected);
	}

	private PathPoint pointAlongBreadcrumbs(final Vec3[] crumbs, final double requestedDistance) {
		if (crumbs.length < 2) {
			return null;
		}
		double remaining = requestedDistance;
		for (int i = 0; i < crumbs.length - 1; i++) {
			Vec3 from = crumbs[i];
			Vec3 to = crumbs[i + 1];
			double length = from.distanceTo(to);
			if (length < 1.0E-6D) {
				continue;
			}
			if (remaining <= length) {
				return new PathPoint(from.lerp(to, remaining / length), from.subtract(to).normalize());
			}
			remaining -= length;
		}
		Vec3 last = crumbs[crumbs.length - 1];
		Vec3 beforeLast = crumbs[crumbs.length - 2];
		Vec3 backwards = last.subtract(beforeLast).normalize();
		return new PathPoint(last.add(backwards.scale(remaining)), backwards.reverse());
	}

	private record PathPoint(Vec3 position, Vec3 forwardTangent) {
	}

	/**
	 * Travel axis of the cab. Wagon resistance is applied only along this vector.
	 */
	private Vec3 establishedTravel() {
		if (this.lastTravelDir.lengthSqr() > 1.0E-8D) {
			return this.lastTravelDir.normalize();
		}
		Vec3 facing = this.getVisualForward();
		return this.isReverser() ? facing.reverse() : facing;
	}

	/**
	 * Rail tangent aligned to the cab. Not the velocity, which wagon resistance
	 * can deflect on a curve.
	 */
	private Vec3 trackTravel(final ServerLevel serverLevel) {
		Vec3 trackLine = this.getTrackLine(serverLevel);
		Vec3 facing = this.getVisualForward();
		if (trackLine.lengthSqr() <= 1.0E-8D) {
			return this.establishedTravel();
		}
		if (trackLine.dot(facing) < 0.0D) {
			trackLine = trackLine.reverse();
		}
		Vec3 travel = this.isReverser() ? trackLine.reverse() : trackLine;
		this.lastTravelDir = travel.normalize();
		return this.lastTravelDir;
	}

	private void followRailHeading(final ServerLevel serverLevel) {
		if (MountedTrackCompat.isMounted(this) || MinecartTrainLogic.isTrainStopped(this)) {
			return;
		}
		Vec3 trackLine = this.getTrackLine(serverLevel);
		if (trackLine.lengthSqr() <= 1.0E-4D) {
			return;
		}
		if (trackLine.dot(this.getVisualForward()) < 0.0D) {
			trackLine = trackLine.reverse();
		}
		float targetYaw = (float) (Mth.atan2(-trackLine.x, trackLine.z) * Mth.RAD_TO_DEG);
		if (Mth.degreesDifferenceAbs(this.getFacingYaw(), targetYaw) > 110.0F) {
			return;
		}
		float newYaw = Mth.approachDegrees(this.getFacingYaw(), targetYaw, 25.0F);
		this.setFacingYaw(newYaw);
		this.applyFacingYaw(newYaw);
		MinecartChainAccess access = (MinecartChainAccess) this;
		access.minecartChain$setLocomotiveYaw(newYaw);
		access.minecartChain$setHasLocomotiveYaw(true);
	}

	/**
	 * Wagons on a curve lag and pull. That may slow the locomotive, but the
	 * resulting velocity stays on the established travel axis and cannot cross
	 * zero into the opposite direction.
	 */
	private void absorbWagonCurveResistance(final Vec3 travel, final double signedBefore) {
		if (!this.hasPermanentTender() || travel.lengthSqr() <= 1.0E-8D) {
			return;
		}
		Vec3 unit = travel.normalize();
		double signedNow = MinecartTrainLogic.horizontalMovement(this).dot(unit);
		double kept = signedBefore >= 0.0D
			? Math.max(0.0D, Math.min(signedBefore, signedNow))
			: Math.min(0.0D, Math.max(signedBefore, signedNow));
		double drag = this.wagonCurveDrag(unit);
		kept = kept >= 0.0D ? Math.max(0.0D, kept - drag) : Math.min(0.0D, kept + drag);
		MinecartTrainLogic.setHorizontalMovement(this, unit.scale(kept));
	}

	private double wagonCurveDrag(final Vec3 travel) {
		if (this.breadcrumbs.size() < 3) {
			return 0.0D;
		}
		Vec3[] crumbs = this.breadcrumbs.toArray(new Vec3[0]);
		double drag = 0.0D;
		double distance = HINGE_DISTANCE;
		int followers = this.level() instanceof ServerLevel level
			? Math.max(0, MinecartTrainLogic.orderedTrain(level, this).size() - 1) : 0;
		for (int wagon = 0; wagon < Math.min(6, followers); wagon++) {
			PathPoint point = this.pointAlongBreadcrumbs(crumbs, distance);
			if (point == null || point.forwardTangent() == null) {
				break;
			}
			Vec3 tangent = point.forwardTangent().horizontal();
			if (tangent.lengthSqr() > 1.0E-8D) {
				drag += (1.0D - Math.abs(tangent.normalize().dot(travel))) * CURVE_RESISTANCE_STEP;
			}
			distance += TENDER_WAGON_DISTANCE;
		}
		return Math.min(drag, MAX_CURVE_RESISTANCE);
	}

	/** Locomotive mass is two units; each additional wagon, passenger and load adds inertia. */
	private double trainMass(final ServerLevel level) {
		double mass = 0.0D;
		Set<UUID> counted = new HashSet<>();
		for (AbstractMinecart cart : MinecartTrainLogic.orderedTrain(level, this)) {
			if (counted.add(cart.getUUID())) {
				mass += cartMass(cart);
			}
		}
		if (this.hasPermanentTender()) {
			CoalTenderEntity tender = this.findPermanentTender(level);
			if (tender != null && counted.add(tender.getUUID())) {
				mass += cartMass(tender);
			}
		}
		return Math.max(2.0D, mass);
	}

	private static double cartMass(final AbstractMinecart cart) {
		double mass = cart instanceof SteamLocomotiveEntity || cart instanceof LongMinecartEntity || cart instanceof LongChestMinecartEntity ? 2.0D : 1.0D;
		mass += cart.getPassengers().size() * 0.5D;
		if (cart instanceof AbstractMinecartContainer container) {
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);
				if (!stack.isEmpty()) {
					mass += (double) stack.getCount() / stack.getMaxStackSize() / container.getContainerSize();
				}
			}
		}
		return mass;
	}

	private double tractionAcceleration(final float throttle) {
		// Restore the original tractive acceleration. Dividing it by consist mass
		// made even a tender/passenger consume the margin over uphill gravity.
		// Mass still affects braking; speed limits and reverser remain unchanged.
		return ACCELERATION * throttle;
	}

	private void applyBrake(final ServerLevel serverLevel, final float brakeIntensity) {
		// A fixed braking force acts on the whole train; it takes longer to stop a heavy consist.
		double deceleration = BRAKE_FORCE * brakeIntensity * 2.0D / this.trainMass(serverLevel);
		Vec3 travel = this.establishedTravel();
		double signed = MinecartTrainLogic.horizontalMovement(this).dot(travel);
		if (Math.abs(signed) <= Math.max(STOP_SPEED, deceleration)) {
			MinecartTrainLogic.applyTrainBrake(this);
		} else {
			double kept = signed > 0.0D
				? Math.max(0.0D, signed - deceleration)
				: Math.min(0.0D, signed + deceleration);
			MinecartTrainLogic.setHorizontalMovement(this, travel.scale(kept));
			for (AbstractMinecart cart : MinecartTrainLogic.orderedTrain(serverLevel, this)) {
				if (cart == this) {
					continue;
				}
				Vec3 velocity = MinecartTrainLogic.horizontalMovement(cart);
				double speed = velocity.length();
				if (speed > 0.0D) {
					MinecartTrainLogic.setHorizontalMovement(cart, velocity.scale(Math.max(0.0D, 1.0D - deceleration / speed)));
				}
			}
		}
		if (this.hasPermanentTender()) {
			this.positionPermanentTender(serverLevel);
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
