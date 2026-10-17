package com.piotrek.minecartchain.mixin;

import com.piotrek.minecartchain.CoalTenderEntity;
import com.piotrek.minecartchain.MinecartChainAccess;
import com.piotrek.minecartchain.MinecartLinkHandler;
import com.piotrek.minecartchain.MinecartTrainLogic;
import com.piotrek.minecartchain.SteamLocomotiveEntity;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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
	private static final String RADIO_LOCOMOTIVE_KEY = "MinecartChainRadioLocomotive";
	@Unique
	private static final String THROTTLE_CHANNEL_KEY = "MinecartChainThrottleChannel";
	@Unique
	private static final String REVERSE_CHANNEL_KEY = "MinecartChainReverseChannel";
	@Unique
	private static final String ARMED_KEY = "MinecartChainArmed";
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
	@Unique
	private static final EntityDataAccessor<Boolean> RADIO_LOCOMOTIVE = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BOOLEAN);
	@Unique
	private static final EntityDataAccessor<Integer> THROTTLE_CHANNEL = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
	@Unique
	private static final EntityDataAccessor<Integer> REVERSE_CHANNEL = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
	@Unique
	private static final EntityDataAccessor<Integer> RECEIVED_THROTTLE_POWER = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
	@Unique
	private static final EntityDataAccessor<Integer> RECEIVED_REVERSE_POWER = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
	@Unique
	private static final EntityDataAccessor<Boolean> REVERSE_PENDING = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BOOLEAN);
	@Unique
	private static final EntityDataAccessor<Boolean> ARMED = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BOOLEAN);

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
		builder.define(RADIO_LOCOMOTIVE, false);
		builder.define(THROTTLE_CHANNEL, 0);
		builder.define(REVERSE_CHANNEL, 1);
		builder.define(RECEIVED_THROTTLE_POWER, 0);
		builder.define(RECEIVED_REVERSE_POWER, 0);
		builder.define(REVERSE_PENDING, false);
		builder.define(ARMED, true);
	}

	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/minecart/MinecartBehavior;tick()V"))
	private void minecartChain$hingedTenderUsesLocomotiveTrack(final MinecartBehavior behavior) {
		AbstractMinecart cart = (AbstractMinecart) (Object) this;
		// The two-block tender is a kinematic wagon controlled by the
		// locomotive's 3D track path, not an independently moving minecart.
		// Still run the surrounding vanilla tick for interpolation and fluids.
		if (cart instanceof CoalTenderEntity tender && tender.hasPermanentLocomotive()
			&& cart.level() instanceof ServerLevel) {
			return;
		}
		behavior.tick();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void minecartChain$tick(final CallbackInfo ci) {
		MinecartTrainLogic.tickLinks((AbstractMinecart) (Object) this);
	}

	@Inject(method = "push", at = @At("HEAD"), cancellable = true)
	private void minecartChain$preventPushBetweenLinkedOrPermanentCarts(final Entity entity, final CallbackInfo ci) {
		if (entity instanceof AbstractMinecart other) {
			AbstractMinecart self = (AbstractMinecart) (Object) this;
			if (self.level() instanceof ServerLevel serverLevel) {
				if (MinecartLinkHandler.isPermanentLink(serverLevel, self, other.getUUID())
					|| MinecartLinkHandler.isPermanentLink(serverLevel, other, self.getUUID())
					|| ((MinecartChainAccess) self).minecartChain$hasLink(other.getUUID())
					|| ((MinecartChainAccess) other).minecartChain$hasLink(self.getUUID())) {
					ci.cancel();
				}
			}
		}
	}

	/**
	 * Soft-implements {@link Entity#onRemoval} for minecarts only.
	 * {@code setRemoved} is final on {@link Entity} and is not present on the
	 * {@link AbstractMinecart} class file, so an {@code @Inject} into
	 * {@code setRemoved} crashes mixin apply. {@code Entity#setRemoved} still
	 * calls {@code onRemoval}, which is empty in vanilla.
	 *
	 * <p>When a cart is destroyed (not merely unloaded), drop partner links so
	 * remaining carts do not keep dead UUID slots. Chunk unload / dimension
	 * change keeps NBT links intact via {@link Entity.RemovalReason#shouldDestroy()}.
	 */
	public void onRemoval(final Entity.RemovalReason reason) {
		if (!reason.shouldDestroy()) {
			return;
		}

		AbstractMinecart self = (AbstractMinecart) (Object) this;
		if (self.level().isClientSide() || !(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		MinecartChainAccess data = this;
		int droppedChains = 0;
		if (this.minecartChain$getFirstLink().isPresent()) {
			UUID linkId = this.minecartChain$getFirstLink().get();
			if (!MinecartLinkHandler.isPermanentLink(serverLevel, self, linkId)) {
				droppedChains++;
			}
			this.minecartChain$notifyPartnerUnlink(serverLevel, self, linkId);
		}
		if (this.minecartChain$getSecondLink().isPresent()) {
			UUID linkId = this.minecartChain$getSecondLink().get();
			if (!MinecartLinkHandler.isPermanentLink(serverLevel, self, linkId)) {
				droppedChains++;
			}
			this.minecartChain$notifyPartnerUnlink(serverLevel, self, linkId);
		}
		data.minecartChain$clearLinks();
		if (droppedChains > 0) {
			ItemEntity chain = new ItemEntity(
				serverLevel,
				self.getX(),
				self.getY() + 0.25D,
				self.getZ(),
				new ItemStack(Items.IRON_CHAIN, droppedChains)
			);
			chain.setDefaultPickUpDelay();
			serverLevel.addFreshEntity(chain);
			serverLevel.playSound(
				null,
				self.getX(),
				self.getY(),
				self.getZ(),
				SoundEvents.CHAIN_BREAK,
				SoundSource.BLOCKS,
				1.0F,
				0.9F
			);
		}
	}

	@Unique
	private void minecartChain$notifyPartnerUnlink(final ServerLevel level, final AbstractMinecart self, final UUID linkId) {
		Entity linkedEntity = level.getEntity(linkId);
		if (linkedEntity instanceof AbstractMinecart linkedMinecart) {
			((MinecartChainAccess) linkedMinecart).minecartChain$removeLink(self.getUUID());
		}
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
		if (this.minecartChain$isRadioLocomotive()) {
			output.putBoolean(RADIO_LOCOMOTIVE_KEY, true);
			output.putInt(THROTTLE_CHANNEL_KEY, this.minecartChain$getThrottleChannel());
			output.putInt(REVERSE_CHANNEL_KEY, this.minecartChain$getReverseChannel());
			output.putBoolean(ARMED_KEY, this.minecartChain$isArmed());
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
		this.minecartChain$setRadioLocomotive(input.getBooleanOr(RADIO_LOCOMOTIVE_KEY, false));
		this.minecartChain$setThrottleChannel(input.getIntOr(THROTTLE_CHANNEL_KEY, 0));
		this.minecartChain$setReverseChannel(input.getIntOr(REVERSE_CHANNEL_KEY, 1));
		this.minecartChain$setArmed(input.getBooleanOr(ARMED_KEY, true));
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
		AbstractMinecart self = (AbstractMinecart) (Object) this;
		if (self.level() instanceof ServerLevel serverLevel && MinecartLinkHandler.isPermanentLink(serverLevel, self, uuid)) {
			return;
		}
		this.minecartChain$getFirstLink()
			.filter(uuid::equals)
			.ifPresent(ignored -> this.minecartChain$setLinkString(LINK_A, EMPTY_LINK));
		this.minecartChain$getSecondLink()
			.filter(uuid::equals)
			.ifPresent(ignored -> this.minecartChain$setLinkString(LINK_B, EMPTY_LINK));
	}

	@Override
	public void minecartChain$clearLinks() {
		AbstractMinecart self = (AbstractMinecart) (Object) this;
		if (self.level() instanceof ServerLevel serverLevel) {
			UUID permLink = null;
			if (self instanceof SteamLocomotiveEntity loco && loco.hasPermanentTender()) {
				permLink = loco.getPermanentTenderId().orElse(null);
			} else if (self instanceof CoalTenderEntity tender && tender.hasPermanentLocomotive()) {
				permLink = tender.getPermanentLocomotiveId().orElse(null);
			}
			if (permLink != null) {
				if (!this.minecartChain$getFirstLink().filter(permLink::equals).isPresent()) {
					this.minecartChain$setLinkString(LINK_A, EMPTY_LINK);
				}
				if (!this.minecartChain$getSecondLink().filter(permLink::equals).isPresent()) {
					this.minecartChain$setLinkString(LINK_B, EMPTY_LINK);
				}
				return;
			}
		}
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

	@Override
	public boolean minecartChain$isRadioLocomotive() {
		return this.minecartChain$getEntityData().get(RADIO_LOCOMOTIVE);
	}

	@Override
	public void minecartChain$setRadioLocomotive(final boolean radio) {
		this.minecartChain$getEntityData().set(RADIO_LOCOMOTIVE, radio);
		if (radio) {
			this.minecartChain$setEngineLever(true);
		}
	}

	@Override
	public int minecartChain$getThrottleChannel() {
		return this.minecartChain$getEntityData().get(THROTTLE_CHANNEL);
	}

	@Override
	public void minecartChain$setThrottleChannel(final int channel) {
		this.minecartChain$getEntityData().set(THROTTLE_CHANNEL, channel & 0xFF);
	}

	@Override
	public int minecartChain$getReverseChannel() {
		return this.minecartChain$getEntityData().get(REVERSE_CHANNEL);
	}

	@Override
	public void minecartChain$setReverseChannel(final int channel) {
		this.minecartChain$getEntityData().set(REVERSE_CHANNEL, channel & 0xFF);
	}

	@Override
	public int minecartChain$getReceivedThrottlePower() {
		return this.minecartChain$getEntityData().get(RECEIVED_THROTTLE_POWER);
	}

	@Override
	public void minecartChain$setReceivedThrottlePower(final int power) {
		this.minecartChain$getEntityData().set(RECEIVED_THROTTLE_POWER, Math.clamp(power, 0, 15));
	}

	@Override
	public int minecartChain$getReceivedReversePower() {
		return this.minecartChain$getEntityData().get(RECEIVED_REVERSE_POWER);
	}

	@Override
	public void minecartChain$setReceivedReversePower(final int power) {
		this.minecartChain$getEntityData().set(RECEIVED_REVERSE_POWER, Math.clamp(power, 0, 15));
	}

	@Override
	public boolean minecartChain$isReversePending() {
		return this.minecartChain$getEntityData().get(REVERSE_PENDING);
	}

	@Override
	public void minecartChain$setReversePending(final boolean pending) {
		this.minecartChain$getEntityData().set(REVERSE_PENDING, pending);
	}

	@Override
	public boolean minecartChain$isArmed() {
		return this.minecartChain$getEntityData().get(ARMED);
	}

	@Override
	public void minecartChain$setArmed(final boolean armed) {
		this.minecartChain$getEntityData().set(ARMED, armed);
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
