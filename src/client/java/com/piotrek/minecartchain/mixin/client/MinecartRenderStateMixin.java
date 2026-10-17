package com.piotrek.minecartchain.mixin.client;

import com.piotrek.minecartchain.client.MinecartChainRenderState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MinecartRenderState.class)
public abstract class MinecartRenderStateMixin implements MinecartChainRenderState {
	@Unique
	private final List<Vec3> minecartChain$chainOffsets = new ArrayList<>(2);
	@Unique
	private boolean minecartChain$hasLever;
	@Unique
	private boolean minecartChain$radioLocomotive;
	@Unique
	private boolean minecartChain$engineActive;
	@Unique
	private boolean minecartChain$fullThrottle;
	@Unique
	private boolean minecartChain$reversed;
	@Unique
	private boolean minecartChain$hasLocomotiveYaw;
	@Unique
	private float minecartChain$locomotiveYaw;
	@Unique
	private boolean minecartChain$mountedTrack;
	@Unique
	private boolean minecartChain$flipMountedTrackControls;

	@Override
	public boolean minecartChain$hasLever() {
		return this.minecartChain$hasLever;
	}

	@Override
	public void minecartChain$setHasLever(final boolean hasLever) {
		this.minecartChain$hasLever = hasLever;
	}

	@Override
	public boolean minecartChain$isRadioLocomotive() {
		return this.minecartChain$radioLocomotive;
	}

	@Override
	public void minecartChain$setRadioLocomotive(final boolean radioLocomotive) {
		this.minecartChain$radioLocomotive = radioLocomotive;
	}

	@Override
	public boolean minecartChain$isEngineActive() {
		return this.minecartChain$engineActive;
	}

	@Override
	public void minecartChain$setEngineActive(final boolean engineActive) {
		this.minecartChain$engineActive = engineActive;
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
	public void minecartChain$setLocomotiveYaw(final float locomotiveYaw) {
		this.minecartChain$locomotiveYaw = locomotiveYaw;
	}

	@Override
	public boolean minecartChain$isMountedTrack() {
		return this.minecartChain$mountedTrack;
	}

	@Override
	public void minecartChain$setMountedTrack(final boolean mountedTrack) {
		this.minecartChain$mountedTrack = mountedTrack;
	}

	@Override
	public boolean minecartChain$shouldFlipMountedTrackControls() {
		return this.minecartChain$flipMountedTrackControls;
	}

	@Override
	public void minecartChain$setFlipMountedTrackControls(final boolean flipMountedTrackControls) {
		this.minecartChain$flipMountedTrackControls = flipMountedTrackControls;
	}

	@Override
	public List<Vec3> minecartChain$chainOffsets() {
		return this.minecartChain$chainOffsets;
	}

	@Override
	public void minecartChain$clearChainOffsets() {
		this.minecartChain$chainOffsets.clear();
	}

	@Override
	public void minecartChain$addChainOffset(final Vec3 offset) {
		this.minecartChain$chainOffsets.add(offset);
	}
}
