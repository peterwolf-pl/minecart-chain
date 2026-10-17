package com.piotrek.minecartchain;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
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

public class LongMinecartItem extends Item {
	public LongMinecartItem(final Properties properties) {
		super(properties);
	}

	protected net.minecraft.world.entity.EntityType<? extends AbstractMinecart> cartType() {
		return MinecartChainMod.LONG_MINECART_ENTITY;
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
		RailShape shape = state.getBlock() instanceof BaseRailBlock rail
			? state.getValue(rail.getShapeProperty())
			: RailShape.NORTH_SOUTH;
		double slopeOffset = shape.isSlope() ? 0.5D : 0.0D;

		if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
			AbstractMinecart cart = AbstractMinecart.createMinecart(
				serverLevel,
				pos.getX() + 0.5D,
				pos.getY() + 0.0625D + slopeOffset,
				pos.getZ() + 0.5D,
				this.cartType(),
				EntitySpawnReason.DISPENSER,
				stack,
				context.getPlayer()
			);
			if (cart == null) {
				return InteractionResult.FAIL;
			}

			Vec3 playerLook = context.getPlayer() != null ? context.getPlayer().getLookAngle().horizontal() : Vec3.ZERO;
			Vec3 frontDir = playerLook.x * playerLook.x >= playerLook.z * playerLook.z
				? (playerLook.x >= 0.0D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(-1.0D, 0.0D, 0.0D))
				: (playerLook.z >= 0.0D ? new Vec3(0.0D, 0.0D, 1.0D) : new Vec3(0.0D, 0.0D, -1.0D));
			if (shape == RailShape.EAST_WEST) {
				frontDir = playerLook.x >= 0.0D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(-1.0D, 0.0D, 0.0D);
			} else if (shape == RailShape.NORTH_SOUTH) {
				frontDir = playerLook.z >= 0.0D ? new Vec3(0.0D, 0.0D, 1.0D) : new Vec3(0.0D, 0.0D, -1.0D);
			} else if (!(shape == RailShape.NORTH_SOUTH || shape == RailShape.EAST_WEST)) {
				Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
				Vec3 exit1 = new Vec3(exits.getFirst().getX(), 0.0D, exits.getFirst().getZ()).normalize();
				Vec3 exit2 = new Vec3(exits.getSecond().getX(), 0.0D, exits.getSecond().getZ()).normalize();
				frontDir = playerLook.dot(exit1) >= playerLook.dot(exit2) ? exit1 : exit2;
			}

			float yaw = (float) (Mth.atan2(-frontDir.x, frontDir.z) * Mth.RAD_TO_DEG);
			cart.setYRot(yaw);
			cart.yRotO = yaw;
			serverLevel.addFreshEntity(cart);
			serverLevel.gameEvent(GameEvent.ENTITY_PLACE, pos, GameEvent.Context.of(context.getPlayer(), serverLevel.getBlockState(pos.below())));
		}

		level.playSound(null, pos, SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, 0.6F, 1.0F);
		if (context.getPlayer() == null || !context.getPlayer().hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
