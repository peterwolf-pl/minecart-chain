package com.piotrek.minecartchain;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MinecartLocomotiveMenu extends AbstractContainerMenu {
	private static final int DATA_FUEL = 0;
	private static final int DATA_MAX_FUEL = 1;
	private static final int DATA_WATER = 2;
	private static final int DATA_MAX_WATER = 3;
	private static final int DATA_COUNT = 4;
	private static final double MAX_INTERACTION_DISTANCE = 8.0D;

	private final MinecartFurnace furnace;
	private final ContainerData data;

	public MinecartLocomotiveMenu(final int containerId, final Inventory inventory) {
		this(containerId, inventory, null, new SimpleContainerData(DATA_COUNT));
	}

	public MinecartLocomotiveMenu(final int containerId, final Inventory inventory, final MinecartFurnace furnace) {
		this(containerId, inventory, furnace, locomotiveData(furnace));
	}

	private MinecartLocomotiveMenu(
		final int containerId,
		final Inventory inventory,
		final MinecartFurnace furnace,
		final ContainerData data
	) {
		super(MinecartChainMod.LOCOMOTIVE_MENU, containerId);
		checkContainerDataCount(data, DATA_COUNT);
		this.furnace = furnace;
		this.data = data;
		this.addStandardInventorySlots(inventory, 8, 84);
		this.addDataSlots(data);
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int slotIndex) {
		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot.hasItem()) {
			ItemStack stack = slot.getItem();
			clicked = stack.copy();
			if (slotIndex < 27) {
				if (!this.moveItemStackTo(stack, 27, 36, false)) {
					return ItemStack.EMPTY;
				}
			} else if (slotIndex < 36) {
				if (!this.moveItemStackTo(stack, 0, 27, false)) {
					return ItemStack.EMPTY;
				}
			} else {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}

			if (stack.getCount() == clicked.getCount()) {
				return ItemStack.EMPTY;
			}

			slot.onTake(player, stack);
		}

		return clicked;
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.furnace == null
			|| this.furnace.isAlive()
			&& !this.furnace.isRemoved()
			&& player.distanceToSqr(this.furnace) <= MAX_INTERACTION_DISTANCE * MAX_INTERACTION_DISTANCE;
	}

	public int fuelTicks() {
		return this.data.get(DATA_FUEL);
	}

	public int maxFuelTicks() {
		return this.data.get(DATA_MAX_FUEL);
	}

	public int waterTicks() {
		return this.data.get(DATA_WATER);
	}

	public int maxWaterTicks() {
		return this.data.get(DATA_MAX_WATER);
	}

	public float fuelProgress() {
		return progress(this.fuelTicks(), this.maxFuelTicks());
	}

	public float waterProgress() {
		return progress(this.waterTicks(), this.maxWaterTicks());
	}

	private static float progress(final int value, final int max) {
		return max <= 0 ? 0.0F : Math.min(1.0F, Math.max(0.0F, (float) value / max));
	}

	private static ContainerData locomotiveData(final MinecartFurnace furnace) {
		return new ContainerData() {
			@Override
			public int get(final int dataId) {
				return switch (dataId) {
					case DATA_FUEL -> ((MinecartLocomotiveAccess) furnace).minecartChain$getLocomotiveFuelTicks();
					case DATA_MAX_FUEL -> MinecartLocomotiveResources.MAX_FUEL_TICKS;
					case DATA_WATER -> ((MinecartChainAccess) furnace).minecartChain$getWaterTicks();
					case DATA_MAX_WATER -> MinecartLocomotiveResources.MAX_WATER_TICKS;
					default -> 0;
				};
			}

			@Override
			public void set(final int dataId, final int value) {
			}

			@Override
			public int getCount() {
				return DATA_COUNT;
			}
		};
	}
}
