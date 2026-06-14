package com.piotrek.minecartchain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class MinecartLinkHandler {
	private static final double MAX_LINK_DISTANCE = 2.0D;
	private static final Map<UUID, UUID> SELECTED_CARTS = new HashMap<>();

	private MinecartLinkHandler() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(entity instanceof AbstractMinecart minecart)) {
				return InteractionResult.PASS;
			}

			ItemStack stack = player.getItemInHand(hand);
			if (stack.getItem() == Items.IRON_CHAIN) {
				if (!level.isClientSide()) {
					handleChainUse(player, level, hand, minecart, stack);
				}
				return InteractionResult.SUCCESS;
			}

			if (minecart instanceof MinecartFurnace furnace) {
				if (stack.getItem() == Items.WATER_BUCKET) {
					if (!level.isClientSide()) {
						handleWaterUse(player, level, hand, furnace, stack);
					}
					return InteractionResult.SUCCESS;
				}

				if (level.fuelValues().isFuel(stack)) {
					if (!level.isClientSide()) {
						handleFuelUse(player, level, hand, furnace, stack);
					}
					return InteractionResult.SUCCESS;
				}

				if (canUseControls(furnace, stack)) {
					MinecartControlLayout.Control control = MinecartControlLayout.hitControl(furnace, player, hitResult);
					if (((MinecartChainAccess) furnace).minecartChain$hasEngineLever() && control == MinecartControlLayout.Control.NONE) {
						if (!level.isClientSide()) {
							openLocomotiveMenu(player, furnace);
						}
						return InteractionResult.SUCCESS;
					}

					if (!level.isClientSide()) {
						handleControlUse(player, level, furnace, stack, control);
					}
					return InteractionResult.SUCCESS;
				}

				if (!level.isClientSide()) {
					openLocomotiveMenu(player, furnace);
				}
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		});
	}

	private static void openLocomotiveMenu(final Player player, final MinecartFurnace furnace) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		serverPlayer.openMenu(new SimpleMenuProvider(
			(containerId, inventory, ignored) -> new MinecartLocomotiveMenu(containerId, inventory, furnace),
			Component.translatable("container.minecart_chain.locomotive")
		));
	}

	private static boolean canUseControls(final AbstractMinecart minecart, final ItemStack stack) {
		return stack.getItem() == Items.LEVER || ((MinecartChainAccess) minecart).minecartChain$hasEngineLever();
	}

	private static void handleFuelUse(
		final Player player, final Level level, final InteractionHand hand, final MinecartFurnace furnace, final ItemStack stack
	) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(furnace instanceof MinecartLocomotiveAccess locomotive)) {
			return;
		}

		if (!locomotive.minecartChain$canAcceptLocomotiveFuel(stack)) {
			send(serverPlayer, "Locomotive fuel tank is full.");
			return;
		}

		int acceptedFuel = locomotive.minecartChain$addLocomotiveFuel(stack);
		if (acceptedFuel <= 0) {
			return;
		}

		Item usedItem = stack.getItem();
		consumeStackWithRemainder(serverPlayer, hand, stack);
		serverPlayer.awardStat(Stats.ITEM_USED.get(usedItem));
		play(level, furnace, SoundEvents.FURNACE_FIRE_CRACKLE, 0.8F, 1.15F);
		send(serverPlayer, "Loaded locomotive fuel. Fuel: " + locomotive.minecartChain$getLocomotiveFuelTicks() + " ticks.");
	}

	private static void handleWaterUse(
		final Player player, final Level level, final InteractionHand hand, final MinecartFurnace furnace, final ItemStack stack
	) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		MinecartChainAccess data = (MinecartChainAccess) furnace;
		int acceptedWater = data.minecartChain$addWaterTicks(
			MinecartLocomotiveResources.WATER_TICKS_PER_BUCKET,
			MinecartLocomotiveResources.MAX_WATER_TICKS
		);
		if (acceptedWater <= 0) {
			send(serverPlayer, "Locomotive water tank is full.");
			return;
		}

		Item usedItem = stack.getItem();
		if (!serverPlayer.hasInfiniteMaterials()) {
			serverPlayer.setItemInHand(hand, new ItemStack(Items.BUCKET));
		}
		serverPlayer.awardStat(Stats.ITEM_USED.get(usedItem));
		play(level, furnace, SoundEvents.BUCKET_EMPTY, 0.9F, 1.0F);
		send(serverPlayer, "Filled locomotive with water. Water: " + data.minecartChain$getWaterTicks() + " ticks.");
	}

	private static void handleChainUse(
		final Player player, final Level level, final InteractionHand hand, final AbstractMinecart minecart, final ItemStack stack
	) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return;
		}

		if (player.isSecondaryUseActive()) {
			unlinkAll(serverPlayer, serverLevel, minecart);
			return;
		}

		refreshActiveLinks(serverLevel, minecart);
		MinecartChainAccess currentData = (MinecartChainAccess) minecart;
		if (!currentData.minecartChain$canAcceptLink()) {
			send(serverPlayer, "This minecart already has two links.");
			return;
		}

		UUID playerId = serverPlayer.getUUID();
		UUID selectedId = SELECTED_CARTS.get(playerId);
		if (selectedId == null) {
			SELECTED_CARTS.put(playerId, minecart.getUUID());
			play(level, minecart, SoundEvents.CHAIN_HIT, 0.85F, 1.1F);
			send(serverPlayer, "First minecart selected. Click another minecart with iron chain to link them.");
			return;
		}

		if (selectedId.equals(minecart.getUUID())) {
			SELECTED_CARTS.remove(playerId);
			play(level, minecart, SoundEvents.CHAIN_HIT, 0.65F, 0.8F);
			send(serverPlayer, "Minecart selection cancelled.");
			return;
		}

		Entity selectedEntity = serverLevel.getEntity(selectedId);
		if (!(selectedEntity instanceof AbstractMinecart selectedMinecart)) {
			SELECTED_CARTS.remove(playerId);
			send(serverPlayer, "The first minecart is no longer loaded. Select it again.");
			return;
		}

		refreshActiveLinks(serverLevel, selectedMinecart);
		refreshActiveLinks(serverLevel, minecart);
		currentData = (MinecartChainAccess) minecart;
		MinecartChainAccess selectedData = (MinecartChainAccess) selectedMinecart;
		if (!selectedData.minecartChain$canAcceptLink()) {
			SELECTED_CARTS.remove(playerId);
			send(serverPlayer, "The first minecart already has two links.");
			return;
		}

		if (selectedData.minecartChain$hasLink(minecart.getUUID())) {
			SELECTED_CARTS.remove(playerId);
			send(serverPlayer, "These minecarts are already linked.");
			return;
		}

		if (selectedMinecart.distanceToSqr(minecart) > MAX_LINK_DISTANCE * MAX_LINK_DISTANCE) {
			SELECTED_CARTS.remove(playerId);
			send(serverPlayer, "Minecarts must be at most 2 blocks apart.");
			return;
		}

		selectedData.minecartChain$addLink(minecart.getUUID());
		currentData.minecartChain$addLink(selectedMinecart.getUUID());
		boolean mountedLever = mountEngineLeverIfNeeded(level, selectedMinecart) | mountEngineLeverIfNeeded(level, minecart);
		SELECTED_CARTS.remove(playerId);
		if (!serverPlayer.hasInfiniteMaterials()) {
			stack.consume(1, serverPlayer);
		}

		play(level, minecart, SoundEvents.CHAIN_PLACE, 1.0F, 1.0F);
		send(serverPlayer, mountedLever ? "Minecarts linked. Furnace minecart controls mounted." : "Minecarts linked with a chain.");
	}

	private static void refreshActiveLinks(final ServerLevel level, final AbstractMinecart minecart) {
		MinecartChainAccess data = (MinecartChainAccess) minecart;
		for (UUID linkId : linkedIds(data)) {
			Entity linkedEntity = level.getEntity(linkId);
			if (!(linkedEntity instanceof AbstractMinecart linkedMinecart) || linkedMinecart.isRemoved()) {
				data.minecartChain$removeLink(linkId);
				continue;
			}

			if (!((MinecartChainAccess) linkedMinecart).minecartChain$hasLink(minecart.getUUID())) {
				data.minecartChain$removeLink(linkId);
			}
		}
	}

	private static boolean mountEngineLeverIfNeeded(final Level level, final AbstractMinecart minecart) {
		if (!(minecart instanceof MinecartFurnace furnace)) {
			return false;
		}

		MinecartChainAccess data = (MinecartChainAccess) minecart;
		if (data.minecartChain$hasEngineLever()) {
			return false;
		}

		data.minecartChain$setEngineLever(true);
		data.minecartChain$setEngineActive(true);
		data.minecartChain$setFullThrottle(false);
		data.minecartChain$setReversed(false);
		MinecartTrainLogic.snapLocomotiveYaw(furnace, MinecartTrainLogic.drivingDirection(furnace));
		play(level, minecart, SoundEvents.LEVER_CLICK, 0.9F, 0.5F);
		return true;
	}

	private static void handleControlUse(
		final Player player,
		final Level level,
		final AbstractMinecart minecart,
		final ItemStack stack,
		final MinecartControlLayout.Control control
	) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		MinecartChainAccess data = (MinecartChainAccess) minecart;
		if (!data.minecartChain$hasEngineLever()) {
			data.minecartChain$setEngineLever(true);
			data.minecartChain$setEngineActive(true);
			data.minecartChain$setFullThrottle(false);
			data.minecartChain$setReversed(false);
			MinecartTrainLogic.snapLocomotiveYaw((MinecartFurnace) minecart, MinecartTrainLogic.drivingDirection((MinecartFurnace) minecart));
			if (!serverPlayer.hasInfiniteMaterials()) {
				stack.consume(1, serverPlayer);
			}
			play(level, minecart, SoundEvents.LEVER_CLICK, 0.9F, 0.5F);
			send(serverPlayer, "Mounted furnace minecart controls.");
			return;
		}

		if (control == MinecartControlLayout.Control.DIRECTION) {
			MinecartFurnace furnace = (MinecartFurnace) minecart;
			if (!MinecartTrainLogic.isTrainStopped(furnace)) {
				play(level, minecart, SoundEvents.LEVER_CLICK, 0.5F, 0.35F);
				send(serverPlayer, "Stop the train before changing direction.");
				return;
			}

			boolean reversed = !data.minecartChain$isReversed();
			data.minecartChain$setReversed(reversed);
			MinecartTrainLogic.snapLocomotiveYaw(furnace, MinecartTrainLogic.drivingDirection(furnace));
			play(level, minecart, SoundEvents.LEVER_CLICK, 0.9F, reversed ? 0.75F : 0.6F);
			send(serverPlayer, reversed ? "Drive direction reversed." : "Drive direction set forward.");
			return;
		}

		if (control == MinecartControlLayout.Control.THROTTLE) {
			boolean fullThrottle = !data.minecartChain$isFullThrottle();
			data.minecartChain$setFullThrottle(fullThrottle);
			play(level, minecart, SoundEvents.LEVER_CLICK, 0.9F, fullThrottle ? 0.7F : 0.55F);
			return;
		}

		if (control == MinecartControlLayout.Control.BRAKE) {
			boolean brake = !data.minecartChain$isEngineActive();
			data.minecartChain$setEngineActive(brake);
			play(level, minecart, SoundEvents.LEVER_CLICK, 0.9F, brake ? 0.5F : 0.6F);
		}
	}

	private static void unlinkAll(final ServerPlayer player, final ServerLevel level, final AbstractMinecart minecart) {
		MinecartChainAccess data = (MinecartChainAccess) minecart;
		List<UUID> links = linkedIds(data);
		if (links.isEmpty()) {
			SELECTED_CARTS.remove(player.getUUID());
			send(player, "This minecart has no links.");
			return;
		}

		for (UUID linkId : links) {
			Entity linkedEntity = level.getEntity(linkId);
			if (linkedEntity instanceof AbstractMinecart linkedMinecart) {
				((MinecartChainAccess) linkedMinecart).minecartChain$removeLink(minecart.getUUID());
			}
		}
		data.minecartChain$clearLinks();
		SELECTED_CARTS.remove(player.getUUID());
		play(level, minecart, SoundEvents.CHAIN_BREAK, 1.0F, 0.9F);
		send(player, "Removed this minecart's chain links.");
	}

	private static List<UUID> linkedIds(final MinecartChainAccess data) {
		List<UUID> links = new ArrayList<>(2);
		data.minecartChain$getFirstLink().ifPresent(links::add);
		data.minecartChain$getSecondLink().ifPresent(links::add);
		return links;
	}

	private static void consumeStackWithRemainder(final ServerPlayer player, final InteractionHand hand, final ItemStack stack) {
		if (player.hasInfiniteMaterials()) {
			return;
		}

		Item item = stack.getItem();
		ItemStackTemplate remainderTemplate = item.getCraftingRemainder();
		stack.consume(1, player);
		if (remainderTemplate == null) {
			return;
		}

		ItemStack remainder = remainderTemplate.create();
		if (stack.isEmpty()) {
			player.setItemInHand(hand, remainder);
		} else if (!player.getInventory().add(remainder)) {
			player.drop(remainder, false);
		}
	}

	private static void play(final Level level, final AbstractMinecart minecart, final net.minecraft.sounds.SoundEvent sound, final float volume, final float pitch) {
		level.playSound(null, minecart.getX(), minecart.getY(), minecart.getZ(), sound, SoundSource.BLOCKS, volume, pitch);
	}

	private static void send(final ServerPlayer player, final String message) {
		player.sendSystemMessage(Component.literal(message));
	}
}
