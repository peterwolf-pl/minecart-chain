package com.piotrek.minecartchain.mixin;

import com.piotrek.minecartchain.MinecartChainAccess;
import com.piotrek.minecartchain.MinecartTrainLogic;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartMixin implements MinecartChainAccess {
	@Unique
	private static final String EMPTY_LINK = "";
	@Unique
	private static final String LINK_A_KEY = "MinecartChainLinkA";
	@Unique
	private static final String LINK_B_KEY = "MinecartChainLinkB";
	@Unique
	private static final String ENGINE_LEVER_KEY = "MinecartChainEngineLever";
	@Unique
	private static final String ENGINE_ACTIVE_KEY = "MinecartChainEngineActive";
	@Unique
	private static final String FULL_THROTTLE_KEY = "MinecartChainFullThrottle";
	@Unique
	private static final String REVERSED_KEY = "MinecartChainReversed";
	@Unique
	private static final String LOCOMOTIVE_YAW_SET_KEY = "MinecartChainLocomotiveYawSet";
	@Unique
	private static final String LOCOMOTIVE_YAW_KEY = "MinecartChainLocomotiveYaw";
	@Unique
	private static final String WATER_TICKS_KEY = "MinecartChainWaterTicks";
	@Unique
	private static final EntityDataAccessor<String> LINK_A = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.STRING);
	@Unique
	private static final EntityDataAccessor<String> LINK_B = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.STRING);
	@Unique
	private static final EntityDataAccessor<Boolean> ENGINE_LEVER = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BOOLEAN);
	@Unique
	private static final EntityDataAccessor<Boolean> ENGINE_ACTIVE = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BOOLEAN);
	@Unique
	private static final EntityDataAccessor<Boolean> FULL_THROTTLE = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BOOLEAN);
	@Unique
	private static final EntityDataAccessor<Boolean> REVERSED = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BOOLEAN);
	@Unique
	private static final EntityDataAccessor<Boolean> LOCOMOTIVE_YAW_SET = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BOOLEAN);
	@Unique
	private static final EntityDataAccessor<Float> LOCOMOTIVE_YAW = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.FLOAT);
	@Unique
	private static final EntityDataAccessor<Integer> WATER_TICKS = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void minecartChain$defineSynchedData(final SynchedEntityData.Builder builder, final CallbackInfo ci) {
		builder.define(LINK_A, EMPTY_LINK);
		builder.define(LINK_B, EMPTY_LINK);
		builder.define(ENGINE_LEVER, false);
		builder.define(ENGINE_ACTIVE, false);
		builder.define(FULL_THROTTLE, false);
		builder.define(REVERSED, false);
		builder.define(LOCOMOTIVE_YAW_SET, false);
		builder.define(LOCOMOTIVE_YAW, 0.0F);
		builder.define(WATER_TICKS, 0);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void minecartChain$tick(final CallbackInfo ci) {
		MinecartTrainLogic.tickLinks((AbstractMinecart) (Object) this);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void minecartChain$save(final ValueOutput output, final CallbackInfo ci) {
		String firstLink = this.minecartChain$getLinkString(LINK_A);
		String secondLink = this.minecartChain$getLinkString(LINK_B);
		if (!firstLink.isEmpty()) {
			output.putString(LINK_A_KEY, firstLink);
		}
		if (!secondLink.isEmpty()) {
			output.putString(LINK_B_KEY, secondLink);
		}
		if (this.minecartChain$hasEngineLever()) {
			output.putBoolean(ENGINE_LEVER_KEY, true);
			output.putBoolean(ENGINE_ACTIVE_KEY, this.minecartChain$isEngineActive());
			output.putBoolean(FULL_THROTTLE_KEY, this.minecartChain$isFullThrottle());
			output.putBoolean(REVERSED_KEY, this.minecartChain$isReversed());
			output.putBoolean(LOCOMOTIVE_YAW_SET_KEY, this.minecartChain$hasLocomotiveYaw());
			output.putFloat(LOCOMOTIVE_YAW_KEY, this.minecartChain$getLocomotiveYaw());
		}
		if (this.minecartChain$getWaterTicks() > 0) {
			output.putInt(WATER_TICKS_KEY, this.minecartChain$getWaterTicks());
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void minecartChain$load(final ValueInput input, final CallbackInfo ci) {
		this.minecartChain$setLinkString(LINK_A, input.getStringOr(LINK_A_KEY, EMPTY_LINK));
		this.minecartChain$setLinkString(LINK_B, input.getStringOr(LINK_B_KEY, EMPTY_LINK));
		this.minecartChain$setEngineLever(input.getBooleanOr(ENGINE_LEVER_KEY, false));
		this.minecartChain$setEngineActive(input.getBooleanOr(ENGINE_ACTIVE_KEY, false));
		this.minecartChain$setFullThrottle(input.getBooleanOr(FULL_THROTTLE_KEY, false));
		this.minecartChain$setReversed(input.getBooleanOr(REVERSED_KEY, false));
		this.minecartChain$setHasLocomotiveYaw(input.getBooleanOr(LOCOMOTIVE_YAW_SET_KEY, false));
		this.minecartChain$setLocomotiveYaw(input.getFloatOr(LOCOMOTIVE_YAW_KEY, 0.0F));
		this.minecartChain$setWaterTicks(input.getIntOr(WATER_TICKS_KEY, 0));
	}

	@Override
	public Optional<UUID> minecartChain$getFirstLink() {
		return this.minecartChain$parseLink(this.minecartChain$getLinkString(LINK_A));
	}

	@Override
	public Optional<UUID> minecartChain$getSecondLink() {
		return this.minecartChain$parseLink(this.minecartChain$getLinkString(LINK_B));
	}

	@Override
	public boolean minecartChain$addLink(final UUID uuid) {
		if (this.minecartChain$hasLink(uuid)) {
			return true;
		}

		if (this.minecartChain$getFirstLink().isEmpty()) {
			this.minecartChain$setLinkString(LINK_A, uuid.toString());
			return true;
		}

		if (this.minecartChain$getSecondLink().isEmpty()) {
			this.minecartChain$setLinkString(LINK_B, uuid.toString());
			return true;
		}

		return false;
	}

	@Override
	public void minecartChain$removeLink(final UUID uuid) {
		this.minecartChain$getFirstLink()
			.filter(uuid::equals)
			.ifPresent(ignored -> this.minecartChain$setLinkString(LINK_A, EMPTY_LINK));
		this.minecartChain$getSecondLink()
			.filter(uuid::equals)
			.ifPresent(ignored -> this.minecartChain$setLinkString(LINK_B, EMPTY_LINK));
	}

	@Override
	public void minecartChain$clearLinks() {
		this.minecartChain$setLinkString(LINK_A, EMPTY_LINK);
		this.minecartChain$setLinkString(LINK_B, EMPTY_LINK);
	}

	@Override
	public boolean minecartChain$hasEngineLever() {
		return this.minecartChain$getEntityData().get(ENGINE_LEVER);
	}

	@Override
	public void minecartChain$setEngineLever(final boolean mounted) {
		this.minecartChain$getEntityData().set(ENGINE_LEVER, mounted);
		if (!mounted) {
			this.minecartChain$setEngineActive(false);
			this.minecartChain$setFullThrottle(false);
			this.minecartChain$setReversed(false);
			this.minecartChain$setHasLocomotiveYaw(false);
		}
	}

	@Override
	public boolean minecartChain$isEngineActive() {
		return this.minecartChain$getEntityData().get(ENGINE_ACTIVE);
	}

	@Override
	public void minecartChain$setEngineActive(final boolean active) {
		this.minecartChain$getEntityData().set(ENGINE_ACTIVE, active);
	}

	@Override
	public boolean minecartChain$isFullThrottle() {
		return this.minecartChain$getEntityData().get(FULL_THROTTLE);
	}

	@Override
	public void minecartChain$setFullThrottle(final boolean fullThrottle) {
		this.minecartChain$getEntityData().set(FULL_THROTTLE, fullThrottle);
	}

	@Override
	public boolean minecartChain$isReversed() {
		return this.minecartChain$getEntityData().get(REVERSED);
	}

	@Override
	public void minecartChain$setReversed(final boolean reversed) {
		this.minecartChain$getEntityData().set(REVERSED, reversed);
	}

	@Override
	public boolean minecartChain$hasLocomotiveYaw() {
		return this.minecartChain$getEntityData().get(LOCOMOTIVE_YAW_SET);
	}

	@Override
	public void minecartChain$setHasLocomotiveYaw(final boolean hasLocomotiveYaw) {
		this.minecartChain$getEntityData().set(LOCOMOTIVE_YAW_SET, hasLocomotiveYaw);
	}

	@Override
	public float minecartChain$getLocomotiveYaw() {
		return this.minecartChain$getEntityData().get(LOCOMOTIVE_YAW);
	}

	@Override
	public void minecartChain$setLocomotiveYaw(final float yaw) {
		this.minecartChain$getEntityData().set(LOCOMOTIVE_YAW, Mth.wrapDegrees(yaw));
	}

	@Override
	public int minecartChain$getWaterTicks() {
		return this.minecartChain$getEntityData().get(WATER_TICKS);
	}

	@Override
	public void minecartChain$setWaterTicks(final int waterTicks) {
		this.minecartChain$getEntityData().set(WATER_TICKS, Math.max(0, waterTicks));
	}

	public float getPickRadius() {
		return (Object) this instanceof MinecartFurnace && this.minecartChain$hasEngineLever() ? 0.95F : 0.0F;
	}

	@Unique
	private Optional<UUID> minecartChain$parseLink(final String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}

		try {
			return Optional.of(UUID.fromString(value));
		} catch (IllegalArgumentException ignored) {
			return Optional.empty();
		}
	}

	@Unique
	private String minecartChain$getLinkString(final EntityDataAccessor<String> accessor) {
		return this.minecartChain$getEntityData().get(accessor);
	}

	@Unique
	private void minecartChain$setLinkString(final EntityDataAccessor<String> accessor, final String value) {
		this.minecartChain$getEntityData().set(accessor, value == null ? EMPTY_LINK : value);
	}

	@Unique
	private SynchedEntityData minecartChain$getEntityData() {
		return ((AbstractMinecart) (Object) this).getEntityData();
	}
}
