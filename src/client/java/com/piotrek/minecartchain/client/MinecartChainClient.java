package com.piotrek.minecartchain.client;

import com.piotrek.minecartchain.MinecartChainMod;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class MinecartChainClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(MinecartChainMod.LOCOMOTIVE_MENU, MinecartLocomotiveScreen::new);
		MinecartChainMod.LOGGER.info("Minecart chain client renderer hooks loaded");
	}
}
