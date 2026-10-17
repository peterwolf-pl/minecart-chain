package com.piotrek.minecartchain;

import com.piotrek.minecartchain.client.MinecartLocomotiveScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

@Mod(MinecartChainMod.MOD_ID)
public final class MinecartChainMod {
    public static final String MOD_ID = "minecart_chain";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);

    public static final Supplier<SignalRailBlock> SIGNAL_RAIL = BLOCKS.registerBlock(
        "signal_rail",
        SignalRailBlock::new,
        () -> BlockBehaviour.Properties.of().noCollision().strength(0.7F).sound(SoundType.METAL)
    );
    public static final Supplier<BlockItem> SIGNAL_RAIL_ITEM = ITEMS.registerSimpleBlockItem("signal_rail", SIGNAL_RAIL);
    public static final Supplier<MenuType<MinecartLocomotiveMenu>> LOCOMOTIVE_MENU = MENUS.register(
        "locomotive",
        () -> new MenuType<>(MinecartLocomotiveMenu::new, FeatureFlags.VANILLA_SET)
    );

    public MinecartChainMod(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(MinecartChainMod::addCreativeTabContents);
        modBus.addListener(MinecartChainMod::registerScreens);
        NeoForgeEvents.register();
        LOGGER.info("Registered Minecart Chain for NeoForge");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.insertAfter(Items.POWERED_RAIL.getDefaultInstance(), SIGNAL_RAIL_ITEM.get().getDefaultInstance(), net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(LOCOMOTIVE_MENU.get(), MinecartLocomotiveScreen::new);
    }
}
