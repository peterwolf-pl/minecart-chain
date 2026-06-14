package com.piotrek.minecartchain;

import java.util.Optional;
import java.util.UUID;

public interface MinecartChainAccess {
	Optional<UUID> minecartChain$getFirstLink();

	Optional<UUID> minecartChain$getSecondLink();

	boolean minecartChain$addLink(UUID uuid);

	void minecartChain$removeLink(UUID uuid);

	void minecartChain$clearLinks();

	boolean minecartChain$hasEngineLever();

	void minecartChain$setEngineLever(boolean mounted);

	boolean minecartChain$isEngineActive();

	void minecartChain$setEngineActive(boolean active);

	boolean minecartChain$isFullThrottle();

	void minecartChain$setFullThrottle(boolean fullThrottle);

	boolean minecartChain$isReversed();

	void minecartChain$setReversed(boolean reversed);

	boolean minecartChain$hasLocomotiveYaw();

	void minecartChain$setHasLocomotiveYaw(boolean hasLocomotiveYaw);

	float minecartChain$getLocomotiveYaw();

	void minecartChain$setLocomotiveYaw(float yaw);

	int minecartChain$getWaterTicks();

	void minecartChain$setWaterTicks(int waterTicks);

	default int minecartChain$addWaterTicks(final int waterTicks, final int maxWaterTicks) {
		int currentWater = this.minecartChain$getWaterTicks();
		int acceptedWater = Math.min(Math.max(0, waterTicks), Math.max(0, maxWaterTicks - currentWater));
		if (acceptedWater > 0) {
			this.minecartChain$setWaterTicks(currentWater + acceptedWater);
		}
		return acceptedWater;
	}

	default boolean minecartChain$hasLink(final UUID uuid) {
		return this.minecartChain$getFirstLink().filter(uuid::equals).isPresent()
			|| this.minecartChain$getSecondLink().filter(uuid::equals).isPresent();
	}

	default boolean minecartChain$canAcceptLink() {
		return this.minecartChain$getFirstLink().isEmpty() || this.minecartChain$getSecondLink().isEmpty();
	}

	default int minecartChain$linkCount() {
		int count = 0;
		if (this.minecartChain$getFirstLink().isPresent()) {
			count++;
		}
		if (this.minecartChain$getSecondLink().isPresent()) {
			count++;
		}
		return count;
	}
}
