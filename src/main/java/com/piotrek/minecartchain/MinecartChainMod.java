package com.piotrek.minecartchain;

import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinecartChainMod implements ModInitializer {
	public static final String MOD_ID = "minecart_chain";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final SignalRailBlock SIGNAL_RAIL = registerBlock(
		"signal_rail",
		key -> new SignalRailBlock(BlockBehaviour.Properties.of().noCollision().strength(0.7F).sound(SoundType.METAL).setId(key))
	);
	public static final Item SIGNAL_RAIL_ITEM = registerBlockItem("signal_rail", SIGNAL_RAIL);
	public static final MenuType<MinecartLocomotiveMenu> LOCOMOTIVE_MENU = Registry.register(
		BuiltInRegistries.MENU,
		id("locomotive"),
		new MenuType<>(MinecartLocomotiveMenu::new, FeatureFlags.VANILLA_SET)
	);

	@Override
	public void onInitialize() {
		registerCreativeTabs();
		MinecartLinkHandler.register();
		LOGGER.info("Registered minecart chain interactions");
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static SignalRailBlock registerBlock(final String path, final Function<ResourceKey<Block>, SignalRailBlock> factory) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(path));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(key));
	}

	private static Item registerBlockItem(final String path, final Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		BlockItem item = new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key));
		item.registerBlocks(Item.BY_BLOCK, item);
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static void registerCreativeTabs() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS)
			.register(output -> output.insertAfter(Items.POWERED_RAIL, SIGNAL_RAIL_ITEM));
	}
}
