package com.piotrek.minecartchain;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

final class NeoForgeEvents {
    private NeoForgeEvents() {}

    static void register() {
        NeoForge.EVENT_BUS.register(NeoForgeEvents.class);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        InteractionResult result = MinecartLinkHandler.handleInteraction(
            event.getEntity(),
            event.getLevel(),
            event.getHand(),
            event.getTarget(),
            new EntityHitResult(event.getTarget(), event.getLocalPos())
        );
        if (result != InteractionResult.PASS) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }
}
