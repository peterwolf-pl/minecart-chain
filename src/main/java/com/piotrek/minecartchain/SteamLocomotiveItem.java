package com.piotrek.minecartchain;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class SteamLocomotiveItem extends Item {
	private final EntityType<? extends SteamLocomotiveEntity> entityType;

	public SteamLocomotiveItem(final EntityType<? extends SteamLocomotiveEntity> entityType, final Item.Properties properties) {
		super(properties);
		this.entityType = entityType;
	}

	public SteamLocomotiveItem(final Item.Properties properties) {
		this(null, properties);
	}

	@Override
	@SuppressWarnings("unchecked")
	public InteractionResult useOn(final UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		if (!state.is(BlockTags.RAILS)) {
			return InteractionResult.FAIL;
		}

		ItemStack stack = context.getItemInHand();
		RailShape shape = state.getBlock() instanceof BaseRailBlock rail
			? state.getValue(rail.getShapeProperty())
			: RailShape.NORTH_SOUTH;
		double slopeOffset = shape.isSlope() ? 0.5D : 0.0D;
		double spawnX = pos.getX() + 0.5D;
		double spawnY = pos.getY() + 0.0625D + slopeOffset;
		double spawnZ = pos.getZ() + 0.5D;

		if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
			SteamLocomotiveEntity locomotive = (SteamLocomotiveEntity) AbstractMinecart.createMinecart(
				serverLevel,
				spawnX,
				spawnY,
				spawnZ,
				MinecartChainMod.STEAM_LOCOMOTIVE_ENTITY.get(),
				EntitySpawnReason.DISPENSER,
				stack,
				context.getPlayer()
			);

			if (locomotive == null) {
				return InteractionResult.FAIL;
			}

			Vec3 playerLook = context.getPlayer() != null ? context.getPlayer().getLookAngle().horizontal() : Vec3.ZERO;
			Vec3 frontDir;

			if (shape == RailShape.EAST_WEST) {
				frontDir = playerLook.x >= 0.0D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(-1.0D, 0.0D, 0.0D);
			} else if (shape == RailShape.NORTH_SOUTH) {
				frontDir = playerLook.z >= 0.0D ? new Vec3(0.0D, 0.0D, 1.0D) : new Vec3(0.0D, 0.0D, -1.0D);
			} else if (shape == RailShape.ASCENDING_EAST) {
				frontDir = new Vec3(1.0D, 0.0D, 0.0D);
			} else if (shape == RailShape.ASCENDING_WEST) {
				frontDir = new Vec3(-1.0D, 0.0D, 0.0D);
			} else if (shape == RailShape.ASCENDING_NORTH) {
				frontDir = new Vec3(0.0D, 0.0D, -1.0D);
			} else if (shape == RailShape.ASCENDING_SOUTH) {
				frontDir = new Vec3(0.0D, 0.0D, 1.0D);
			} else {
				Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
				Vec3 exit1 = new Vec3(exits.getFirst().getX(), 0.0D, exits.getFirst().getZ()).normalize();
				Vec3 exit2 = new Vec3(exits.getSecond().getX(), 0.0D, exits.getSecond().getZ()).normalize();
				frontDir = playerLook.dot(exit1) >= playerLook.dot(exit2) ? exit1 : exit2;
			}

			float yaw = (float) (Mth.atan2(-frontDir.x, frontDir.z) * Mth.RAD_TO_DEG);
			locomotive.setFacingYaw(yaw);
			locomotive.setYRot(yaw);
			locomotive.yRotO = yaw;
			locomotive.setYHeadRot(yaw);
			locomotive.setYBodyRot(yaw);
			MinecartChainAccess data = (MinecartChainAccess) locomotive;
			data.minecartChain$setLocomotiveYaw(yaw);
			data.minecartChain$setHasLocomotiveYaw(true);

			if (AbstractMinecart.useExperimentalMovement(serverLevel)) {
				List<Entity> collisions = serverLevel.getEntities(locomotive, locomotive.getBoundingBox());
				for (Entity entity : collisions) {
					if (entity instanceof AbstractMinecart) {
						return InteractionResult.FAIL;
					}
				}
			}

			serverLevel.addFreshEntity(locomotive);
			serverLevel.gameEvent(
				GameEvent.ENTITY_PLACE,
				pos,
				GameEvent.Context.of(context.getPlayer(), serverLevel.getBlockState(pos.below()))
			);
		}

		level.playSound(
			null,
			pos,
			SoundEvents.ARMOR_EQUIP_IRON.value(),
			SoundSource.BLOCKS,
			1.0F,
			0.8F
		);

		if (context.getPlayer() == null || !context.getPlayer().hasInfiniteMaterials()) {
			stack.shrink(1);
		}

		return InteractionResult.SUCCESS;
	}
}
