package com.piotrek.minecartchain;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;

/**
 * Optional bridge to Splinecart. Reflection keeps Splinecart optional at
 * compile time and at runtime while still allowing both mods to share the
 * authoritative along-track velocity.
 */
final class MountedTrackCompat {
	private static final String SPLINE_FOLLOWER = "io.github.foundationgames.splinecart.entity.TrackFollowerEntity";
	private static final String GET_VELOCITY = "splinecart$getTrackVelocity";
	private static final String GET_DIRECTION = "splinecart$getTrackDirection";
	private static final String SET_VELOCITY = "splinecart$setTrackVelocity";

	private static Class<?> followerClass;
	private static Method getVelocity;
	private static Method getDirection;
	private static Method setVelocity;
	private static volatile boolean resolved;

	private MountedTrackCompat() {
	}

	static boolean isMounted(final AbstractMinecart minecart) {
		return bridge(minecart) != null;
	}

	static Vec3 horizontalMovement(final AbstractMinecart minecart) {
		Entity follower = bridge(minecart);
		if (follower == null) {
			return minecart.getDeltaMovement().horizontal();
		}

		Vec3 direction = direction(follower);
		if (direction.lengthSqr() <= 1.0E-8D) {
			return follower.getDeltaMovement().horizontal();
		}
		return direction.scale(velocity(follower)).horizontal();
	}

	static Vec3 trackDirection(final AbstractMinecart minecart) {
		Entity follower = bridge(minecart);
		if (follower == null) {
			return Vec3.ZERO;
		}
		Vec3 horizontal = direction(follower).horizontal();
		return horizontal.lengthSqr() <= 1.0E-8D ? Vec3.ZERO : horizontal.normalize();
	}

	static void setHorizontalMovement(final AbstractMinecart minecart, final Vec3 movement) {
		Entity follower = bridge(minecart);
		if (follower == null) {
			Vec3 current = minecart.getDeltaMovement();
			minecart.setDeltaMovement(movement.x, current.y, movement.z);
			return;
		}

		Vec3 direction = direction(follower);
		Vec3 horizontalDirection = direction.horizontal();
		double horizontalScale = horizontalDirection.length();
		if (horizontalScale > 1.0E-8D) {
			setVelocity(follower, movement.dot(horizontalDirection.scale(1.0D / horizontalScale)) / horizontalScale);
		}
		minecart.setDeltaMovement(Vec3.ZERO);
	}

	static void addHorizontalMovement(final AbstractMinecart minecart, final Vec3 adjustment) {
		setHorizontalMovement(minecart, horizontalMovement(minecart).add(adjustment));
	}

	private static Entity bridge(final AbstractMinecart minecart) {
		Entity vehicle = minecart.getVehicle();
		if (vehicle == null) {
			return null;
		}
		resolve(vehicle.getClass());
		return followerClass != null && followerClass.isInstance(vehicle) ? vehicle : null;
	}

	private static void resolve(final Class<?> candidate) {
		if (resolved || !candidate.getName().equals(SPLINE_FOLLOWER)) {
			return;
		}

		synchronized (MountedTrackCompat.class) {
			if (resolved) {
				return;
			}

			try {
				followerClass = candidate;
				getVelocity = candidate.getMethod(GET_VELOCITY);
				getDirection = candidate.getMethod(GET_DIRECTION);
				setVelocity = candidate.getMethod(SET_VELOCITY, double.class);
			} catch (NoSuchMethodException exception) {
				followerClass = null;
			} finally {
				resolved = true;
			}
		}
	}

	private static double velocity(final Entity follower) {
		try {
			return ((Number) getVelocity.invoke(follower)).doubleValue();
		} catch (IllegalAccessException | InvocationTargetException exception) {
			return 0.0D;
		}
	}

	private static Vec3 direction(final Entity follower) {
		try {
			return (Vec3) getDirection.invoke(follower);
		} catch (IllegalAccessException | InvocationTargetException exception) {
			return Vec3.ZERO;
		}
	}

	private static void setVelocity(final Entity follower, final double velocity) {
		try {
			setVelocity.invoke(follower, velocity);
		} catch (IllegalAccessException | InvocationTargetException ignored) {
		}
	}
}
