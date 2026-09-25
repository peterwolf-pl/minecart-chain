package com.piotrek.minecartchain.mixin;

import com.piotrek.minecartchain.MinecartTrainLogic;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NewMinecartBehavior.class)
public abstract class NewMinecartBehaviorMixin extends MinecartBehavior {
	private NewMinecartBehaviorMixin(final AbstractMinecart minecart) {
		super(minecart);
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void minecartChain$skipVanillaPhysicsOnExternalTrack(final CallbackInfo ci) {
		if (!this.level().isClientSide() && MinecartTrainLogic.shouldSkipVanillaPhysics(this.minecart)) {
			this.minecart.setOnRails(false);
			ci.cancel();
		}
	}
}
