package com.piotrek.minecartchain.client;

import java.util.List;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.world.phys.Vec3;

public interface MinecartChainRenderState {
	BlockModelRenderState minecartChain$leverModel();

	BlockModelRenderState minecartChain$throttleLeverModel();

	BlockModelRenderState minecartChain$directionLeverModel();

	BlockModelRenderState minecartChain$brakeBaseModel();

	BlockModelRenderState minecartChain$directionBaseModel();

	BlockModelRenderState minecartChain$throttleBaseModel();

	BlockModelRenderState minecartChain$chimneyModel();

	BlockModelRenderState minecartChain$chainXModel();

	BlockModelRenderState minecartChain$chainZModel();

	boolean minecartChain$hasLever();

	void minecartChain$setHasLever(boolean hasLever);

	boolean minecartChain$isFullThrottle();

	void minecartChain$setFullThrottle(boolean fullThrottle);

	boolean minecartChain$isReversed();

	void minecartChain$setReversed(boolean reversed);

	boolean minecartChain$hasLocomotiveYaw();

	void minecartChain$setHasLocomotiveYaw(boolean hasLocomotiveYaw);

	float minecartChain$getLocomotiveYaw();

	void minecartChain$setLocomotiveYaw(float yaw);

	List<Vec3> minecartChain$chainOffsets();

	void minecartChain$clearChainOffsets();
}
