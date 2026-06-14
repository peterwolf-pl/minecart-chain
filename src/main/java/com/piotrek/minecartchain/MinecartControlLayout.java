package com.piotrek.minecartchain;

import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class MinecartControlLayout {
	public static final double REAR_CONTROL_X = 0.68D;
	public static final double BRAKE_CONTROL_Z = 0.38D;
	public static final double DIRECTION_CONTROL_Z = 0.0D;
	public static final double THROTTLE_CONTROL_Z = -0.38D;
	public static final double CHIMNEY_RENDER_X = -0.42D;
	public static final double CHIMNEY_RENDER_Z = 0.0D;
	public static final double CHIMNEY_SMOKE_FORWARD_OFFSET = CHIMNEY_RENDER_X;
	public static final double CHIMNEY_SMOKE_Y = 2.45D;

	private static final double CONTROL_HIT_X_RADIUS = 0.42D;
	private static final double CONTROL_HIT_Z_RADIUS = 0.18D;
	private static final double CONTROL_HIT_Y_MIN = 0.05D;
	private static final double CONTROL_HIT_Y_MAX = 1.1D;
	private static final double CONTROL_AIM_RADIUS = 0.58D;

	private MinecartControlLayout() {
	}

	public static Control hitControl(final AbstractMinecart minecart, final Player player, final EntityHitResult hitResult) {
		Vec3 forward = forwardDirection(minecart);
		if (forward.lengthSqr() <= 1.0E-5D) {
			return Control.NONE;
		}
		Vec3 right = new Vec3(-forward.z, 0.0D, forward.x).normalize();

		Control rayControl = rayControl(minecart, player, forward, right);
		if (rayControl != Control.NONE) {
			return rayControl;
		}

		if (hitResult != null) {
			return controlAtLocalPoint(toLocal(hitResult.getLocation().subtract(minecart.position()), forward, right));
		}

		return Control.NONE;
	}

	private static Control controlAtLocalPoint(final Vec3 localPoint) {
		if (localPoint.y < CONTROL_HIT_Y_MIN || localPoint.y > CONTROL_HIT_Y_MAX) {
			return Control.NONE;
		}
		if (Math.abs(Math.abs(localPoint.x) - REAR_CONTROL_X) > CONTROL_HIT_X_RADIUS) {
			return Control.NONE;
		}
		return controlAtLocalZ(localPoint.z);
	}

	private static Control rayControl(final AbstractMinecart minecart, final Player player, final Vec3 forward, final Vec3 right) {
		Vec3 localOrigin = toLocal(player.getEyePosition().subtract(minecart.position()), forward, right);
		Vec3 localDirection = toLocal(player.getViewVector(1.0F).normalize(), forward, right);
		double reach = Math.max(5.0D, player.entityInteractionRange() + 1.0D);
		Vec3 localEnd = localOrigin.add(localDirection.scale(reach));

		Control hitControl = nearestPanelHitControl(localOrigin, localEnd);
		if (hitControl != Control.NONE) {
			return hitControl;
		}

		Control aimControl = Control.NONE;
		double nearestAimDistance = Double.POSITIVE_INFINITY;
		for (Control control : clickableControls()) {
			double aimDistance = nearestPanelAimDistanceSqr(localOrigin, localDirection, reach, controlZ(control));
			if (aimDistance < nearestAimDistance) {
				nearestAimDistance = aimDistance;
				aimControl = control;
			}
		}

		double maxAimDistance = CONTROL_AIM_RADIUS * CONTROL_AIM_RADIUS;
		return nearestAimDistance <= maxAimDistance ? aimControl : Control.NONE;
	}

	private static Control nearestPanelHitControl(final Vec3 localOrigin, final Vec3 localEnd) {
		Control nearestControl = Control.NONE;
		double nearestDistance = Double.POSITIVE_INFINITY;
		for (Control control : clickableControls()) {
			double distance = nearestPanelHitDistance(localOrigin, localEnd, controlZ(control));
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearestControl = control;
			}
		}
		return Double.isFinite(nearestDistance) ? nearestControl : Control.NONE;
	}

	private static double nearestPanelHitDistance(final Vec3 localOrigin, final Vec3 localEnd, final double localZ) {
		double rearHit = hitDistance(panelBox(REAR_CONTROL_X, localZ), localOrigin, localEnd);
		double mirroredHit = hitDistance(panelBox(-REAR_CONTROL_X, localZ), localOrigin, localEnd);
		return Math.min(rearHit, mirroredHit);
	}

	private static double nearestPanelAimDistanceSqr(final Vec3 localOrigin, final Vec3 localDirection, final double reach, final double localZ) {
		double rearAim = aimDistanceSqr(new Vec3(REAR_CONTROL_X, controlCenterY(), localZ), localOrigin, localDirection, reach);
		double mirroredAim = aimDistanceSqr(new Vec3(-REAR_CONTROL_X, controlCenterY(), localZ), localOrigin, localDirection, reach);
		return Math.min(rearAim, mirroredAim);
	}

	private static double aimDistanceSqr(final Vec3 center, final Vec3 localOrigin, final Vec3 localDirection, final double reach) {
		double lengthSqr = localDirection.lengthSqr();
		if (lengthSqr <= 1.0E-8D) {
			return Double.POSITIVE_INFINITY;
		}

		double rayPosition = center.subtract(localOrigin).dot(localDirection) / lengthSqr;
		if (rayPosition < 0.0D || rayPosition > reach) {
			return Double.POSITIVE_INFINITY;
		}

		Vec3 closest = localOrigin.add(localDirection.scale(rayPosition));
		return closest.distanceToSqr(center);
	}

	private static double controlCenterY() {
		return (CONTROL_HIT_Y_MIN + CONTROL_HIT_Y_MAX) * 0.5D;
	}

	private static double hitDistance(final AABB box, final Vec3 localOrigin, final Vec3 localEnd) {
		return box.clip(localOrigin, localEnd)
			.map(localOrigin::distanceToSqr)
			.orElse(Double.POSITIVE_INFINITY);
	}

	private static AABB panelBox(final double localX, final double localZ) {
		return new AABB(
			localX - CONTROL_HIT_X_RADIUS,
			CONTROL_HIT_Y_MIN,
			localZ - CONTROL_HIT_Z_RADIUS,
			localX + CONTROL_HIT_X_RADIUS,
			CONTROL_HIT_Y_MAX,
			localZ + CONTROL_HIT_Z_RADIUS
		);
	}

	private static Control controlAtLocalZ(final double localZ) {
		Control nearestControl = Control.NONE;
		double nearestDistance = CONTROL_HIT_Z_RADIUS;
		for (Control control : clickableControls()) {
			double distance = Math.abs(localZ - controlZ(control));
			if (distance <= nearestDistance) {
				nearestDistance = distance;
				nearestControl = control;
			}
		}
		return nearestControl;
	}

	private static Control[] clickableControls() {
		return new Control[] { Control.BRAKE, Control.DIRECTION, Control.THROTTLE };
	}

	private static double controlZ(final Control control) {
		return switch (control) {
			case BRAKE -> BRAKE_CONTROL_Z;
			case DIRECTION -> DIRECTION_CONTROL_Z;
			case THROTTLE -> THROTTLE_CONTROL_Z;
			case NONE -> Double.NaN;
		};
	}

	private static Vec3 toLocal(final Vec3 offset, final Vec3 forward, final Vec3 right) {
		return new Vec3(offset.dot(forward), offset.y, -offset.dot(right));
	}

	private static Vec3 forwardDirection(final AbstractMinecart minecart) {
		MinecartChainAccess data = (MinecartChainAccess) minecart;
		if (data.minecartChain$hasLocomotiveYaw()) {
			return MinecartTrainLogic.directionFromYaw(data.minecartChain$getLocomotiveYaw());
		}

		Vec3 forward = new Vec3(minecart.getMotionDirection().getStepX(), 0.0D, minecart.getMotionDirection().getStepZ());
		if (forward.lengthSqr() > 1.0E-5D) {
			return forward.normalize();
		}

		double yaw = Math.toRadians(minecart.getYRot());
		return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
	}

	public enum Control {
		NONE,
		BRAKE,
		DIRECTION,
		THROTTLE
	}
}
