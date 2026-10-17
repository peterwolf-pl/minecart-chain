package com.piotrek.minecartchain;

import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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
	public static final RadioLocomotiveItem RADIO_LOCOMOTIVE_ITEM = registerItem(
		"radio_locomotive",
		key -> new RadioLocomotiveItem(new Item.Properties().setId(key))
	);
	public static final ResourceKey<EntityType<?>> COAL_TENDER_ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("coal_tender"));
	public static final EntityType<CoalTenderEntity> COAL_TENDER_ENTITY = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		COAL_TENDER_ENTITY_KEY,
		EntityType.Builder.<CoalTenderEntity>of(CoalTenderEntity::new, MobCategory.MISC)
			.sized(0.98F, 1.1F)
			.clientTrackingRange(10)
			.build(COAL_TENDER_ENTITY_KEY)
	);
	public static final CoalTenderItem COAL_TENDER_ITEM = registerItem(
		"coal_tender",
		key -> new CoalTenderItem(new Item.Properties().setId(key))
	);
	public static final ResourceKey<EntityType<?>> LONG_MINECART_ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("long_minecart"));
	public static final EntityType<LongMinecartEntity> LONG_MINECART_ENTITY = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		LONG_MINECART_ENTITY_KEY,
		EntityType.Builder.<LongMinecartEntity>of(LongMinecartEntity::new, MobCategory.MISC)
			.sized(0.98F, 0.7F)
			.clientTrackingRange(8)
			.build(LONG_MINECART_ENTITY_KEY)
	);
	public static final LongMinecartItem LONG_MINECART_ITEM = registerItem(
		"long_minecart",
		key -> new LongMinecartItem(new Item.Properties().setId(key))
	);
	public static final ResourceKey<EntityType<?>> LONG_CHEST_MINECART_ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("long_chest_minecart"));
	public static final EntityType<LongChestMinecartEntity> LONG_CHEST_MINECART_ENTITY = Registry.register(
		BuiltInRegistries.ENTITY_TYPE, LONG_CHEST_MINECART_ENTITY_KEY,
		EntityType.Builder.<LongChestMinecartEntity>of(LongChestMinecartEntity::new, MobCategory.MISC)
			.sized(0.98F, 1.2F).clientTrackingRange(10).build(LONG_CHEST_MINECART_ENTITY_KEY)
	);
	public static final LongChestMinecartItem LONG_CHEST_MINECART_ITEM = registerItem(
		"long_chest_minecart", key -> new LongChestMinecartItem(new Item.Properties().stacksTo(1).setId(key))
	);
	public static final ResourceKey<EntityType<?>> STEAM_LOCOMOTIVE_ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("steam_locomotive"));
	public static final EntityType<SteamLocomotiveEntity> STEAM_LOCOMOTIVE_ENTITY = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		STEAM_LOCOMOTIVE_ENTITY_KEY,
		EntityType.Builder.<SteamLocomotiveEntity>of(SteamLocomotiveEntity::new, MobCategory.MISC)
			.sized(0.98F, 1.2F)
			.clientTrackingRange(10)
			.build(STEAM_LOCOMOTIVE_ENTITY_KEY)
	);
	public static final SteamLocomotiveItem STEAM_LOCOMOTIVE_ITEM = registerItem(
		"steam_locomotive",
		key -> new SteamLocomotiveItem(new Item.Properties().setId(key))
	);
	public static final SteamLocomotiveWithTenderItem STEAM_LOCOMOTIVE_WITH_TENDER_ITEM = registerItem(
		"steam_locomotive_with_tender",
		key -> new SteamLocomotiveWithTenderItem(new Item.Properties().setId(key))
	);
	public static final RadioSteamLocomotiveItem RADIO_STEAM_LOCOMOTIVE_ITEM = registerItem(
		"radio_steam_locomotive",
		key -> new RadioSteamLocomotiveItem(new Item.Properties().setId(key))
	);
	public static final MenuType<MinecartLocomotiveMenu> LOCOMOTIVE_MENU = Registry.register(
		BuiltInRegistries.MENU,
		id("locomotive"),
		new MenuType<>(MinecartLocomotiveMenu::new, FeatureFlags.VANILLA_SET)
	);
	public static final MenuType<RadioLocomotiveMenu> RADIO_LOCOMOTIVE_MENU = Registry.register(
		BuiltInRegistries.MENU,
		id("radio_locomotive"),
		new MenuType<>(RadioLocomotiveMenu::new, FeatureFlags.VANILLA_SET)
	);

	@Override
	public void onInitialize() {
		MinecartChainConfig.load();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			MinecartChainCommands.register(dispatcher)
		);
		registerCreativeTabs();
		PayloadTypeRegistry.serverboundPlay().register(RadioLocomotiveJumperPayload.TYPE, RadioLocomotiveJumperPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RadioLocomotiveJumperPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				if (context.player().containerMenu instanceof RadioLocomotiveMenu menu) {
					menu.handleSetJumper(payload.isReverse(), payload.bit(), payload.targetState());
				}
			});
		});
		PayloadTypeRegistry.serverboundPlay().register(SteamLocomotiveInputPayload.TYPE, SteamLocomotiveInputPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SteamLocomotiveInputPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				if (context.player().getVehicle() instanceof SteamLocomotiveEntity locomotive) {
					locomotive.handleDriverInput(
						payload.throttle(), payload.reverser(), payload.coupleNext(), payload.uncoupleLast()
					);
				}
			});
		});
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(MinecartLinkHandler::handleInteraction);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			MinecartLinkHandler.clearSelection(handler.getPlayer().getUUID())
		);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MinecartLinkHandler.clearRuntimeState();
			MinecartTrainLogic.clearRuntimeState();
			SignalRailBlock.clearRuntimeState();
		});
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("splinecart")) {
			SplinecartPlacementHandler.register();
		}
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

	private static <T extends Item> T registerItem(final String path, final Function<ResourceKey<Item>, T> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(key));
	}

	private static void registerCreativeTabs() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS)
			.register(output -> output.accept(LONG_CHEST_MINECART_ITEM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(LONG_CHEST_MINECART_ITEM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS)
			.register(output -> {
				output.insertAfter(Items.POWERED_RAIL, SIGNAL_RAIL_ITEM);
				output.insertAfter(SIGNAL_RAIL_ITEM, STEAM_LOCOMOTIVE_ITEM);
				output.insertAfter(STEAM_LOCOMOTIVE_ITEM, STEAM_LOCOMOTIVE_WITH_TENDER_ITEM);
				output.accept(LONG_MINECART_ITEM);
				output.accept(COAL_TENDER_ITEM);
				if (RadioLocomotiveCompat.isTxRxModLoaded()) {
					output.insertAfter(COAL_TENDER_ITEM, RADIO_LOCOMOTIVE_ITEM);
					output.insertAfter(RADIO_LOCOMOTIVE_ITEM, RADIO_STEAM_LOCOMOTIVE_ITEM);
				}
			});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> {
				output.insertAfter(Items.FURNACE_MINECART, STEAM_LOCOMOTIVE_ITEM);
				output.insertAfter(STEAM_LOCOMOTIVE_ITEM, STEAM_LOCOMOTIVE_WITH_TENDER_ITEM);
				output.accept(LONG_MINECART_ITEM);
				output.accept(COAL_TENDER_ITEM);
				if (RadioLocomotiveCompat.isTxRxModLoaded()) {
					output.insertAfter(COAL_TENDER_ITEM, RADIO_LOCOMOTIVE_ITEM);
					output.insertAfter(RADIO_LOCOMOTIVE_ITEM, RADIO_STEAM_LOCOMOTIVE_ITEM);
				}
			});
	}
}
