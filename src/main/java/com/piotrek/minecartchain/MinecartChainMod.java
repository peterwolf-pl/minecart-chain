package com.piotrek.minecartchain;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MinecartChainMod.MOD_ID)
public class MinecartChainMod {
	public static final String MOD_ID = "minecart_chain";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
	public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);

	public static final DeferredBlock<SignalRailBlock> SIGNAL_RAIL = BLOCKS.registerBlock(
		"signal_rail",
		SignalRailBlock::new,
		() -> BlockBehaviour.Properties.of().noCollision().strength(0.7F).sound(SoundType.METAL)
	);
	public static final DeferredItem<BlockItem> SIGNAL_RAIL_ITEM = ITEMS.registerSimpleBlockItem("signal_rail", SIGNAL_RAIL);

	public static final DeferredHolder<EntityType<?>, EntityType<CoalTenderEntity>> COAL_TENDER_ENTITY = ENTITY_TYPES.register(
		"coal_tender",
		() -> EntityType.Builder.<CoalTenderEntity>of(CoalTenderEntity::new, MobCategory.MISC)
			.sized(0.98F, 1.1F)
			.clientTrackingRange(10)
			.build(ResourceKey.create(Registries.ENTITY_TYPE, id("coal_tender")))
	);
	public static final DeferredHolder<EntityType<?>, EntityType<LongMinecartEntity>> LONG_MINECART_ENTITY = ENTITY_TYPES.register(
		"long_minecart",
		() -> EntityType.Builder.<LongMinecartEntity>of(LongMinecartEntity::new, MobCategory.MISC)
			.sized(0.98F, 0.7F)
			.clientTrackingRange(8)
			.build(ResourceKey.create(Registries.ENTITY_TYPE, id("long_minecart")))
	);
	public static final DeferredHolder<EntityType<?>, EntityType<LongChestMinecartEntity>> LONG_CHEST_MINECART_ENTITY = ENTITY_TYPES.register(
		"long_chest_minecart",
		() -> EntityType.Builder.<LongChestMinecartEntity>of(LongChestMinecartEntity::new, MobCategory.MISC)
			.sized(0.98F, 1.2F)
			.clientTrackingRange(10)
			.build(ResourceKey.create(Registries.ENTITY_TYPE, id("long_chest_minecart")))
	);
	public static final DeferredHolder<EntityType<?>, EntityType<SteamLocomotiveEntity>> STEAM_LOCOMOTIVE_ENTITY = ENTITY_TYPES.register(
		"steam_locomotive",
		() -> EntityType.Builder.<SteamLocomotiveEntity>of(SteamLocomotiveEntity::new, MobCategory.MISC)
			.sized(0.98F, 1.2F)
			.clientTrackingRange(10)
			.build(ResourceKey.create(Registries.ENTITY_TYPE, id("steam_locomotive")))
	);

	public static final DeferredItem<RadioLocomotiveItem> RADIO_LOCOMOTIVE_ITEM = ITEMS.registerItem(
		"radio_locomotive",
		RadioLocomotiveItem::new
	);
	public static final DeferredItem<CoalTenderItem> COAL_TENDER_ITEM = ITEMS.registerItem(
		"coal_tender",
		CoalTenderItem::new
	);
	public static final DeferredItem<LongMinecartItem> LONG_MINECART_ITEM = ITEMS.registerItem(
		"long_minecart",
		LongMinecartItem::new
	);
	public static final DeferredItem<LongChestMinecartItem> LONG_CHEST_MINECART_ITEM = ITEMS.registerItem(
		"long_chest_minecart",
		LongChestMinecartItem::new,
		p -> p.stacksTo(1)
	);
	public static final DeferredItem<SteamLocomotiveItem> STEAM_LOCOMOTIVE_ITEM = ITEMS.registerItem(
		"steam_locomotive",
		SteamLocomotiveItem::new
	);
	public static final DeferredItem<SteamLocomotiveWithTenderItem> STEAM_LOCOMOTIVE_WITH_TENDER_ITEM = ITEMS.registerItem(
		"steam_locomotive_with_tender",
		SteamLocomotiveWithTenderItem::new
	);
	public static final DeferredItem<RadioSteamLocomotiveItem> RADIO_STEAM_LOCOMOTIVE_ITEM = ITEMS.registerItem(
		"radio_steam_locomotive",
		RadioSteamLocomotiveItem::new
	);

	public static final DeferredHolder<MenuType<?>, MenuType<MinecartLocomotiveMenu>> LOCOMOTIVE_MENU = MENUS.register(
		"locomotive",
		() -> new MenuType<>(MinecartLocomotiveMenu::new, FeatureFlags.VANILLA_SET)
	);
	public static final DeferredHolder<MenuType<?>, MenuType<RadioLocomotiveMenu>> RADIO_LOCOMOTIVE_MENU = MENUS.register(
		"radio_locomotive",
		() -> new MenuType<>(RadioLocomotiveMenu::new, FeatureFlags.VANILLA_SET)
	);

	public MinecartChainMod(IEventBus modBus) {
		MinecartChainConfig.load();
		BLOCKS.register(modBus);
		ITEMS.register(modBus);
		ENTITY_TYPES.register(modBus);
		MENUS.register(modBus);
		modBus.addListener(MinecartChainMod::addCreative);
		modBus.addListener(MinecartChainMod::registerPayloads);
		NeoForge.EVENT_BUS.register(GameEvents.class);
		LOGGER.info("Registered minecart chain interactions");
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static void registerPayloads(final RegisterPayloadHandlersEvent event) {
		final var registrar = event.registrar(MOD_ID);
		registrar.playToServer(RadioLocomotiveJumperPayload.TYPE, RadioLocomotiveJumperPayload.CODEC, (payload, context) -> {
			context.enqueueWork(() -> {
				if (context.player().containerMenu instanceof RadioLocomotiveMenu menu) {
					menu.handleSetJumper(payload.isReverse(), payload.bit(), payload.targetState());
				}
			});
		});
		registrar.playToServer(SteamLocomotiveInputPayload.TYPE, SteamLocomotiveInputPayload.CODEC, (payload, context) -> {
			context.enqueueWork(() -> {
				if (context.player().getVehicle() instanceof SteamLocomotiveEntity locomotive) {
					locomotive.handleDriverInput(
						payload.throttle(), payload.reverser(), payload.coupleNext(), payload.uncoupleLast()
					);
				}
			});
		});
	}

	private static void addCreative(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
			event.insertAfter(
				Items.POWERED_RAIL.getDefaultInstance(),
				SIGNAL_RAIL_ITEM.get().getDefaultInstance(),
				CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
			);
			event.insertAfter(
				SIGNAL_RAIL_ITEM.get().getDefaultInstance(),
				STEAM_LOCOMOTIVE_ITEM.get().getDefaultInstance(),
				CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
			);
			event.insertAfter(
				STEAM_LOCOMOTIVE_ITEM.get().getDefaultInstance(),
				STEAM_LOCOMOTIVE_WITH_TENDER_ITEM.get().getDefaultInstance(),
				CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
			);
			event.accept(LONG_MINECART_ITEM.get().getDefaultInstance());
			event.accept(LONG_CHEST_MINECART_ITEM.get().getDefaultInstance());
			event.accept(COAL_TENDER_ITEM.get().getDefaultInstance());
			if (RadioLocomotiveCompat.isTxRxModLoaded()) {
				event.insertAfter(
					COAL_TENDER_ITEM.get().getDefaultInstance(),
					RADIO_LOCOMOTIVE_ITEM.get().getDefaultInstance(),
					CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
				);
				event.insertAfter(
					RADIO_LOCOMOTIVE_ITEM.get().getDefaultInstance(),
					RADIO_STEAM_LOCOMOTIVE_ITEM.get().getDefaultInstance(),
					CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
				);
			}
		}
		if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
			event.accept(LONG_CHEST_MINECART_ITEM.get().getDefaultInstance());
			event.insertAfter(
				Items.FURNACE_MINECART.getDefaultInstance(),
				STEAM_LOCOMOTIVE_ITEM.get().getDefaultInstance(),
				CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
			);
			event.insertAfter(
				STEAM_LOCOMOTIVE_ITEM.get().getDefaultInstance(),
				STEAM_LOCOMOTIVE_WITH_TENDER_ITEM.get().getDefaultInstance(),
				CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
			);
			event.accept(LONG_MINECART_ITEM.get().getDefaultInstance());
			event.accept(COAL_TENDER_ITEM.get().getDefaultInstance());
			if (RadioLocomotiveCompat.isTxRxModLoaded()) {
				event.insertAfter(
					COAL_TENDER_ITEM.get().getDefaultInstance(),
					RADIO_LOCOMOTIVE_ITEM.get().getDefaultInstance(),
					CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
				);
				event.insertAfter(
					RADIO_LOCOMOTIVE_ITEM.get().getDefaultInstance(),
					RADIO_STEAM_LOCOMOTIVE_ITEM.get().getDefaultInstance(),
					CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
				);
			}
		}
	}

	public static final class GameEvents {
		private GameEvents() {
		}

		@SubscribeEvent
		public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
			InteractionResult result = MinecartLinkHandler.handleInteraction(
				event.getEntity(),
				event.getLevel(),
				event.getHand(),
				event.getTarget(),
				new EntityHitResult(event.getTarget(), event.getLocation())
			);
			if (result != InteractionResult.PASS) {
				event.setCancellationResult(result);
				event.setCanceled(true);
			}
		}

		@SubscribeEvent
		public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
			if (event.getEntity() instanceof ServerPlayer player) {
				MinecartLinkHandler.clearSelection(player.getUUID());
			}
		}

		@SubscribeEvent
		public static void onServerStopped(ServerStoppedEvent event) {
			MinecartLinkHandler.clearRuntimeState();
			MinecartTrainLogic.clearRuntimeState();
			SignalRailBlock.clearRuntimeState();
		}

		@SubscribeEvent
		public static void onRegisterCommands(RegisterCommandsEvent event) {
			MinecartChainCommands.register(event.getDispatcher());
		}
	}
}
