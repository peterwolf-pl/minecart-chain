package com.piotrek.minecartchain;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class SignalRailBlock extends PoweredRailBlock {
	public static final EnumProperty<SignalMode> MODE = EnumProperty.create("mode", SignalMode.class);
	private static final int SAME_TRAIN_REPEAT_DELAY_TICKS = 100;
	private static final int ACTIVATION_CLEANUP_TICKS = 1200;
	private static final Map<ActivationKey, Long> ACTIVATIONS = new HashMap<>();

	public SignalRailBlock(final BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(MODE, SignalMode.THROTTLE));
	}

	@Override
	protected InteractionResult useWithoutItem(
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Player player,
		final BlockHitResult hitResult
	) {
		SignalMode nextMode = state.getValue(MODE).next();
		if (!level.isClientSide()) {
			level.setBlock(pos, state.setValue(MODE, nextMode), 3);
			level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.65F, nextMode.pitch());
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.translatable(
					"message.minecart_chain.signal_rail.mode",
					Component.translatable(nextMode.translationKey())
				));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void entityInside(
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Entity entity,
		final InsideBlockEffectApplier effectApplier,
		final boolean isPrecise
	) {
		if (
			level.isClientSide()
				|| !state.getValue(POWERED)
				|| !(level instanceof ServerLevel serverLevel)
				|| !(entity instanceof AbstractMinecart minecart)
		) {
			return;
		}

		MinecartTrainLogic.controlledLocomotive(serverLevel, minecart)
			.ifPresent(locomotive -> this.applySignal(serverLevel, pos, state.getValue(MODE), locomotive));
	}

	@Override
	protected void updateState(final BlockState state, final Level level, final BlockPos pos, final Block block) {
		boolean isPowered = state.getValue(POWERED);
		boolean shouldPower = level.hasNeighborSignal(pos);
		if (shouldPower == isPowered) {
			return;
		}

		BlockState newState = state.setValue(POWERED, shouldPower);
		level.setBlock(pos, newState, 3);
		level.updateNeighborsAt(pos.below(), this);
		if (state.getValue(SHAPE).isSlope()) {
			level.updateNeighborsAt(pos.above(), this);
		}
		if (!shouldPower) {
			clearActivations(GlobalPos.of(level.dimension(), pos));
		}
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SHAPE, POWERED, WATERLOGGED, MODE);
	}

	private void applySignal(final ServerLevel level, final BlockPos pos, final SignalMode mode, final MinecartFurnace locomotive) {
		long gameTime = level.getGameTime();
		cleanupActivations(gameTime);

		ActivationKey key = new ActivationKey(GlobalPos.of(level.dimension(), pos), locomotive.getUUID());
		Long previousTick = ACTIVATIONS.get(key);
		ACTIVATIONS.put(key, gameTime);
		if (previousTick != null && gameTime - previousTick < SAME_TRAIN_REPEAT_DELAY_TICKS) {
			return;
		}

		MinecartChainAccess data = (MinecartChainAccess) locomotive;
		float pitch = mode.pitch();
		switch (mode) {
			case THROTTLE -> {
				boolean fullThrottle = !data.minecartChain$isFullThrottle();
				data.minecartChain$setFullThrottle(fullThrottle);
				pitch = fullThrottle ? 0.7F : 0.55F;
			}
			case BRAKE -> {
				boolean brake = !data.minecartChain$isEngineActive();
				data.minecartChain$setEngineActive(brake);
				pitch = brake ? 0.5F : 0.6F;
			}
			case REVERSE -> {
				boolean reversed = !data.minecartChain$isReversed();
				data.minecartChain$setReversed(reversed);
				MinecartTrainLogic.snapLocomotiveYaw(locomotive, MinecartTrainLogic.drivingDirection(locomotive));
				pitch = reversed ? 0.75F : 0.6F;
			}
		}

		level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.9F, pitch);
	}

	private static void clearActivations(final GlobalPos railPos) {
		ACTIVATIONS.keySet().removeIf(key -> key.railPos().equals(railPos));
	}

	private static void cleanupActivations(final long gameTime) {
		if (ACTIVATIONS.size() < 512 && gameTime % 200L != 0L) {
			return;
		}

		Iterator<Map.Entry<ActivationKey, Long>> iterator = ACTIVATIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<ActivationKey, Long> entry = iterator.next();
			if (gameTime - entry.getValue() > ACTIVATION_CLEANUP_TICKS) {
				iterator.remove();
			}
		}
	}

	private record ActivationKey(GlobalPos railPos, UUID locomotiveId) {
	}

	public enum SignalMode implements StringRepresentable {
		THROTTLE("throttle", 0.7F),
		BRAKE("brake", 0.5F),
		REVERSE("reverse", 0.75F);

		private final String serializedName;
		private final float pitch;

		SignalMode(final String serializedName, final float pitch) {
			this.serializedName = serializedName;
			this.pitch = pitch;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}

		public SignalMode next() {
			return switch (this) {
				case THROTTLE -> BRAKE;
				case BRAKE -> REVERSE;
				case REVERSE -> THROTTLE;
			};
		}

		private float pitch() {
			return this.pitch;
		}

		private String translationKey() {
			return "message.minecart_chain.signal_rail.mode." + this.serializedName;
		}
	}
}
