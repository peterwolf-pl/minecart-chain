package com.piotrek.minecartchain.mixin.client;

import com.piotrek.minecartchain.client.MinecartChainRenderState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MinecartRenderState.class)
public abstract class MinecartRenderStateMixin implements MinecartChainRenderState {
	@Unique
	private final BlockModelRenderState minecartChain$leverModel = new BlockModelRenderState();
	@Unique
	private final BlockModelRenderState minecartChain$throttleLeverModel = new BlockModelRenderState();
	@Unique
	private final BlockModelRenderState minecartChain$directionLeverModel = new BlockModelRenderState();
	@Unique
	private final BlockModelRenderState minecartChain$brakeBaseModel = new BlockModelRenderState();
	@Unique
	private final BlockModelRenderState minecartChain$directionBaseModel = new BlockModelRenderState();
	@Unique
	private final BlockModelRenderState minecartChain$throttleBaseModel = new BlockModelRenderState();
	@Unique
	private final BlockModelRenderState minecartChain$chimneyModel = new BlockModelRenderState();
	@Unique
	private final BlockModelRenderState minecartChain$chainXModel = new BlockModelRenderState();
	@Unique
	private final BlockModelRenderState minecartChain$chainZModel = new BlockModelRenderState();
	@Unique
	private final List<Vec3> minecartChain$chainOffsets = new ArrayList<>(2);
	@Unique
	private boolean minecartChain$hasLever;
	@Unique
	private boolean minecartChain$fullThrottle;
	@Unique
	private boolean minecartChain$reversed;
	@Unique
	private boolean minecartChain$hasLocomotiveYaw;
	@Unique
	private float minecartChain$locomotiveYaw;

	@Override
	public BlockModelRenderState minecartChain$leverModel() {
		return this.minecartChain$leverModel;
	}

	@Override
	public BlockModelRenderState minecartChain$throttleLeverModel() {
		return this.minecartChain$throttleLeverModel;
	}

	@Override
	public BlockModelRenderState minecartChain$directionLeverModel() {
		return this.minecartChain$directionLeverModel;
	}

	@Override
	public BlockModelRenderState minecartChain$brakeBaseModel() {
		return this.minecartChain$brakeBaseModel;
	}

	@Override
	public BlockModelRenderState minecartChain$directionBaseModel() {
		return this.minecartChain$directionBaseModel;
	}

	@Override
	public BlockModelRenderState minecartChain$throttleBaseModel() {
		return this.minecartChain$throttleBaseModel;
	}

	@Override
	public BlockModelRenderState minecartChain$chimneyModel() {
		return this.minecartChain$chimneyModel;
	}

	@Override
	public BlockModelRenderState minecartChain$chainXModel() {
		return this.minecartChain$chainXModel;
	}

	@Override
	public BlockModelRenderState minecartChain$chainZModel() {
		return this.minecartChain$chainZModel;
	}

	@Override
	public boolean minecartChain$hasLever() {
		return this.minecartChain$hasLever;
	}

	@Override
	public void minecartChain$setHasLever(final boolean hasLever) {
		this.minecartChain$hasLever = hasLever;
	}

	@Override
	public boolean minecartChain$isFullThrottle() {
		return this.minecartChain$fullThrottle;
	}

	@Override
	public void minecartChain$setFullThrottle(final boolean fullThrottle) {
		this.minecartChain$fullThrottle = fullThrottle;
	}

	@Override
	public boolean minecartChain$isReversed() {
		return this.minecartChain$reversed;
	}

	@Override
	public void minecartChain$setReversed(final boolean reversed) {
		this.minecartChain$reversed = reversed;
	}

	@Override
	public boolean minecartChain$hasLocomotiveYaw() {
		return this.minecartChain$hasLocomotiveYaw;
	}

	@Override
	public void minecartChain$setHasLocomotiveYaw(final boolean hasLocomotiveYaw) {
		this.minecartChain$hasLocomotiveYaw = hasLocomotiveYaw;
	}

	@Override
	public float minecartChain$getLocomotiveYaw() {
		return this.minecartChain$locomotiveYaw;
	}

	@Override
	public void minecartChain$setLocomotiveYaw(final float yaw) {
		this.minecartChain$locomotiveYaw = yaw;
	}

	@Override
	public List<Vec3> minecartChain$chainOffsets() {
		return this.minecartChain$chainOffsets;
	}

	@Override
	public void minecartChain$clearChainOffsets() {
		this.minecartChain$chainOffsets.clear();
	}
}
