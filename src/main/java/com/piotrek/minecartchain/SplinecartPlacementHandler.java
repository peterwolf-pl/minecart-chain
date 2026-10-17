package com.piotrek.minecartchain;

import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import io.github.foundationgames.splinecart.util.SplineSegment;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/**
 * Handles placing all kinds of locomotives and carts onto Splinecart track ties.
 * Supports straight, curved, sloped, and banked spline segments.
 */
public final class SplinecartPlacementHandler {
	private static volatile Field minecartTypeField;

	private SplinecartPlacementHandler() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(SplinecartPlacementHandler::handleUse);
	}

	public static boolean isTrainOrCartItem(final ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		Item item = stack.getItem();
		return item instanceof SteamLocomotiveItem
			|| item instanceof RadioSteamLocomotiveItem
			|| item instanceof SteamLocomotiveWithTenderItem
			|| item instanceof RadioLocomotiveItem
			|| item instanceof CoalTenderItem
			|| item instanceof LongMinecartItem
			|| item instanceof MinecartItem;
	}

	public static InteractionResult handleUse(
		final Player player,
		final Level world,
		final InteractionHand hand,
		final BlockHitResult hitResult
	) {
		ItemStack stack = player.getItemInHand(hand);
		if (!isTrainOrCartItem(stack)) {
			return InteractionResult.PASS;
		}

		BlockPos pos = hitResult.getBlockPos();
		BlockEntity be = world.getBlockEntity(pos);
		if (!(be instanceof TrackTiesBlockEntity tie)) {
			return InteractionResult.PASS;
		}

		List<SplineSegment> candidates = getConnectedSegments(tie);
		if (candidates.isEmpty()) {
			return InteractionResult.PASS;
		}

		Vector3d hitPoint = new Vector3d(hitResult.getLocation().x(), hitResult.getLocation().y(), hitResult.getLocation().z());
		Vec3 playerLook = player.getLookAngle().horizontal();
		SelectedSegment selected = selectBestSegment(candidates, hitPoint, playerLook);
		if (selected == null) {
			return InteractionResult.PASS;
		}

		if (world.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(world instanceof ServerLevel serverLevel)) {
			return InteractionResult.FAIL;
		}

		SplineSegment segment = selected.segment;
		double progress = selected.progress;
		Vector3d spawn = selected.spawn;
		Vector3d tangent = selected.tangent;
		float yaw = (float) Math.toDegrees(Math.atan2(-tangent.x(), tangent.z()));

		boolean placed = placeVehicle(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		if (!placed) {
			return InteractionResult.FAIL;
		}

		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}

		BlockPos spawnPos = BlockPos.containing(spawn.x(), spawn.y(), spawn.z());
		serverLevel.playSound(null, spawnPos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
		serverLevel.gameEvent(GameEvent.ENTITY_PLACE, spawnPos, GameEvent.Context.of(player, serverLevel.getBlockState(pos)));

		return InteractionResult.SUCCESS;
	}

	private static List<SplineSegment> getConnectedSegments(final TrackTiesBlockEntity tie) {
		List<SplineSegment> candidates = new ArrayList<>(4);
		TrackTiesBlockEntity next = tie.next();
		if (next != null) {
			candidates.add(new SplineSegment(tie, next, tie.nextBanking()));
		}
		TrackTiesBlockEntity nextAlt = tie.nextAlt();
		if (nextAlt != null) {
			candidates.add(new SplineSegment(tie, nextAlt, tie.nextAltBanking()));
		}
		TrackTiesBlockEntity prev = tie.prev();
		if (prev != null) {
			candidates.add(new SplineSegment(prev, tie, prev.nextBanking()));
		}
		TrackTiesBlockEntity prevAlt = tie.prevAlt();
		if (prevAlt != null) {
			boolean banking = tie.getBlockPos().equals(prevAlt.nextAltPos()) ? prevAlt.nextAltBanking() : prevAlt.nextBanking();
			candidates.add(new SplineSegment(prevAlt, tie, banking));
		}
		return candidates;
	}

	private static double findNearestProgress(final SplineSegment segment, final Vector3d hitPoint) {
		int samples = 20;
		double bestT = 0.0;
		double minDistanceSq = Double.MAX_VALUE;
		Vector3d tempPos = new Vector3d();
		Matrix3d dummyBasis = new Matrix3d();
		Vector3d dummyDeriv = new Vector3d();

		for (int i = 0; i <= samples; i++) {
			double t = (double) i / samples;
			segment.interpolate(t, tempPos, dummyBasis, dummyDeriv);
			double distSq = tempPos.distanceSquared(hitPoint);
			if (distSq < minDistanceSq) {
				minDistanceSq = distSq;
				bestT = t;
			}
		}

		double low = Math.max(0.0D, bestT - (1.0D / samples));
		double high = Math.min(1.0D, bestT + (1.0D / samples));
		for (int step = 0; step < 8; step++) {
			double m1 = low + (high - low) / 3.0D;
			double m2 = high - (high - low) / 3.0D;
			segment.interpolate(m1, tempPos, dummyBasis, dummyDeriv);
			double d1 = tempPos.distanceSquared(hitPoint);
			segment.interpolate(m2, tempPos, dummyBasis, dummyDeriv);
			double d2 = tempPos.distanceSquared(hitPoint);
			if (d1 < d2) {
				high = m2;
			} else {
				low = m1;
			}
		}
		return Math.clamp((low + high) * 0.5D, 0.01D, 0.99D);
	}

	private record SelectedSegment(SplineSegment segment, double progress, Vector3d spawn, Vector3d tangent) {
	}

	private static SelectedSegment selectBestSegment(
		final List<SplineSegment> candidates,
		final Vector3d hitPoint,
		final Vec3 playerLook
	) {
		SplineSegment bestSeg = null;
		double bestProg = 0.0D;
		double bestScore = Double.MAX_VALUE;
		Vector3d bestSpawn = new Vector3d();
		Vector3d bestTangent = new Vector3d();

		Vector3d tempPos = new Vector3d();
		Matrix3d tempBasis = new Matrix3d();
		Vector3d tempDeriv = new Vector3d();

		for (SplineSegment candidate : candidates) {
			double t = findNearestProgress(candidate, hitPoint);
			candidate.interpolate(t, tempPos, tempBasis, tempDeriv);
			double distSq = tempPos.distanceSquared(hitPoint);

			Vector3d normTangent = new Vector3d(tempDeriv).normalize();
			double lookDot = (playerLook.x * normTangent.x() + playerLook.z * normTangent.z());
			double score = distSq - (lookDot > 0.0D ? 0.1D : 0.0D);

			if (score < bestScore) {
				bestScore = score;
				bestSeg = candidate;
				bestProg = t;
				bestSpawn.set(tempPos);
				bestTangent.set(normTangent);
			}
		}

		if (bestSeg == null) {
			return null;
		}
		return new SelectedSegment(bestSeg, bestProg, bestSpawn, bestTangent);
	}

	private static boolean placeVehicle(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		Item item = stack.getItem();

		if (item instanceof SteamLocomotiveWithTenderItem) {
			return placeLocomotiveWithTender(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		} else if (item instanceof RadioSteamLocomotiveItem) {
			return placeRadioSteamLocomotive(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		} else if (item instanceof SteamLocomotiveItem) {
			return placeSteamLocomotive(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		} else if (item instanceof RadioLocomotiveItem) {
			return placeRadioFurnace(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		} else if (item instanceof CoalTenderItem) {
			return placeCoalTender(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		} else if (item instanceof LongMinecartItem) {
			return placeLongMinecart(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		} else if (item instanceof MinecartItem) {
			return placeVanillaOrModMinecart(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		}

		return false;
	}

	private static boolean placeSteamLocomotive(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		SteamLocomotiveEntity loco = (SteamLocomotiveEntity) AbstractMinecart.createMinecart(
			serverLevel,
			spawn.x(), spawn.y(), spawn.z(),
			MinecartChainMod.STEAM_LOCOMOTIVE_ENTITY,
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
		if (loco == null) {
			return false;
		}

		loco.setFacingYaw(yaw);
		loco.setYRot(yaw);
		loco.yRotO = yaw;
		loco.setYHeadRot(yaw);
		loco.setYBodyRot(yaw);
		MinecartChainAccess locoData = (MinecartChainAccess) loco;
		locoData.minecartChain$setLocomotiveYaw(yaw);
		locoData.minecartChain$setHasLocomotiveYaw(true);

		TrackFollowerEntity follower = TrackFollowerEntity.createPlaced(
			serverLevel,
			segment.start().getBlockPos(),
			segment.end().getBlockPos(),
			progress
		);
		if (follower == null) {
			return false;
		}

		serverLevel.addFreshEntity(follower);
		serverLevel.addFreshEntity(loco);
		loco.startRiding(follower, true, false);
		return true;
	}

	private static boolean placeRadioSteamLocomotive(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		SteamLocomotiveEntity loco = (SteamLocomotiveEntity) AbstractMinecart.createMinecart(
			serverLevel,
			spawn.x(), spawn.y(), spawn.z(),
			MinecartChainMod.STEAM_LOCOMOTIVE_ENTITY,
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
		if (loco == null) {
			return false;
		}

		MinecartChainAccess data = (MinecartChainAccess) loco;
		data.minecartChain$setRadioLocomotive(true);
		data.minecartChain$setEngineLever(true);
		data.minecartChain$setEngineActive(false);
		data.minecartChain$setFullThrottle(false);
		data.minecartChain$setReversed(false);
		data.minecartChain$setReversePending(false);
		data.minecartChain$setThrottleChannel(0);
		data.minecartChain$setReverseChannel(1);
		data.minecartChain$setArmed(false);

		loco.setFacingYaw(yaw);
		loco.setYRot(yaw);
		loco.yRotO = yaw;
		loco.setYHeadRot(yaw);
		loco.setYBodyRot(yaw);
		data.minecartChain$setLocomotiveYaw(yaw);
		data.minecartChain$setHasLocomotiveYaw(true);

		TrackFollowerEntity follower = TrackFollowerEntity.createPlaced(
			serverLevel,
			segment.start().getBlockPos(),
			segment.end().getBlockPos(),
			progress
		);
		if (follower == null) {
			return false;
		}

		serverLevel.addFreshEntity(follower);
		serverLevel.addFreshEntity(loco);
		loco.startRiding(follower, true, false);
		return true;
	}

	private static boolean placeLocomotiveWithTender(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		SteamLocomotiveEntity loco = (SteamLocomotiveEntity) AbstractMinecart.createMinecart(
			serverLevel,
			spawn.x(), spawn.y(), spawn.z(),
			MinecartChainMod.STEAM_LOCOMOTIVE_ENTITY,
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
		if (loco == null) {
			return false;
		}

		loco.setFacingYaw(yaw);
		loco.setYRot(yaw);
		loco.yRotO = yaw;
		loco.setYHeadRot(yaw);
		loco.setYBodyRot(yaw);
		MinecartChainAccess locoData = (MinecartChainAccess) loco;
		locoData.minecartChain$setLocomotiveYaw(yaw);
		locoData.minecartChain$setHasLocomotiveYaw(true);

		TrackFollowerEntity locoFollower = TrackFollowerEntity.createPlaced(
			serverLevel,
			segment.start().getBlockPos(),
			segment.end().getBlockPos(),
			progress
		);
		if (locoFollower == null) {
			return false;
		}

		// Walk actual spline samples behind the cab. Progress is not arc length;
		// clamping it to the first tie used to stack both vehicles on that tie.
		SplineSegment tenderSegment = segment;
		double tenderProg = progress;
		Vector3d tenderSpawn = new Vector3d(spawn);
		Vector3d previousSample = new Vector3d(spawn);
		double walked = 0.0D;
		boolean room = false;
		for (int sample = 0; sample < 16000; sample++) {
			if (tenderProg <= 0.0D) {
				TrackTiesBlockEntity prevTie = tenderSegment.start().prev();
				if (prevTie == null) {
					return false; // No complete rear track: do not spawn overlapping vehicles.
				}
				tenderSegment = new SplineSegment(prevTie, tenderSegment.start(), prevTie.nextBanking());
				tenderProg = 1.0D;
			}
			tenderProg = Math.max(0.0D, tenderProg - 0.002D);
			tenderSegment.interpolate(tenderProg, tenderSpawn, new Matrix3d(), new Vector3d());
			walked += tenderSpawn.distance(previousSample);
			previousSample.set(tenderSpawn);
			double dx = tenderSpawn.x() - spawn.x();
			double dz = tenderSpawn.z() - spawn.z();
			if (walked >= SteamLocomotiveEntity.HINGE_DISTANCE
				&& Math.hypot(dx, dz) >= SteamLocomotiveEntity.HINGE_DISTANCE
				&& dx * tangent.x() + dz * tangent.z() < 0.0D) {
				room = true;
				break;
			}
		}
		if (!room) {
			return false;
		}

		CoalTenderEntity tender = (CoalTenderEntity) AbstractMinecart.createMinecart(
			serverLevel,
			tenderSpawn.x(), tenderSpawn.y(), tenderSpawn.z(),
			MinecartChainMod.COAL_TENDER_ENTITY,
			EntitySpawnReason.SPAWN_ITEM_USE,
			ItemStack.EMPTY,
			player
		);
		if (tender == null) {
			return false;
		}

		tender.setYRot(yaw);
		tender.yRotO = yaw;
		tender.setYHeadRot(yaw);
		tender.setYBodyRot(yaw);

		TrackFollowerEntity tenderFollower = TrackFollowerEntity.createPlaced(
			serverLevel,
			tenderSegment.start().getBlockPos(),
			tenderSegment.end().getBlockPos(),
			tenderProg
		);
		if (tenderFollower == null) {
			return false;
		}

		loco.setPermanentTender(tender.getUUID());
		tender.setPermanentLocomotive(loco.getUUID());
		loco.initBreadcrumbs(tender.position(), loco.position());

		MinecartChainAccess tenderData = (MinecartChainAccess) tender;
		locoData.minecartChain$addLink(tender.getUUID());
		tenderData.minecartChain$addLink(loco.getUUID());

		serverLevel.addFreshEntity(locoFollower);
		serverLevel.addFreshEntity(loco);
		loco.startRiding(locoFollower, true, false);

		serverLevel.addFreshEntity(tenderFollower);
		serverLevel.addFreshEntity(tender);
		tender.startRiding(tenderFollower, true, false);

		return true;
	}

	private static boolean placeRadioFurnace(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		MinecartFurnace furnace = (MinecartFurnace) AbstractMinecart.createMinecart(
			serverLevel,
			spawn.x(), spawn.y(), spawn.z(),
			EntityTypes.FURNACE_MINECART,
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
		if (furnace == null) {
			return false;
		}

		MinecartChainAccess data = (MinecartChainAccess) furnace;
		data.minecartChain$setRadioLocomotive(true);
		data.minecartChain$setEngineLever(true);
		data.minecartChain$setEngineActive(false);
		data.minecartChain$setFullThrottle(false);
		data.minecartChain$setReversed(false);
		data.minecartChain$setReversePending(false);
		data.minecartChain$setThrottleChannel(0);
		data.minecartChain$setReverseChannel(1);
		data.minecartChain$setArmed(false);

		Vec3 facing = new Vec3(tangent.x(), 0.0D, tangent.z()).normalize();
		MinecartTrainLogic.snapLocomotiveYaw(furnace, facing);

		TrackFollowerEntity follower = TrackFollowerEntity.createPlaced(
			serverLevel,
			segment.start().getBlockPos(),
			segment.end().getBlockPos(),
			progress
		);
		if (follower == null) {
			return false;
		}

		serverLevel.addFreshEntity(follower);
		serverLevel.addFreshEntity(furnace);
		furnace.startRiding(follower, true, false);
		return true;
	}

	private static boolean placeCoalTender(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		CoalTenderEntity tender = (CoalTenderEntity) AbstractMinecart.createMinecart(
			serverLevel,
			spawn.x(), spawn.y(), spawn.z(),
			MinecartChainMod.COAL_TENDER_ENTITY,
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
		if (tender == null) {
			return false;
		}

		tender.setYRot(yaw);
		tender.yRotO = yaw;
		tender.setYHeadRot(yaw);
		tender.setYBodyRot(yaw);

		TrackFollowerEntity follower = TrackFollowerEntity.createPlaced(
			serverLevel,
			segment.start().getBlockPos(),
			segment.end().getBlockPos(),
			progress
		);
		if (follower == null) {
			return false;
		}

		serverLevel.addFreshEntity(follower);
		serverLevel.addFreshEntity(tender);
		tender.startRiding(follower, true, false);
		return true;
	}

	private static boolean placeLongMinecart(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		AbstractMinecart cart = AbstractMinecart.createMinecart(
			serverLevel,
			spawn.x(), spawn.y(), spawn.z(),
			((LongMinecartItem) stack.getItem()).cartType(),
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
		if (cart == null) {
			return false;
		}

		cart.setYRot(yaw);
		cart.yRotO = yaw;

		TrackFollowerEntity follower = TrackFollowerEntity.createPlaced(
			serverLevel,
			segment.start().getBlockPos(),
			segment.end().getBlockPos(),
			progress
		);
		if (follower == null) {
			return false;
		}

		serverLevel.addFreshEntity(follower);
		serverLevel.addFreshEntity(cart);
		cart.startRiding(follower, true, false);
		return true;
	}

	private static boolean placeVanillaOrModMinecart(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		EntityType<? extends AbstractMinecart> cartType = getMinecartType(stack.getItem());
		if (cartType == null) {
			return false;
		}

		AbstractMinecart cart = AbstractMinecart.createMinecart(
			serverLevel,
			spawn.x(), spawn.y(), spawn.z(),
			cartType,
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
		if (cart == null) {
			return false;
		}

		cart.absSnapRotationTo(yaw, 0.0F);

		TrackFollowerEntity follower = TrackFollowerEntity.createPlaced(
			serverLevel,
			segment.start().getBlockPos(),
			segment.end().getBlockPos(),
			progress
		);
		if (follower == null) {
			return false;
		}

		serverLevel.addFreshEntity(follower);
		serverLevel.addFreshEntity(cart);
		cart.startRiding(follower, true, false);
		return true;
	}

	@SuppressWarnings("unchecked")
	private static EntityType<? extends AbstractMinecart> getMinecartType(final Item item) {
		if (item == Items.MINECART) {
			return EntityTypes.MINECART;
		}
		if (item == Items.CHEST_MINECART) {
			return EntityTypes.CHEST_MINECART;
		}
		if (item == Items.FURNACE_MINECART) {
			return EntityTypes.FURNACE_MINECART;
		}
		if (item == Items.TNT_MINECART) {
			return EntityTypes.TNT_MINECART;
		}
		if (item == Items.HOPPER_MINECART) {
			return EntityTypes.HOPPER_MINECART;
		}
		if (item == Items.COMMAND_BLOCK_MINECART) {
			return EntityTypes.COMMAND_BLOCK_MINECART;
		}

		if (item instanceof MinecartItem) {
			try {
				if (minecartTypeField == null) {
					for (Field f : MinecartItem.class.getDeclaredFields()) {
						if (EntityType.class.isAssignableFrom(f.getType())) {
							minecartTypeField = f;
							minecartTypeField.setAccessible(true);
							break;
						}
					}
				}
				if (minecartTypeField != null) {
					return (EntityType<? extends AbstractMinecart>) minecartTypeField.get(item);
				}
			} catch (Throwable ignored) {
			}
		}

		return null;
	}
}
