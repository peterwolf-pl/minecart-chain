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
	private static final int OCCUPANCY_TIMEOUT_TICKS = 15;
	private static final int ACTIVATION_CLEANUP_TICKS = 1200;
	private static final Map<ActivationKey, Occupancy> OCCUPANCIES = new HashMap<>();

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
		if (!shouldPower && level instanceof ServerLevel serverLevel) {
			releaseHeldBrakes(serverLevel, GlobalPos.of(level.dimension(), pos));
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(
		final BlockState state,
		final ServerLevel level,
		final BlockPos pos,
		final boolean movedByPiston
	) {
		releaseHeldBrakes(level, GlobalPos.of(level.dimension(), pos));
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SHAPE, POWERED, WATERLOGGED, MODE);
	}

	private void applySignal(final ServerLevel level, final BlockPos pos, final SignalMode mode, final MinecartFurnace locomotive) {
		long gameTime = level.getGameTime();
		cleanupOccupancies(gameTime);

		ActivationKey key = new ActivationKey(
			GlobalPos.of(level.dimension(), pos),
			MinecartTrainLogic.connectedTrainKey(level, locomotive)
		);
		Occupancy occupancy = OCCUPANCIES.get(key);
		boolean occupying = occupancy != null && gameTime - occupancy.lastSeenGameTime < OCCUPANCY_TIMEOUT_TICKS;

		if (mode == SignalMode.BRAKE) {
			this.holdBrake(level, pos, key, locomotive, occupancy, occupying, gameTime);
			return;
		}

		if (occupying) {
			occupancy.lastSeenGameTime = gameTime;
			return;
		}

		// Reverse only works while the train is stopped. Do not mark occupancy
		// until the lever actually toggles, otherwise a moving pass locks the rail
		// and the train cannot reverse once it does stop.
		if (mode == SignalMode.REVERSE && !MinecartTrainLogic.isTrainStopped(locomotive)) {
			return;
		}

		MinecartChainAccess data = (MinecartChainAccess) locomotive;
		float pitch = mode.pitch();
		if (mode == SignalMode.THROTTLE) {
			boolean fullThrottle = !data.minecartChain$isFullThrottle();
			data.minecartChain$setFullThrottle(fullThrottle);
			pitch = fullThrottle ? 0.7F : 0.55F;
		} else {
			boolean reversed = !data.minecartChain$isReversed();
			data.minecartChain$setReversed(reversed);
			MinecartTrainLogic.snapLocomotiveYaw(locomotive, MinecartTrainLogic.engineDirection(locomotive));
			pitch = reversed ? 0.75F : 0.6F;
		}

		OCCUPANCIES.put(key, new Occupancy(locomotive.getUUID(), gameTime, false));
		level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.9F, pitch);
	}

	private void holdBrake(
		final ServerLevel level,
		final BlockPos pos,
		final ActivationKey key,
		final MinecartFurnace locomotive,
		final Occupancy occupancy,
		final boolean occupying,
		final long gameTime
	) {
		MinecartChainAccess data = (MinecartChainAccess) locomotive;
		boolean alreadyBraked = data.minecartChain$isEngineActive();
		if (!alreadyBraked) {
			data.minecartChain$setEngineActive(true);
			level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.9F, 0.5F);
		}
		MinecartTrainLogic.applyTrainBrake(locomotive);
		if (occupying) {
			occupancy.lastSeenGameTime = gameTime;
			occupancy.locomotiveId = locomotive.getUUID();
			occupancy.brakeHeld = true;
			return;
		}
		OCCUPANCIES.put(key, new Occupancy(locomotive.getUUID(), gameTime, true));
	}

	private static void releaseHeldBrakes(final ServerLevel level, final GlobalPos railPos) {
		Iterator<Map.Entry<ActivationKey, Occupancy>> iterator = OCCUPANCIES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<ActivationKey, Occupancy> entry = iterator.next();
			if (!entry.getKey().railPos().equals(railPos)) {
				continue;
			}

			Occupancy occupancy = entry.getValue();
			if (occupancy.brakeHeld) {
				Entity entity = level.getEntityInAnyDimension(occupancy.locomotiveId);
				if (entity instanceof MinecartFurnace locomotive) {
					MinecartChainAccess data = (MinecartChainAccess) locomotive;
					if (data.minecartChain$hasEngineLever() && data.minecartChain$isEngineActive()) {
						data.minecartChain$setEngineActive(false);
						MinecartTrainLogic.startConsistForward(locomotive);
						level.playSound(
							null,
							locomotive.blockPosition(),
							SoundEvents.LEVER_CLICK,
							SoundSource.BLOCKS,
							0.8F,
							0.6F
						);
					}
				}
			}
			iterator.remove();
		}
	}

	private static void cleanupOccupancies(final long gameTime) {
		if (OCCUPANCIES.size() < 512 && gameTime % 200L != 0L) {
			return;
		}

		OCCUPANCIES.entrySet().removeIf(entry ->
			!entry.getValue().brakeHeld && gameTime - entry.getValue().lastSeenGameTime > ACTIVATION_CLEANUP_TICKS
		);
	}

	static void clearRuntimeState() {
		OCCUPANCIES.clear();
	}

	private record ActivationKey(GlobalPos railPos, UUID trainKey) {
	}

	private static final class Occupancy {
		private UUID locomotiveId;
		private long lastSeenGameTime;
		private boolean brakeHeld;

		private Occupancy(final UUID locomotiveId, final long lastSeenGameTime, final boolean brakeHeld) {
			this.locomotiveId = locomotiveId;
			this.lastSeenGameTime = lastSeenGameTime;
			this.brakeHeld = brakeHeld;
		}
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
