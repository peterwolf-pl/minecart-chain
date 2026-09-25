package com.piotrek.minecartchain;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class RadioLocomotiveMenu extends AbstractContainerMenu {
	public static final int DATA_THROTTLE_CH = 0;
	public static final int DATA_REVERSE_CH = 1;
	public static final int DATA_THROTTLE_PWR = 2;
	public static final int DATA_REVERSE_PWR = 3;
	public static final int DATA_IS_REVERSED = 4;
	public static final int DATA_IS_STOPPED = 5;
	public static final int DATA_REVERSE_PENDING = 6;
	public static final int DATA_FUEL = 7;
	public static final int DATA_MAX_FUEL = 8;
	public static final int DATA_WATER = 9;
	public static final int DATA_MAX_WATER = 10;
	public static final int DATA_LAYOUT_MODE = 11;
	public static final int DATA_LINK_COUNT = 12;
	public static final int DATA_IS_ARMED = 13;
	public static final int DATA_COUNT = 14;
	private static final double MAX_INTERACTION_DISTANCE = 8.0D;

	private final MinecartFurnace furnace;
	private final ContainerData data;
	private final Container inputContainer = new SimpleContainer(2);

	public RadioLocomotiveMenu(final int containerId, final Inventory inventory) {
		this(containerId, inventory, null, new SimpleContainerData(DATA_COUNT));
	}

	public RadioLocomotiveMenu(final int containerId, final Inventory inventory, final MinecartFurnace furnace) {
		this(containerId, inventory, furnace, createData(furnace));
	}

	private RadioLocomotiveMenu(
		final int containerId,
		final Inventory inventory,
		final MinecartFurnace furnace,
		final ContainerData data
	) {
		super(resolveMenuType(), containerId);
		checkContainerDataCount(data, DATA_COUNT);
		this.furnace = furnace;
		this.data = data;

		// Input slots for fuel and water
		this.addSlot(new Slot(this.inputContainer, 0, 68, 100) {
			@Override
			public boolean mayPlace(final ItemStack stack) {
				return MinecartLocomotiveResources.isCookingFuel(stack);
			}

			@Override
			public void set(final ItemStack stack) {
				super.set(stack);
				RadioLocomotiveMenu.this.tryConsumeInputs();
			}
		});

		this.addSlot(new Slot(this.inputContainer, 1, 132, 100) {
			@Override
			public boolean mayPlace(final ItemStack stack) {
				return stack.is(Items.WATER_BUCKET);
			}

			@Override
			public void set(final ItemStack stack) {
				super.set(stack);
				RadioLocomotiveMenu.this.tryConsumeInputs();
			}
		});

		this.addStandardInventorySlots(inventory, 28, 132);
		this.addDataSlots(data);
	}

	@SuppressWarnings("unchecked")
	private static MenuType<RadioLocomotiveMenu> resolveMenuType() {
		Object menu = MinecartChainMod.RADIO_LOCOMOTIVE_MENU;
		if (menu instanceof java.util.function.Supplier<?> supplier) {
			return (MenuType<RadioLocomotiveMenu>) supplier.get();
		}
		return (MenuType<RadioLocomotiveMenu>) menu;
	}

	public void tryConsumeInputs() {
		if (this.furnace == null || this.furnace.level().isClientSide()) {
			return;
		}

		Slot fuelSlot = this.slots.get(0);
		if (fuelSlot.hasItem()) {
			ItemStack fuelStack = fuelSlot.getItem();
			if (MinecartLocomotiveResources.isCookingFuel(fuelStack)) {
				MinecartLocomotiveAccess locAccess = (MinecartLocomotiveAccess) this.furnace;
				if (locAccess.minecartChain$canAcceptLocomotiveFuel(fuelStack)) {
					int added = locAccess.minecartChain$addLocomotiveFuel(fuelStack);
					if (added > 0) {
						fuelStack.shrink(1);
						fuelSlot.setChanged();
						this.furnace.level().playSound(
							null, this.furnace.getX(), this.furnace.getY(), this.furnace.getZ(),
							SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.2F
						);
					}
				}
			}
		}

		Slot waterSlot = this.slots.get(1);
		if (waterSlot.hasItem()) {
			ItemStack waterStack = waterSlot.getItem();
			if (waterStack.is(Items.WATER_BUCKET)) {
				MinecartChainAccess chainAccess = (MinecartChainAccess) this.furnace;
				int accepted = chainAccess.minecartChain$addWaterTicks(
					MinecartLocomotiveResources.WATER_TICKS_PER_BUCKET,
					MinecartLocomotiveResources.MAX_WATER_TICKS
				);
				if (accepted > 0) {
					waterSlot.set(new ItemStack(Items.BUCKET));
					this.furnace.level().playSound(
						null, this.furnace.getX(), this.furnace.getY(), this.furnace.getZ(),
						SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.9F, 1.0F
					);
				}
			}
		}
	}

	public void handleSetJumper(final boolean isReverse, final int bit, final boolean state) {
		if (this.furnace == null || !this.furnace.isAlive() || bit < 0 || bit > 7) {
			return;
		}

		MinecartChainAccess dataAccess = (MinecartChainAccess) this.furnace;
		int current = isReverse ? dataAccess.minecartChain$getReverseChannel() : dataAccess.minecartChain$getThrottleChannel();
		int updated = state ? (current | (1 << bit)) : (current & ~(1 << bit));
		if (!isReverse) {
			dataAccess.minecartChain$setThrottleChannel(updated);
		} else {
			dataAccess.minecartChain$setReverseChannel(updated);
		}

		this.furnace.level().playSound(
			null, this.furnace.getX(), this.furnace.getY(), this.furnace.getZ(),
			SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, state ? 1.15F : 0.85F
		);
		this.broadcastChanges();
	}

	public void setBitLocally(final boolean isReverse, final int bit, final boolean state) {
		if (bit < 0 || bit > 7) {
			return;
		}
		int slot = isReverse ? DATA_REVERSE_CH : DATA_THROTTLE_CH;
		int current = this.data.get(slot) & 0xFF;
		int updated = state ? (current | (1 << bit)) : (current & ~(1 << bit));
		this.data.set(slot, updated);
	}

	public void handleJumperToggle(final boolean isReverse, final int bit) {
		if (this.furnace == null || !this.furnace.isAlive() || bit < 0 || bit > 7) {
			return;
		}

		MinecartChainAccess dataAccess = (MinecartChainAccess) this.furnace;
		int current = isReverse ? dataAccess.minecartChain$getReverseChannel() : dataAccess.minecartChain$getThrottleChannel();
		boolean currentBit = (current & (1 << bit)) != 0;
		handleSetJumper(isReverse, bit, !currentBit);
	}

	public void toggleBitLocally(final boolean isReverse, final int bit) {
		if (bit < 0 || bit > 7) {
			return;
		}
		int slot = isReverse ? DATA_REVERSE_CH : DATA_THROTTLE_CH;
		int current = this.data.get(slot) & 0xFF;
		boolean currentBit = (current & (1 << bit)) != 0;
		setBitLocally(isReverse, bit, !currentBit);
	}

	@Override
	public boolean clickMenuButton(final Player player, final int id) {
		if (id >= 0 && id <= 7) {
			this.handleJumperToggle(false, id);
			return true;
		} else if (id >= 8 && id <= 15) {
			this.handleJumperToggle(true, id - 8);
			return true;
		}
		return false;
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int slotIndex) {
		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot.hasItem()) {
			ItemStack stack = slot.getItem();
			clicked = stack.copy();
			// Slots 0 & 1 are fuel & water inputs
			// Slots 2..28 are player inventory
			// Slots 29..37 are hotbar
			if (slotIndex < 2) {
				if (!this.moveItemStackTo(stack, 2, 38, true)) {
					return ItemStack.EMPTY;
				}
			} else {
				if (MinecartLocomotiveResources.isCookingFuel(stack)) {
					if (!this.moveItemStackTo(stack, 0, 1, false)) {
						return ItemStack.EMPTY;
					}
				} else if (stack.is(Items.WATER_BUCKET)) {
					if (!this.moveItemStackTo(stack, 1, 2, false)) {
						return ItemStack.EMPTY;
					}
				} else if (slotIndex < 29) {
					if (!this.moveItemStackTo(stack, 29, 38, false)) {
						return ItemStack.EMPTY;
					}
				} else if (!this.moveItemStackTo(stack, 2, 29, false)) {
					return ItemStack.EMPTY;
				}
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
	public void removed(final Player player) {
		super.removed(player);
		this.clearContainer(player, this.inputContainer);
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.furnace == null
			|| (this.furnace.isAlive()
			&& !this.furnace.isRemoved()
			&& player.distanceToSqr(this.furnace) <= MAX_INTERACTION_DISTANCE * MAX_INTERACTION_DISTANCE);
	}

	public int throttleChannel() {
		return this.data.get(DATA_THROTTLE_CH) & 0xFF;
	}

	public int reverseChannel() {
		return this.data.get(DATA_REVERSE_CH) & 0xFF;
	}

	public int throttlePower() {
		return this.data.get(DATA_THROTTLE_PWR);
	}

	public int reversePower() {
		return this.data.get(DATA_REVERSE_PWR);
	}

	public boolean isReversed() {
		return this.data.get(DATA_IS_REVERSED) != 0;
	}

	public boolean isStopped() {
		return this.data.get(DATA_IS_STOPPED) != 0;
	}

	public boolean isReversePending() {
		return this.data.get(DATA_REVERSE_PENDING) != 0;
	}

	public boolean isLayoutMode() {
		return this.data.get(DATA_LAYOUT_MODE) != 0;
	}

	public int linkCount() {
		return this.data.get(DATA_LINK_COUNT);
	}

	public boolean isArmed() {
		return this.data.get(DATA_IS_ARMED) != 0;
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
		int max = maxFuelTicks();
		return max <= 0 ? 0.0F : Math.min(1.0F, Math.max(0.0F, (float) fuelTicks() / max));
	}

	public float waterProgress() {
		int max = maxWaterTicks();
		return max <= 0 ? 0.0F : Math.min(1.0F, Math.max(0.0F, (float) waterTicks() / max));
	}

	private static ContainerData createData(final MinecartFurnace furnace) {
		return new ContainerData() {
			@Override
			public int get(final int dataId) {
				MinecartChainAccess chain = (MinecartChainAccess) furnace;
				MinecartLocomotiveAccess loc = (MinecartLocomotiveAccess) furnace;
				return switch (dataId) {
					case DATA_THROTTLE_CH -> chain.minecartChain$getThrottleChannel() & 0xFF;
					case DATA_REVERSE_CH -> chain.minecartChain$getReverseChannel() & 0xFF;
					case DATA_THROTTLE_PWR -> chain.minecartChain$getReceivedThrottlePower();
					case DATA_REVERSE_PWR -> chain.minecartChain$getReceivedReversePower();
					case DATA_IS_REVERSED -> chain.minecartChain$isReversed() ? 1 : 0;
					case DATA_IS_STOPPED -> MinecartTrainLogic.isTrainStopped(furnace) ? 1 : 0;
					case DATA_REVERSE_PENDING -> chain.minecartChain$isReversePending() ? 1 : 0;
					case DATA_FUEL -> loc.minecartChain$getLocomotiveFuelTicks();
					case DATA_MAX_FUEL -> MinecartLocomotiveResources.MAX_FUEL_TICKS;
					case DATA_WATER -> chain.minecartChain$getWaterTicks();
					case DATA_MAX_WATER -> MinecartLocomotiveResources.MAX_WATER_TICKS;
					case DATA_LAYOUT_MODE -> MinecartChainConfig.layoutMode ? 1 : 0;
					case DATA_LINK_COUNT -> chain.minecartChain$linkCount();
					case DATA_IS_ARMED -> chain.minecartChain$isArmed() ? 1 : 0;
					default -> 0;
				};
			}

			@Override
			public void set(final int dataId, final int value) {
				MinecartChainAccess chain = (MinecartChainAccess) furnace;
				switch (dataId) {
					case DATA_THROTTLE_CH -> chain.minecartChain$setThrottleChannel(value);
					case DATA_REVERSE_CH -> chain.minecartChain$setReverseChannel(value);
					case DATA_THROTTLE_PWR -> chain.minecartChain$setReceivedThrottlePower(value);
					case DATA_REVERSE_PWR -> chain.minecartChain$setReceivedReversePower(value);
					case DATA_IS_REVERSED -> chain.minecartChain$setReversed(value != 0);
					case DATA_REVERSE_PENDING -> chain.minecartChain$setReversePending(value != 0);
				}
			}

			@Override
			public int getCount() {
				return DATA_COUNT;
			}
		};
	}
}
