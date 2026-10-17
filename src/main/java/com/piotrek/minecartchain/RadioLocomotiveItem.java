package com.piotrek.minecartchain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class RadioLocomotiveItem extends Item {
	public RadioLocomotiveItem(final Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(final UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		if (!state.is(BlockTags.RAILS)) {
			return InteractionResult.FAIL;
		}

		ItemStack stack = context.getItemInHand();
		RailShape shape = state.getBlock() instanceof BaseRailBlock rail ? state.getValue(rail.getShapeProperty()) : RailShape.NORTH_SOUTH;
		double slopeOffset = shape.isSlope() ? 0.5D : 0.0D;
		double spawnX = pos.getX() + 0.5D;
		double spawnY = pos.getY() + 0.0625D + slopeOffset;
		double spawnZ = pos.getZ() + 0.5D;

		if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
			MinecartFurnace furnace = (MinecartFurnace) AbstractMinecart.createMinecart(
				serverLevel,
				spawnX,
				spawnY,
				spawnZ,
				EntityType.FURNACE_MINECART,
				EntitySpawnReason.DISPENSER,
				stack,
				context.getPlayer()
			);

			if (furnace == null) {
				return InteractionResult.FAIL;
			}

			MinecartChainAccess data = (MinecartChainAccess) furnace;
			data.minecartChain$setRadioLocomotive(true);
			data.minecartChain$setEngineLever(true);
			data.minecartChain$setEngineActive(false);
			data.minecartChain$setFullThrottle(false);
			data.minecartChain$setReversed(false);
			data.minecartChain$setReversePending(false);
			data.minecartChain$setThrottleChannel(0);
			data.minecartChain$setReverseChannel(1);
			data.minecartChain$setArmed(false); // Safety lockout: must see 50% stop/neutral before driving!

			Vec3 facing = MinecartTrainLogic.engineDirection(furnace);
			if (facing.lengthSqr() <= 1.0E-5D) {
				facing = shape == RailShape.EAST_WEST ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 0.0D, 1.0D);
			}
			MinecartTrainLogic.snapLocomotiveYaw(furnace, facing);

			serverLevel.addFreshEntity(furnace);
			serverLevel.gameEvent(
				GameEvent.ENTITY_PLACE,
				pos,
				GameEvent.Context.of(context.getPlayer(), serverLevel.getBlockState(pos.below()))
			);
		}

		stack.shrink(1);
		return InteractionResult.SUCCESS;
	}
}
