package com.piotrek.minecartchain.client;

import net.minecraft.client.renderer.entity.state.MinecartRenderState;

public class SteamLocomotiveRenderState extends MinecartRenderState {
	public float throttle;
	public boolean reverser;
	public boolean couplerEngaged;
	public float wheelRotation;
	public float pitch;
	public float yaw;
	public float roll;
	public double speed;
	public boolean onSpline;

	public float getThrottle() {
		return this.throttle;
	}

	public void setThrottle(final float throttle) {
		this.throttle = throttle;
	}

	public boolean isCouplerEngaged() {
		return this.couplerEngaged;
	}

	public void setCouplerEngaged(final boolean couplerEngaged) {
		this.couplerEngaged = couplerEngaged;
	}

	public float getWheelRotation() {
		return this.wheelRotation;
	}

	public void setWheelRotation(final float wheelRotation) {
		this.wheelRotation = wheelRotation;
	}

	public float getPitch() {
		return this.pitch;
	}

	public void setPitch(final float pitch) {
		this.pitch = pitch;
	}

	public float getYaw() {
		return this.yaw;
	}

	public void setYaw(final float yaw) {
		this.yaw = yaw;
	}

	public float getRoll() {
		return this.roll;
	}

	public void setRoll(final float roll) {
		this.roll = roll;
	}

	public double getSpeed() {
		return this.speed;
	}

	public void setSpeed(final double speed) {
		this.speed = speed;
	}
}
