package com.piotrek.minecartchain.mixin.client;

import com.piotrek.minecartchain.SteamLocomotiveEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(
		method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private void minecartChain$hidePlayerBodyInFirstPersonCab(
		final Entity entity,
		final Frustum frustum,
		final double camX,
		final double camY,
		final double camZ,
		final float partialTick,
		final CallbackInfoReturnable<Boolean> cir
	) {
		Minecraft client = Minecraft.getInstance();
		if (entity instanceof LocalPlayer && client.player == entity) {
			if (client.player.getVehicle() instanceof SteamLocomotiveEntity) {
				if (client.options.getCameraType().isFirstPerson()) {
					cir.setReturnValue(false);
				}
			}
		}
	}
}
