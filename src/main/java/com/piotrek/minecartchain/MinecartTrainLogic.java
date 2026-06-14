package com.piotrek.minecartchain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

public final class MinecartTrainLogic {
	private static final double LINK_DISTANCE = 2.0D;
	private static final double MAX_LINK_DISTANCE = 2.2D;
	private static final double MIN_LINK_DISTANCE = 1.55D;
	private static final double LINK_BREAK_DISTANCE = 12.0D;
	private static final double LINK_STIFFNESS = 0.07D;
	private static final double MIN_DISTANCE_PUSH = 0.1D;
	private static final double MAX_LINK_IMPULSE = 0.1D;
	private static final double ENGINE_PUSH = 0.06D;
	private static final double ENGINE_ASSIST = 0.03D;
	private static final double SLOW_THROTTLE_FACTOR = 1.0D / 3.0D;
	private static final double MAX_ENGINE_SPEED = 0.58D;
	private static final double SLOW_ENGINE_SPEED = 0.32D;
	private static final double FOLLOWER_VELOCITY_BLEND = 0.68D;
	private static final double FOLLOWER_PULL = 0.07D;
	private static final double FOLLOWER_OVERTAKE_BRAKE = 0.64D;
	private static final double MAX_FOLLOWER_SPEED = 0.55D;
	private static final double FOLLOWER_SPACING_DEADZONE = 0.08D;
	private static final double FOLLOWER_SPACING_RECOVERY_SPEED = 0.08D;
	private static final double FOLLOWER_LEADER_SPEED_MARGIN = 0.025D;
	private static final double BRAKE_DAMPING = 0.28D;
	private static final double POWERED_ENGINE_LINK_WEIGHT = 0.25D;
	private static final double SMOKE_SPEED_THRESHOLD = 0.02D;
	private static final double STOPPED_SPEED_THRESHOLD = 0.025D;
	private static final int SMOKE_INTERVAL_TICKS = 4;
	private static final int FULL_THROTTLE_SMOKE_INTERVAL_TICKS = 2;
	private static final float MAX_LOCOMOTIVE_YAW_STEP = 18.0F;
	private static final float MAX_LOCOMOTIVE_YAW_SNAP = 150.0F;
	private static final float MIN_LOCOMOTIVE_YAW_UPDATE = 0.5F;
	private static final int MAX_TRAIN_LENGTH = 32;

	private MinecartTrainLogic() {
	}

	public static void tickLinks(final AbstractMinecart minecart) {
		Level level = minecart.level();
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
			return;
		}

		MinecartChainAccess data = (MinecartChainAccess) minecart;
		for (AbstractMinecart linked : linkedMinecarts(serverLevel, data)) {
			if (minecart.getUUID().compareTo(linked.getUUID()) < 0) {
				applyLinkConstraint(minecart, linked);
			}
		}
		guidePoweredTrainAfterLinkedCartTick(serverLevel, minecart);
	}

	public static Vec3 engineDirection(final MinecartFurnace furnace) {
		Level level = furnace.level();
		if (level instanceof ServerLevel serverLevel) {
			Optional<AbstractMinecart> closestLinked = linkedMinecarts(serverLevel, (MinecartChainAccess) furnace).stream()
				.min(Comparator.comparingDouble(furnace::distanceToSqr));
			if (closestLinked.isPresent()) {
				Vec3 awayFromLinkedCart = furnace.position().subtract(closestLinked.get().position()).horizontal();
				if (awayFromLinkedCart.lengthSqr() > 1.0E-5D) {
					return awayFromLinkedCart.normalize();
				}
			}
		}

		Vec3 movement = furnace.getDeltaMovement().horizontal();
		if (movement.lengthSqr() > 1.0E-4D) {
			return movement.normalize();
		}

		Direction direction = furnace.getMotionDirection();
		Vec3 fallback = new Vec3(direction.getStepX(), 0.0D, direction.getStepZ());
		return fallback.lengthSqr() > 0.0D ? fallback.normalize() : Vec3.ZERO;
	}

	public static Vec3 drivingDirection(final MinecartFurnace furnace) {
		Vec3 direction = engineDirection(furnace);
		return ((MinecartChainAccess) furnace).minecartChain$isReversed() ? direction.reverse() : direction;
	}

	public static boolean isTrainStopped(final MinecartFurnace furnace) {
		if (!(furnace.level() instanceof ServerLevel serverLevel)) {
			return isStopped(furnace);
		}

		for (AbstractMinecart minecart : connectedTrain(serverLevel, furnace)) {
			if (!isStopped(minecart)) {
				return false;
			}
		}
		return true;
	}

	public static int connectedTrainSize(final MinecartFurnace furnace) {
		if (!(furnace.level() instanceof ServerLevel serverLevel)) {
			return 1;
		}

		return connectedTrain(serverLevel, furnace).size();
	}

	public static Optional<MinecartFurnace> controlledLocomotive(final ServerLevel level, final AbstractMinecart minecart) {
		return connectedTrain(level, minecart).stream()
			.filter(MinecartFurnace.class::isInstance)
			.map(MinecartFurnace.class::cast)
			.filter(furnace -> ((MinecartChainAccess) furnace).minecartChain$hasEngineLever())
			.min(Comparator.comparingDouble(minecart::distanceToSqr));
	}

	public static int locomotiveFuelCost(final MinecartFurnace furnace) {
		return Math.max(1, connectedTrainSize(furnace));
	}

	public static int locomotiveWaterCost(final MinecartFurnace furnace) {
		return 1 + Math.max(0, connectedTrainSize(furnace) - 1) / 2;
	}

	public static void updateLocomotiveYaw(final MinecartFurnace furnace, final Vec3 direction) {
		if (direction.horizontalDistanceSqr() <= 1.0E-5D) {
			return;
		}

		MinecartChainAccess data = (MinecartChainAccess) furnace;
		float targetYaw = yawFromDirection(direction);
		if (!data.minecartChain$hasLocomotiveYaw()) {
			data.minecartChain$setLocomotiveYaw(targetYaw);
			data.minecartChain$setHasLocomotiveYaw(true);
			return;
		}

		float currentYaw = data.minecartChain$getLocomotiveYaw();
		float difference = Mth.degreesDifference(currentYaw, targetYaw);
		if (Math.abs(difference) > MAX_LOCOMOTIVE_YAW_SNAP) {
			return;
		}

		float nextYaw = Mth.approachDegrees(currentYaw, targetYaw, MAX_LOCOMOTIVE_YAW_STEP);
		if (Mth.degreesDifferenceAbs(currentYaw, nextYaw) >= MIN_LOCOMOTIVE_YAW_UPDATE) {
			data.minecartChain$setLocomotiveYaw(nextYaw);
		}
	}

	public static void snapLocomotiveYaw(final MinecartFurnace furnace, final Vec3 direction) {
		if (direction.horizontalDistanceSqr() <= 1.0E-5D) {
			return;
		}

		MinecartChainAccess data = (MinecartChainAccess) furnace;
		data.minecartChain$setLocomotiveYaw(yawFromDirection(direction));
		data.minecartChain$setHasLocomotiveYaw(true);
	}

	public static void applyEngineAssist(final MinecartFurnace furnace, final Vec3 direction, final boolean fullThrottle) {
		if (direction.lengthSqr() <= 1.0E-5D) {
			return;
		}

		Vec3 horizontal = furnace.getDeltaMovement().horizontal().add(direction.scale(ENGINE_ASSIST * throttleFactor(fullThrottle)));
		double speed = horizontal.length();
		double maxSpeed = fullThrottle ? MAX_ENGINE_SPEED : SLOW_ENGINE_SPEED;
		if (speed > maxSpeed) {
			horizontal = horizontal.normalize().scale(maxSpeed);
		}

		Vec3 current = furnace.getDeltaMovement();
		furnace.setDeltaMovement(horizontal.x, current.y, horizontal.z);
	}

	public static void guidePoweredTrain(final MinecartFurnace furnace, final Vec3 engineDirection) {
		if (!(furnace.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		List<AbstractMinecart> train = orderedTrain(serverLevel, furnace);
		if (train.size() < 2) {
			return;
		}

		Vec3 fallbackDirection = engineDirection.lengthSqr() > 1.0E-5D ? engineDirection.normalize() : Vec3.ZERO;
		for (int i = 1; i < train.size(); i++) {
			guideFollower(train.get(i - 1), train.get(i), fallbackDirection);
		}
	}

	private static void guidePoweredTrainAfterLinkedCartTick(final ServerLevel level, final AbstractMinecart minecart) {
		if (minecart instanceof MinecartFurnace) {
			return;
		}

		for (AbstractMinecart trainCart : connectedTrain(level, minecart)) {
			if (trainCart instanceof MinecartFurnace furnace && isPoweredLocomotive(furnace)) {
				guidePoweredTrain(furnace, drivingDirection(furnace));
				return;
			}
		}
	}

	public static void applyTrainBrake(final MinecartFurnace furnace) {
		if (!(furnace.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		for (AbstractMinecart minecart : orderedTrain(serverLevel, furnace)) {
			Vec3 current = minecart.getDeltaMovement();
			minecart.setDeltaMovement(current.x * BRAKE_DAMPING, current.y, current.z * BRAKE_DAMPING);
		}
	}

	public static Vec3 enginePushVector(final Vec3 direction, final boolean fullThrottle) {
		if (direction.lengthSqr() <= 1.0E-5D) {
			return Vec3.ZERO;
		}

		Vec3 normalized = direction.normalize();
		double push = ENGINE_PUSH * throttleFactor(fullThrottle);
		return new Vec3(normalized.x * push, 0.0D, normalized.z * push);
	}

	public static void emitLocomotiveSmoke(final MinecartFurnace furnace) {
		if (!(furnace.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		boolean fullThrottle = ((MinecartChainAccess) furnace).minecartChain$isFullThrottle();
		int smokeInterval = fullThrottle ? FULL_THROTTLE_SMOKE_INTERVAL_TICKS : SMOKE_INTERVAL_TICKS;
		if (furnace.tickCount % smokeInterval != 0) {
			return;
		}
		if (furnace.getDeltaMovement().horizontalDistanceSqr() < SMOKE_SPEED_THRESHOLD * SMOKE_SPEED_THRESHOLD) {
			return;
		}

		Vec3 forward = locomotiveForward(furnace);
		if (forward.lengthSqr() <= 1.0E-5D) {
			return;
		}

		Vec3 smokePos = furnace.position()
			.add(forward.normalize().scale(MinecartControlLayout.CHIMNEY_SMOKE_FORWARD_OFFSET))
			.add(0.0D, MinecartControlLayout.CHIMNEY_SMOKE_Y, 0.0D);
		int darkSmokeCount = fullThrottle ? 5 : 2;
		int whiteSmokeCount = fullThrottle ? 4 : 1;
		double smokeSpeed = fullThrottle ? 0.025D : 0.015D;
		serverLevel.sendParticles(
			ParticleTypes.LARGE_SMOKE,
			smokePos.x,
			smokePos.y,
			smokePos.z,
			darkSmokeCount,
			0.04D,
			0.08D,
			0.04D,
			smokeSpeed
		);
		serverLevel.sendParticles(
			ParticleTypes.CLOUD,
			smokePos.x,
			smokePos.y + 0.04D,
			smokePos.z,
			whiteSmokeCount,
			0.05D,
			0.1D,
			0.05D,
			smokeSpeed
		);
	}

	public static float yawFromDirection(final Vec3 direction) {
		return Mth.wrapDegrees((float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG));
	}

	public static Vec3 directionFromYaw(final float yaw) {
		double radians = Math.toRadians(yaw);
		return new Vec3(Math.cos(radians), 0.0D, Math.sin(radians));
	}

	private static Vec3 locomotiveForward(final MinecartFurnace furnace) {
		MinecartChainAccess data = (MinecartChainAccess) furnace;
		if (data.minecartChain$hasLocomotiveYaw()) {
			return directionFromYaw(data.minecartChain$getLocomotiveYaw());
		}
		return engineDirection(furnace);
	}

	private static boolean isStopped(final AbstractMinecart minecart) {
		return minecart.getDeltaMovement().horizontalDistanceSqr() <= STOPPED_SPEED_THRESHOLD * STOPPED_SPEED_THRESHOLD;
	}

	private static List<AbstractMinecart> linkedMinecarts(final ServerLevel level, final MinecartChainAccess data) {
		List<AbstractMinecart> minecarts = new ArrayList<>(2);
		addLinkedMinecart(level, data.minecartChain$getFirstLink(), minecarts);
		addLinkedMinecart(level, data.minecartChain$getSecondLink(), minecarts);
		return minecarts;
	}

	private static void addLinkedMinecart(final ServerLevel level, final Optional<UUID> linkId, final List<AbstractMinecart> minecarts) {
		if (linkId.isEmpty()) {
			return;
		}

		Entity entity = level.getEntity(linkId.get());
		if (entity instanceof AbstractMinecart minecart) {
			minecarts.add(minecart);
		}
	}

	private static List<AbstractMinecart> orderedTrain(final ServerLevel level, final AbstractMinecart engine) {
		List<AbstractMinecart> train = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		AbstractMinecart previous = null;
		AbstractMinecart current = engine;

		while (current != null && seen.add(current.getUUID()) && train.size() < MAX_TRAIN_LENGTH) {
			train.add(current);
			AbstractMinecart next = nextLinkedMinecart(level, current, previous, seen);
			previous = current;
			current = next;
		}

		return train;
	}

	private static List<AbstractMinecart> connectedTrain(final ServerLevel level, final AbstractMinecart start) {
		List<AbstractMinecart> train = new ArrayList<>();
		List<AbstractMinecart> pending = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		pending.add(start);
		seen.add(start.getUUID());

		for (int index = 0; index < pending.size() && train.size() < MAX_TRAIN_LENGTH; index++) {
			AbstractMinecart current = pending.get(index);
			train.add(current);
			for (AbstractMinecart linked : linkedMinecarts(level, (MinecartChainAccess) current)) {
				if (seen.add(linked.getUUID())) {
					pending.add(linked);
				}
			}
		}

		return train;
	}

	private static AbstractMinecart nextLinkedMinecart(
		final ServerLevel level, final AbstractMinecart current, final AbstractMinecart previous, final Set<UUID> seen
	) {
		return linkedMinecarts(level, (MinecartChainAccess) current).stream()
			.filter(linked -> previous == null || !linked.getUUID().equals(previous.getUUID()))
			.filter(linked -> !seen.contains(linked.getUUID()))
			.min(Comparator.comparingDouble(current::distanceToSqr))
			.orElse(null);
	}

	private static void guideFollower(final AbstractMinecart leader, final AbstractMinecart follower, final Vec3 fallbackDirection) {
		Vec3 toLeader = leader.position().subtract(follower.position()).horizontal();
		double distance = toLeader.length();
		if (distance <= 1.0E-4D) {
			return;
		}

		Vec3 leaderVelocity = leader.getDeltaMovement().horizontal();
		Vec3 localForward = leaderVelocity.lengthSqr() > 1.0E-5D ? leaderVelocity.normalize() : fallbackDirection;
		if (distance < MIN_LINK_DISTANCE) {
			Vec3 followerVelocity = follower.getDeltaMovement();
			Vec3 railFollowerVelocity = constrainToRail(follower, followerVelocity.horizontal().scale(FOLLOWER_OVERTAKE_BRAKE));
			double push = Mth.clamp((MIN_LINK_DISTANCE - distance) * MIN_DISTANCE_PUSH, 0.04D, MAX_LINK_IMPULSE);
			Vec3 pushAway = railPullVector(follower, toLeader.reverse(), push);
			Vec3 corrected = keepFollowerFromClosingGap(leader, follower, railFollowerVelocity.add(pushAway), toLeader, distance);
			corrected = capHorizontalSpeed(corrected, MAX_FOLLOWER_SPEED);
			follower.setDeltaMovement(corrected.x, followerVelocity.y, corrected.z);
			return;
		}

		if (localForward.lengthSqr() > 1.0E-5D && toLeader.dot(localForward) < -0.05D) {
			Vec3 slowed = constrainToRail(follower, follower.getDeltaMovement().horizontal().scale(FOLLOWER_OVERTAKE_BRAKE));
			Vec3 pullBack = railPullVector(follower, toLeader, MAX_LINK_IMPULSE);
			Vec3 corrected = keepFollowerFromClosingGap(leader, follower, slowed.add(pullBack), toLeader, distance);
			corrected = capHorizontalSpeed(corrected, MAX_FOLLOWER_SPEED);
			follower.setDeltaMovement(corrected.x, follower.getDeltaMovement().y, corrected.z);
			return;
		}

		Vec3 followerVelocity = follower.getDeltaMovement();
		Vec3 railFollowerVelocity = constrainToRail(follower, followerVelocity.horizontal());
		Vec3 railLeaderVelocity = constrainToRail(follower, leaderVelocity);
		Vec3 blendedVelocity = railFollowerVelocity.scale(1.0D - FOLLOWER_VELOCITY_BLEND)
			.add(railLeaderVelocity.scale(FOLLOWER_VELOCITY_BLEND));

		if (distance > LINK_DISTANCE) {
			double pull = Mth.clamp((distance - LINK_DISTANCE) * FOLLOWER_PULL, 0.0D, MAX_LINK_IMPULSE);
			blendedVelocity = blendedVelocity.add(railPullVector(follower, toLeader, pull));
		}

		blendedVelocity = constrainToRail(follower, blendedVelocity);
		blendedVelocity = keepFollowerFromClosingGap(leader, follower, blendedVelocity, toLeader, distance);
		blendedVelocity = capHorizontalSpeed(blendedVelocity, MAX_FOLLOWER_SPEED);
		follower.setDeltaMovement(blendedVelocity.x, followerVelocity.y, blendedVelocity.z);
	}

	private static Vec3 keepFollowerFromClosingGap(
		final AbstractMinecart leader,
		final AbstractMinecart follower,
		final Vec3 candidateVelocity,
		final Vec3 toLeader,
		final double distance
	) {
		if (distance > LINK_DISTANCE + FOLLOWER_SPACING_DEADZONE || toLeader.lengthSqr() <= 1.0E-8D) {
			return candidateVelocity;
		}

		Vec3 gapDirection = toLeader.scale(1.0D / distance);
		Vec3 leaderVelocity = constrainToRail(follower, leader.getDeltaMovement().horizontal());
		double leaderTowardGap = leaderVelocity.dot(gapDirection);
		double followerTowardLeader = candidateVelocity.horizontal().dot(gapDirection);
		double maxFollowerTowardLeader = leaderTowardGap - FOLLOWER_LEADER_SPEED_MARGIN;
		if (distance < LINK_DISTANCE) {
			maxFollowerTowardLeader -= Mth.clamp(
				(LINK_DISTANCE - distance) * MIN_DISTANCE_PUSH,
				0.0D,
				FOLLOWER_SPACING_RECOVERY_SPEED
			);
		}

		if (followerTowardLeader <= maxFollowerTowardLeader) {
			return candidateVelocity;
		}

		Vec3 excessClosing = gapDirection.scale(followerTowardLeader - maxFollowerTowardLeader);
		return constrainToRail(follower, candidateVelocity.subtract(excessClosing));
	}

	private static void applyLinkConstraint(final AbstractMinecart first, final AbstractMinecart second) {
		Vec3 delta = second.position().subtract(first.position()).horizontal();
		double distance = delta.length();
		if (distance <= 1.0E-4D) {
			return;
		}

		if (distance > LINK_BREAK_DISTANCE) {
			((MinecartChainAccess) first).minecartChain$removeLink(second.getUUID());
			((MinecartChainAccess) second).minecartChain$removeLink(first.getUUID());
			return;
		}

		Vec3 direction = delta.scale(1.0D / distance);
		if (distance < MIN_LINK_DISTANCE) {
			pushMinecartsApart(first, second, direction, distance);
			return;
		}

		if (distance > MAX_LINK_DISTANCE) {
			clampLinkDistance(first, second, direction, distance);
			distance = MAX_LINK_DISTANCE;
		}

		double error = distance - LINK_DISTANCE;
		if (Math.abs(error) < 0.04D) {
			return;
		}

		double impulse = Mth.clamp(error * LINK_STIFFNESS, -MAX_LINK_IMPULSE, MAX_LINK_IMPULSE);
		Vec3 adjustment = direction.scale(impulse);
		Vec3 firstAdjustment = constrainToRail(first, adjustment).scale(linkWeight(first));
		Vec3 secondAdjustment = constrainToRail(second, adjustment.reverse()).scale(linkWeight(second));
		first.setDeltaMovement(first.getDeltaMovement().add(firstAdjustment));
		second.setDeltaMovement(second.getDeltaMovement().add(secondAdjustment));
	}

	private static void pushMinecartsApart(final AbstractMinecart first, final AbstractMinecart second, final Vec3 direction, final double distance) {
		double deficit = MIN_LINK_DISTANCE - distance;
		Vec3 correction = direction.scale(deficit * 0.5D);
		Vec3 firstCorrection = constrainToRail(first, correction.reverse());
		Vec3 secondCorrection = constrainToRail(second, correction);
		first.setPos(first.getX() + firstCorrection.x, first.getY(), first.getZ() + firstCorrection.z);
		second.setPos(second.getX() + secondCorrection.x, second.getY(), second.getZ() + secondCorrection.z);

		double impulse = Mth.clamp(deficit * MIN_DISTANCE_PUSH, 0.04D, MAX_LINK_IMPULSE);
		Vec3 push = direction.scale(impulse);
		first.setDeltaMovement(first.getDeltaMovement().add(constrainToRail(first, push.reverse())));
		second.setDeltaMovement(second.getDeltaMovement().add(constrainToRail(second, push)));
	}

	private static void clampLinkDistance(final AbstractMinecart first, final AbstractMinecart second, final Vec3 direction, final double distance) {
		double excess = distance - MAX_LINK_DISTANCE;
		Vec3 correction = direction.scale(excess * 0.5D);
		Vec3 firstCorrection = constrainToRail(first, correction);
		Vec3 secondCorrection = constrainToRail(second, correction.reverse());
		first.setPos(first.getX() + firstCorrection.x, first.getY(), first.getZ() + firstCorrection.z);
		second.setPos(second.getX() + secondCorrection.x, second.getY(), second.getZ() + secondCorrection.z);

		Vec3 damping = direction.scale(MAX_LINK_IMPULSE);
		first.setDeltaMovement(first.getDeltaMovement().add(constrainToRail(first, damping).scale(linkWeight(first))));
		second.setDeltaMovement(second.getDeltaMovement().add(constrainToRail(second, damping.reverse()).scale(linkWeight(second))));
	}

	private static Vec3 railPullVector(final AbstractMinecart minecart, final Vec3 desiredDirection, final double magnitude) {
		if (magnitude <= 0.0D || desiredDirection.horizontalDistanceSqr() <= 1.0E-8D) {
			return Vec3.ZERO;
		}

		Optional<Direction.Axis> railAxis = railAxis(minecart, desiredDirection);
		if (railAxis.isEmpty()) {
			return desiredDirection.horizontal().normalize().scale(magnitude);
		}

		Direction.Axis axis = railAxis.get();
		double signed = axis == Direction.Axis.X ? desiredDirection.x : desiredDirection.z;
		if (Math.abs(signed) <= 1.0E-8D) {
			return Vec3.ZERO;
		}

		double value = Math.signum(signed) * magnitude;
		return axis == Direction.Axis.X ? new Vec3(value, 0.0D, 0.0D) : new Vec3(0.0D, 0.0D, value);
	}

	private static Vec3 constrainToRail(final AbstractMinecart minecart, final Vec3 vector) {
		Vec3 horizontal = vector.horizontal();
		if (horizontal.lengthSqr() <= 1.0E-8D) {
			return Vec3.ZERO;
		}

		Optional<Direction.Axis> railAxis = railAxis(minecart, horizontal);
		if (railAxis.isEmpty()) {
			return horizontal;
		}

		return railAxis.get() == Direction.Axis.X ? new Vec3(horizontal.x, 0.0D, 0.0D) : new Vec3(0.0D, 0.0D, horizontal.z);
	}

	private static Vec3 capHorizontalSpeed(final Vec3 vector, final double maxSpeed) {
		double speedSqr = vector.horizontalDistanceSqr();
		if (speedSqr <= maxSpeed * maxSpeed) {
			return vector;
		}

		Vec3 horizontal = vector.horizontal().normalize().scale(maxSpeed);
		return new Vec3(horizontal.x, vector.y, horizontal.z);
	}

	private static Optional<Direction.Axis> railAxis(final AbstractMinecart minecart, final Vec3 desiredDirection) {
		if (!(minecart.level() instanceof ServerLevel serverLevel) || !minecart.isOnRails()) {
			return Optional.empty();
		}

		BlockPos railPos = minecart.getCurrentBlockPosOrRailBelow();
		BlockState railState = serverLevel.getBlockState(railPos);
		if (!BaseRailBlock.isRail(railState)) {
			railPos = railPos.below();
			railState = serverLevel.getBlockState(railPos);
		}

		if (!BaseRailBlock.isRail(railState) || !(railState.getBlock() instanceof BaseRailBlock railBlock)) {
			return Optional.empty();
		}

		RailShape shape = railState.getValue(railBlock.getShapeProperty());
		return axisForShape(shape, desiredDirection);
	}

	private static double throttleFactor(final boolean fullThrottle) {
		return fullThrottle ? 1.0D : SLOW_THROTTLE_FACTOR;
	}

	private static double linkWeight(final AbstractMinecart minecart) {
		if (minecart instanceof MinecartFurnace furnace && isPoweredLocomotive(furnace)) {
			return POWERED_ENGINE_LINK_WEIGHT;
		}

		return 1.0D;
	}

	private static boolean isPoweredLocomotive(final MinecartFurnace furnace) {
		MinecartChainAccess data = (MinecartChainAccess) furnace;
		return data.minecartChain$hasEngineLever()
			&& !data.minecartChain$isEngineActive()
			&& data.minecartChain$getWaterTicks() > 0
			&& furnace instanceof MinecartLocomotiveAccess locomotive
			&& locomotive.minecartChain$getLocomotiveFuelTicks() > 0;
	}

	private static Optional<Direction.Axis> axisForShape(final RailShape shape, final Vec3 desiredDirection) {
		return switch (shape) {
			case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> Optional.of(Direction.Axis.Z);
			case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> Optional.of(Direction.Axis.X);
			case SOUTH_EAST, SOUTH_WEST, NORTH_WEST, NORTH_EAST -> Optional.empty();
		};
	}
}
