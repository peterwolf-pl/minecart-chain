package com.piotrek.minecartchain.mixin;

import com.piotrek.minecartchain.SteamLocomotiveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LocomotiveLeverReachMixin {
	@Redirect(method = "handleUseItemOn", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerPlayer;isWithinBlockInteractionRange(Lnet/minecraft/core/BlockPos;D)Z"))
	private boolean minecartChain$leverReach(final ServerPlayer player, final BlockPos pos, final double tolerance) {
		if (player.getVehicle() instanceof SteamLocomotiveEntity
			&& player.level().getBlockState(pos).getBlock() instanceof LeverBlock) {
			double reach = player.blockInteractionRange() * 2.0D + tolerance;
			return new AABB(pos).distanceToSqr(player.getEyePosition()) < reach * reach;
		}
		return player.isWithinBlockInteractionRange(pos, tolerance);
	}
}
