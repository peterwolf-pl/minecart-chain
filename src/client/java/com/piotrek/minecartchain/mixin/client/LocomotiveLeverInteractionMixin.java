package com.piotrek.minecartchain.mixin.client;

import com.piotrek.minecartchain.SteamLocomotiveEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class LocomotiveLeverInteractionMixin {
	@Unique private HitResult minecartChain$originalHit;
	@Unique private boolean minecartChain$leverTarget;

	@Inject(method = "startUseItem", at = @At("HEAD"))
	private void minecartChain$targetLeverFromCab(final CallbackInfo ci) {
		Minecraft client = (Minecraft) (Object) this;
		if (client.player == null || client.level == null
			|| !(client.player.getVehicle() instanceof SteamLocomotiveEntity)) {
			return;
		}
		var camera = client.gameRenderer.getMainCamera();
		Vec3 origin = camera.position();
		var forward = camera.forwardVector();
		double reach = client.player.blockInteractionRange() * 2.0D;
		Vec3 end = origin.add(forward.x() * reach, forward.y() * reach, forward.z() * reach);
		var hit = client.level.clip(new ClipContext(origin, end, ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE, client.player));
		if (hit.getType() != HitResult.Type.BLOCK
			|| !(client.level.getBlockState(hit.getBlockPos()).getBlock() instanceof LeverBlock)
			|| hit.getLocation().distanceToSqr(client.player.getEyePosition()) > reach * reach) {
			return;
		}
		// Only right-click targeting is extended. Mining/attacking retains vanilla
		// reach, and normal useItemOn still enforces permissions and item rules.
		// Block raycasting also avoids the locomotive's own cab intercepting use.
		this.minecartChain$originalHit = client.hitResult;
		this.minecartChain$leverTarget = true;
		client.hitResult = hit;
	}

	@Inject(method = "startUseItem", at = @At("RETURN"))
	private void minecartChain$restoreTarget(final CallbackInfo ci) {
		if (this.minecartChain$leverTarget) {
			((Minecraft) (Object) this).hitResult = this.minecartChain$originalHit;
			this.minecartChain$originalHit = null;
			this.minecartChain$leverTarget = false;
		}
	}
}
