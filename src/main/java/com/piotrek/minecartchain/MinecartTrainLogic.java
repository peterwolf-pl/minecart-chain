package com.piotrek.minecartchain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
	private static final double MIN_GUIDED_LINK_DISTANCE = 1.3D;
	private static final double LINK_BREAK_DISTANCE = 12.0D;
	private static final double LINK_STIFFNESS = 0.07D;
	private static final double LINK_DAMPING = 0.22D;
	private static final double MIN_DISTANCE_PUSH = 0.1D;
	private static final double MAX_LINK_IMPULSE = 0.1D;
	private static final double MAX_GUIDED_LINK_IMPULSE = 0.055D;
	private static final double ENGINE_PUSH = 0.06D;
	private static final double ENGINE_ASSIST = 0.03D;
	private static final double SLOW_THROTTLE_FACTOR = 1.0D / 3.0D;
	private static final double MAX_ENGINE_SPEED = 0.58D;
	private static final double MAX_MOUNTED_TRACK_SPEED = 0.4D;
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
	private static final float MAX_LOCOMOTIVE_YAW_STEP = 28.0F;
	private static final float MIN_LOCOMOTIVE_YAW_UPDATE = 0.5F;
	private static final int MAX_TRAIN_LENGTH = 32;
	private static final int AFTER_TICK_GUIDE_CACHE_CLEANUP_INTERVAL = 200;
	private static final int AFTER_TICK_GUIDE_CACHE_TTL_TICKS = 1200;
	private static final int EXTERNAL_TRACK_GUIDE_TTL_TICKS = 1;
	private static final Map<UUID, Long> LAST_AFTER_TICK_GUIDE = new HashMap<>();
	private static final Map<UUID, ExternalTrackGuide> EXTERNAL_TRACK_GUIDES = new HashMap<>();

	private MinecartTrainLogic() {
	}

	public static void tickLinks(final AbstractMinecart minecart) {
		Level level = minecart.level();
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
			return;
		}

		MinecartChainAccess data = (MinecartChainAccess) minecart;
		Optional<UUID> firstLink = data.minecartChain$getFirstLink();
		Optional<UUID> secondLink = data.minecartChain$getSecondLink();
		if (firstLink.isEmpty() && secondLink.isEmpty()) {
			return;
		}

		for (AbstractMinecart linked : linkedMinecarts(serverLevel, firstLink, secondLink)) {
			if (minecart.getUUID().compareTo(linked.getUUID()) < 0) {
				applyLinkConstraint(minecart, linked);
			}
		}
		guidePoweredTrainAfterLinkedCartTick(serverLevel, minecart);
	}

	public static Vec3 engineDirection(final MinecartFurnace furnace) {
		Vec3 mountedMovement = MountedTrackCompat.horizontalMovement(furnace);
		if (MountedTrackCompat.isMounted(furnace) && mountedMovement.lengthSqr() > 1.0E-5D) {
			Vec3 actualDirection = mountedMovement.normalize();
			return ((MinecartChainAccess) furnace).minecartChain$isReversed() ? actualDirection.reverse() : actualDirection;
		}

		Vec3 movement = furnace.getDeltaMovement().horizontal();
		if (movement.lengthSqr() > 1.0E-4D) {
			Vec3 actualDirection = movement.normalize();
			Vec3 engineFront = ((MinecartChainAccess) furnace).minecartChain$isReversed()
				? actualDirection.reverse()
				: actualDirection;
			return alignDirectionToTrack(furnace, engineFront);
		}

		Level level = furnace.level();
		if (level instanceof ServerLevel serverLevel) {
			AbstractMinecart closestLinked = closestLinkedMinecart(serverLevel, furnace);
			if (closestLinked != null) {
				Vec3 awayFromLinkedCart = furnace.position().subtract(closestLinked.position()).horizontal();
				if (awayFromLinkedCart.lengthSqr() > 1.0E-5D) {
					return alignDirectionToTrack(furnace, awayFromLinkedCart.normalize());
				}
			}
		}

		Direction direction = furnace.getMotionDirection();
		Vec3 fallback = new Vec3(direction.getStepX(), 0.0D, direction.getStepZ());
		return fallback.lengthSqr() > 0.0D ? alignDirectionToTrack(furnace, fallback.normalize()) : Vec3.ZERO;
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

	/**
	 * Returns a stable key shared by every currently loaded cart in one linked
	 * consist. Track controllers use it to keep one route reserved until the
	 * last linked cart has cleared a switch.
	 */
	public static UUID connectedTrainKey(final ServerLevel level, final AbstractMinecart minecart) {
		UUID key = minecart.getUUID();
		for (AbstractMinecart linked : connectedTrain(level, minecart)) {
			if (linked.getUUID().compareTo(key) < 0) {
				key = linked.getUUID();
			}
		}
		return key;
	}

	public static Optional<MinecartFurnace> controlledLocomotive(final ServerLevel level, final AbstractMinecart minecart) {
		MinecartFurnace closest = null;
		double closestDistance = Double.POSITIVE_INFINITY;
		for (AbstractMinecart trainCart : connectedTrain(level, minecart)) {
			if (!(trainCart instanceof MinecartFurnace furnace)
				|| !((MinecartChainAccess) furnace).minecartChain$hasEngineLever()) {
				continue;
			}

			double distance = minecart.distanceToSqr(furnace);
			if (distance < closestDistance) {
				closest = furnace;
				closestDistance = distance;
			}
		}
		return Optional.ofNullable(closest);
	}

	public static int locomotiveFuelCost(final MinecartFurnace furnace) {
		return locomotiveFuelCost(connectedTrainSize(furnace));
	}

	public static int locomotiveWaterCost(final MinecartFurnace furnace) {
		return locomotiveWaterCost(connectedTrainSize(furnace));
	}

	public static int locomotiveFuelCost(final int connectedTrainSize) {
		return Math.max(1, connectedTrainSize);
	}

	public static int locomotiveWaterCost(final int connectedTrainSize) {
		return 1 + Math.max(0, connectedTrainSize - 1) / 2;
	}

	/**
	 * Lets a custom track controller expose its current path direction without
	 * making Minecart Chain depend directly on that track mod.
	 */
	public static void markExternalTrackGuide(final AbstractMinecart minecart, final Vec3 pathDirection) {
		if (!(minecart.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		Vec3 horizontal = pathDirection.horizontal();
		if (horizontal.lengthSqr() <= 1.0E-8D) {
			return;
		}

		long gameTime = serverLevel.getGameTime();
		cleanupExternalTrackGuides(gameTime);
		EXTERNAL_TRACK_GUIDES.put(
			minecart.getUUID(),
			new ExternalTrackGuide(serverLevel, gameTime, horizontal.normalize())
		);
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

		Vec3 horizontal = horizontalMovement(furnace).add(direction.scale(ENGINE_ASSIST * throttleFactor(fullThrottle)));
		double speed = horizontal.length();
		double maxSpeed = fullThrottle ? MAX_ENGINE_SPEED : SLOW_ENGINE_SPEED;
		if (MountedTrackCompat.isMounted(furnace)) {
			maxSpeed = MAX_MOUNTED_TRACK_SPEED * throttleFactor(fullThrottle);
		}
		if (speed > maxSpeed) {
			horizontal = horizontal.normalize().scale(maxSpeed);
		}

		setHorizontalMovement(furnace, horizontal);
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
			if (trainCart instanceof MinecartFurnace furnace && isPoweredLocomotive(furnace) && markAfterTickGuided(level, furnace)) {
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
			setHorizontalMovement(minecart, horizontalMovement(minecart).scale(BRAKE_DAMPING));
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

	public static boolean usesMountedTrack(final AbstractMinecart minecart) {
		return MountedTrackCompat.isMounted(minecart);
	}

	public static boolean shouldFlipMountedTrackLocomotiveControls(final AbstractMinecart minecart) {
		if (!MountedTrackCompat.isMounted(minecart)) {
			return false;
		}

		Vec3 trackDirection = MountedTrackCompat.trackDirection(minecart);
		if (trackDirection.lengthSqr() <= 1.0E-8D) {
			return false;
		}

		MinecartChainAccess data = (MinecartChainAccess) minecart;
		// Splinecart renders passengers through a 180-degree follower transform before
		// Minecraft's minecart body rotation, so the unflipped control frame is opposite
		// the spline segment's positive direction.
		if (data.minecartChain$hasLocomotiveYaw()) {
			return directionFromYaw(data.minecartChain$getLocomotiveYaw()).dot(trackDirection) > 0.0D;
		}

		Vec3 movement = MountedTrackCompat.horizontalMovement(minecart);
		return movement.lengthSqr() > 1.0E-5D && movement.dot(trackDirection) > 0.0D;
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
		if (horizontalMovement(furnace).horizontalDistanceSqr() < SMOKE_SPEED_THRESHOLD * SMOKE_SPEED_THRESHOLD) {
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
		return horizontalMovement(minecart).horizontalDistanceSqr() <= STOPPED_SPEED_THRESHOLD * STOPPED_SPEED_THRESHOLD;
	}

	private static List<AbstractMinecart> linkedMinecarts(final ServerLevel level, final MinecartChainAccess data) {
		return linkedMinecarts(level, data.minecartChain$getFirstLink(), data.minecartChain$getSecondLink());
	}

	private static List<AbstractMinecart> linkedMinecarts(
		final ServerLevel level, final Optional<UUID> firstLink, final Optional<UUID> secondLink
	) {
		List<AbstractMinecart> minecarts = new ArrayList<>(2);
		addLinkedMinecart(level, firstLink, minecarts);
		addLinkedMinecart(level, secondLink, minecarts);
		return minecarts;
	}

	private static AbstractMinecart closestLinkedMinecart(final ServerLevel level, final AbstractMinecart minecart) {
		AbstractMinecart closest = null;
		double closestDistance = Double.POSITIVE_INFINITY;
		for (AbstractMinecart linked : linkedMinecarts(level, (MinecartChainAccess) minecart)) {
			double distance = minecart.distanceToSqr(linked);
			if (distance < closestDistance) {
				closest = linked;
				closestDistance = distance;
			}
		}
		return closest;
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
		AbstractMinecart closest = null;
		double closestDistance = Double.POSITIVE_INFINITY;
		for (AbstractMinecart linked : linkedMinecarts(level, (MinecartChainAccess) current)) {
			UUID linkedId = linked.getUUID();
			if ((previous != null && linkedId.equals(previous.getUUID())) || seen.contains(linkedId)) {
				continue;
			}

			double distance = current.distanceToSqr(linked);
			if (distance < closestDistance) {
				closest = linked;
				closestDistance = distance;
			}
		}
		return closest;
	}

	private static void guideFollower(final AbstractMinecart leader, final AbstractMinecart follower, final Vec3 fallbackDirection) {
		Vec3 toLeader = leader.position().subtract(follower.position()).horizontal();
		double distance = toLeader.length();
		if (distance <= 1.0E-4D) {
			return;
		}

		Vec3 leaderVelocity = horizontalMovement(leader);
		Vec3 localForward = leaderVelocity.lengthSqr() > 1.0E-5D ? leaderVelocity.normalize() : fallbackDirection;
		if (distance < MIN_LINK_DISTANCE) {
			Vec3 followerVelocity = horizontalMovement(follower);
			Vec3 railFollowerVelocity = constrainToRail(follower, followerVelocity.scale(FOLLOWER_OVERTAKE_BRAKE));
			double push = Mth.clamp((MIN_LINK_DISTANCE - distance) * MIN_DISTANCE_PUSH, 0.04D, MAX_LINK_IMPULSE);
			Vec3 pushAway = railPullVector(follower, toLeader.reverse(), push);
			Vec3 corrected = keepFollowerFromClosingGap(
				leader, follower, railFollowerVelocity.add(pushAway), toLeader, distance, fallbackDirection
			);
			corrected = capHorizontalSpeed(corrected, MAX_FOLLOWER_SPEED);
			setHorizontalMovement(follower, corrected);
			return;
		}

		if (localForward.lengthSqr() > 1.0E-5D && toLeader.dot(localForward) < -0.05D) {
			Vec3 slowed = constrainToRail(follower, horizontalMovement(follower).scale(FOLLOWER_OVERTAKE_BRAKE));
			Vec3 pullBack = railPullVector(follower, toLeader, MAX_LINK_IMPULSE);
			Vec3 corrected = keepFollowerFromClosingGap(
				leader, follower, slowed.add(pullBack), toLeader, distance, fallbackDirection
			);
			corrected = capHorizontalSpeed(corrected, MAX_FOLLOWER_SPEED);
			setHorizontalMovement(follower, corrected);
			return;
		}

		Vec3 followerVelocity = horizontalMovement(follower);
		Vec3 railFollowerVelocity = constrainToRail(follower, followerVelocity);
		Vec3 railLeaderVelocity = transferTrackVelocity(leader, follower, fallbackDirection);
		Vec3 blendedVelocity = railFollowerVelocity.scale(1.0D - FOLLOWER_VELOCITY_BLEND)
			.add(railLeaderVelocity.scale(FOLLOWER_VELOCITY_BLEND));

		if (distance > LINK_DISTANCE) {
			double pull = Mth.clamp((distance - LINK_DISTANCE) * FOLLOWER_PULL, 0.0D, MAX_LINK_IMPULSE);
			blendedVelocity = blendedVelocity.add(railPullVector(follower, toLeader, pull));
		}

		blendedVelocity = constrainToRail(follower, blendedVelocity);
		blendedVelocity = keepFollowerFromClosingGap(
			leader, follower, blendedVelocity, toLeader, distance, fallbackDirection
		);
		blendedVelocity = capHorizontalSpeed(blendedVelocity, MAX_FOLLOWER_SPEED);
		setHorizontalMovement(follower, blendedVelocity);
	}

	private static Vec3 keepFollowerFromClosingGap(
		final AbstractMinecart leader,
		final AbstractMinecart follower,
		final Vec3 candidateVelocity,
		final Vec3 toLeader,
		final double distance,
		final Vec3 fallbackDirection
	) {
		if (distance > LINK_DISTANCE + FOLLOWER_SPACING_DEADZONE || toLeader.lengthSqr() <= 1.0E-8D) {
			return candidateVelocity;
		}

		Vec3 gapDirection = toLeader.scale(1.0D / distance);
		Vec3 leaderVelocity = transferTrackVelocity(leader, follower, fallbackDirection);
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

	private static Vec3 transferTrackVelocity(
		final AbstractMinecart leader,
		final AbstractMinecart follower,
		final Vec3 fallbackDirection
	) {
		Vec3 leaderVelocity = horizontalMovement(leader).horizontal();
		double leaderSpeed = leaderVelocity.length();
		if (leaderSpeed <= 1.0E-8D) {
			return Vec3.ZERO;
		}

		Vec3 followerTrack = guidedTrackDirection(follower);
		if (followerTrack.lengthSqr() <= 1.0E-8D) {
			return constrainToRail(follower, leaderVelocity);
		}

		Vec3 directionReference = leaderVelocity;
		if (Math.abs(directionReference.dot(followerTrack)) <= 1.0E-4D) {
			Vec3 followerVelocity = horizontalMovement(follower).horizontal();
			directionReference = followerVelocity.lengthSqr() > 1.0E-8D ? followerVelocity : fallbackDirection;
		}
		double sign = directionReference.dot(followerTrack) < 0.0D ? -1.0D : 1.0D;
		return followerTrack.scale(leaderSpeed * sign);
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
		double minimumDistance = minimumLinkDistance(first, second);
		if (distance < minimumDistance) {
			pushMinecartsApart(first, second, direction, distance, minimumDistance);
			return;
		}

		if (distance > MAX_LINK_DISTANCE) {
			clampLinkDistance(first, second, direction, distance);
			return;
		}

		double error = distance - LINK_DISTANCE;
		if (error <= 0.04D) {
			return;
		}

		double separationSpeed = horizontalMovement(second).subtract(horizontalMovement(first)).horizontal().dot(direction);
		double impulseLimit = linkImpulseLimit(first, second);
		double impulse = Mth.clamp(
			error * LINK_STIFFNESS + separationSpeed * LINK_DAMPING,
			0.0D,
			impulseLimit
		);
		if (impulse <= 1.0E-6D) {
			return;
		}
		Vec3 adjustment = direction.scale(impulse);
		Vec3 firstAdjustment = constrainToRail(first, adjustment).scale(linkWeight(first));
		Vec3 secondAdjustment = constrainToRail(second, adjustment.reverse()).scale(linkWeight(second));
		addHorizontalMovement(first, firstAdjustment);
		addHorizontalMovement(second, secondAdjustment);
	}

	private static void pushMinecartsApart(
		final AbstractMinecart first,
		final AbstractMinecart second,
		final Vec3 direction,
		final double distance,
		final double minimumDistance
	) {
		double deficit = minimumDistance - distance;
		Vec3 correction = direction.scale(deficit * 0.5D);
		Vec3 firstCorrection = constrainToRail(first, correction.reverse());
		Vec3 secondCorrection = constrainToRail(second, correction);
		moveIfNotMounted(first, firstCorrection);
		moveIfNotMounted(second, secondCorrection);

		double impulseLimit = linkImpulseLimit(first, second);
		double minimumImpulse = impulseLimit == MAX_GUIDED_LINK_IMPULSE ? 0.01D : 0.04D;
		double impulse = Mth.clamp(deficit * MIN_DISTANCE_PUSH, minimumImpulse, impulseLimit);
		Vec3 push = direction.scale(impulse);
		addHorizontalMovement(first, constrainToRail(first, push.reverse()));
		addHorizontalMovement(second, constrainToRail(second, push));
	}

	private static void clampLinkDistance(final AbstractMinecart first, final AbstractMinecart second, final Vec3 direction, final double distance) {
		double excess = distance - MAX_LINK_DISTANCE;
		Vec3 correction = direction.scale(excess * 0.5D);
		Vec3 firstCorrection = constrainToRail(first, correction);
		Vec3 secondCorrection = constrainToRail(second, correction.reverse());
		moveIfNotMounted(first, firstCorrection);
		moveIfNotMounted(second, secondCorrection);

		double separationSpeed = horizontalMovement(second).subtract(horizontalMovement(first)).horizontal().dot(direction);
		double pull = Mth.clamp(
			excess * LINK_STIFFNESS + Math.max(0.0D, separationSpeed) * LINK_DAMPING,
			0.0D,
			linkImpulseLimit(first, second)
		);
		Vec3 damping = direction.scale(pull);
		addHorizontalMovement(first, constrainToRail(first, damping).scale(linkWeight(first)));
		addHorizontalMovement(second, constrainToRail(second, damping.reverse()).scale(linkWeight(second)));
	}

	private static double linkImpulseLimit(final AbstractMinecart first, final AbstractMinecart second) {
		return externalTrackDirection(first).lengthSqr() > 1.0E-8D
			|| externalTrackDirection(second).lengthSqr() > 1.0E-8D
			? MAX_GUIDED_LINK_IMPULSE
			: MAX_LINK_IMPULSE;
	}

	private static double minimumLinkDistance(final AbstractMinecart first, final AbstractMinecart second) {
		return externalTrackDirection(first).lengthSqr() > 1.0E-8D
			|| externalTrackDirection(second).lengthSqr() > 1.0E-8D
			? MIN_GUIDED_LINK_DISTANCE
			: MIN_LINK_DISTANCE;
	}

	private static Vec3 railPullVector(final AbstractMinecart minecart, final Vec3 desiredDirection, final double magnitude) {
		if (magnitude <= 0.0D || desiredDirection.horizontalDistanceSqr() <= 1.0E-8D) {
			return Vec3.ZERO;
		}

		Vec3 guidedDirection = guidedTrackDirection(minecart);
		if (guidedDirection.lengthSqr() > 1.0E-8D) {
			double signed = desiredDirection.dot(guidedDirection);
			return Math.abs(signed) <= 1.0E-8D
				? Vec3.ZERO
				: guidedDirection.scale(Math.signum(signed) * magnitude);
		}

		Optional<Direction.Axis> railAxis = railAxis(minecart);
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

		Vec3 guidedDirection = guidedTrackDirection(minecart);
		if (guidedDirection.lengthSqr() > 1.0E-8D) {
			return guidedDirection.scale(horizontal.dot(guidedDirection));
		}

		Optional<Direction.Axis> railAxis = railAxis(minecart);
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

	private static Vec3 horizontalMovement(final AbstractMinecart minecart) {
		return MountedTrackCompat.horizontalMovement(minecart);
	}

	private static void setHorizontalMovement(final AbstractMinecart minecart, final Vec3 movement) {
		Vec3 externalDirection = externalTrackDirection(minecart);
		if (externalDirection.lengthSqr() > 1.0E-8D) {
			Vec3 current = minecart.getDeltaMovement();
			Vec3 constrained = externalDirection.scale(movement.horizontal().dot(externalDirection));
			minecart.setDeltaMovement(constrained.x, current.y, constrained.z);
			return;
		}

		Vec3 curvedRailDirection = curvedRailDirection(minecart);
		if (curvedRailDirection.lengthSqr() > 1.0E-8D) {
			Vec3 current = minecart.getDeltaMovement();
			Vec3 constrained = curvedRailDirection.scale(movement.horizontal().dot(curvedRailDirection));
			minecart.setDeltaMovement(constrained.x, current.y, constrained.z);
			return;
		}
		MountedTrackCompat.setHorizontalMovement(minecart, movement);
	}

	private static void addHorizontalMovement(final AbstractMinecart minecart, final Vec3 adjustment) {
		MountedTrackCompat.addHorizontalMovement(minecart, adjustment);
	}

	private static void moveIfNotMounted(final AbstractMinecart minecart, final Vec3 correction) {
		if (!MountedTrackCompat.isMounted(minecart)
			&& externalTrackDirection(minecart).lengthSqr() <= 1.0E-8D
			&& !isCurvedRail(minecart)) {
			minecart.setPos(minecart.getX() + correction.x, minecart.getY(), minecart.getZ() + correction.z);
		}
	}

	private static Optional<Direction.Axis> railAxis(final AbstractMinecart minecart) {
		return railShape(minecart).flatMap(MinecartTrainLogic::axisForShape);
	}

	private static Optional<RailShape> railShape(final AbstractMinecart minecart) {
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

		return Optional.of(railState.getValue(railBlock.getShapeProperty()));
	}

	private static boolean isCurvedRail(final AbstractMinecart minecart) {
		return railShape(minecart).filter(MinecartTrainLogic::isCurvedShape).isPresent();
	}

	private static Vec3 guidedTrackDirection(final AbstractMinecart minecart) {
		Vec3 externalDirection = externalTrackDirection(minecart);
		if (externalDirection.lengthSqr() > 1.0E-8D) {
			return externalDirection;
		}

		Vec3 mountedDirection = MountedTrackCompat.trackDirection(minecart);
		if (mountedDirection.lengthSqr() > 1.0E-8D) {
			return mountedDirection;
		}

		Vec3 curvedRailDirection = curvedRailDirection(minecart);
		if (curvedRailDirection.lengthSqr() > 1.0E-8D) {
			return curvedRailDirection;
		}

		return Vec3.ZERO;
	}

	private static Vec3 curvedRailDirection(final AbstractMinecart minecart) {
		if (!isCurvedRail(minecart)) {
			return Vec3.ZERO;
		}

		Vec3 movement = minecart.getDeltaMovement().horizontal();
		return movement.lengthSqr() > 1.0E-8D ? movement.normalize() : Vec3.ZERO;
	}

	private static Vec3 alignDirectionToTrack(final AbstractMinecart minecart, final Vec3 direction) {
		Vec3 trackDirection = guidedTrackDirection(minecart);
		if (trackDirection.lengthSqr() <= 1.0E-8D) {
			return direction;
		}
		return direction.dot(trackDirection) < 0.0D ? trackDirection.reverse() : trackDirection;
	}

	private static Vec3 externalTrackDirection(final AbstractMinecart minecart) {
		if (!(minecart.level() instanceof ServerLevel serverLevel)) {
			return Vec3.ZERO;
		}

		ExternalTrackGuide guide = EXTERNAL_TRACK_GUIDES.get(minecart.getUUID());
		if (guide == null || guide.level() != serverLevel) {
			return Vec3.ZERO;
		}

		long age = serverLevel.getGameTime() - guide.gameTime();
		if (age < 0L || age > EXTERNAL_TRACK_GUIDE_TTL_TICKS) {
			EXTERNAL_TRACK_GUIDES.remove(minecart.getUUID(), guide);
			return Vec3.ZERO;
		}
		return guide.direction();
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

	private static boolean markAfterTickGuided(final ServerLevel level, final MinecartFurnace furnace) {
		long gameTime = level.getGameTime();
		cleanupAfterTickGuideCache(gameTime);
		Long previousTick = LAST_AFTER_TICK_GUIDE.put(furnace.getUUID(), gameTime);
		return previousTick == null || previousTick != gameTime;
	}

	private static void cleanupAfterTickGuideCache(final long gameTime) {
		if (LAST_AFTER_TICK_GUIDE.size() < 512 && gameTime % AFTER_TICK_GUIDE_CACHE_CLEANUP_INTERVAL != 0L) {
			return;
		}

		LAST_AFTER_TICK_GUIDE.entrySet().removeIf(entry -> gameTime - entry.getValue() > AFTER_TICK_GUIDE_CACHE_TTL_TICKS);
	}

	private static void cleanupExternalTrackGuides(final long gameTime) {
		if (EXTERNAL_TRACK_GUIDES.size() < 512 && gameTime % AFTER_TICK_GUIDE_CACHE_CLEANUP_INTERVAL != 0L) {
			return;
		}

		EXTERNAL_TRACK_GUIDES.entrySet().removeIf(entry -> {
			ExternalTrackGuide guide = entry.getValue();
			long age = guide.level().getGameTime() - guide.gameTime();
			return age < 0L || age > EXTERNAL_TRACK_GUIDE_TTL_TICKS;
		});
	}

	static void clearRuntimeState() {
		LAST_AFTER_TICK_GUIDE.clear();
		EXTERNAL_TRACK_GUIDES.clear();
	}

	private static Optional<Direction.Axis> axisForShape(final RailShape shape) {
		return switch (shape) {
			case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> Optional.of(Direction.Axis.Z);
			case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> Optional.of(Direction.Axis.X);
			case SOUTH_EAST, SOUTH_WEST, NORTH_WEST, NORTH_EAST -> Optional.empty();
		};
	}

	private static boolean isCurvedShape(final RailShape shape) {
		return switch (shape) {
			case SOUTH_EAST, SOUTH_WEST, NORTH_WEST, NORTH_EAST -> true;
			default -> false;
		};
	}

	private record ExternalTrackGuide(ServerLevel level, long gameTime, Vec3 direction) {
	}
	}
