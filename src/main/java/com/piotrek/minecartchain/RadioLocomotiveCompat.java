package com.piotrek.minecartchain;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class RadioLocomotiveCompat {
	public static final String TXRX_MOD_ID = "peterwolfs_txrx_redstone";
	private static final Identifier RECEIVER_ID = Identifier.fromNamespaceAndPath(TXRX_MOD_ID, "receiver");

	private RadioLocomotiveCompat() {
	}

	public static boolean isTxRxModLoaded() {
		return FabricLoader.getInstance().isModLoaded(TXRX_MOD_ID);
	}

	public static boolean isReceiverItem(final ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return RECEIVER_ID.equals(id);
	}

	public static int getChannelPower(final int channel) {
		if (!isTxRxModLoaded()) {
			return 0;
		}
		return Bridge.getPower(channel);
	}

	private static final class Bridge {
		static int getPower(final int channel) {
			com.piotrek.peterwolfstxrx.network.RedstoneChannelManager mgr =
				com.piotrek.peterwolfstxrx.network.RedstoneChannelManager.get();
			return mgr != null ? mgr.getChannelPower(channel) : 0;
		}
	}
}
