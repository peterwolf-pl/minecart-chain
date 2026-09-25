package com.piotrek.minecartchain.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.piotrek.minecartchain.MinecartChainMod;
import com.piotrek.minecartchain.SteamLocomotiveEntity;
import com.piotrek.minecartchain.SteamLocomotiveInputPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;

public class MinecartChainClient implements ClientModInitializer {
	private static final KeyMapping.Category MINECART_CHAIN_CATEGORY = KeyMapping.Category.register(
		Identifier.fromNamespaceAndPath(MinecartChainMod.MOD_ID, "main")
	);

	public static KeyMapping REVERSER_KEY;
	public static KeyMapping COUPLE_KEY;
	private static float lastSentThrottle = 0.0F;
	private static boolean lastSentReverser = false;

	@Override
	public void onInitializeClient() {
		MenuScreens.register(MinecartChainMod.LOCOMOTIVE_MENU, MinecartLocomotiveScreen::new);
		MenuScreens.register(MinecartChainMod.RADIO_LOCOMOTIVE_MENU, RadioLocomotiveScreen::new);

		EntityRendererRegistry.register(MinecartChainMod.STEAM_LOCOMOTIVE_ENTITY, SteamLocomotiveRenderer::new);
		ModelLayerRegistry.registerModelLayer(SteamLocomotiveRenderer.LAYER_LOCATION, SteamLocomotiveModel::createBodyLayer);

		REVERSER_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.minecart_chain.reverser",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_R,
			MINECART_CHAIN_CATEGORY
		));

		COUPLE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.minecart_chain.couple",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_C,
			MINECART_CHAIN_CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}
			if (client.player.getVehicle() instanceof SteamLocomotiveEntity locomotive) {
				boolean keyUp = client.options.keyUp.isDown();
				boolean keyDown = client.options.keyDown.isDown();

				float t = locomotive.getThrottle();
				if (keyUp) {
					t = Math.min(1.0F, t + 0.02F);
				} else if (keyDown) {
					t = Math.max(-1.0F, t - 0.02F);
				}

				boolean reverser = locomotive.isReverser();
				while (REVERSER_KEY.consumeClick()) {
					reverser = !reverser;
				}

				boolean toggleCoupler = false;
				while (COUPLE_KEY.consumeClick()) {
					toggleCoupler = true;
				}

				if (Math.abs(t - lastSentThrottle) > 0.005F || reverser != lastSentReverser || toggleCoupler) {
					lastSentThrottle = t;
					lastSentReverser = reverser;
					locomotive.setThrottle(t);
					locomotive.setReverser(reverser);
					ClientPlayNetworking.send(new SteamLocomotiveInputPayload(t, reverser, toggleCoupler));
				}
			} else {
				lastSentThrottle = 0.0F;
				lastSentReverser = false;
			}
		});

		HudElementRegistry.addLast(
			MinecartChainMod.id("steam_locomotive_hud"),
			(graphicsExtractor, deltaTracker) -> {
				Minecraft client = Minecraft.getInstance();
				if (client.player == null || !(client.player.getVehicle() instanceof SteamLocomotiveEntity locomotive)) {
					return;
				}

				Font font = client.font;
				int screenWidth = client.getWindow().getGuiScaledWidth();
				int screenHeight = client.getWindow().getGuiScaledHeight();

				float throttle = locomotive.getThrottle();
				boolean reverser = locomotive.isReverser();
				double speedKmh = locomotive.getDeltaMovement().horizontal().length() * 20.0D * 3.6D;

				String throttleText;
				int throttleColor;
				if (throttle > 0.01F) {
					throttleText = String.format("Throttle: %.0f%% (W)", throttle * 100.0F);
					throttleColor = 0xFF55FFFF; // cyan
				} else if (throttle < -0.01F) {
					throttleText = String.format("Brake: %.0f%% (S)", -throttle * 100.0F);
					throttleColor = 0xFFFF5555; // red
				} else {
					throttleText = "Throttle: Idle";
					throttleColor = 0xFFFFAA00; // orange
				}

				String reverserText = reverser ? "Reverser: REVERSE [R]" : "Reverser: FORWARD [R]";
				int reverserColor = reverser ? 0xFFFFCC00 : 0xFF55FF55;
				String speedText = String.format("Speed: %.1f km/h", speedKmh);
				String couplerText = locomotive.isCouplerEngaged() ? "Coupler: CONNECTED [C]" : "Coupler: READY [C]";
				int couplerColor = locomotive.isCouplerEngaged() ? 0xFF55FF55 : 0xFFFFAA00;

				int boxWidth = 158;
				int boxHeight = 56;
				int boxX = (screenWidth - boxWidth) / 2;
				int boxY = screenHeight - boxHeight - 48;

				graphicsExtractor.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x88000000);
				graphicsExtractor.fill(boxX, boxY, boxX + boxWidth, boxY + 1, 0xFF555555);
				graphicsExtractor.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, 0xFF555555);
				graphicsExtractor.fill(boxX, boxY, boxX + 1, boxY + boxHeight, 0xFF555555);
				graphicsExtractor.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF555555);

				graphicsExtractor.text(font, speedText, boxX + 8, boxY + 6, 0xFFFFFFFF, false);
				graphicsExtractor.text(font, throttleText, boxX + 8, boxY + 18, throttleColor, false);
				graphicsExtractor.text(font, reverserText, boxX + 8, boxY + 30, reverserColor, false);
				graphicsExtractor.text(font, couplerText, boxX + 8, boxY + 42, couplerColor, false);
			}
		);

		MinecartChainMod.LOGGER.info("Minecart chain client renderer hooks loaded");
	}
}
