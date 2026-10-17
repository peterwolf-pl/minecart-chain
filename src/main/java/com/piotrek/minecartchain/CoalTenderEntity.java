package com.piotrek.minecartchain;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class CoalTenderEntity extends AbstractMinecartContainer {
	// Explicitly synced: a free-standing tender uses vanilla rotation, while a
	// hinged tender renders its two-block body along the locomotive's 3D path.
	private static final EntityDataAccessor<Float> HINGE_PITCH =
		SynchedEntityData.defineId(CoalTenderEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> HINGED =
		SynchedEntityData.defineId(CoalTenderEntity.class, EntityDataSerializers.BOOLEAN);
	private NonNullList<ItemStack> itemStacks = NonNullList.withSize(27, ItemStack.EMPTY);

	// Wheel rotation animation
	private float wheelRotation = 0.0F;
	private float prevWheelRotation = 0.0F;

	// Permanent locomotive tracking (locomotive-with-tender pair)
	private Optional<UUID> permanentLocomotiveId = Optional.empty();

	public CoalTenderEntity(final EntityType<? extends CoalTenderEntity> entityType, final Level level) {
		super(entityType, level);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(HINGE_PITCH, 0.0F);
		builder.define(HINGED, false);
	}

	public float getHingePitch() {
		return this.entityData.get(HINGE_PITCH);
	}

	public boolean isHinged() {
		return this.entityData.get(HINGED);
	}

	public void setHingePitch(final float pitch) {
		this.entityData.set(HINGE_PITCH, pitch);
		this.setXRot(pitch);
	}

	// --- Wheel rotation ---

	public float getWheelRotation(final float partialTick) {
		return Mth.lerp(partialTick, this.prevWheelRotation, this.wheelRotation);
	}

	// --- Permanent locomotive tracking ---

	public Optional<UUID> getPermanentLocomotiveId() {
		return this.permanentLocomotiveId;
	}

	public boolean hasPermanentLocomotive() {
		return this.permanentLocomotiveId.isPresent();
	}

	public void setPermanentLocomotive(final UUID id) {
		this.permanentLocomotiveId = Optional.of(id);
		this.entityData.set(HINGED, true);
	}

	// --- Container ---

	@Override
	public int getContainerSize() {
		return 27;
	}

	@Override
	public NonNullList<ItemStack> getItemStacks() {
		return this.itemStacks;
	}

	@Override
	public void clearItemStacks() {
		this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
	}

	@Override
	protected Item getDropItem() {
		// If part of a locomotive-with-tender pair, no separate drop – handled by locomotive
		if (this.hasPermanentLocomotive()) {
			return Items.AIR;
		}
		return MinecartChainMod.COAL_TENDER_ITEM;
	}

	@Override
	public ItemStack getPickResult() {
		if (this.hasPermanentLocomotive()) {
			return new ItemStack(MinecartChainMod.STEAM_LOCOMOTIVE_WITH_TENDER_ITEM);
		}
		return new ItemStack(MinecartChainMod.COAL_TENDER_ITEM);
	}

	@Override
	public void destroy(final ServerLevel level, final DamageSource damageSource) {
		if (this.hasPermanentLocomotive()) {
			// Find the parent locomotive and destroy it too (it will drop the combined item)
			UUID locoId = this.permanentLocomotiveId.get();
			Entity locoEntity = level.getEntity(locoId);
			if (locoEntity instanceof SteamLocomotiveEntity locomotive && !locomotive.isRemoved()) {
				this.chestVehicleDestroyed(damageSource, level, this);
				this.discard();
				locomotive.destroy(level, damageSource);
				return;
			}
		}
		super.destroy(level, damageSource);
		this.chestVehicleDestroyed(damageSource, level, this);
	}

	@Override
	public void tick() {
		this.prevWheelRotation = this.wheelRotation;

		if (this.hasPermanentLocomotive() && this.level() instanceof ServerLevel serverLevel) {
			UUID locoId = this.permanentLocomotiveId.orElse(null);
			Entity loco = locoId != null ? serverLevel.getEntity(locoId) : null;
			if (loco instanceof SteamLocomotiveEntity locomotive && !locomotive.isRemoved()) {
				// Reinforce mutual permanent links
				MinecartChainAccess tenderData = (MinecartChainAccess) this;
				if (!tenderData.minecartChain$hasLink(locoId)) {
					tenderData.minecartChain$addLink(locoId);
				}
				MinecartChainAccess locoData = (MinecartChainAccess) locomotive;
				if (!locoData.minecartChain$hasLink(this.getUUID())) {
					locoData.minecartChain$addLink(this.getUUID());
				}
				if (!locomotive.hasPermanentTender() || !locomotive.getPermanentTenderId().filter(id -> id.equals(this.getUUID())).isPresent()) {
					locomotive.setPermanentTender(this.getUUID());
				}
			}

			// Keep the Splinecart follower: it owns track movement and client interpolation.

		}

		// Retain vanilla's interpolation bookkeeping, but never let a permanent
		// tender run on its own momentum between hinge updates. Otherwise each
		// tender tick advances it in addition to the locomotive, producing the
		// visible elastic gap (and jerks in replay recordings).
		if (this.hasPermanentLocomotive()) {
			this.setDeltaMovement(Vec3.ZERO);
		}
		super.tick();

		// Entity iteration normally ticks the locomotive before its tender. The
		// tender's vanilla tick would therefore move it once more after the
		// locomotive had already corrected the consist, producing a visible
		// one-tick delay. Apply the authoritative breadcrumb position after this
		// tick as well, so both normal rendering and Flashback record the final,
		// smoothly interpolated position rather than the intermediate one.
		if (this.hasPermanentLocomotive() && !MountedTrackCompat.isMounted(this)
			&& this.level() instanceof ServerLevel serverLevel) {
			UUID locomotiveId = this.permanentLocomotiveId.orElse(null);
			Entity entity = locomotiveId != null ? serverLevel.getEntity(locomotiveId) : null;
			if (entity instanceof SteamLocomotiveEntity parent && !parent.isRemoved()) {
				parent.positionPermanentTender(serverLevel);
			}
		}

		// Permanent consists use the locomotive as the single source of wheel
		// direction. This is independent of cardinal direction and prevents tiny
		// tender position corrections from spinning wheels after the train stops.
		SteamLocomotiveEntity locomotive = MinecartTrainLogic.connectedSteamLocomotive(this);
		Vec3 movement = locomotive != null
			? MinecartTrainLogic.horizontalMovement(locomotive)
			: MinecartTrainLogic.horizontalMovement(this);
		double hSpeed = movement.length();
		if (locomotive == null && hSpeed <= 0.0015D) {
			double dX = this.getX() - this.xOld;
			double dZ = this.getZ() - this.zOld;
			double moved = Math.sqrt(dX * dX + dZ * dZ);
			if (moved > 0.003D) {
				hSpeed = moved;
				movement = new Vec3(dX, 0.0D, dZ);
			}
		}
		if (hSpeed > 0.0015D) {
			float sign;
			if (locomotive != null) {
				sign = locomotive.isReverser() ? 1.0F : -1.0F;
			} else {
				double yawRad = Math.toRadians(this.getYRot());
				Vec3 facing = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
				sign = movement.dot(facing) >= 0.0D ? -1.0F : 1.0F;
			}
			this.wheelRotation += (float) (sign * (hSpeed / 0.1875D));
		}
	}

	@Override
	public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory) {
		return ChestMenu.threeRows(containerId, inventory, this);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.minecart_chain.coal_tender");
	}

	@Override
	public InteractionResult interactAt(final Player player, final Vec3 location, final InteractionHand hand) {
		return this.interact(player, hand);
	}

	@Override
	public InteractionResult interact(final Player player, final InteractionHand hand) {
		InteractionResult result = super.interact(player, hand);
		if (result.consumesAction()) {
			return result;
		}
		if (!player.isSecondaryUseActive()) {
			player.openMenu(this);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		this.permanentLocomotiveId.ifPresent(id -> output.putString("PermanentLocomotive", id.toString()));
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		String idStr = input.getStringOr("PermanentLocomotive", "");
		if (!idStr.isBlank()) {
			try {
				this.setPermanentLocomotive(UUID.fromString(idStr));
			} catch (IllegalArgumentException ignored) {
				this.permanentLocomotiveId = Optional.empty();
			}
		}
	}
}
