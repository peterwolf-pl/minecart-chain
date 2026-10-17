package com.piotrek.minecartchain.mixin;

import com.piotrek.minecartchain.CoalTenderEntity;
import com.piotrek.minecartchain.MinecartTrainLogic;
import com.piotrek.minecartchain.SteamLocomotiveEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OldMinecartBehavior.class)
public abstract class OldMinecartBehaviorMixin extends MinecartBehavior {
	private OldMinecartBehaviorMixin(final AbstractMinecart minecart) {
		super(minecart);
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void minecartChain$skipVanillaPhysicsOnExternalTrack(final CallbackInfo ci) {
		if (!this.level().isClientSide()) {
			// Skip vanilla physics for externally guided minecarts
			if (MinecartTrainLogic.shouldSkipVanillaPhysics(this.minecart)) {
				this.minecart.setOnRails(false);
				ci.cancel();
				return;
			}

			// Skip vanilla physics for permanent tender managed by an active locomotive
			if (this.minecart instanceof CoalTenderEntity tender
				&& tender.hasPermanentLocomotive()
				&& this.level() instanceof ServerLevel serverLevel) {
				java.util.UUID locoId = tender.getPermanentLocomotiveId().orElse(null);
				if (locoId != null) {
					Entity locoEntity = serverLevel.getEntity(locoId);
					if (locoEntity instanceof SteamLocomotiveEntity && !locoEntity.isRemoved()) {
						// Locomotive manages positioning; cancel vanilla rail physics for tender
						ci.cancel();
					}
				}
			}
		}
	}
}
