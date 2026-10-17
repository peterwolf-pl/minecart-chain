package com.piotrek.minecartchain.client;

import java.util.List;
import net.minecraft.world.phys.Vec3;

public interface MinecartChainRenderState {
	boolean minecartChain$hasLever();

	void minecartChain$setHasLever(boolean hasLever);

	boolean minecartChain$isRadioLocomotive();

	void minecartChain$setRadioLocomotive(boolean radioLocomotive);

	boolean minecartChain$isEngineActive();

	void minecartChain$setEngineActive(boolean engineActive);

	boolean minecartChain$isFullThrottle();

	void minecartChain$setFullThrottle(boolean fullThrottle);

	boolean minecartChain$isReversed();

	void minecartChain$setReversed(boolean reversed);

	boolean minecartChain$hasLocomotiveYaw();

	void minecartChain$setHasLocomotiveYaw(boolean hasLocomotiveYaw);

	float minecartChain$getLocomotiveYaw();

	void minecartChain$setLocomotiveYaw(float locomotiveYaw);

	boolean minecartChain$isMountedTrack();

	void minecartChain$setMountedTrack(boolean mountedTrack);

	boolean minecartChain$shouldFlipMountedTrackControls();

	void minecartChain$setFlipMountedTrackControls(boolean flipMountedTrackControls);

	List<Vec3> minecartChain$chainOffsets();

	void minecartChain$clearChainOffsets();

	void minecartChain$addChainOffset(Vec3 offset);
}
